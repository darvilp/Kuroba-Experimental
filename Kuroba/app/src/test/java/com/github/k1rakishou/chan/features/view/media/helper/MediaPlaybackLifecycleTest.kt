package com.github.k1rakishou.chan.features.view.media.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPlaybackLifecycleTest {
  @Test
  fun `buffering playback keeps requested intent`() {
    assertEquals(
      true,
      MediaPlaybackLifecycle.resolvePlaybackIntent(
        previousIntent = null,
        playWhenReady = true,
        playbackEnded = false
      )
    )
  }

  @Test
  fun `player that has not requested playback keeps default intent`() {
    assertNull(
      MediaPlaybackLifecycle.resolvePlaybackIntent(
        previousIntent = null,
        playWhenReady = false,
        playbackEnded = false
      )
    )
  }

  @Test
  fun `explicit pause is retained`() {
    assertEquals(
      false,
      MediaPlaybackLifecycle.resolvePlaybackIntent(
        previousIntent = true,
        playWhenReady = false,
        playbackEnded = false
      )
    )
  }

  @Test
  fun `ended playback does not resume implicitly`() {
    assertEquals(
      false,
      MediaPlaybackLifecycle.resolvePlaybackIntent(
        previousIntent = true,
        playWhenReady = true,
        playbackEnded = true
      )
    )
  }

  @Test
  fun `inactive page always pauses`() {
    assertTrue(
      MediaPlaybackLifecycle.shouldPause(
        isPausing = false,
        pauseInBackground = false,
        isBecomingInactive = true
      )
    )
  }

  @Test
  fun `background pause follows setting`() {
    assertTrue(
      MediaPlaybackLifecycle.shouldPause(
        isPausing = true,
        pauseInBackground = true,
        isBecomingInactive = false
      )
    )
    assertFalse(
      MediaPlaybackLifecycle.shouldPause(
        isPausing = true,
        pauseInBackground = false,
        isBecomingInactive = false
      )
    )
  }
}
