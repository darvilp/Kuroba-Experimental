package com.github.k1rakishou.chan.features.view.media.helper

import com.google.android.exoplayer2.util.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class Vp8SoftwareDecoderPreferenceTest {

  @Test
  fun `software VP8 decoders are preferred on Samsung devices`() {
    val hardwareDecoder = Decoder("vendor.vp8.decoder", softwareOnly = false)
    val firstSoftwareDecoder = Decoder("android.vp8.decoder", softwareOnly = true)
    val secondSoftwareDecoder = Decoder("google.vp8.decoder", softwareOnly = true)
    val decoders = listOf(hardwareDecoder, firstSoftwareDecoder, secondSoftwareDecoder)

    val result = reorder(
      manufacturer = "samsung",
      mimeType = MimeTypes.VIDEO_VP8,
      decoders = decoders
    )

    assertEquals(
      listOf(firstSoftwareDecoder, secondSoftwareDecoder, hardwareDecoder),
      result
    )
  }

  @Test
  fun `manufacturer matching is case insensitive`() {
    val hardwareDecoder = Decoder("vendor.vp8.decoder", softwareOnly = false)
    val softwareDecoder = Decoder("android.vp8.decoder", softwareOnly = true)

    val result = reorder(
      manufacturer = "SAMSUNG",
      mimeType = MimeTypes.VIDEO_VP8,
      decoders = listOf(hardwareDecoder, softwareDecoder)
    )

    assertEquals(listOf(softwareDecoder, hardwareDecoder), result)
  }

  @Test
  fun `non VP8 decoder order is unchanged on Samsung devices`() {
    val decoders = listOf(
      Decoder("vendor.h264.decoder", softwareOnly = false),
      Decoder("android.h264.decoder", softwareOnly = true)
    )

    val result = reorder(
      manufacturer = "samsung",
      mimeType = MimeTypes.VIDEO_H264,
      decoders = decoders
    )

    assertSame(decoders, result)
  }

  @Test
  fun `VP8 decoder order is unchanged on non Samsung devices`() {
    val decoders = listOf(
      Decoder("vendor.vp8.decoder", softwareOnly = false),
      Decoder("android.vp8.decoder", softwareOnly = true)
    )

    val result = reorder(
      manufacturer = "Google",
      mimeType = MimeTypes.VIDEO_VP8,
      decoders = decoders
    )

    assertSame(decoders, result)
  }

  @Test
  fun `Samsung VP8 decoder order is unchanged when no software decoder exists`() {
    val decoders = listOf(
      Decoder("first.vendor.vp8.decoder", softwareOnly = false),
      Decoder("second.vendor.vp8.decoder", softwareOnly = false)
    )

    val result = reorder(
      manufacturer = "samsung",
      mimeType = MimeTypes.VIDEO_VP8,
      decoders = decoders
    )

    assertSame(decoders, result)
  }

  @Test
  fun `already preferred Samsung VP8 decoder order reuses original list`() {
    val decoders = listOf(
      Decoder("android.vp8.decoder", softwareOnly = true),
      Decoder("vendor.vp8.decoder", softwareOnly = false)
    )

    val result = reorder(
      manufacturer = "samsung",
      mimeType = MimeTypes.VIDEO_VP8,
      decoders = decoders
    )

    assertSame(decoders, result)
  }

  private fun reorder(
    manufacturer: String,
    mimeType: String,
    decoders: List<Decoder>
  ): List<Decoder> {
    return Vp8SoftwareDecoderPreference.reorder(
      manufacturer = manufacturer,
      mimeType = mimeType,
      decoderInfos = decoders,
      isSoftwareOnly = Decoder::softwareOnly
    )
  }

  private data class Decoder(
    val name: String,
    val softwareOnly: Boolean
  )
}
