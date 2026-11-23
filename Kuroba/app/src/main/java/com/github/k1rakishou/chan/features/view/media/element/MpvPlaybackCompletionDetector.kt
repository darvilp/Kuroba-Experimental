package com.github.k1rakishou.chan.features.view.media.element

import com.github.k1rakishou.v2.parameters.VideoEndBehavior

internal object MpvPlaybackCompletionDetector {
  fun shouldReportCompletion(videoEndBehavior: VideoEndBehavior, eofReached: Boolean): Boolean {
    return videoEndBehavior == VideoEndBehavior.AutoAdvance && eofReached
  }
}
