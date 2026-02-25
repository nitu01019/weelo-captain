package com.weelo.logistics.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

/**
 * OPTIMIZED IMAGE COMPONENTS
 * ==========================
 * Centralized image loading with caching and memory optimization.
 * Instagram-style hard caching with S3 URL normalization.
 * 
 * Benefits:
 * - Memory caching for repeated images (25% RAM)
 * - Disk caching for network images (100MB)
 * - S3 URL normalization: same image = same cache key regardless of signature
 * - Backend URL change (new upload) = automatic cache miss = fresh fetch
 * - Crossfade animations (300ms)
 * - Proper content scaling
 */

/**
 * Normalize S3 pre-signed URLs by stripping query parameters.
 * S3 URLs include `?X-Amz-Algorithm=...&X-Amz-Credential=...&X-Amz-Signature=...`
 * which change on every API call even for the same image.
 * 
 * By using just the base path as cache key:
 * - Same image → same cache key → instant from cache (Instagram behavior)
 * - New upload → different S3 path → cache miss → fresh network fetch
 */
private fun normalizeImageUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return url.substringBefore("?")
}

/**
 * Optimized AsyncImage for loading images from URLs with Instagram-style hard caching.
 * 
 * Cache strategy:
 * - memoryCacheKey = base URL (without S3 query params)
 * - diskCacheKey = base URL (without S3 query params)
 * - Full URL used for network fetch (includes valid S3 signature)
 * - Once cached, image loads instantly without network round-trip
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun OptimizedNetworkImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable (() -> Unit)? = null,
    crossfade: Boolean = false,
    targetSizeDp: Dp? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetSizePx = remember(targetSizeDp, density) {
        targetSizeDp?.let { with(density) { it.roundToPx() } }
    }
    
    // Normalize URL for cache key — strip S3 query params
    val cacheKey = remember(imageUrl) { normalizeImageUrl(imageUrl) }
    
    // OPTIMIZATION: Remember the image request to avoid recreation
    val imageRequest = remember(imageUrl, cacheKey, crossfade, targetSizePx) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(crossfade)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .apply {
                targetSizePx?.let {
                    size(it)
                    precision(coil.size.Precision.INEXACT)
                }
            }
            // Instagram-style: use normalized URL as cache key
            // Same image = same key even when S3 signature changes
            .apply {
                cacheKey?.let {
                    memoryCacheKey(it)
                    diskCacheKey(it)
                }
            }
            .build()
    }
    
    AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}

/**
 * Optimized AsyncImage for loading images from URI (local files, content URIs)
 */
@Composable
fun OptimizedUriImage(
    uri: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    crossfade: Boolean = false,
    targetSizeDp: Dp? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetSizePx = remember(targetSizeDp, density) {
        targetSizeDp?.let { with(density) { it.roundToPx() } }
    }
    
    // OPTIMIZATION: Remember the image request to avoid recreation
    val imageRequest = remember(uri, crossfade, targetSizePx) {
        ImageRequest.Builder(context)
            .data(uri)
            .crossfade(crossfade)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .apply {
                targetSizePx?.let {
                    size(it)
                    precision(coil.size.Precision.INEXACT)
                }
            }
            .build()
    }
    
    AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}

/**
 * Optimized drawable resource image with remembered painter
 */
@Composable
fun OptimizedDrawableImage(
    @DrawableRes drawableRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    // painterResource is already optimized internally by Compose
    Image(
        painter = painterResource(id = drawableRes),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}

/**
 * Helper to get a remembered painter for drawable resources
 * Use this when you need just the painter without the Image composable
 */
@Composable
fun rememberDrawablePainter(@DrawableRes drawableRes: Int): Painter {
    return painterResource(id = drawableRes)
}
