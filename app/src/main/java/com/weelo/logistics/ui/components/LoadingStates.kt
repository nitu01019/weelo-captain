package com.weelo.logistics.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.R
import com.weelo.logistics.ui.theme.*
import kotlin.math.max
import kotlin.math.min

// =============================================================================
// WEELO CAPTAIN - PREMIUM LOADING & STATE COMPONENTS
// =============================================================================
// Polished loading, error, and empty states with Saffron Yellow theme
// Smooth animations for professional user experience
// =============================================================================

// =============================================================================
// FULL SCREEN LOADING
// =============================================================================

/**
 * Full screen loading with animated logo/spinner
 */
@Composable
fun FullScreenLoading(
    modifier: Modifier = Modifier,
    message: String? = null
) {
    val resolvedMessage = message ?: stringResource(R.string.loading)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated loading indicator
            PulsingLoadingIndicator()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = resolvedMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }
    }
}

/**
 * Pulsing loading indicator (more polished than standard CircularProgressIndicator)
 */
@Composable
fun PulsingLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = Primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = modifier
            .size((48 * scale).dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = color,
            strokeWidth = 3.dp
        )
    }
}

/**
 * Three-dot loading animation
 */
@Composable
fun ThreeDotsLoading(
    modifier: Modifier = Modifier,
    color: Color = Primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val delay = index * 150
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = delay),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

// =============================================================================
// CONTENT LOADING (Inline)
// =============================================================================

/**
 * Loading overlay for content sections
 */
@Composable
fun ContentLoading(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
        
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Surface.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Primary,
                    strokeWidth = 3.dp
                )
            }
        }
    }
}

/**
 * Pull-to-refresh style loading at top
 */
@Composable
fun TopLoadingBar(
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isLoading,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        LinearProgressIndicator(
            modifier = modifier
                .fillMaxWidth()
                .height(3.dp),
            color = Primary,
            trackColor = Primary.copy(alpha = 0.2f)
        )
    }
}

// =============================================================================
// EMPTY STATES
// =============================================================================

enum class EmptyStateKind {
    FIRST_RUN_SETUP,
    NO_RESULTS_SEARCH,
    NO_RESULTS_FILTER,
    ALL_CAUGHT_UP,
    NO_AVAILABILITY,
    NO_ACTIVITY_YET,
    NO_ACTIVE_LIVE_DATA,
    NO_MATCHING_REQUIREMENT
}

enum class EmptyStateLayoutStyle(
    val maxIllustrationWidthDp: Int,
    val maxTextWidthDp: Int,
    val showFramedIllustration: Boolean
) {
    CENTER_COMPACT(maxIllustrationWidthDp = 252, maxTextWidthDp = 300, showFramedIllustration = false),
    CENTER_WIDE(maxIllustrationWidthDp = 276, maxTextWidthDp = 320, showFramedIllustration = false),
    MODAL_COMPACT(maxIllustrationWidthDp = 207, maxTextWidthDp = 260, showFramedIllustration = false),
    CARD_COMPACT(maxIllustrationWidthDp = 218, maxTextWidthDp = 280, showFramedIllustration = true)
}

enum class EmptyStateArtwork(@DrawableRes val drawableRes: Int) {
    VEHICLES_FIRST_RUN(R.drawable.empty_vehicles_soft),
    DRIVERS_FIRST_RUN(R.drawable.empty_drivers_soft),
    TRIPS_FIRST_RUN(R.drawable.empty_trips_soft),
    REQUESTS_ALL_CAUGHT_UP(R.drawable.empty_requests_soft),
    FLEET_FILTER(R.drawable.empty_fleet_filter_soft),
    DRIVER_SEARCH(R.drawable.empty_driver_search_soft),
    TRIP_FILTER(R.drawable.empty_trip_filter_soft),
    NOTIFICATIONS_ALL_CAUGHT_UP(R.drawable.empty_notifications_all_caught_up_soft),
    TRIP_HISTORY(R.drawable.empty_trip_history_soft),
    FLEET_MAP_IDLE(R.drawable.empty_fleet_map_idle_soft),
    MATCHING_TRUCKS(R.drawable.empty_matching_trucks_soft),
    MATCHING_DRIVERS(R.drawable.empty_matching_drivers_soft),
    ASSIGNMENT_BUSY_DRIVERS(R.drawable.empty_assignment_busy_drivers_soft),
    VEHICLE_DETAILS_NOT_FOUND(R.drawable.empty_vehicle_details_not_found_soft),
    DRIVER_DETAILS_NOT_FOUND(R.drawable.empty_driver_details_not_found_soft),
    TRIP_DETAILS_NOT_FOUND(R.drawable.empty_trip_details_not_found_soft),
    CREATE_TRIP_NO_VEHICLES(R.drawable.empty_create_trip_no_vehicles_soft),
    CREATE_TRIP_NO_DRIVERS(R.drawable.empty_create_trip_no_drivers_soft),
    PERFORMANCE_NO_TRENDS(R.drawable.empty_performance_trends_soft),
    PERFORMANCE_NO_FEEDBACK(R.drawable.empty_performance_feedback_soft),
    EARNINGS_NO_TRIPS(R.drawable.empty_earnings_trips_soft),
    EARNINGS_PENDING_CAUGHT_UP(R.drawable.empty_earnings_pending_caught_up_soft)
}

enum class AsyncScreenVisualState {
    LOADING,
    ERROR,
    EMPTY,
    CONTENT
}

data class EmptyStateSpec(
    val kind: EmptyStateKind,
    val artwork: EmptyStateArtwork,
    val title: String,
    val subtitle: String,
    val actionLabel: String? = null
)

fun firstRunEmptyStateSpec(
    artwork: EmptyStateArtwork,
    title: String,
    subtitle: String,
    actionLabel: String? = null
) = EmptyStateSpec(
    kind = EmptyStateKind.FIRST_RUN_SETUP,
    artwork = artwork,
    title = title,
    subtitle = subtitle,
    actionLabel = actionLabel
)

fun filterEmptyStateSpec(
    artwork: EmptyStateArtwork,
    title: String,
    subtitle: String
) = EmptyStateSpec(
    kind = EmptyStateKind.NO_RESULTS_FILTER,
    artwork = artwork,
    title = title,
    subtitle = subtitle
)

fun searchEmptyStateSpec(
    artwork: EmptyStateArtwork,
    title: String,
    subtitle: String
) = EmptyStateSpec(
    kind = EmptyStateKind.NO_RESULTS_SEARCH,
    artwork = artwork,
    title = title,
    subtitle = subtitle
)

fun allCaughtUpEmptyStateSpec(
    artwork: EmptyStateArtwork,
    title: String,
    subtitle: String
) = EmptyStateSpec(
    kind = EmptyStateKind.ALL_CAUGHT_UP,
    artwork = artwork,
    title = title,
    subtitle = subtitle
)

fun noAvailabilityEmptyStateSpec(
    artwork: EmptyStateArtwork,
    title: String,
    subtitle: String
) = EmptyStateSpec(
    kind = EmptyStateKind.NO_AVAILABILITY,
    artwork = artwork,
    title = title,
    subtitle = subtitle
)

fun noActivityYetEmptyStateSpec(
    artwork: EmptyStateArtwork,
    title: String,
    subtitle: String,
    actionLabel: String? = null
) = EmptyStateSpec(
    kind = EmptyStateKind.NO_ACTIVITY_YET,
    artwork = artwork,
    title = title,
    subtitle = subtitle,
    actionLabel = actionLabel
)

fun noActiveLiveDataEmptyStateSpec(
    artwork: EmptyStateArtwork,
    title: String,
    subtitle: String
) = EmptyStateSpec(
    kind = EmptyStateKind.NO_ACTIVE_LIVE_DATA,
    artwork = artwork,
    title = title,
    subtitle = subtitle
)

fun noMatchingRequirementEmptyStateSpec(
    artwork: EmptyStateArtwork,
    title: String,
    subtitle: String
) = EmptyStateSpec(
    kind = EmptyStateKind.NO_MATCHING_REQUIREMENT,
    artwork = artwork,
    title = title,
    subtitle = subtitle
)

@Composable
fun EmptyStateHost(
    spec: EmptyStateSpec,
    modifier: Modifier = Modifier,
    layoutStyle: EmptyStateLayoutStyle = EmptyStateLayoutStyle.CENTER_COMPACT,
    onAction: (() -> Unit)? = null,
    sectionBlendMode: SectionBlendMode = SectionBlendMode.FULL_HOST,
    sectionBackgroundOverride: Color? = null
) {
    val useHaloBlend = !layoutStyle.showFramedIllustration
    val defaultHaloAlpha = when (layoutStyle) {
        EmptyStateLayoutStyle.CENTER_WIDE -> 0.56f
        EmptyStateLayoutStyle.CENTER_COMPACT -> 0.54f
        EmptyStateLayoutStyle.MODAL_COMPACT -> 0.42f
        EmptyStateLayoutStyle.CARD_COMPACT -> 0f
    }
    val palette = spec.artwork.blendPalette()
    val resolvedSectionBackground = sectionBackgroundOverride ?: palette.sectionBackground
    val resolvedSectionBlendMode = when {
        sectionBlendMode == SectionBlendMode.FULL_HOST &&
            (layoutStyle == EmptyStateLayoutStyle.MODAL_COMPACT || layoutStyle == EmptyStateLayoutStyle.CARD_COMPACT) ->
            SectionBlendMode.PANEL
        else -> sectionBlendMode
    }
    val resolvedHaloAlpha = min(defaultHaloAlpha, palette.haloAlpha)
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (resolvedSectionBlendMode == SectionBlendMode.FULL_HOST) {
                    Modifier.background(resolvedSectionBackground.copy(alpha = 0.62f))
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        IllustratedEmptyState(
            illustrationRes = spec.artwork.drawableRes,
            title = spec.title,
            subtitle = spec.subtitle,
            actionLabel = spec.actionLabel,
            onAction = onAction,
            maxIllustrationWidthDp = layoutStyle.maxIllustrationWidthDp,
            maxTextWidthDp = layoutStyle.maxTextWidthDp,
            showFramedIllustration = layoutStyle.showFramedIllustration,
            enableHaloBlend = useHaloBlend,
            haloAlpha = resolvedHaloAlpha,
            sectionBackgroundColor = if (resolvedSectionBlendMode == SectionBlendMode.PANEL) resolvedSectionBackground else null,
            sectionBlendMode = if (resolvedSectionBlendMode == SectionBlendMode.PANEL) SectionBlendMode.PANEL else SectionBlendMode.NONE,
            paletteHaloOverride = palette.haloColor,
            paletteHaloAlphaOverride = resolvedHaloAlpha
        )
    }
}

/**
 * Polished empty state with icon, title, and action
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon with background
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Primary
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    contentColor = OnPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = actionLabel,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/**
 * Illustrated empty state for high-visibility list/first-use surfaces.
 * Uses lightweight local assets and a soft fade/slide to keep it polished
 * without heavy animation cost.
 */
@Composable
fun IllustratedEmptyState(
    @DrawableRes illustrationRes: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    maxIllustrationWidthDp: Int = 252,
    maxTextWidthDp: Int = 300,
    showFramedIllustration: Boolean = false,
    enableHaloBlend: Boolean = true,
    haloColor: Color = IllustrationCanvas,
    haloAlpha: Float = 0.54f,
    animationDurationMs: Int = 200,
    sectionBackgroundColor: Color? = null,
    sectionBlendMode: SectionBlendMode = SectionBlendMode.NONE,
    paletteHaloOverride: Color? = null,
    paletteHaloAlphaOverride: Float? = null
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val configuration = LocalConfiguration.current
    val resolvedHaloColor = paletteHaloOverride ?: haloColor
    val resolvedHaloAlpha = (paletteHaloAlphaOverride ?: haloAlpha).coerceIn(0f, 1f)
    val baseIllustrationMaxWidth = if (configuration.screenWidthDp >= 600) {
        max(maxIllustrationWidthDp, 280)
    } else {
        maxIllustrationWidthDp
    }
    val resolvedIllustrationMaxWidth = when {
        configuration.screenHeightDp <= 430 -> min(baseIllustrationMaxWidth, 196)
        configuration.screenHeightDp <= 520 -> min(baseIllustrationMaxWidth, 230)
        else -> baseIllustrationMaxWidth
    }
    val isShortHeight = configuration.screenHeightDp <= 520

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(animationDurationMs)) + slideInVertically(
            animationSpec = tween(animationDurationMs),
            initialOffsetY = { it / 12 }
        ),
        exit = fadeOut(animationSpec = tween(160))
    ) {
        val contentColumn: @Composable (Modifier) -> Unit = { contentModifier ->
            Column(
                modifier = contentModifier
                    .wrapContentWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 20.dp, vertical = if (isShortHeight) 10.dp else 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val illustrationModifier = Modifier
                    .widthIn(max = resolvedIllustrationMaxWidth.dp)
                    .aspectRatio(3f / 2f)

                if (showFramedIllustration) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            WeeloIllustration(
                                drawableRes = illustrationRes,
                                modifier = illustrationModifier,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                animateIn = false
                            )
                        }
                    }
                } else if (enableHaloBlend) {
                    val haloShape = RoundedCornerShape(26.dp)
                    Box(
                        modifier = illustrationModifier
                            .clip(haloShape)
                            .background(resolvedHaloColor.copy(alpha = resolvedHaloAlpha))
                            .border(
                                width = 1.dp,
                                color = White.copy(alpha = 0.78f),
                                shape = haloShape
                            )
                            .padding(if (isShortHeight) 2.dp else 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        WeeloIllustration(
                            drawableRes = illustrationRes,
                            modifier = Modifier.fillMaxSize(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            animateIn = false
                        )
                    }
                } else {
                    WeeloIllustration(
                        drawableRes = illustrationRes,
                        modifier = illustrationModifier,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        animateIn = false
                    )
                }

                Spacer(modifier = Modifier.height(if (isShortHeight) 12.dp else 16.dp))

                Text(
                    text = title,
                    modifier = Modifier.widthIn(max = maxTextWidthDp.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(if (isShortHeight) 6.dp else 8.dp))

                Text(
                    text = subtitle,
                    modifier = Modifier.widthIn(max = maxTextWidthDp.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                if (actionLabel != null && onAction != null) {
                    Spacer(modifier = Modifier.height(if (isShortHeight) 12.dp else 16.dp))
                    Button(
                        onClick = onAction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            contentColor = OnPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(text = actionLabel, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        val backgroundColor = sectionBackgroundColor
        when {
            backgroundColor == null || sectionBlendMode == SectionBlendMode.NONE -> {
                contentColumn(modifier)
            }
            sectionBlendMode == SectionBlendMode.PANEL -> {
                val panelShape = RoundedCornerShape(22.dp)
                Box(
                    modifier = modifier
                        .clip(panelShape)
                        .background(backgroundColor.copy(alpha = 0.90f))
                        .border(1.dp, White.copy(alpha = 0.72f), panelShape)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    contentColumn(Modifier)
                }
            }
            else -> {
                val hostShape = RoundedCornerShape(24.dp)
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .clip(hostShape)
                        .background(backgroundColor.copy(alpha = 0.78f))
                        .border(1.dp, White.copy(alpha = 0.68f), hostShape)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    contentColumn(Modifier)
                }
            }
        }
    }
}

/**
 * Empty state for vehicles list
 */
@Composable
fun EmptyVehicles(
    onAddVehicle: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyStateHost(
        spec = firstRunEmptyStateSpec(
            artwork = EmptyStateArtwork.VEHICLES_FIRST_RUN,
            title = stringResource(R.string.empty_vehicles_title),
            subtitle = stringResource(R.string.empty_vehicles_subtitle),
            actionLabel = stringResource(R.string.empty_action_add_vehicle)
        ),
        modifier = modifier,
        onAction = onAddVehicle
    )
}

/**
 * Empty state for drivers list
 */
@Composable
fun EmptyDrivers(
    onAddDriver: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyStateHost(
        spec = firstRunEmptyStateSpec(
            artwork = EmptyStateArtwork.DRIVERS_FIRST_RUN,
            title = stringResource(R.string.empty_drivers_title),
            subtitle = stringResource(R.string.empty_drivers_subtitle),
            actionLabel = stringResource(R.string.empty_action_add_driver)
        ),
        modifier = modifier,
        onAction = onAddDriver
    )
}

/**
 * Empty state for trips/bookings
 */
@Composable
fun EmptyTrips(
    modifier: Modifier = Modifier
) {
    EmptyStateHost(
        spec = noActivityYetEmptyStateSpec(
            artwork = EmptyStateArtwork.TRIPS_FIRST_RUN,
            title = stringResource(R.string.empty_trips_title),
            subtitle = stringResource(R.string.empty_trips_subtitle)
        ),
        modifier = modifier
    )
}

/**
 * Empty state for notifications
 */
@Composable
fun EmptyNotifications(
    modifier: Modifier = Modifier
) {
    EmptyStateHost(
        spec = allCaughtUpEmptyStateSpec(
            artwork = EmptyStateArtwork.NOTIFICATIONS_ALL_CAUGHT_UP,
            title = stringResource(R.string.empty_title_driver_notifications_caught_up),
            subtitle = stringResource(R.string.empty_subtitle_driver_notifications_caught_up)
        ),
        modifier = modifier
    )
}

// =============================================================================
// PHASE 1 FOUNDATION — ASYNC / ERROR / SKELETON HELPERS
// =============================================================================

/**
 * Standardized full-screen async state host for loading/error/empty/content flows.
 * Additive helper only; existing screens can adopt it incrementally.
 */
@Composable
fun AsyncScreenStateHost(
    isLoading: Boolean,
    errorMessage: String? = null,
    isEmpty: Boolean = false,
    modifier: Modifier = Modifier,
    emptyContent: (@Composable () -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    errorTitle: String = stringResource(R.string.ui_retry_error_title),
    loadingContent: @Composable () -> Unit = { FullScreenLoading() },
    content: @Composable () -> Unit
) {
    val visualState = when {
        isLoading -> AsyncScreenVisualState.LOADING
        !errorMessage.isNullOrBlank() -> AsyncScreenVisualState.ERROR
        isEmpty && emptyContent != null -> AsyncScreenVisualState.EMPTY
        else -> AsyncScreenVisualState.CONTENT
    }

    when (visualState) {
        AsyncScreenVisualState.LOADING -> loadingContent()
        AsyncScreenVisualState.ERROR -> RetryErrorStatePanel(
            title = errorTitle,
            message = errorMessage ?: stringResource(R.string.ui_retry_error_message_generic),
            onRetry = onRetry,
            modifier = modifier
        )
        AsyncScreenVisualState.EMPTY -> emptyContent?.invoke()
        AsyncScreenVisualState.CONTENT -> content()
    }
}

/**
 * Reusable retryable error panel with optional illustration support.
 */
@Composable
fun RetryErrorStatePanel(
    title: String,
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    @DrawableRes illustrationRes: Int? = null,
    actionLabel: String = stringResource(R.string.retry)
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (illustrationRes != null) {
            val palette = remember(illustrationRes) { blendPaletteForIllustrationRes(illustrationRes) }
            IllustratedEmptyState(
                illustrationRes = illustrationRes,
                title = title,
                subtitle = message,
                actionLabel = if (onRetry != null) actionLabel else null,
                onAction = onRetry,
                maxIllustrationWidthDp = 220,
                maxTextWidthDp = 320,
                showFramedIllustration = false,
                sectionBackgroundColor = palette?.sectionBackground,
                sectionBlendMode = if (palette != null) SectionBlendMode.FULL_HOST else SectionBlendMode.NONE,
                paletteHaloOverride = palette?.haloColor,
                paletteHaloAlphaOverride = palette?.haloAlpha
            )
        } else {
            ErrorState(
                title = title,
                message = message,
                onRetry = onRetry ?: {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Compact inline empty-state for sections inside already-loaded screens.
 */
@Composable
fun InlineSectionEmptyState(
    spec: EmptyStateSpec,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
    layoutStyle: EmptyStateLayoutStyle = EmptyStateLayoutStyle.CARD_COMPACT
) {
    val palette = remember(spec.artwork) { spec.artwork.blendPalette() }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(BorderRadius.large),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.small),
            contentAlignment = Alignment.Center
        ) {
            IllustratedEmptyState(
                illustrationRes = spec.artwork.drawableRes,
                title = spec.title,
                subtitle = spec.subtitle,
                actionLabel = spec.actionLabel,
                onAction = onAction,
                maxIllustrationWidthDp = layoutStyle.maxIllustrationWidthDp,
                maxTextWidthDp = layoutStyle.maxTextWidthDp,
                showFramedIllustration = layoutStyle.showFramedIllustration,
                modifier = Modifier.fillMaxWidth(),
                sectionBackgroundColor = palette.sectionBackground,
                sectionBlendMode = SectionBlendMode.PANEL,
                paletteHaloOverride = palette.haloColor,
                paletteHaloAlphaOverride = min(
                    when (layoutStyle) {
                        EmptyStateLayoutStyle.CENTER_WIDE -> 0.56f
                        EmptyStateLayoutStyle.CENTER_COMPACT -> 0.54f
                        EmptyStateLayoutStyle.MODAL_COMPACT -> 0.42f
                        EmptyStateLayoutStyle.CARD_COMPACT -> 0.36f
                    },
                    palette.haloAlpha
                )
            )
        }
    }
}

@Composable
private fun SectionSkeletonLine(
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    height: androidx.compose.ui.unit.Dp = 14.dp
) {
    val transition = rememberInfiniteTransition(label = "section_skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(AnimationDuration.skeleton, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction.coerceIn(0.1f, 1f))
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(SkeletonBase.copy(alpha = alpha))
    )
}

/**
 * Generic section skeleton block for async-heavy sections (Phase 1 base primitive).
 */
@Composable
fun SectionSkeletonBlock(
    modifier: Modifier = Modifier,
    titleLineWidthFraction: Float = 0.42f,
    rowCount: Int = 3,
    showLeadingAvatar: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(BorderRadius.large),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPaddingLarge),
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            SectionSkeletonLine(widthFraction = titleLineWidthFraction, height = 18.dp)
            Spacer(modifier = Modifier.height(Spacing.xs))

            repeat(max(1, rowCount)) { index ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
                ) {
                    if (showLeadingAvatar && index == 0) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SkeletonBase.copy(alpha = 0.7f))
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        SectionSkeletonLine(widthFraction = if (index % 2 == 0) 0.84f else 0.66f)
                        SectionSkeletonLine(widthFraction = if (index % 2 == 0) 0.48f else 0.38f, height = 12.dp)
                    }
                }
                if (index < rowCount - 1) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                }
            }
        }
    }
}

// =============================================================================
// ERROR STATES
// =============================================================================

/**
 * Error state with retry option
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Something went wrong"
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Error icon with theme colors
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(ErrorLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Error
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedButton(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Try Again",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Network error state
 */
@Composable
fun NetworkErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    ErrorState(
        title = "No Internet Connection",
        message = "Please check your internet connection and try again",
        onRetry = onRetry,
        modifier = modifier
    )
}

// =============================================================================
// LOADING BUTTON
// =============================================================================

/**
 * Premium Loading Button with theme colors
 */
@Composable
fun LoadingButton(
    onClick: () -> Unit,
    text: String,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Primary,
            contentColor = OnPrimary,
            disabledContainerColor = Primary.copy(alpha = 0.4f),
            disabledContentColor = OnPrimary.copy(alpha = 0.6f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        AnimatedContent(
            targetState = isLoading,
            transitionSpec = {
                fadeIn(animationSpec = tween(200)) togetherWith
                fadeOut(animationSpec = tween(200))
            },
            label = "loading"
        ) { loading ->
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = OnPrimary,
                    strokeWidth = 2.5.dp
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

// =============================================================================
// ANIMATED VISIBILITY HELPERS
// =============================================================================

/**
 * Fade in/out content based on condition
 */
@Composable
fun FadeVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier,
        content = content
    )
}

/**
 * Slide up content based on condition
 */
@Composable
fun SlideUpVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
        content = content
    )
}
