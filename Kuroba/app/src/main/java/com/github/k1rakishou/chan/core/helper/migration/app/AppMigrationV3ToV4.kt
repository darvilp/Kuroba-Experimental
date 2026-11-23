package com.github.k1rakishou.chan.core.helper.migration.app

import android.content.Context
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.KurobaSettings
import com.github.k1rakishou.v2.parameters.VideoEndBehavior

class AppMigrationV3ToV4(
  private val kurobaSettings: KurobaSettings
) : ApplicationMigration {
  override val version: Int
    get() = 4
  override val changes: String?
    get() = "Added options to loop, auto-advance, or stop when video playback ends."

  override fun perform(context: Context) {
    // Old builds could save the preference without persisting the migration version.
    if (kurobaSettings.application.initialSettingsState.allSettings
        .containsKey(KurobaSettingKey.Application.VideoEndBehavior.raw)) {
      return
    }

    val legacyAutoLoop = kurobaSettings.application.videoAutoLoop.readBlocking()
    val videoEndBehavior = VideoEndBehavior.fromLegacyAutoLoop(legacyAutoLoop)

    kurobaSettings.application.videoEndBehavior.writeBlocking(videoEndBehavior)
  }
}
