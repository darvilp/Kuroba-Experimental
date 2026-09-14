package com.github.k1rakishou.chan.features.view.media.helper

import android.app.Application
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.features.view.media.MediaLocation
import com.github.k1rakishou.chan.features.view.media.ViewableMedia
import com.github.k1rakishou.chan.features.view.media.element.MediaViewContract
import com.github.k1rakishou.v2.KurobaSettings
import com.github.k1rakishou.v2.parameters.VideoEndBehavior
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28])
class ExoPlayerWrapperTest {
  private val listeners = mutableListOf<Player.Listener>()
  private val completions = mutableListOf<VideoEndBehavior>()
  private val player = Mockito.mock(ExoPlayer::class.java)
  private val settings = Mockito.mock(KurobaSettings::class.java, Mockito.RETURNS_DEEP_STUBS)
  private lateinit var wrapper: ExoPlayerWrapper
  private var playRequested = false

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    Mockito.`when`(settings.application.videoEndBehavior.readBlocking()).thenReturn(VideoEndBehavior.AutoAdvance)
    Mockito.doAnswer { invocation ->
      val listener = invocation.getArgument<Player.Listener>(0)
      listeners.add(listener)
      null
    }.`when`(player).addListener(any(Player.Listener::class.java))
    Mockito.doAnswer { invocation ->
      listeners.remove(invocation.getArgument<Player.Listener>(0))
      null
    }.`when`(player).removeListener(any(Player.Listener::class.java))
    Mockito.doAnswer { playRequested = true; null }.`when`(player).play()
    Mockito.doAnswer { playRequested = false; null }.`when`(player).pause()
    Mockito.doAnswer { invocation ->
      playRequested = invocation.getArgument(0)
      null
    }.`when`(player).setPlayWhenReady(Mockito.anyBoolean())

    val dataSourceFactory = Mockito.mock(DataSource.Factory::class.java)
    wrapper = ExoPlayerWrapper(
      context = RuntimeEnvironment.getApplication(),
      kurobaSettings = settings,
      threadDownloadManager = Mockito.mock(ThreadDownloadManager::class.java),
      cachedHttpDataSourceFactory = dataSourceFactory,
      fileDataSourceFactory = dataSourceFactory,
      contentDataSourceFactory = dataSourceFactory,
      mediaViewContract = Mockito.mock(MediaViewContract::class.java),
      onAudioDetected = {},
      onPlaybackEnded = { behavior -> completions.add(behavior) }
    )

    // Replace only the decoder boundary. Exercise the real wrapper's preparation and lifecycle.
    ExoPlayerWrapper::class.java.getDeclaredField("actualExoPlayer\$delegate").apply {
      isAccessible = true
      set(wrapper, lazyOf(player))
    }
  }

  @After
  fun tearDown() {
    wrapper.release()
    Dispatchers.resetMain()
  }

  @Test
  fun `paused video still reports completion after revisit and direct play`() = runTest {
    prepareVideo()
    wrapper.start()
    wrapper.pause()
    wrapper.deactivate()

    // The view restores a paused page with preload and seek, without calling wrapper.start().
    prepareVideo()
    wrapper.seekTo(0, 1000L)
    assertFalse(playRequested)

    // The actual view controls and double-tap gesture play the underlying player directly.
    wrapper.actualExoPlayer.play()
    finishPlayback()

    assertEquals(listOf(VideoEndBehavior.AutoAdvance), completions)
  }

  @Test
  fun `released wrapper cannot handle completion from a reused player`() = runTest {
    prepareVideo()
    wrapper.start()
    wrapper.release()
    finishPlayback()

    assertEquals(emptyList<VideoEndBehavior>(), completions)
  }

  @Test
  fun `repeated preparation registers completion listener once`() = runTest {
    prepareVideo()
    prepareVideo()
    finishPlayback()

    assertEquals(listOf(VideoEndBehavior.AutoAdvance), completions)
  }

  private suspend fun prepareVideo() = coroutineScope {
    val preload = async(start = CoroutineStart.UNDISPATCHED) {
      wrapper.preload(
        viewableMedia = Mockito.mock(ViewableMedia.Video::class.java),
        mediaLocation = MediaLocation.Local("/test.webm", isUri = false),
        prevPosition = 1000L,
        prevWindowIndex = 0
      )
    }

    dispatchPlaybackState(Player.STATE_READY)
    preload.await()
  }

  private fun finishPlayback() {
    dispatchPlaybackState(Player.STATE_ENDED)
  }

  private fun dispatchPlaybackState(state: Int) {
    listeners.toList().forEach { listener -> listener.onPlaybackStateChanged(state) }
  }
}
