package com.github.k1rakishou.chan.features.view.media.helper

object MediaPlaybackLifecycle {
  fun resolvePlaybackIntent(
    previousIntent: Boolean?,
    playWhenReady: Boolean,
    playbackEnded: Boolean
  ): Boolean? {
    if (playbackEnded) {
      return false
    }

    if (playWhenReady) {
      return true
    }

    if (previousIntent != null) {
      return false
    }

    return null
  }

  fun shouldPause(
    isPausing: Boolean,
    pauseInBackground: Boolean,
    isBecomingInactive: Boolean
  ): Boolean {
    return isBecomingInactive || (isPausing && pauseInBackground)
  }
}
