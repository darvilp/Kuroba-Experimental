package com.github.k1rakishou.chan.features.view.media.element

import com.github.k1rakishou.v2.parameters.VideoEndBehavior

internal object MpvPlaybackCompletionDetector {
  private const val END_POSITION_TOLERANCE_SECONDS = 2.0

  fun shouldReportCompletion(
    videoEndBehavior: VideoEndBehavior,
    eofReached: Boolean,
    lastKnownPositionSeconds: Double?,
    durationSeconds: Double?
  ): Boolean {
    if (videoEndBehavior != VideoEndBehavior.AutoAdvance) {
      return false
    }

    if (eofReached) {
      return true
    }

    if (
      lastKnownPositionSeconds == null ||
      durationSeconds == null ||
      !lastKnownPositionSeconds.isFinite() ||
      !durationSeconds.isFinite() ||
      lastKnownPositionSeconds < 0.0 ||
      durationSeconds <= 0.0
    ) {
      return false
    }

    val remainingSeconds = durationSeconds - lastKnownPositionSeconds
    return remainingSeconds in -END_POSITION_TOLERANCE_SECONDS..END_POSITION_TOLERANCE_SECONDS
  }
}
