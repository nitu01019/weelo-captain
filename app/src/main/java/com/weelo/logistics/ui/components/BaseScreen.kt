package com.weelo.logistics.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.ui.theme.Primary
import com.weelo.logistics.ui.theme.Surface
import com.weelo.logistics.ui.theme.TextPrimary
import com.weelo.logistics.ui.theme.TextSecondary

/**
 * =============================================================================
 * BASE SCREEN - Consistent Screen Layout
 * =============================================================================
 * 
 * Provides a consistent base layout for all screens with:
 * - Top app bar with navigation
 * - Loading states
 * - Error handling
 * - Smooth animations
 * =============================================================================
 */

/**
 * Screen state for managing loading/error/content states
 */
sealed class ScreenState<out T> {
    object Loading : ScreenState<Nothing>()
    data class Error(val message: String, val retry: (() -> Unit)? = null) : ScreenState<Nothing>()
    data class Success<T>(val data: T) : ScreenState<T>()
    object Empty : ScreenState<Nothing>()
}

/**
 * Base screen with top app bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseScreen(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    isLoading: Boolean = false,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    navigationIcon?.invoke() ?: run {
                        if (onNavigateBack != null) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = TextPrimary
                                )
                            }
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = TextPrimary
                )
            )
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        containerColor = Surface
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main content with animation
            AnimatedVisibility(
                visible = !isLoading && error == null,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(200))
            ) {
                content(PaddingValues(0.dp))
            }
            
            // Loading state
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FullScreenLoading()
            }
            
            // Error state
            AnimatedVisibility(
                visible = error != null && !isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                error?.let { errorMessage ->
                    ErrorState(
                        message = errorMessage,
                        onRetry = onRetry ?: {}
                    )
                }
            }
            
            // Top loading bar for refresh
            TopLoadingBar(
                isLoading = isLoading,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

/**
 * Base lazy list screen with automatic loading states
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LazyListScreen(
    title: String,
    items: List<T>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    emptyState: @Composable () -> Unit = {},
    skeletonContent: @Composable () -> Unit = { SkeletonList() },
    floatingActionButton: @Composable () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    itemContent: LazyListScope.() -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        if (!isLoading && items.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "(${items.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = TextPrimary
                )
            )
        },
        floatingActionButton = floatingActionButton,
        containerColor = Surface
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                // Initial loading - show skeleton
                isLoading && items.isEmpty() -> {
                    Box(modifier = Modifier.padding(contentPadding)) {
                        skeletonContent()
                    }
                }
                // Empty state
                !isLoading && items.isEmpty() -> {
                    emptyState()
                }
                // Content
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = contentPadding,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemContent()
                    }
                }
            }
        }
    }
}

/**
 * Screen state handler - renders correct UI based on state
 */
@Composable
fun <T> ScreenStateHandler(
    state: ScreenState<T>,
    loadingContent: @Composable () -> Unit = { FullScreenLoading() },
    errorContent: @Composable (String, (() -> Unit)?) -> Unit = { msg, retry ->
        ErrorState(message = msg, onRetry = retry ?: {})
    },
    emptyContent: @Composable () -> Unit = {},
    successContent: @Composable (T) -> Unit
) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith
            fadeOut(animationSpec = tween(200))
        },
        label = "screenState"
    ) { currentState ->
        when (currentState) {
            is ScreenState.Loading -> loadingContent()
            is ScreenState.Error -> errorContent(currentState.message, currentState.retry)
            is ScreenState.Empty -> emptyContent()
            is ScreenState.Success -> successContent(currentState.data)
        }
    }
}

/**
 * Animated counter for numbers
 */
@Composable
fun AnimatedCounter(
    count: Int,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium
) {
    var oldCount by remember { mutableIntStateOf(count) }
    
    SideEffect {
        oldCount = count
    }
    
    AnimatedContent(
        targetState = count,
        transitionSpec = {
            if (targetState > initialState) {
                slideInVertically { -it } + fadeIn() togetherWith
                slideOutVertically { it } + fadeOut()
            } else {
                slideInVertically { it } + fadeIn() togetherWith
                slideOutVertically { -it } + fadeOut()
            }
        },
        label = "counter",
        modifier = modifier
    ) { targetCount ->
        Text(
            text = targetCount.toString(),
            style = style,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}
