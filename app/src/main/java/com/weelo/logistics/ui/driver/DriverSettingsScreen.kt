package com.weelo.logistics.ui.driver

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weelo.logistics.R
import com.weelo.logistics.data.preferences.DriverPreferences
import com.weelo.logistics.ui.components.PrimaryTopBar
import com.weelo.logistics.ui.components.SectionCard
import com.weelo.logistics.ui.components.responsiveHorizontalPadding
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Driver Settings Screen - PRD-03 Compliant
 * App settings and preferences
 *
 * LANGUAGE CHANGE: Inline ModalBottomSheet with language list.
 * No separate screen. No navigation. No Activity.recreate().
 * Uses MainActivity.updateLocale() for instant dashboard update.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverSettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    // Get MainActivity reference for updateLocale().
    // LocalContext is a locale-wrapped context from createConfigurationContext()
    // which does NOT have MainActivity in its ContextWrapper chain.
    // Solution: Use the Activity from LocalView's context (always the real Activity).
    val localView = androidx.compose.ui.platform.LocalView.current
    val mainActivity = remember {
        var ctx: android.content.Context = localView.context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is com.weelo.logistics.MainActivity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    
    // =========================================================================
    // BLINKIT-STYLE LANGUAGE SWITCH OVERLAY
    //
    // When user changes language from Settings bottom sheet:
    //   1. Dismiss sheet
    //   2. Show subtle overlay (covers screen → no flash/flicker)
    //   3. Save language to cache + SharedPrefs (synchronous)
    //   4. Update locale (triggers recomposition under overlay)
    //   5. Fire backend save (async, non-blocking)
    //   6. Hold overlay for 300ms minimum (smooth, no jarring flash)
    //   7. Fade out overlay → dashboard shows fully translated
    //
    // PERFORMANCE: Overlay is a simple Box with alpha. Zero GPU cost.
    // Total visible delay: 300-400ms. Feels premium and intentional.
    // =========================================================================
    var showLanguageOverlay by remember { mutableStateOf(false) }
    val overlayCoroutineScope = rememberCoroutineScope()
    
    // Get current language from DriverPreferences (same source as onboarding check)
    val driverPrefs = remember { DriverPreferences.getInstance(context) }
    val currentLanguage by driverPrefs.selectedLanguage.collectAsState(initial = "en")
    
    val application = remember(context) {
        (context as? Activity)?.application
            ?: (context.applicationContext as android.app.Application)
    }
    // LanguageViewModel for async backend save
    val languageViewModel: LanguageViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory(application)
    )
    
    // Responsive layout
    val horizontalPadding = responsiveHorizontalPadding()
    
    Box(modifier = Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(title = stringResource(R.string.settings), onBackClick = onNavigateBack)
        
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard(stringResource(R.string.notifications)) {
                SettingRow(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.push_notifications),
                    subtitle = stringResource(R.string.push_notifications_desc),
                    trailing = {
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { notificationsEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Primary)
                        )
                    }
                )
            }
            
            SectionCard(stringResource(R.string.preferences)) {
                SettingRow(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.language),
                    subtitle = getLanguageName(currentLanguage.ifEmpty { "en" }),
                    onClick = { showLanguageSheet = true }
                )
                Divider()
                SettingRow(
                    icon = Icons.Default.DarkMode,
                    title = stringResource(R.string.theme),
                    subtitle = stringResource(R.string.theme_light),
                    onClick = { /* TODO */ }
                )
            }
            
            SectionCard(stringResource(R.string.account)) {
                SettingRow(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.privacy_policy),
                    subtitle = stringResource(R.string.privacy_policy_desc),
                    onClick = { /* TODO */ }
                )
                Divider()
                SettingRow(
                    icon = Icons.Default.Description,
                    title = stringResource(R.string.terms_conditions),
                    subtitle = stringResource(R.string.terms_desc),
                    onClick = { /* TODO */ }
                )
            }
            
            SectionCard(stringResource(R.string.support)) {
                SettingRow(
                    icon = Icons.Default.Help,
                    title = stringResource(R.string.help_support),
                    subtitle = stringResource(R.string.help_support_desc),
                    onClick = { /* TODO */ }
                )
                Divider()
                SettingRow(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.about),
                    subtitle = stringResource(R.string.about_version_format, "1.0.0"),
                    onClick = { /* TODO */ }
                )
            }
            
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(Error.copy(alpha = 0.1f))
            ) {
                SettingRow(
                    icon = Icons.Default.Logout,
                    title = stringResource(R.string.logout),
                    subtitle = stringResource(R.string.logout_subtitle),
                    iconTint = Error,
                    titleColor = Error,
                    onClick = { showLogoutDialog = true }
                )
            }
        }
    }
    
    // =========================================================================
    // LOGOUT CONFIRMATION DIALOG
    // =========================================================================
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.Default.Logout, stringResource(R.string.cd_logout), tint = Error) },
            title = { Text(stringResource(R.string.logout_confirmation_title)) },
            text = { Text(stringResource(R.string.logout_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text(stringResource(R.string.logout), color = Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // =========================================================================
    // LANGUAGE SELECTION BOTTOM SHEET
    //
    // Simple list of languages. No TTS, no phone mockup, no animations.
    // On selection:
    //   1. Save locally via DriverPreferences (commit = synchronous)
    //   2. Save to backend via LanguageViewModel (async, non-blocking)
    //   3. Update locale instantly via MainActivity.updateLocale()
    //   4. Dismiss sheet
    //
    // Dashboard text updates INSTANTLY — no reload, no restart.
    //
    // MODULARITY: Self-contained in this composable. No navigation needed.
    // SCALABILITY: O(1) state change. Backend save is fire-and-forget.
    // ROLE ISOLATION: Only shown in Driver Settings. Transporter unaffected.
    // =========================================================================
    if (showLanguageSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        
        ModalBottomSheet(
            onDismissRequest = { showLanguageSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = Surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                // Header
                Text(
                    text = stringResource(R.string.select_language),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                
                Spacer(Modifier.height(8.dp))
                
                // Language list
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    items(
                        items = settingsLanguageList,
                        key = { it.code }
                    ) { lang ->
                        val isSelected = currentLanguage == lang.code
                        
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = lang.nativeName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Primary else TextPrimary
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = lang.englishName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = if (isSelected) Primary else TextSecondary
                                )
                            },
                            trailingContent = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Primary
                                    )
                                }
                            },
                            modifier = Modifier.clickable {
                                if (!isSelected) {
                                    // 1. Dismiss sheet FIRST (so overlay covers clean screen)
                                    showLanguageSheet = false
                                    
                                    // 2. Show overlay → save → update locale → fade out
                                    overlayCoroutineScope.launch {
                                        showLanguageOverlay = true
                                        val overlayStartTime = System.currentTimeMillis()
                                        
                                        // 3. Save to DataStore + SharedPreferences (synchronous commit)
                                        //    MUST complete BEFORE updateLocale() so cold start
                                        //    reads the new language from SharedPreferences.
                                        driverPrefs.saveLanguage(lang.code)
                                        
                                        // 4. Update locale → triggers recomposition under overlay
                                        //    All stringResource() calls resolve to new language.
                                        //    Uses mainActivity (walked up ContextWrapper chain)
                                        //    because LocalContext is a locale-wrapped ContextWrapper,
                                        //    NOT the Activity directly.
                                        mainActivity?.updateLocale(lang.code)
                                        
                                        // 5. Fire backend save (async, non-blocking)
                                        //    Uses viewModelScope internally — fire-and-forget.
                                        languageViewModel.saveLanguagePreference(lang.code)
                                        
                                        // 6. Ensure minimum 300ms overlay (Blinkit-style polish)
                                        //    Even if save + locale update is instant (~2ms),
                                        //    the overlay stays for 300ms to feel smooth.
                                        val elapsed = System.currentTimeMillis() - overlayStartTime
                                        if (elapsed < 300) {
                                            delay(300 - elapsed)
                                        }
                                        
                                        // 7. Fade out overlay → fully translated UI visible
                                        showLanguageOverlay = false
                                        
                                        timber.log.Timber.i(
                                            "🌐 Settings language changed: ${lang.code} → instant update (${System.currentTimeMillis() - overlayStartTime}ms)"
                                        )
                                    }
                                } else {
                                    // Same language selected — just dismiss
                                    showLanguageSheet = false
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (isSelected) Primary.copy(alpha = 0.08f)
                                    else Surface
                            )
                        )
                    }
                }
            }
        }
    }
    
    // =========================================================================
    // BLINKIT-STYLE LANGUAGE SWITCH OVERLAY
    //
    // Premium animated overlay with bouncing vehicle icons (truck, tempo, tractor).
    // Covers the screen so user never sees partial translation or flicker.
    // Fades in/out smoothly (200ms each). Minimum visible time: 300ms.
    //
    // ANIMATION: Three vehicle icons bounce vertically with staggered timing.
    // Inspired by Blinkit's grocery bag bounce loader — same feel, Weelo branding.
    //
    // PERFORMANCE: 3 Image composables + hardware-accelerated graphicsLayer transforms.
    // infiniteTransition is GPU-accelerated. Zero layout cost. ~0.5ms per frame.
    // =========================================================================
    AnimatedVisibility(
        visible = showLanguageOverlay,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "vehicle_bounce")
        
        // Three vehicles bounce with staggered timing (0ms, 150ms, 300ms offset)
        // Each bounces up 12dp and back over 600ms — smooth, not frantic
        val bounce1 = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -12f,
            animationSpec = infiniteRepeatable(
                animation = tween(300, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bounce1"
        )
        val bounce2 = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -12f,
            animationSpec = infiniteRepeatable(
                animation = tween(300, delayMillis = 150, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bounce2"
        )
        val bounce3 = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -12f,
            animationSpec = infiniteRepeatable(
                animation = tween(300, delayMillis = 300, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bounce3"
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface.copy(alpha = 0.95f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Bouncing vehicle icons row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mini truck (tempo)
                    Image(
                        painter = painterResource(R.drawable.vehicle_mini),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer { translationY = bounce1.value }
                    )
                    // Open truck
                    Image(
                        painter = painterResource(R.drawable.vehicle_open),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer { translationY = bounce2.value }
                    )
                    // Container truck
                    Image(
                        painter = painterResource(R.drawable.vehicle_container),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer { translationY = bounce3.value }
                    )
                }
                
                // Subtle loading dots bar
                LinearProgressIndicator(
                    modifier = Modifier
                        .width(120.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Primary,
                    trackColor = Primary.copy(alpha = 0.15f)
                )
            }
        }
    }
    } // End outer Box
}

@Composable
fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconTint: androidx.compose.ui.graphics.Color = Primary,
    titleColor: androidx.compose.ui.graphics.Color = TextPrimary,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = titleColor
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(Icons.Default.ChevronRight, null, tint = TextSecondary)
        }
    }
}

/**
 * Language item for the Settings bottom sheet.
 * Lightweight data class — no TTS, no locale object, no background text.
 * Only what the list UI needs.
 */
private data class SettingsLanguageItem(
    val code: String,
    val nativeName: String,
    val englishName: String
)

/**
 * All supported languages for the Settings bottom sheet.
 * Same set as LanguageSelectionScreen but without onboarding UI data.
 * Order: Hindi first (most common), then alphabetical by English name.
 */
private val settingsLanguageList = listOf(
    SettingsLanguageItem("hi", "हिन्दी", "Hindi"),
    SettingsLanguageItem("en", "English", "English"),
    SettingsLanguageItem("bn", "বাংলা", "Bengali"),
    SettingsLanguageItem("gu", "ગુજરાતી", "Gujarati"),
    SettingsLanguageItem("kn", "ಕನ್ನಡ", "Kannada"),
    SettingsLanguageItem("ml", "മലയാളം", "Malayalam"),
    SettingsLanguageItem("mr", "मराठी", "Marathi"),
    SettingsLanguageItem("or", "ଓଡ଼ିଆ", "Odia"),
    SettingsLanguageItem("pa", "ਪੰਜਾਬੀ", "Punjabi"),
    SettingsLanguageItem("raj", "राजस्थानी", "Rajasthani"),
    SettingsLanguageItem("ta", "தமிழ்", "Tamil"),
    SettingsLanguageItem("te", "తెలుగు", "Telugu")
)

/**
 * Get display name for language code (used in Settings row subtitle)
 */
private fun getLanguageName(code: String): String {
    return settingsLanguageList.firstOrNull { it.code == code }
        ?.let { "${it.nativeName} (${it.englishName})" }
        ?: "English"
}
