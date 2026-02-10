package com.weelo.logistics.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest

/**
 * OPTIMIZED IMAGE COMPONENTS
 * ==========================
 * Centralized image loading with caching and memory optimization.
 * 
 * Benefits:
 * - Memory caching for repeated images
 * - Disk caching for network images
 * - Crossfade animations
 * - Proper content scaling
 */

/**
 * Optimized AsyncImage for loading images from URLs with caching
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun OptimizedNetworkImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    // OPTIMIZATION: Remember the image request to avoid recreation
    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
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
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    
    // OPTIMIZATION: Remember the image request to avoid recreation
    val imageRequest = remember(uri) {
        ImageRequest.Builder(context)
            .data(uri)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
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
