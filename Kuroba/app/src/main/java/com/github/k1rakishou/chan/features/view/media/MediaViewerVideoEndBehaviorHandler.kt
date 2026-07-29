package com.github.k1rakishou.chan.features.view.media

import com.github.k1rakishou.v2.parameters.VideoEndBehavior

internal object MediaViewerVideoEndBehaviorHandler {

  fun shouldReplayFromStartWhenRevisited(
    videoEndBehavior: VideoEndBehavior,
    completedPagerPosition: Int,
    totalMediaCount: Int
  ): Boolean {
    return nextPagerPositionOrNull(
      videoEndBehavior = videoEndBehavior,
      completedPagerPosition = completedPagerPosition,
      currentPagerPosition = completedPagerPosition,
      totalMediaCount = totalMediaCount
    ) != null
  }

  fun nextPagerPositionOrNull(
    videoEndBehavior: VideoEndBehavior,
    completedPagerPosition: Int,
    currentPagerPosition: Int,
    totalMediaCount: Int
  ): Int? {
    if (videoEndBehavior != VideoEndBehavior.AutoAdvance) {
      return null
    }

    if (completedPagerPosition < 0 || currentPagerPosition < 0 || totalMediaCount <= 0) {
      return null
    }

    if (
      completedPagerPosition != currentPagerPosition ||
      currentPagerPosition >= totalMediaCount
    ) {
      return null
    }

    val nextPagerPosition = currentPagerPosition + 1
    if (nextPagerPosition >= totalMediaCount) {
      return null
    }

    return nextPagerPosition
  }
}
