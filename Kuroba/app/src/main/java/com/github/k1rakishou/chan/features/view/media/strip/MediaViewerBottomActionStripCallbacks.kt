package com.github.k1rakishou.chan.features.view.media.strip

import com.github.k1rakishou.chan.features.view.media.ViewableMedia
import com.github.k1rakishou.model.data.descriptor.PostDescriptor

interface MediaViewerBottomActionStripCallbacks {
  suspend fun reloadMedia()
  suspend fun downloadMedia(isLongClick: Boolean): Boolean
  fun onOptionsButtonClick()
  fun hasNextPost(): Boolean
  fun onNextPostClick()
  fun onShowRepliesButtonClick(postDescriptor: PostDescriptor)
  fun onGoToPostMediaClick(viewableMedia: ViewableMedia, postDescriptor: PostDescriptor)
}