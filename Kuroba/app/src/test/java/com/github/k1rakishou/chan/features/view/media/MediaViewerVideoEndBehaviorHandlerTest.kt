package com.github.k1rakishou.chan.features.view.media

import com.github.k1rakishou.v2.parameters.VideoEndBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
