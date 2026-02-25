package com.weelo.logistics.core.image

import android.content.Context
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Centralized Image Loader Configuration
 * 
 * Optimized Coil configuration for Weelo Captain app
 * - Disk cache: 50 MB for profile photos and license images
 * - Memory cache: 25% of available RAM
 * - Network timeout: 30 seconds (for slow connections)
 * - Cache duration: 7 days (matches backend pre-signed URL expiry)
 */
object ImageLoaderConfig {
    
    private var imageLoader: ImageLoader? = null
    
    /**
     * Get or create singleton ImageLoader
     */
    fun getInstance(context: Context): ImageLoader {
        return imageLoader ?: synchronized(this) {
            imageLoader ?: createImageLoader(context).also { imageLoader = it }
        }
    }
    
    private fun createImageLoader(context: Context): ImageLoader {
        // Custom OkHttpClient with longer timeout for large images
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            // Memory Cache Configuration
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25) // Use 25% of available RAM
                    .build()
            }
            // Disk Cache Configuration — 100MB for profile photos, license images, fleet photos
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100 * 1024 * 1024) // 100 MB
                    .build()
            }
            // Cache Policies
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            // Instagram-style: Don't respect S3 cache headers.
            // S3 pre-signed URLs have short expiry headers which force re-fetches.
            // We control cache invalidation via URL-based cache keys instead.
            // New photo upload = new S3 path = automatic cache miss = fresh fetch.
            .respectCacheHeaders(false)
            .build()
    }
    
    /**
     * Clear all caches (memory + disk)
     * Call on logout or when storage is low
     */
    @OptIn(ExperimentalCoilApi::class)
    fun clearCache(context: Context) {
        getInstance(context).apply {
            memoryCache?.clear()
            diskCache?.clear()
        }
    }
    
    /**
     * Get cache size information
     */
    @OptIn(ExperimentalCoilApi::class)
    fun getCacheSize(context: Context): CacheInfo {
        val loader = getInstance(context)
        return CacheInfo(
            memorySize = loader.memoryCache?.size?.toLong() ?: 0L,
            memoryMaxSize = loader.memoryCache?.maxSize?.toLong() ?: 0L,
            diskSize = loader.diskCache?.size ?: 0L,
            diskMaxSize = loader.diskCache?.maxSize ?: 0L
        )
    }
}

data class CacheInfo(
    val memorySize: Long,
    val memoryMaxSize: Long,
    val diskSize: Long,
    val diskMaxSize: Long
) {
    fun memoryUsagePercent(): Int = 
        if (memoryMaxSize > 0) ((memorySize * 100) / memoryMaxSize).toInt() else 0
    
    fun diskUsagePercent(): Int = 
        if (diskMaxSize > 0) ((diskSize * 100) / diskMaxSize).toInt() else 0
}
