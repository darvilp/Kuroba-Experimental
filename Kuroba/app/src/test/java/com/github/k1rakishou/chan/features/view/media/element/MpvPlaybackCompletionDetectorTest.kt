package com.github.k1rakishou.chan.features.view.media.element

import com.github.k1rakishou.v2.parameters.VideoEndBehavior
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvPlaybackCompletionDetectorTest {
  @Test
  fun `only confirmed eof completes auto advance`() {
    assertTrue(MpvPlaybackCompletionDetector.shouldReportCompletion(VideoEndBehavior.AutoAdvance, eofReached = true))
    assertFalse(MpvPlaybackCompletionDetector.shouldReportCompletion(VideoEndBehavior.AutoAdvance, eofReached = false))
  }

  @Test
  fun `loop and stop do not advance at eof`() {
    for (behavior in listOf(VideoEndBehavior.Loop, VideoEndBehavior.Stop)) {
      assertFalse(MpvPlaybackCompletionDetector.shouldReportCompletion(behavior, eofReached = true))
    }
  }
}
