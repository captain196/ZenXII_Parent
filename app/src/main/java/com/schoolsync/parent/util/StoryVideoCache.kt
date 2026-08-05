package com.schoolsync.parent.util

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Process-wide 100 MB LRU disk cache for story video playback.
 *
 * Ported from the Grader_S reference viewer: a single shared
 * [SimpleCache] + [CacheDataSource] so a clip the parent already
 * watched (or swiped past) replays instantly without re-downloading —
 * the main reason the reference viewer feels smoother than a fresh
 * ExoPlayer-per-URL with no cache.
 */
@UnstableApi
object StoryVideoCache {

    private const val MAX_BYTES = 100L * 1024 * 1024 // 100 MB
    private const val FOLDER = "story_media_cache"

    @Volatile private var cache: SimpleCache? = null

    private fun simpleCache(context: Context): SimpleCache {
        return cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(context.cacheDir, FOLDER),
                LeastRecentlyUsedCacheEvictor(MAX_BYTES),
                StandaloneDatabaseProvider(context.applicationContext)
            ).also { cache = it }
        }
    }

    fun dataSourceFactory(context: Context): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(simpleCache(context.applicationContext))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context.applicationContext))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
