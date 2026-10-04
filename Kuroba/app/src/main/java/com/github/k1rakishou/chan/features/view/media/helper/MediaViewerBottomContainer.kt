package com.github.k1rakishou.chan.features.view.media.helper

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.updatePaddings
import javax.inject.Inject

class MediaViewerBottomContainer @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null
) : LinearLayout(context, attributeSet), WindowInsetsListener {

  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    orientation = VERTICAL
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    globalWindowInsetsManager.addInsetsUpdatesListener(this)
    onInsetsChanged()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
  }

  override fun onFinishInflate() {
    super.onFinishInflate()

    onInsetsChanged()
  }

  override fun onInsetsChanged() {
    updatePaddings(
      left = globalWindowInsetsManager.left(),
      right = globalWindowInsetsManager.right(),
      top = 0,
      bottom = globalWindowInsetsManager.bottom()
    )
  }
}
