package com.github.k1rakishou.chan.core.helper.migration.app

import android.content.Context
import com.github.k1rakishou.v2.KurobaSettings
import com.github.k1rakishou.v2.parameters.VideoEndBehavior

class AppMigration_V3_V4(
  private val kurobaSettings: KurobaSettings
) : ApplicationMigration {
  override val version: Int
    get() = 4
  override val changes: String?
    get() = "Added options to loop, auto-advance, or stop when video playback ends."

  override fun perform(context: Context) {
    val legacyAutoLoop = kurobaSettings.application.videoAutoLoop.readBlocking()
    val videoEndBehavior = VideoEndBehavior.fromLegacyAutoLoop(legacyAutoLoop)

    kurobaSettings.application.videoEndBehavior.writeBlocking(videoEndBehavior)
  }
}
