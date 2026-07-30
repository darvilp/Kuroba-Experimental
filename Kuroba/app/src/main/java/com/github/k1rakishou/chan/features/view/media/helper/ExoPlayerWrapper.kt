package com.github.k1rakishou.chan.features.view.media.helper

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.github.k1rakishou.chan.core.concurrency.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.features.view.media.MediaLocation
import com.github.k1rakishou.chan.features.view.media.ViewableMedia
import com.github.k1rakishou.chan.features.view.media.element.MediaViewContract
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.file.ExternalFile
import com.github.k1rakishou.fsaf.file.RawFile
import com.github.k1rakishou.v2.KurobaSettings
import com.github.k1rakishou.v2.parameters.VideoEndBehavior
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


@OptIn(UnstableApi::class)
class ExoPlayerWrapper(
  private val context: Context,
  private val kurobaSettings: KurobaSettings,
  private val threadDownloadManager: ThreadDownloadManager,
  private val cachedHttpDataSourceFactory: DataSource.Factory,
  private val fileDataSourceFactory: DataSource.Factory,
  private val contentDataSourceFactory: DataSource.Factory,
  private val mediaViewContract: MediaViewContract,
  private val onAudioDetected: () -> Unit,
  private val onPlaybackEnded: (VideoEndBehavior) -> Unit = {}
) {
  private val scope = KurobaCoroutineScope()
  private val reusableExoPlayerLazy = lazy { getOrCreateExoPlayer() }
  private val reusableExoPlayer: ReusableExoPlayer
    get() = reusableExoPlayerLazy.value
  val actualExoPlayer by lazy { reusableExoPlayer.exoPlayer }

  private var timelineUpdateJob: Job? = null
  private var cancelActivePreload: (() -> Unit)? = null
  private var firstFrameListener: Player.Listener? = null
  private var audioDetectionListener: AnalyticsListener? = null

  private var _hasContent = false
  val hasContent: Boolean
    get() = _hasContent

  private var firstFrameRendered: CompletableDeferred<MediaLocation>? = null
  private var activeVideoEndBehavior = VideoEndBehavior.Loop

  private val playbackStateListener = object : Player.Listener {
    override fun onPlaybackStateChanged(state: Int) {
      if (state == Player.STATE_ENDED) {
        onPlaybackEnded(activeVideoEndBehavior)
      }
    }
  }

  private val _positionAndDurationFlow = MutableStateFlow(Pair(0L, 0L))
  val positionAndDurationFlow: StateFlow<Pair<Long, Long>>
    get() = _positionAndDurationFlow.asStateFlow()

  suspend fun preload(
    viewableMedia: ViewableMedia,
    mediaLocation: MediaLocation,
    prevPosition: Long,
    prevWindowIndex: Int
  ) {
    coroutineScope {
      val mediaSource = createMediaSource(viewableMedia, mediaLocation)

      clearPlaybackListeners()
      actualExoPlayer.stop()
      actualExoPlayer.playWhenReady = false
      actualExoPlayer.setMediaSource(mediaSource)

      if (prevWindowIndex >= 0 && prevPosition >= 0) {
        actualExoPlayer.seekTo(prevWindowIndex, prevPosition)
      }

      actualExoPlayer.prepare()

      val prevRenderedVideo = firstFrameRendered
      val shouldRecreateDeferred = prevRenderedVideo == null || !prevRenderedVideo.isCompleted
        || (prevRenderedVideo.isCompleted && prevRenderedVideo.getCompleted() != mediaLocation)

      if (shouldRecreateDeferred) {
        firstFrameRendered?.cancel()
        firstFrameRendered = CompletableDeferred()
      }

      firstFrameListener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
          firstFrameRendered?.complete(mediaLocation)
          clearFirstFrameListener(this)
        }
      }
      actualExoPlayer.addListener(requireNotNull(firstFrameListener))

      audioDetectionListener = object : AnalyticsListener {
        override fun onAudioEnabled(eventTime: AnalyticsListener.EventTime, counters: DecoderCounters) {
          onAudioDetected()
          clearAudioDetectionListener(this)
        }
      }
      actualExoPlayer.addAnalyticsListener(requireNotNull(audioDetectionListener))

      try {
        _hasContent = withTimeout(MAX_BG_AUDIO_DOWNLOAD_WAIT_TIME_MS) { awaitForContentOrError() }
      } catch (error: Throwable) {
        clearPlaybackListeners()
        throw error
      }
    }
  }

  private suspend fun createMediaSource(
    viewableMedia: ViewableMedia,
    mediaLocation: MediaLocation
  ): MediaSource {
    if (mediaLocation is MediaLocation.Local) {
      return ProgressiveMediaSource.Factory(fileDataSourceFactory)
        .createMediaSource(MediaItem.fromUri(Uri.parse(mediaLocation.path)))
    }

    mediaLocation as MediaLocation.Remote

    val threadDescriptor = viewableMedia.viewableMediaMeta.ownerPostDescriptor?.threadDescriptor()
    val soundPostActualSoundMedia = viewableMedia.viewableMediaMeta.soundPostActualSoundMedia

    // Check whether we can use video from the thread downloader cache
    if (threadDescriptor != null && threadDownloadManager.canUseThreadDownloaderCache(threadDescriptor)) {
      val file = threadDownloadManager.findDownloadedFile(mediaLocation.url, threadDescriptor)
      if (file != null) {
        // We can, use the cached video
        val videoSource = when (file) {
          is RawFile -> {
            ProgressiveMediaSource.Factory(fileDataSourceFactory)
              .createMediaSource(MediaItem.fromUri(Uri.parse(file.getFullPath())))
          }
          is ExternalFile -> {
            ProgressiveMediaSource.Factory(contentDataSourceFactory)
              .createMediaSource(MediaItem.fromUri(file.getUri()))
          }
          else -> error("Unknown file type: ${file.javaClass.simpleName}")
        }

        // Check whether there is sound post link
        val urlRaw = (soundPostActualSoundMedia?.mediaLocation as? MediaLocation.Remote)?.urlRaw
        if (urlRaw == null) {
          // There is no link, use only the video source
          return videoSource
        }

        // There is, merge local video with remote audio (since we don't download sound posts' audio
        // locally)
        val audioSource = ProgressiveMediaSource.Factory(cachedHttpDataSourceFactory)
          .createMediaSource(MediaItem.fromUri(Uri.parse(urlRaw)))

        return MergingMediaSource(videoSource, audioSource)
      }

      // fallthrough
    }

    // Thread is not downloaded or the file is not cached, check for the sound post link and use
    // merged source if there is
    if (soundPostActualSoundMedia != null) {
      val urlRaw = (soundPostActualSoundMedia.mediaLocation as? MediaLocation.Remote)?.urlRaw
      if (urlRaw != null) {
        val videoSource = ProgressiveMediaSource.Factory(cachedHttpDataSourceFactory)
          .createMediaSource(MediaItem.fromUri(Uri.parse(mediaLocation.url.toString())))
        val audioSource = ProgressiveMediaSource.Factory(cachedHttpDataSourceFactory)
          .createMediaSource(MediaItem.fromUri(Uri.parse(urlRaw)))

        return MergingMediaSource(videoSource, audioSource)
      }
    }

    // There is no sound post link, just use regular remote video source
    return ProgressiveMediaSource.Factory(cachedHttpDataSourceFactory)
      .createMediaSource(MediaItem.fromUri(Uri.parse(mediaLocation.url.toString())))
  }

  suspend fun startAndAwaitFirstFrame(mediaLocation: MediaLocation) {
    start()

    val deferred = requireNotNull(firstFrameRendered) { "firstFrameRendered is null!" }

    if (actualExoPlayer.videoFormat == null) {
      deferred.complete(mediaLocation)
    }

    deferred.await()
  }

  fun start() {
    activeVideoEndBehavior = kurobaSettings.application.videoEndBehavior.readBlocking()
    actualExoPlayer.removeListener(playbackStateListener)
    actualExoPlayer.addListener(playbackStateListener)

    actualExoPlayer.repeatMode = if (activeVideoEndBehavior == VideoEndBehavior.Loop) {
      Player.REPEAT_MODE_ALL
    } else {
      Player.REPEAT_MODE_OFF
    }

    actualExoPlayer.volume = if (mediaViewContract.isSoundCurrentlyMuted()) {
      0f
    } else {
      1f
    }

    timelineUpdateJob?.cancel()
    timelineUpdateJob = scope.launch {
      while (isActive) {
        _positionAndDurationFlow.value = Pair(
          actualExoPlayer.currentPosition.coerceAtLeast(0),
          actualExoPlayer.duration.coerceAtLeast(0)
        )

        delay(1000L)
      }
    }

    actualExoPlayer.play()
  }

  fun muteUnMute(mute: Boolean) {
    actualExoPlayer.volume = if (mute) {
      0f
    } else {
      1f
    }
  }

  fun pause() {
    actualExoPlayer.pause()
  }

  fun deactivate() {
    cancelActivePreload?.invoke()
    cancelActivePreload = null

    _hasContent = false
    actualExoPlayer.removeListener(playbackStateListener)

    timelineUpdateJob?.cancel()
    timelineUpdateJob = null

    firstFrameRendered?.cancel()
    firstFrameRendered = null
    clearPlaybackListeners()

    if (!reusableExoPlayerLazy.isInitialized()) {
      return
    }

    actualExoPlayer.pause()
    actualExoPlayer.stop()
  }

  fun release() {
    cancelActivePreload?.invoke()
    cancelActivePreload = null

    _hasContent = false

    timelineUpdateJob?.cancel()
    timelineUpdateJob = null

    scope.cancelChildren()

    firstFrameRendered?.cancel()
    firstFrameRendered = null
    clearPlaybackListeners()

    if (!reusableExoPlayerLazy.isInitialized()) {
      return
    }

    synchronized(reusableExoPlayer) {
      reusableExoPlayer.giveBack()
    }
  }

  fun setNoContent() {
    _hasContent = false
  }

  fun isPlaying(): Boolean {
    return actualExoPlayer.isPlaying
  }

  fun seekTo(windowIndex: Int, position: Long) {
    actualExoPlayer.seekTo(windowIndex, position)
  }

  fun hasNoVideo(): Boolean {
    return actualExoPlayer.videoFormat == null
  }

  fun resetPosition() {
    actualExoPlayer.seekTo(0, 0)
  }

  private suspend fun awaitForContentOrError(): Boolean {
    return suspendCancellableCoroutine { cancellableContinuation ->
      lateinit var listener: Player.Listener
      lateinit var cancelPreload: () -> Unit

      fun clearPreload() {
        actualExoPlayer.removeListener(listener)

        if (cancelActivePreload === cancelPreload) {
          cancelActivePreload = null
        }
      }

      cancelPreload = {
        cancellableContinuation.cancel()
      }

      listener = object : Player.Listener {

        override fun onPlayerErrorChanged(error: PlaybackException?) {
          if (error == null) {
            return
          }

          Logger.e(TAG, "preload() error", error)
          clearPreload()

          if (cancellableContinuation.isActive) {
            cancellableContinuation.resumeWithException(error)
          }
        }

        override fun onPlaybackStateChanged(state: Int) {
          if (state == Player.STATE_ENDED || state == Player.STATE_READY) {
            clearPreload()

            if (cancellableContinuation.isActive) {
              val hasContent = state == Player.STATE_READY
              cancellableContinuation.resume(hasContent)
            }
          }
        }

      }

      actualExoPlayer.addListener(listener)
      cancelActivePreload = cancelPreload

      cancellableContinuation.invokeOnCancellation {
        clearPreload()
      }
    }
  }

  private fun clearPlaybackListeners() {
    firstFrameListener?.let { listener ->
      actualExoPlayer.removeListener(listener)
    }
    firstFrameListener = null

    audioDetectionListener?.let { listener ->
      actualExoPlayer.removeAnalyticsListener(listener)
    }
    audioDetectionListener = null
  }

  private fun clearFirstFrameListener(listener: Player.Listener) {
    if (firstFrameListener !== listener) {
      return
    }

    actualExoPlayer.removeListener(listener)
    firstFrameListener = null
  }

  private fun clearAudioDetectionListener(listener: AnalyticsListener) {
    if (audioDetectionListener !== listener) {
      return
    }

    actualExoPlayer.removeAnalyticsListener(listener)
    audioDetectionListener = null
  }

  private fun getOrCreateExoPlayer(): ReusableExoPlayer {
    return synchronized(reusableExoPlayerCache) {
      val exoPlayer = reusableExoPlayerCache
        .firstOrNull { reusableExoPlayer -> reusableExoPlayer.notUsed }

      if (exoPlayer != null) {
        Logger.d(TAG, "getOrCreateExoPlayer() acquiring already instantiated player, " +
          "total players count: ${reusableExoPlayerCache.size}")

        exoPlayer.acquire()
        return exoPlayer
      }

      val newExoPlayer = ExoPlayer.Builder(context).build()
      val newReusableExoPlayer = ReusableExoPlayer(isUsed = true, newExoPlayer)
      reusableExoPlayerCache.add(newReusableExoPlayer)

      Logger.d(TAG, "getOrCreateExoPlayer() creating a new player, " +
        "total players count: ${reusableExoPlayerCache.size}")

      return@synchronized newReusableExoPlayer
    }
  }

  class ReusableExoPlayer(
    private var isUsed: Boolean,
    val exoPlayer: ExoPlayer
  ) {
    val notUsed: Boolean
      @Synchronized
      get() = !isUsed

    @Synchronized
    fun acquire() {
      isUsed = true
    }

    @Synchronized
    fun giveBack() {
      exoPlayer.stop()
      isUsed = false
    }

    @Synchronized
    fun releaseCompletely() {
      exoPlayer.release()
      isUsed = false
    }
  }

  companion object {
    private const val TAG = "ExoPlayerWrapper"
    private const val MAX_BG_AUDIO_DOWNLOAD_WAIT_TIME_MS = 30_000L

    const val SEEK_POSITION_DELTA = 100

    private val reusableExoPlayerCache = mutableListOf<ReusableExoPlayer>()

    fun releaseAll() {
      reusableExoPlayerCache.forEachIndexed { index, reusableExoPlayer ->
        Logger.d(TAG, "releaseAll() releasing ${index + 1} / ${reusableExoPlayerCache.size} player")
        reusableExoPlayer.releaseCompletely()
      }

      reusableExoPlayerCache.clear()
    }
  }

}
