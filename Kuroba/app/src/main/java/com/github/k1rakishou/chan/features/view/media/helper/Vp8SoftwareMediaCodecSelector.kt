package com.github.k1rakishou.chan.features.view.media.helper

import android.os.Build
import com.github.k1rakishou.core_logger.Logger
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.util.MimeTypes

/**
 * Some Samsung hardware VP8 decoders initialize successfully but render particular valid WebM
 * streams extremely slowly while audio continues at normal speed. Since decoder initialization
 * succeeds, ExoPlayer's decoder fallback cannot help. Prefer the Android software decoder for VP8
 * on Samsung devices while preserving ExoPlayer's default order everywhere else.
 */
internal class Vp8SoftwareMediaCodecSelector(
  private val manufacturer: String = Build.MANUFACTURER,
  private val delegate: MediaCodecSelector = MediaCodecSelector.DEFAULT
) : MediaCodecSelector {

  override fun getDecoderInfos(
    mimeType: String,
    requiresSecureDecoder: Boolean,
    requiresTunnelingDecoder: Boolean
  ): List<MediaCodecInfo> {
    val decoderInfos = delegate.getDecoderInfos(
      mimeType,
      requiresSecureDecoder,
      requiresTunnelingDecoder
    )
    val reorderedDecoderInfos = Vp8SoftwareDecoderPreference.reorder(
      manufacturer = manufacturer,
      mimeType = mimeType,
      decoderInfos = decoderInfos,
      isSoftwareOnly = MediaCodecInfo::softwareOnly
    )

    if (reorderedDecoderInfos !== decoderInfos) {
      Logger.d(
        TAG,
        "getDecoderInfos() preferring software VP8 decoder on '$manufacturer': " +
          "${decoderInfos.describe()} -> ${reorderedDecoderInfos.describe()}"
      )
    }

    return reorderedDecoderInfos
  }

  private fun List<MediaCodecInfo>.describe(): String {
    return joinToString(prefix = "[", postfix = "]") { decoderInfo ->
      "${decoderInfo.name}(softwareOnly=${decoderInfo.softwareOnly})"
    }
  }

  private companion object {
    private const val TAG = "Vp8SoftwareCodecSelector"
  }
}

internal object Vp8SoftwareDecoderPreference {

  fun <T> reorder(
    manufacturer: String,
    mimeType: String,
    decoderInfos: List<T>,
    isSoftwareOnly: (T) -> Boolean
  ): List<T> {
    if (!manufacturer.equals(SAMSUNG_MANUFACTURER, ignoreCase = true)
      || mimeType != MimeTypes.VIDEO_VP8
    ) {
      return decoderInfos
    }

    val softwareDecoders = decoderInfos.filter(isSoftwareOnly)
    if (softwareDecoders.isEmpty()) {
      return decoderInfos
    }

    val reorderedDecoderInfos = softwareDecoders + decoderInfos.filterNot(isSoftwareOnly)
    if (reorderedDecoderInfos == decoderInfos) {
      return decoderInfos
    }

    return reorderedDecoderInfos
  }

  private const val SAMSUNG_MANUFACTURER = "samsung"
}
