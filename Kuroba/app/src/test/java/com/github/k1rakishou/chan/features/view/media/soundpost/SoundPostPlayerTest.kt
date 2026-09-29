package com.github.k1rakishou.chan.features.view.media.soundpost

import android.app.Application
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.ProxyStorage
import com.github.k1rakishou.chan.features.view.media.ViewableMedia
import com.github.k1rakishou.chan.features.view.media.element.MediaViewContract
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarManager
import com.github.k1rakishou.v2.KurobaSettings
import com.github.k1rakishou.v2.parameters.VideoEndBehavior
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28])
class SoundPostPlayerTest {
  private val settings = Mockito.mock(KurobaSettings::class.java, Mockito.RETURNS_DEEP_STUBS)

  @Test
  fun `long audio completes a short video once without looping`() {
    checkCompletion(autoLoop = false, expectedCompletions = 1)
  }

  @Test
  fun `looping sound post restarts without reporting completion`() {
    checkCompletion(autoLoop = true, expectedCompletions = 0)
  }

  private fun checkCompletion(autoLoop: Boolean, expectedCompletions: Int) {
    val audio = Mockito.mock(ExoPlayer::class.java)
    val target = Mockito.mock(SoundPostSyncTarget::class.java)
    var playing = true
    var completions = 0
    Mockito.`when`(audio.duration).thenReturn(15000L)
    Mockito.`when`(audio.currentPosition).thenReturn(15000L)
    Mockito.`when`(audio.playbackState).thenReturn(Player.STATE_ENDED)
    Mockito.`when`(target.isReady()).thenReturn(true)
    Mockito.`when`(target.durationMs()).thenReturn(5000L)
    Mockito.`when`(target.positionMs()).thenReturn(1000L)
    Mockito.`when`(target.isPlaying()).thenAnswer { playing }
    Mockito.doAnswer {
      playing = false
      null
    }.`when`(target).pause()

    val player = createPlayer { completions++ }
    val fields = mapOf(
      "audioPlayer" to audio,
      "target" to target,
      "attached" to true,
      "autoLoop" to autoLoop
    )
    for ((name, value) in fields) {
      SoundPostPlayer::class.java.getDeclaredField(name).apply {
        isAccessible = true
        set(player, value)
      }
    }

    val tick = SoundPostPlayer::class.java.getDeclaredMethod("tick").apply {
      isAccessible = true
    }
    tick.invoke(player)
    tick.invoke(player)
    assertEquals(expectedCompletions, completions)
    if (!autoLoop) {
      Mockito.verify(target, Mockito.times(1)).pause()
      Mockito.verify(audio, Mockito.times(1)).pause()
      playing = true
      tick.invoke(player) // Direct Play restarts the completed cycle.
      tick.invoke(player) // The next end must notify again.
      assertEquals(2, completions)
    } else {
      Mockito.verify(target, Mockito.atLeastOnce()).seekTo(0L)
      Mockito.verify(audio, Mockito.atLeastOnce()).seekTo(0L)
    }
  }

  @Test
  fun `pending sound load loops only for auto advance without starting paused video`() {
    for (behavior in VideoEndBehavior.entries) {
      Mockito.`when`(settings.application.videoEndBehavior.readBlocking()).thenReturn(behavior)
      val player = createPlayer {}
      val target = Mockito.mock(SoundPostSyncTarget::class.java)
      val loading = Job()
      SoundPostPlayer::class.java.getDeclaredField("loadJob").apply {
        isAccessible = true
        set(player, loading)
      }

      player.attach(target, isForced = false, isLifecycleChange = false)
      if (behavior == VideoEndBehavior.AutoAdvance) {
        Mockito.verify(target).setLooping(true)
      } else {
        Mockito.verify(target, Mockito.never()).setLooping(Mockito.anyBoolean())
      }
      Mockito.verify(target, Mockito.never()).play()
      player.detach()
      loading.cancel()
    }
  }

  private fun createPlayer(onCompleted: () -> Unit): SoundPostPlayer {
    return SoundPostPlayer(
      context = RuntimeEnvironment.getApplication(),
      kurobaSettings = settings,
      cacheHandler = Mockito.mock(CacheHandler::class.java),
      proxyStorage = Mockito.mock(ProxyStorage::class.java),
      dialogFactory = Mockito.mock(DialogFactory::class.java),
      soundPostAudioDownloader = Mockito.mock(SoundPostAudioDownloader::class.java),
      snackbarManager = Mockito.mock(SnackbarManager::class.java),
      mediaViewContract = Mockito.mock(MediaViewContract::class.java),
      ownerMedia = Mockito.mock(ViewableMedia.Video::class.java),
      soundMedia = Mockito.mock(ViewableMedia.Audio::class.java),
      state = SoundPostPlayer.State(),
      onPlaybackCompleted = onCompleted
    )
  }
}
