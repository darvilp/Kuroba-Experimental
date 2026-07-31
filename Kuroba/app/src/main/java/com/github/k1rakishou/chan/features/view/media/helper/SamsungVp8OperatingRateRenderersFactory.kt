package com.github.k1rakishou.chan.features.view.media.helper

import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.github.k1rakishou.core_logger.Logger
import kotlin.math.max

@OptIn(UnstableApi::class)
internal class SamsungVp8OperatingRateRenderersFactory(
  context: Context
) : DefaultRenderersFactory(context) {

  override fun buildVideoRenderers(
    context: Context,
    extensionRendererMode: Int,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler,
    eventListener: VideoRendererEventListener,
    allowedVideoJoiningTimeMs: Long,
    out: ArrayList<Renderer>
  ) {
    val firstRendererIndex = out.size

    super.buildVideoRenderers(
      context,
      extensionRendererMode,
      mediaCodecSelector,
      enableDecoderFallback,
      eventHandler,
      eventListener,
      allowedVideoJoiningTimeMs,
      out
    )

    val stockVideoRendererIndex = (firstRendererIndex until out.size)
      .firstOrNull { index -> out[index].javaClass == MediaCodecVideoRenderer::class.java }
      ?: error("Default MediaCodecVideoRenderer is missing")

    out[stockVideoRendererIndex] = SamsungVp8OperatingRateVideoRenderer(
      MediaCodecVideoRenderer.Builder(context)
        .setCodecAdapterFactory(getCodecAdapterFactory())
        .setMediaCodecSelector(mediaCodecSelector)
        .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
        .setEnableDecoderFallback(enableDecoderFallback)
        .setEventHandler(eventHandler)
        .setEventListener(eventListener)
        .setMaxDroppedFramesToNotify(MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)
        // The custom policy below retains Media3's normal 30 fps threshold for every non-target
        // format. A zero renderer threshold lets the targeted VP8 rate be written explicitly.
        .setAssumedMinimumCodecOperatingRate(0f)
    )
  }

  private class SamsungVp8OperatingRateVideoRenderer(
    builder: MediaCodecVideoRenderer.Builder
  ) : MediaCodecVideoRenderer(builder) {

    override fun getCodecOperatingRateV23(
      targetPlaybackSpeed: Float,
      format: Format,
      streamFormats: Array<Format>
    ): Float {
      val media3OperatingRate = super.getCodecOperatingRateV23(
        targetPlaybackSpeed,
        format,
        streamFormats
      )
      val resolvedOperatingRate = Vp8OperatingRatePolicy.resolve(
        media3OperatingRate = media3OperatingRate,
        targetPlaybackSpeed = targetPlaybackSpeed,
        sampleMimeType = format.sampleMimeType
      )

      if (format.sampleMimeType == MimeTypes.VIDEO_VP8) {
        Logger.d(
          TAG,
          "$VIDEO_DIAGNOSTIC_PREFIX codec operating rate: " +
            "media3=$media3OperatingRate, resolved=$resolvedOperatingRate, " +
            "playbackSpeed=$targetPlaybackSpeed, size=${format.width}x${format.height}, " +
            "frameRate=${format.frameRate}"
        )
      }

      return resolvedOperatingRate
    }
  }

  companion object {
    private const val TAG = "Vp8OperatingRate"
    const val VIDEO_DIAGNOSTIC_PREFIX = "VP8_DIAGNOSTIC"
  }
}

@OptIn(UnstableApi::class)
internal object Vp8OperatingRatePolicy {
  private const val SAMSUNG_MANUFACTURER = "samsung"
  private const val MEDIA3_ASSUMED_MINIMUM_CODEC_OPERATING_RATE = 30f
  private const val VP8_SOURCE_FRAME_RATE_FLOOR = 30f
  private const val CODEC_OPERATING_RATE_UNSET = -1f

  fun shouldApply(manufacturer: String): Boolean {
    return manufacturer.equals(SAMSUNG_MANUFACTURER, ignoreCase = true)
  }

  fun resolve(
    media3OperatingRate: Float,
    targetPlaybackSpeed: Float,
    sampleMimeType: String?
  ): Float {
    if (sampleMimeType == MimeTypes.VIDEO_VP8) {
      val vp8OperatingRateFloor = VP8_SOURCE_FRAME_RATE_FLOOR * targetPlaybackSpeed
      return max(media3OperatingRate, vp8OperatingRateFloor)
    }

    if (media3OperatingRate > MEDIA3_ASSUMED_MINIMUM_CODEC_OPERATING_RATE) {
      return media3OperatingRate
    }

    return CODEC_OPERATING_RATE_UNSET
  }
}
