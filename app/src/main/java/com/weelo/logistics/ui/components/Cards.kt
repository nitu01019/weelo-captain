package com.weelo.logistics.ui.components

import androidx.compose.animation.core.*
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.ui.theme.*

// =============================================================================
// WEELO CAPTAIN - PREMIUM CARD COMPONENTS
// =============================================================================
// Modern, clean card designs with Saffron Yellow accents
// Features: Subtle shadows, smooth animations, professional feel
// =============================================================================

// Static cache to track animated cards across recompositions/navigation
private object AnimationCache {
    private val animatedCounts = mutableMapOf<String, Int>()
    
    fun hasAnimated(key: String): Boolean = animatedCounts.containsKey(key)
    fun getLastCount(key: String): Int = animatedCounts[key] ?: 0
    fun setAnimated(key: String, count: Int) { animatedCounts[key] = count }
    fun reset() { animatedCounts.clear() }
}

/**
 * Premium Info Card - Display stats/metrics with animated counting
 * Clean white card with subtle yellow accents
 * Usage: Dashboard cards showing count, revenue, etc.
 */
@Composable
fun InfoCard(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    iconTint: Color = Primary,
    animateValue: Boolean = false,
    targetCount: Int = 0,
    cardKey: String = title,
    showAccent: Boolean = true
) {
    var displayCount by remember { mutableIntStateOf(
        if (AnimationCache.hasAnimated(cardKey)) AnimationCache.getLastCount(cardKey) else 0
    )}
    
    val lastAnimatedCount = AnimationCache.getLastCount(cardKey)
    val hasAnimatedBefore = AnimationCache.hasAnimated(cardKey)
    val shouldAnimate = animateValue && targetCount > 0 && 
                        (!hasAnimatedBefore || targetCount > lastAnimatedCount)
    
    LaunchedEffect(targetCount, shouldAnimate) {
        if (shouldAnimate && targetCount > 0) {
            val startFrom = if (!hasAnimatedBefore) 0 else lastAnimatedCount
            val duration = 800L
            val startTime = System.currentTimeMillis()
            val diff = targetCount - startFrom
            
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                val easedProgress = 1f - (1f - progress) * (1f - progress) * (1f - progress)
                displayCount = (startFrom + (diff * easedProgress)).toInt()
                if (progress >= 1f) break
                kotlinx.coroutines.delay(16)
            }
            
            displayCount = targetCount
            AnimationCache.setAnimated(cardKey, targetCount)
        } else if (targetCount > 0) {
            displayCount = targetCount
        }
    }
    
    val displayValue = if (animateValue && targetCount > 0) displayCount.toString() else value
    
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        shape = RoundedCornerShape(BorderRadius.large),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon with accent background
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(BorderRadius.medium))
                    .background(if (showAccent) iconTint.copy(alpha = 0.12f) else SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(Spacing.medium))
            
            // Value
            Text(
                text = displayValue,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.height(Spacing.xs))
            
            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Premium Status Chip - Display status with modern styling
 * Clean, professional status indicators
 * Usage: Trip status, driver availability, etc.
 */
@Composable
fun StatusChip(
    text: String,
    status: ChipStatus,
    modifier: Modifier = Modifier,
    size: ChipSize = ChipSize.MEDIUM
) {
    val (backgroundColor, textColor, dotColor) = when (status) {
        ChipStatus.AVAILABLE -> Triple(SuccessLight, Success, Success)
        ChipStatus.IN_PROGRESS -> Triple(PrimaryLight, Primary, Primary)
        ChipStatus.COMPLETED -> Triple(SurfaceVariant, TextSecondary, TextSecondary)
        ChipStatus.PENDING -> Triple(WarningLight, Warning, Warning)
        ChipStatus.CANCELLED -> Triple(ErrorLight, Error, Error)
        ChipStatus.ONLINE -> Triple(SuccessLight, Success, Success)
        ChipStatus.OFFLINE -> Triple(SurfaceVariant, TextSecondary, TextSecondary)
    }
    
    val (horizontalPadding, verticalPadding, fontSize) = when (size) {
        ChipSize.SMALL -> Triple(8.dp, 4.dp, 10.sp)
        ChipSize.MEDIUM -> Triple(12.dp, 6.dp, 12.sp)
        ChipSize.LARGE -> Triple(16.dp, 8.dp, 14.sp)
    }

    Surface(
        modifier = modifier,
        color = backgroundColor,
        shape = RoundedCornerShape(BorderRadius.pill)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = textColor,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

enum class ChipStatus {
    AVAILABLE,
    IN_PROGRESS,
    COMPLETED,
    PENDING,
    CANCELLED,
    ONLINE,
    OFFLINE
}

enum class ChipSize {
    SMALL, MEDIUM, LARGE
}

/**
 * Premium List Item Card - Modern list item with subtle hover effect
 * Clean design with proper spacing and typography
 * Usage: Vehicle list, driver list, trip list
 */
@Composable
fun ListItemCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        shape = RoundedCornerShape(BorderRadius.large),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        onClick = { onClick?.invoke() }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.cardPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(Spacing.medium))
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                if (trailingContent != null) {
                    Spacer(modifier = Modifier.width(Spacing.medium))
                    trailingContent()
                }
            }
            
            if (showDivider) {
                Divider(
                    color = Divider,
                    thickness = 1.dp
                )
            }
        }
    }
}

/**
 * Premium Section Card - Card with styled header
 * Modern sectioned content grouping
 * Usage: Grouping related information
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        shape = RoundedCornerShape(BorderRadius.large),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPaddingLarge)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(Spacing.xxs))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                if (action != null) {
                    action()
                }
            }
            
            Spacer(modifier = Modifier.height(Spacing.medium))
            
            // Content
            content()
        }
    }
}

/**
 * Premium Highlight Card - Card with yellow accent border
 * For featured or important content
 */
@Composable
fun HighlightCard(
    modifier: Modifier = Modifier,
    accentColor: Color = Primary,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        shape = RoundedCornerShape(BorderRadius.large),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(2.dp, accentColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPaddingLarge)
        ) {
            content()
        }
    }
}

/**
 * Premium Gradient Card - Card with gradient background
 * Eye-catching card for CTAs and featured content
 */
@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    gradientColors: List<Color> = PrimaryGradientColors,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        shape = RoundedCornerShape(BorderRadius.large)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(gradientColors))
                .padding(Spacing.cardPaddingLarge)
        ) {
            content()
        }
    }
}

/**
 * Premium Quick Action Card - For dashboard quick actions
 * Compact card with icon and label
 */
@Composable
fun QuickActionCard(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconTint: Color = Primary,
    showBadge: Boolean = false,
    badgeCount: Int = 0
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        shape = RoundedCornerShape(BorderRadius.large),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(BorderRadius.medium))
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(26.dp)
                    )
                }
                
                // Badge
                if (showBadge && badgeCount > 0) {
                    Badge(
                        containerColor = Error,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                    ) {
                        Text(
                            text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(Spacing.small))
            
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// =============================================================================
// MEDIA / HERO CARDS (Phase 1 Foundation)
// =============================================================================

enum class CardArtworkPlacement {
    TOP_BLEED,
    TOP_INSET
}

val IllustrationCanvas = Color(0xFFF7F3EA)

enum class CardArtwork(@DrawableRes val drawableRes: Int) {
    AUTH_ROLE_TRANSPORTER(com.weelo.logistics.R.drawable.card_auth_role_transporter_soft),
    AUTH_ROLE_DRIVER(com.weelo.logistics.R.drawable.card_auth_role_driver_soft),
    AUTH_LOGIN_TRANSPORTER(com.weelo.logistics.R.drawable.card_auth_login_transporter_soft),
    AUTH_LOGIN_DRIVER(com.weelo.logistics.R.drawable.card_auth_login_driver_soft),
    AUTH_OTP(com.weelo.logistics.R.drawable.card_auth_otp_soft),
    AUTH_SIGNUP_PHONE(com.weelo.logistics.R.drawable.card_auth_signup_phone_soft),
    AUTH_SIGNUP_NAME(com.weelo.logistics.R.drawable.card_auth_signup_name_soft),
    AUTH_SIGNUP_LOCATION(com.weelo.logistics.R.drawable.card_auth_signup_location_soft),
    ADD_VEHICLE_TYPE_SELECTOR(com.weelo.logistics.R.drawable.card_add_vehicle_type_selector_soft),
    ADD_VEHICLE_TRUCK_CATEGORY(com.weelo.logistics.R.drawable.empty_matching_trucks_soft),
    DETAIL_VEHICLE(com.weelo.logistics.R.drawable.card_detail_vehicle_soft),
    DETAIL_DRIVER(com.weelo.logistics.R.drawable.card_detail_driver_soft),
    DETAIL_TRIP(com.weelo.logistics.R.drawable.card_detail_trip_soft),
    DRIVER_PERFORMANCE(com.weelo.logistics.R.drawable.card_driver_performance_soft),
    DRIVER_EARNINGS(com.weelo.logistics.R.drawable.card_driver_earnings_soft),
    DRIVER_DOCUMENTS(com.weelo.logistics.R.drawable.card_driver_documents_soft),
    DRIVER_SETTINGS(com.weelo.logistics.R.drawable.card_driver_settings_soft)
}

data class CardMediaSpec(
    val artwork: CardArtwork,
    val headerHeight: Dp = 132.dp,
    val placement: CardArtworkPlacement = CardArtworkPlacement.TOP_BLEED,
    val contentScale: ContentScale = ContentScale.Crop,
    val overlayBrush: Brush? = null,
    val edgeScrim: Brush? = null,
    val containerColor: Color = IllustrationCanvas,
    val showInsetFrame: Boolean = false,
    val insetPadding: Dp = 8.dp,
    val fitContentPadding: Dp = 3.dp,
    val enableEdgeBlend: Boolean = true,
    val enableImageFadeIn: Boolean = true,
    val imageFadeDurationMs: Int = 180
)

fun bannerGeneratedArtSpec(
    artwork: CardArtwork,
    headerHeight: Dp,
    overlayBrush: Brush? = null,
    fitContentPadding: Dp = 2.dp,
    imageFadeDurationMs: Int = 170
) = CardMediaSpec(
    artwork = artwork,
    headerHeight = headerHeight,
    placement = CardArtworkPlacement.TOP_BLEED,
    contentScale = ContentScale.Fit,
    containerColor = IllustrationCanvas,
    showInsetFrame = false,
    fitContentPadding = fitContentPadding,
    enableEdgeBlend = true,
    enableImageFadeIn = true,
    imageFadeDurationMs = imageFadeDurationMs,
    overlayBrush = overlayBrush
)

fun insetGeneratedArtSpec(
    artwork: CardArtwork,
    headerHeight: Dp,
    overlayBrush: Brush? = null,
    fitContentPadding: Dp = 3.dp,
    insetPadding: Dp = 8.dp,
    imageFadeDurationMs: Int = 170
) = CardMediaSpec(
    artwork = artwork,
    headerHeight = headerHeight,
    placement = CardArtworkPlacement.TOP_INSET,
    contentScale = ContentScale.Fit,
    containerColor = IllustrationCanvas,
    showInsetFrame = true,
    insetPadding = insetPadding,
    fitContentPadding = fitContentPadding,
    enableEdgeBlend = true,
    enableImageFadeIn = true,
    imageFadeDurationMs = imageFadeDurationMs,
    overlayBrush = overlayBrush
)

private fun Modifier.edgeFeatherBlend(
    enabled: Boolean,
    color: Color,
    thickness: Dp
): Modifier = if (!enabled) this else drawWithCache {
    val px = thickness.toPx().coerceAtLeast(1f)
    val topBrush = Brush.verticalGradient(colors = listOf(color, Color.Transparent))
    val bottomBrush = Brush.verticalGradient(colors = listOf(Color.Transparent, color))
    val leftBrush = Brush.horizontalGradient(colors = listOf(color, Color.Transparent))
    val rightBrush = Brush.horizontalGradient(colors = listOf(Color.Transparent, color))
    onDrawWithContent {
        drawContent()
        drawRect(
            brush = topBrush,
            topLeft = Offset.Zero,
            size = Size(size.width, px)
        )
        drawRect(
            brush = bottomBrush,
            topLeft = Offset(0f, size.height - px),
            size = Size(size.width, px)
        )
        drawRect(
            brush = leftBrush,
            topLeft = Offset.Zero,
            size = Size(px, size.height)
        )
        drawRect(
            brush = rightBrush,
            topLeft = Offset(size.width - px, 0f),
            size = Size(px, size.height)
        )
    }
}

/**
 * Shared drawable illustration renderer with optional one-time fade.
 * The animation is keyed to drawableRes so it does not replay on normal recomposition.
 */
@Composable
fun WeeloIllustration(
    @DrawableRes drawableRes: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    animateIn: Boolean = true,
    fadeDurationMs: Int = 180
) {
    var visible by remember(drawableRes, animateIn) { mutableStateOf(!animateIn) }
    LaunchedEffect(drawableRes, animateIn) {
        if (animateIn) visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = fadeDurationMs),
        label = "illustrationAlpha"
    )

    Image(
        painter = painterResource(id = drawableRes),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        alpha = alpha
    )
}

/**
 * Reusable premium card with a top media region and text content beneath.
 * Intended for selective use on hero/summary/option cards, not dense form containers.
 */
@Composable
fun MediaHeaderCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    mediaSpec: CardMediaSpec? = null,
    trailingHeaderContent: (@Composable BoxScope.() -> Unit)? = null,
    footerContent: (@Composable ColumnScope.() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        shape = RoundedCornerShape(BorderRadius.large),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            mediaSpec?.let { spec ->
                val artworkPalette = spec.artwork.blendPaletteOrNull()
                val requestedContainerColor = if (spec.containerColor == White) IllustrationCanvas else spec.containerColor
                val shouldUseArtworkContainer = spec.containerColor == IllustrationCanvas && artworkPalette != null
                val resolvedContainerColor = if (shouldUseArtworkContainer) {
                    artworkPalette!!.cardContainer
                } else {
                    requestedContainerColor
                }
                val edgeBlendColor = if (shouldUseArtworkContainer) {
                    artworkPalette!!.edgeBlendColor.copy(alpha = if (spec.showInsetFrame) 0.16f else 0.13f)
                } else {
                    resolvedContainerColor.copy(alpha = if (spec.showInsetFrame) 0.12f else 0.10f)
                }
                val baseEdgeThickness = if (spec.showInsetFrame) 12.dp else 14.dp
                val resolvedEdgeThickness = when {
                    spec.contentScale != ContentScale.Fit -> 0.dp
                    spec.headerHeight <= 96.dp -> 8.dp
                    spec.headerHeight <= 120.dp -> 10.dp
                    else -> baseEdgeThickness
                }
                val mediaShape = when (spec.placement) {
                    CardArtworkPlacement.TOP_BLEED -> RoundedCornerShape(
                        topStart = BorderRadius.large,
                        topEnd = BorderRadius.large,
                        bottomStart = BorderRadius.small,
                        bottomEnd = BorderRadius.small
                    )
                    CardArtworkPlacement.TOP_INSET -> RoundedCornerShape(BorderRadius.medium)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (spec.placement == CardArtworkPlacement.TOP_BLEED) {
                                Modifier.height(spec.headerHeight)
                            } else {
                                Modifier
                                    .padding(
                                        start = Spacing.cardPadding,
                                        end = Spacing.cardPadding,
                                        top = Spacing.cardPadding,
                                        bottom = Spacing.small
                                    )
                                    .height(spec.headerHeight)
                            }
                        )
                        .clip(mediaShape)
                        .background(resolvedContainerColor)
                ) {
                    val imageModifier = Modifier
                        .fillMaxSize()
                        .let { base ->
                            if (spec.contentScale == ContentScale.Fit) {
                                base.padding(spec.fitContentPadding)
                            } else {
                                base
                            }
                        }
                    if (spec.showInsetFrame) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(spec.insetPadding),
                            shape = RoundedCornerShape(BorderRadius.medium),
                            color = resolvedContainerColor,
                            tonalElevation = 0.dp,
                            shadowElevation = Elevation.xs,
                            border = BorderStroke(1.dp, Divider.copy(alpha = 0.55f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(resolvedContainerColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .edgeFeatherBlend(
                                            enabled = spec.enableEdgeBlend && spec.contentScale == ContentScale.Fit,
                                            color = edgeBlendColor,
                                            thickness = resolvedEdgeThickness
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    WeeloIllustration(
                                        drawableRes = spec.artwork.drawableRes,
                                        modifier = imageModifier,
                                        contentDescription = null,
                                        contentScale = spec.contentScale,
                                        animateIn = spec.enableImageFadeIn,
                                        fadeDurationMs = spec.imageFadeDurationMs
                                    )
                                }
                                spec.edgeScrim?.let { brush ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(brush)
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(resolvedContainerColor)
                                .edgeFeatherBlend(
                                    enabled = spec.enableEdgeBlend && spec.contentScale == ContentScale.Fit,
                                    color = edgeBlendColor,
                                    thickness = resolvedEdgeThickness
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            WeeloIllustration(
                                drawableRes = spec.artwork.drawableRes,
                                modifier = imageModifier,
                                contentDescription = null,
                                contentScale = spec.contentScale,
                                animateIn = spec.enableImageFadeIn,
                                fadeDurationMs = spec.imageFadeDurationMs
                            )
                            spec.edgeScrim?.let { brush ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(brush)
                                )
                            }
                        }
                    }
                    spec.overlayBrush?.let { brush ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(brush)
                        )
                    }
                    if (trailingHeaderContent != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(Spacing.small),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            trailingHeaderContent()
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.cardPaddingLarge)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                if (content != null) {
                    Spacer(modifier = Modifier.height(Spacing.medium))
                    content()
                }
                if (footerContent != null) {
                    Spacer(modifier = Modifier.height(Spacing.medium))
                    footerContent()
                }
            }
        }
    }
}

/**
 * Entity-centric hero card used on details screens (vehicle/driver/trip).
 */
@Composable
fun HeroEntityCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    mediaSpec: CardMediaSpec? = null,
    leadingAvatar: (@Composable () -> Unit)? = null,
    statusContent: (@Composable () -> Unit)? = null,
    metaContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    val resolvedMediaSpec = mediaSpec?.let { spec ->
        if (spec.showInsetFrame) {
            spec
        } else {
            spec.copy(
                placement = CardArtworkPlacement.TOP_INSET,
                contentScale = ContentScale.Fit,
                containerColor = IllustrationCanvas,
                showInsetFrame = true,
                insetPadding = if (spec.insetPadding < 8.dp) 8.dp else spec.insetPadding,
                enableImageFadeIn = true
            )
        }
    }
    MediaHeaderCard(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        mediaSpec = resolvedMediaSpec
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingAvatar != null) {
                    leadingAvatar()
                }
                if (statusContent != null) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        statusContent()
                    }
                }
            }
            if (metaContent != null) {
                metaContent()
            }
        }
    }
}

/**
 * Compact informational banner card for support hints, warnings, and helper content.
 */
@Composable
fun InlineInfoBannerCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Primary,
    containerColor: Color = SurfaceMuted,
    action: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(BorderRadius.large),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.xs)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.medium, vertical = Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(BorderRadius.medium))
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            action?.invoke()
        }
    }
}
