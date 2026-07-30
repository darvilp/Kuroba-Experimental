package com.github.k1rakishou.chan.features.view.media.helper

import android.content.Context
import com.github.k1rakishou.common.AppConstants
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache

class ExoPlayerCache(
  context: Context,
  appConstants: AppConstants
) {
  val actualCache by lazy {
    SimpleCache(
      appConstants.exoPlayerCacheDir,
      LeastRecentlyUsedCacheEvictor(appConstants.exoPlayerDiskCacheMaxSize),
      StandaloneDatabaseProvider(context)
    )
  }
}
