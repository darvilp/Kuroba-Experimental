package com.github.k1rakishou.chan.features.view.media

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextPostNavigationTest {
  private val thread = ChanDescriptor.ThreadDescriptor.create("test", "test", 1L)

  @Test
  fun `single attachment posts advance in viewer order`() {
    val media = listOf(image(3), image(1), image(2))

    assertEquals(1, media.nextPostPositionOrNull(0))
    assertEquals(2, media.nextPostPositionOrNull(1))
  }

  @Test
  fun `remaining attachments from the current post are skipped`() {
    val media = listOf(image(1), image(1), image(1), image(2), image(2))

    assertEquals(3, media.nextPostPositionOrNull(0))
    assertEquals(3, media.nextPostPositionOrNull(1))
    assertEquals(3, media.nextPostPositionOrNull(2))
    assertNull(media.nextPostPositionOrNull(3))
  }

  @Test
  fun `last post does not wrap`() {
    assertNull(listOf(image(1), image(2)).nextPostPositionOrNull(1))
    assertNull(listOf(image(1)).nextPostPositionOrNull(0))
  }

  @Test
  fun `media without an owner post is skipped`() {
    val media = listOf(image(1), image(null), image(1), image(2))

    assertEquals(3, media.nextPostPositionOrNull(0))
    assertNull(media.nextPostPositionOrNull(1))
  }

  @Test
  fun `invalid positions and empty lists cannot advance`() {
    assertNull(emptyList<ViewableMedia>().nextPostPositionOrNull(0))
    assertNull(listOf(image(1)).nextPostPositionOrNull(-1))
    assertNull(listOf(image(1)).nextPostPositionOrNull(1))
  }

  @Test
  fun `post identity includes the thread`() {
    val otherThread = ChanDescriptor.ThreadDescriptor.create("test", "other", 1L)
    val media = listOf(image(1), image(1, PostDescriptor.create(otherThread, 1)))

    assertEquals(1, media.nextPostPositionOrNull(0))
  }

  private fun image(postNo: Long?, owner: PostDescriptor? = postNo?.let { PostDescriptor.create(thread, it) }): ViewableMedia {
    return ViewableMedia.Image(
      mediaLocation = MediaLocation.Local("/test/$postNo.jpg", isUri = false),
      previewLocation = null,
      spoilerLocation = null,
      viewableMediaMeta = ViewableMediaMeta(
        mediaViewerSoundPostsEnabled = false,
        ownerPostDescriptor = owner,
        serverMediaName = null,
        originalMediaName = null,
        extension = "jpg",
        mediaWidth = null,
        mediaHeight = null,
        mediaSize = null,
        mediaHash = null,
        isSpoiler = false
      )
    )
  }
}
