package com.github.k1rakishou.chan.features.view.media.helper

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.github.k1rakishou.core_logger.Logger

@OptIn(UnstableApi::class)
internal class Vp8PlaybackDiagnostics : AnalyticsListener {
  private var isVp8Playback = false

  override fun onVideoDecoderInitialized(
    eventTime: AnalyticsListener.EventTime,
    decoderName: String,
    initializedTimestampMs: Long,
    initializationDurationMs: Long
  ) {
    if (!isVp8Playback) {
      return
    }

    Logger.d(
      TAG,
      "$VIDEO_DIAGNOSTIC_PREFIX decoder initialized: name='$decoderName', " +
        "initializationDurationMs=$initializationDurationMs"
    )
  }

  override fun onVideoInputFormatChanged(
    eventTime: AnalyticsListener.EventTime,
    format: Format,
    decoderReuseEvaluation: DecoderReuseEvaluation?
  ) {
    isVp8Playback = format.sampleMimeType == MimeTypes.VIDEO_VP8
    if (!isVp8Playback) {
      return
    }

    Logger.d(
      TAG,
      "$VIDEO_DIAGNOSTIC_PREFIX input format changed: mimeType='${format.sampleMimeType}', " +
        "codecs='${format.codecs}', size=${format.width}x${format.height}, " +
        "frameRate=${format.frameRate}, reuseResult=${decoderReuseEvaluation?.result}"
    )
  }

  override fun onDroppedVideoFrames(
    eventTime: AnalyticsListener.EventTime,
    droppedFrames: Int,
    elapsedMs: Long
  ) {
    if (!isVp8Playback) {
      return
    }

    Logger.d(
      TAG,
      "$VIDEO_DIAGNOSTIC_PREFIX dropped video frames: count=$droppedFrames, elapsedMs=$elapsedMs"
    )
  }

  override fun onVideoFrameProcessingOffset(
    eventTime: AnalyticsListener.EventTime,
    totalProcessingOffsetUs: Long,
    frameCount: Int
  ) {
    if (!isVp8Playback) {
      return
    }

    val averageProcessingOffsetUs = if (frameCount > 0) {
      totalProcessingOffsetUs / frameCount
    } else {
      0L
    }

    Logger.d(
      TAG,
      "$VIDEO_DIAGNOSTIC_PREFIX video frame processing offset: " +
        "averageUs=$averageProcessingOffsetUs, totalUs=$totalProcessingOffsetUs, " +
        "frameCount=$frameCount"
    )
  }

  override fun onVideoDisabled(
    eventTime: AnalyticsListener.EventTime,
    decoderCounters: DecoderCounters
  ) {
    if (!isVp8Playback) {
      return
    }

    decoderCounters.ensureUpdated()
    Logger.d(
      TAG,
      "$VIDEO_DIAGNOSTIC_PREFIX video disabled: " +
        "droppedToKeyframeCount=${decoderCounters.droppedToKeyframeCount}, " +
        "droppedBufferCount=${decoderCounters.droppedBufferCount}, " +
        "maxConsecutiveDroppedBufferCount=${decoderCounters.maxConsecutiveDroppedBufferCount}, " +
        "renderedOutputBufferCount=${decoderCounters.renderedOutputBufferCount}"
    )
    isVp8Playback = false
  }

  override fun onVideoCodecError(
    eventTime: AnalyticsListener.EventTime,
    videoCodecError: Exception
  ) {
    if (!isVp8Playback) {
      return
    }

    Logger.e(
      TAG,
      "$VIDEO_DIAGNOSTIC_PREFIX video codec error",
      videoCodecError
    )
  }

  override fun onPlayerError(
    eventTime: AnalyticsListener.EventTime,
    error: PlaybackException
  ) {
    if (!isVp8Playback) {
      return
    }

    Logger.e(
      TAG,
      "$VIDEO_DIAGNOSTIC_PREFIX player error",
      error
    )
  }

  companion object {
    private const val TAG = "Vp8PlaybackDiagnostics"
    private const val VIDEO_DIAGNOSTIC_PREFIX = "VP8_DIAGNOSTIC"
  }
}
