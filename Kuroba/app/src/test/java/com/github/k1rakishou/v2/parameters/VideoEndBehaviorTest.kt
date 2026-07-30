package com.github.k1rakishou.v2.parameters

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoEndBehaviorTest {

  @Test
  fun `legacy enabled auto loop migrates to loop`() {
    assertEquals(
      VideoEndBehavior.Loop,
      VideoEndBehavior.fromLegacyAutoLoop(autoLoop = true)
    )
  }

  @Test
  fun `legacy disabled auto loop migrates to stop`() {
    assertEquals(
      VideoEndBehavior.Stop,
      VideoEndBehavior.fromLegacyAutoLoop(autoLoop = false)
    )
  }

  @Test
  fun `video end behavior has the supported three states`() {
    assertEquals(
      listOf(
        VideoEndBehavior.Loop,
        VideoEndBehavior.AutoAdvance,
        VideoEndBehavior.Stop
      ),
      VideoEndBehavior.entries
    )
  }
}
