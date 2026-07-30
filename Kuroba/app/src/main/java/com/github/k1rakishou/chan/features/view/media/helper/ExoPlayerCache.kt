package com.github.k1rakishou.chan.features.view.media.helper

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.github.k1rakishou.common.AppConstants

@OptIn(UnstableApi::class)
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
