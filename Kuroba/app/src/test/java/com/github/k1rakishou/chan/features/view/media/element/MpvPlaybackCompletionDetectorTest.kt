package com.github.k1rakishou.chan.features.view.media.element

import com.github.k1rakishou.v2.parameters.VideoEndBehavior
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvPlaybackCompletionDetectorTest {

  @Test
  fun `stable eof signal completes auto advance`() {
    assertTrue(
      MpvPlaybackCompletionDetector.shouldReportCompletion(
        videoEndBehavior = VideoEndBehavior.AutoAdvance,
        eofReached = true,
        lastKnownPositionSeconds = null,
        durationSeconds = null
      )
    )
  }

  @Test
  fun `end file event near duration completes auto advance`() {
    assertTrue(
      MpvPlaybackCompletionDetector.shouldReportCompletion(
        videoEndBehavior = VideoEndBehavior.AutoAdvance,
        eofReached = false,
        lastKnownPositionSeconds = 28.7,
        durationSeconds = 30.0
      )
    )
  }

  @Test
  fun `end file event far from duration does not complete auto advance`() {
    assertFalse(
      MpvPlaybackCompletionDetector.shouldReportCompletion(
        videoEndBehavior = VideoEndBehavior.AutoAdvance,
        eofReached = false,
        lastKnownPositionSeconds = 12.0,
        durationSeconds = 30.0
      )
    )
  }

  @Test
  fun `position far beyond duration does not masquerade as completion`() {
    assertFalse(
      MpvPlaybackCompletionDetector.shouldReportCompletion(
        videoEndBehavior = VideoEndBehavior.AutoAdvance,
        eofReached = false,
        lastKnownPositionSeconds = 40.0,
        durationSeconds = 30.0
      )
    )
  }

  @Test
  fun `non auto advance behaviors never report completion`() {
    for (videoEndBehavior in listOf(VideoEndBehavior.Loop, VideoEndBehavior.Stop)) {
      assertFalse(
        MpvPlaybackCompletionDetector.shouldReportCompletion(
          videoEndBehavior = videoEndBehavior,
          eofReached = true,
          lastKnownPositionSeconds = 30.0,
          durationSeconds = 30.0
        )
      )
    }
  }

  @Test
  fun `invalid timing cannot masquerade as completion`() {
    assertFalse(
      MpvPlaybackCompletionDetector.shouldReportCompletion(
        videoEndBehavior = VideoEndBehavior.AutoAdvance,
        eofReached = false,
        lastKnownPositionSeconds = Double.NaN,
        durationSeconds = 30.0
      )
    )
  }
}
