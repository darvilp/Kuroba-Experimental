package com.github.k1rakishou.chan.features.view.media.helper

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Vp8OperatingRatePolicyTest {
  @Test
  fun `workaround only applies to Samsung devices`() {
    assertTrue(Vp8OperatingRatePolicy.shouldApply("samsung"))
    assertTrue(Vp8OperatingRatePolicy.shouldApply("SAMSUNG"))
    assertFalse(Vp8OperatingRatePolicy.shouldApply("Google"))
  }

  @Test
  fun `unknown VP8 frame rate gets explicit 30 fps operating rate`() {
    assertEquals(
      30f,
      Vp8OperatingRatePolicy.resolve(
        media3OperatingRate = -1f,
        targetPlaybackSpeed = 1f,
        sampleMimeType = MimeTypes.VIDEO_VP8
      ),
      0f
    )
  }

  @Test
  fun `VP8 operating rate follows playback speed`() {
    assertEquals(
      60f,
      Vp8OperatingRatePolicy.resolve(
        media3OperatingRate = -1f,
        targetPlaybackSpeed = 2f,
        sampleMimeType = MimeTypes.VIDEO_VP8
      ),
      0f
    )
  }

  @Test
  fun `known higher VP8 frame rate remains unchanged`() {
    assertEquals(
      60f,
      Vp8OperatingRatePolicy.resolve(
        media3OperatingRate = 60f,
        targetPlaybackSpeed = 1f,
        sampleMimeType = MimeTypes.VIDEO_VP8
      ),
      0f
    )
  }

  @Test
  fun `ordinary formats retain Media3 minimum operating rate policy`() {
    assertEquals(
      -1f,
      Vp8OperatingRatePolicy.resolve(
        media3OperatingRate = 24f,
        targetPlaybackSpeed = 1f,
        sampleMimeType = MimeTypes.VIDEO_H264
      ),
      0f
    )
    assertEquals(
      -1f,
      Vp8OperatingRatePolicy.resolve(
        media3OperatingRate = 30f,
        targetPlaybackSpeed = 1f,
        sampleMimeType = MimeTypes.VIDEO_H264
      ),
      0f
    )
    assertEquals(
      60f,
      Vp8OperatingRatePolicy.resolve(
        media3OperatingRate = 60f,
        targetPlaybackSpeed = 1f,
        sampleMimeType = MimeTypes.VIDEO_H264
      ),
      0f
    )
  }
}
