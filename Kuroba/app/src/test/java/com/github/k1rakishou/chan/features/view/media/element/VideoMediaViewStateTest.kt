package com.github.k1rakishou.chan.features.view.media.element

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoMediaViewStateTest {

  @Test
  fun `exo player state preserves pending replay when cloned`() {
    val state = ExoPlayerVideoMediaView.VideoMediaViewState(
      replayFromStartOnNextShow = true
    )

    val clonedState = state.clone() as ExoPlayerVideoMediaView.VideoMediaViewState

    assertTrue(clonedState.replayFromStartOnNextShow)
  }

  @Test
  fun `exo player position reset consumes pending replay`() {
    val state = ExoPlayerVideoMediaView.VideoMediaViewState(
      replayFromStartOnNextShow = true
    )

    state.resetPosition()

    assertFalse(state.replayFromStartOnNextShow)
  }

  @Test
  fun `mpv state preserves pending replay when cloned`() {
    val state = MpvVideoMediaView.VideoMediaViewState(
      replayFromStartOnNextShow = true
    )

    val clonedState = state.clone() as MpvVideoMediaView.VideoMediaViewState

    assertTrue(clonedState.replayFromStartOnNextShow)
  }

  @Test
  fun `mpv position reset consumes pending replay`() {
    val state = MpvVideoMediaView.VideoMediaViewState(
      replayFromStartOnNextShow = true
    )

    state.resetPosition()

    assertFalse(state.replayFromStartOnNextShow)
  }
}
