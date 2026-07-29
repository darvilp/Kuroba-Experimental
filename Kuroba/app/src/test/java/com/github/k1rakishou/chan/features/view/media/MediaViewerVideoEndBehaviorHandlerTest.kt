package com.github.k1rakishou.chan.features.view.media

import com.github.k1rakishou.v2.parameters.VideoEndBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaViewerVideoEndBehaviorHandlerTest {

  @Test
  fun `auto advance returns the next pager position`() {
    val nextPagerPosition = MediaViewerVideoEndBehaviorHandler.nextPagerPositionOrNull(
      videoEndBehavior = VideoEndBehavior.AutoAdvance,
      completedPagerPosition = 0,
      currentPagerPosition = 0,
      totalMediaCount = 2
    )

    assertEquals(1, nextPagerPosition)
  }

  @Test
  fun `auto advanced media is replayed from the start when revisited`() {
    val shouldReplay = MediaViewerVideoEndBehaviorHandler.shouldReplayFromStartWhenRevisited(
      videoEndBehavior = VideoEndBehavior.AutoAdvance,
      completedPagerPosition = 0,
      totalMediaCount = 2
    )

    assertTrue(shouldReplay)
  }

  @Test
  fun `last auto advance media is not marked for replay when no advance occurs`() {
    val shouldReplay = MediaViewerVideoEndBehaviorHandler.shouldReplayFromStartWhenRevisited(
      videoEndBehavior = VideoEndBehavior.AutoAdvance,
      completedPagerPosition = 1,
      totalMediaCount = 2
    )

    assertFalse(shouldReplay)
  }

  @Test
  fun `loop and stop media are not marked for replay`() {
    val loopShouldReplay = MediaViewerVideoEndBehaviorHandler.shouldReplayFromStartWhenRevisited(
      videoEndBehavior = VideoEndBehavior.Loop,
      completedPagerPosition = 0,
      totalMediaCount = 2
    )
    val stopShouldReplay = MediaViewerVideoEndBehaviorHandler.shouldReplayFromStartWhenRevisited(
      videoEndBehavior = VideoEndBehavior.Stop,
      completedPagerPosition = 0,
      totalMediaCount = 2
    )

    assertFalse(loopShouldReplay)
    assertFalse(stopShouldReplay)
  }

  @Test
  fun `loop does not advance`() {
    val nextPagerPosition = MediaViewerVideoEndBehaviorHandler.nextPagerPositionOrNull(
      videoEndBehavior = VideoEndBehavior.Loop,
      completedPagerPosition = 0,
      currentPagerPosition = 0,
      totalMediaCount = 2
    )

    assertNull(nextPagerPosition)
  }

  @Test
  fun `stop does not advance`() {
    val nextPagerPosition = MediaViewerVideoEndBehaviorHandler.nextPagerPositionOrNull(
      videoEndBehavior = VideoEndBehavior.Stop,
      completedPagerPosition = 0,
      currentPagerPosition = 0,
      totalMediaCount = 2
    )

    assertNull(nextPagerPosition)
  }

  @Test
  fun `stale completion from an inactive page does not advance`() {
    val nextPagerPosition = MediaViewerVideoEndBehaviorHandler.nextPagerPositionOrNull(
      videoEndBehavior = VideoEndBehavior.AutoAdvance,
      completedPagerPosition = 0,
      currentPagerPosition = 1,
      totalMediaCount = 3
    )

    assertNull(nextPagerPosition)
  }

  @Test
  fun `last media does not wrap to the first`() {
    val nextPagerPosition = MediaViewerVideoEndBehaviorHandler.nextPagerPositionOrNull(
      videoEndBehavior = VideoEndBehavior.AutoAdvance,
      completedPagerPosition = 1,
      currentPagerPosition = 1,
      totalMediaCount = 2
    )

    assertNull(nextPagerPosition)
  }

  @Test
  fun `invalid pager state does not advance`() {
    val negativePosition = MediaViewerVideoEndBehaviorHandler.nextPagerPositionOrNull(
      videoEndBehavior = VideoEndBehavior.AutoAdvance,
      completedPagerPosition = -1,
      currentPagerPosition = -1,
      totalMediaCount = 2
    )
    val emptyMediaList = MediaViewerVideoEndBehaviorHandler.nextPagerPositionOrNull(
      videoEndBehavior = VideoEndBehavior.AutoAdvance,
      completedPagerPosition = 0,
      currentPagerPosition = 0,
      totalMediaCount = 0
    )
    val positionPastMediaList = MediaViewerVideoEndBehaviorHandler.nextPagerPositionOrNull(
      videoEndBehavior = VideoEndBehavior.AutoAdvance,
      completedPagerPosition = 2,
      currentPagerPosition = 2,
      totalMediaCount = 2
    )

    assertNull(negativePosition)
    assertNull(emptyMediaList)
    assertNull(positionPastMediaList)
  }
}
