package com.github.k1rakishou.chan.core.helper.migration.app

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import com.github.k1rakishou.v2.KurobaInitialSettingsState
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.KurobaSettings
import com.github.k1rakishou.v2.parameters.VideoEndBehavior
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28])
class ApplicationMigrationHelperTest {
  private val settings = Mockito.mock(KurobaSettings::class.java, Mockito.RETURNS_DEEP_STUBS)
  private val context: Context = RuntimeEnvironment.getApplication()

  @Test
  fun `existing video preference survives migration replay`() {
    for (behavior in VideoEndBehavior.entries) {
      Mockito.`when`(settings.application.initialSettingsState).thenReturn(
        KurobaInitialSettingsState(
          mapOf(KurobaSettingKey.Application.VideoEndBehavior.raw to behavior.name.toByteArray()),
          emptySet()
        )
      )
      Mockito.`when`(settings.application.videoAutoLoop.readBlocking()).thenReturn(false)
      AppMigrationV3ToV4(settings).perform(context)
    }

    Mockito.verifyNoInteractions(settings.application.videoEndBehavior)
  }

  @Test
  fun `legacy preference converts only when new preference is absent`() {
    Mockito.`when`(settings.application.initialSettingsState)
      .thenReturn(KurobaInitialSettingsState(emptyMap(), emptySet()))
    for ((legacy, expected) in listOf(true to VideoEndBehavior.Loop, false to VideoEndBehavior.Stop)) {
      Mockito.`when`(settings.application.videoAutoLoop.readBlocking()).thenReturn(legacy)
      AppMigrationV3ToV4(settings).perform(context)
      Mockito.verify(settings.application.videoEndBehavior).writeBlocking(expected)
    }
  }

  @Test
  fun `existing version four install executes cookie migration five`() {
    checkMigrationSelection(storedVersion = 4, expectedMigrations = listOf(5))
  }

  @Test
  fun `unset version on update runs video and cookie migrations`() {
    setInstallTimes(updated = true)
    checkMigrationSelection(storedVersion = -1, expectedMigrations = listOf(4, 5))
  }

  @Test
  fun `fresh installation persists latest version without migrating`() {
    setInstallTimes(updated = false)
    checkMigrationSelection(storedVersion = -1, expectedMigrations = emptyList())
  }

  private fun setInstallTimes(updated: Boolean) {
    val info = PackageInfo().apply {
      packageName = context.packageName
      firstInstallTime = 1000L
      lastUpdateTime = if (updated) 2000L else 1000L
    }
    Shadows.shadowOf(context.packageManager).installPackage(info)
  }

  private fun checkMigrationSelection(storedVersion: Int, expectedMigrations: List<Int>) {
    Mockito.`when`(settings.nonBackupable.applicationMigrationVersion.readBlocking()).thenReturn(storedVersion)
    val helper = ApplicationMigrationHelper(settings)
    val field = ApplicationMigrationHelper::class.java.getDeclaredField("_migrations").apply {
      isAccessible = true
    }
    @Suppress("UNCHECKED_CAST")
    val registered = field.get(helper) as List<ApplicationMigration>
    assertEquals(listOf(1, 2, 3, 4, 5), registered.map { it.version })
    assertEquals(AppMigrationV4ToV5::class.java, registered.last().javaClass)

    // Replace external migration effects; exercise the real version selection and persistence.
    val performed = mutableListOf<Int>()
    field.set(helper, registered.map { migration ->
      object : ApplicationMigration {
        override val version = migration.version
        override val changes: String? = null
        override fun perform(context: Context) { performed.add(version) }
      }
    })
    helper.processMigrations(context)

    assertEquals(expectedMigrations, performed)
    Mockito.verify(settings.nonBackupable.applicationMigrationVersion).writeBlocking(5)
  }
}
