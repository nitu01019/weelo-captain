package com.weelo.logistics.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.R
import com.weelo.logistics.ui.theme.*

/**
 * User profile data for the navigation drawer
 * Populated from backend API /profile endpoint
 */
data class DrawerUserProfile(
    val id: String,
    val phone: String,
    val name: String,
    val role: String,  // "transporter" or "driver"
    val email: String? = null,
    val businessName: String? = null,  // For transporter
    val profilePhotoUrl: String? = null,
    val isVerified: Boolean = false
)

/**
 * Navigation menu item
 */
data class DrawerMenuItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val badgeCount: Int = 0,
    val onClick: () -> Unit
)

/**
 * Modern Navigation Drawer - Used by both Transporter and Driver dashboards
 * 
 * Features:
 * - User profile header with real data from backend
 * - Role-specific menu items
 * - Logout functionality
 * - Clean Material 3 design
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeloNavigationDrawer(
    userProfile: DrawerUserProfile?,
    isLoading: Boolean = false,
    selectedItemId: String = "dashboard",
    menuItems: List<DrawerMenuItem>,
    onProfileClick: () -> Unit,
    onLogout: () -> Unit,
    content: @Composable () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = Color.White
            ) {
                DrawerContent(
                    userProfile = userProfile,
                    isLoading = isLoading,
                    selectedItemId = selectedItemId,
                    menuItems = menuItems,
                    onProfileClick = onProfileClick,
                    onLogout = onLogout,
                    onCloseDrawer = { /* Will be handled by drawerState */ }
                )
            }
        },
        content = content
    )
}

/**
 * Internal drawer content - exposed for use in screens
 */
@Composable
fun DrawerContentInternal(
    userProfile: DrawerUserProfile?,
    isLoading: Boolean,
    selectedItemId: String,
    menuItems: List<DrawerMenuItem>,
    onProfileClick: () -> Unit,
    onLogout: () -> Unit
) {
    DrawerContent(
        userProfile = userProfile,
        isLoading = isLoading,
        selectedItemId = selectedItemId,
        menuItems = menuItems,
        onProfileClick = onProfileClick,
        onLogout = onLogout,
        onCloseDrawer = {}
    )
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun DrawerContent(
    userProfile: DrawerUserProfile?,
    isLoading: Boolean,
    selectedItemId: String,
    menuItems: List<DrawerMenuItem>,
    onProfileClick: () -> Unit,
    onLogout: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(Color.White)
    ) {
        // Profile Header
        DrawerHeader(
            userProfile = userProfile,
            isLoading = isLoading,
            onProfileClick = onProfileClick
        )
        
        Divider(color = TextDisabled.copy(alpha = 0.2f))
        
        // Menu Items
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
            menuItems.forEach { item ->
                DrawerMenuItemRow(
                    item = item,
                    isSelected = item.id == selectedItemId
                )
            }
        }
        
        Divider(color = TextDisabled.copy(alpha = 0.2f))
        
        // Logout Button
        DrawerLogoutButton(onLogout = onLogout)
        
        // App Version
        Text(
            text = stringResource(R.string.app_version_format, "1.0.0"),
            style = MaterialTheme.typography.bodySmall,
            color = TextDisabled,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.CenterHorizontally)
        )
    }
}

// =============================================================================
// DRAWER HEADER SHIMMER BRUSH
// =============================================================================
// Creates a smooth shimmer animation for the drawer header loading state.
// Uses the same infinite transition pattern as SkeletonLoading.kt but with
// white-on-gold colors to match the drawer's gradient background.
//
// PERFORMANCE: Single shared transition for all shimmer elements.
// MODULARITY: Private to this file — not exposed outside NavigationDrawer.
// =============================================================================

@Composable
private fun drawerShimmerBrush(): Brush {
    val shimmerTransition = rememberInfiniteTransition(label = "drawer_shimmer")
    val shimmerOffset by shimmerTransition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drawer_shimmer_offset"
    )
    return Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.15f),
            Color.White.copy(alpha = 0.40f),
            Color.White.copy(alpha = 0.15f)
        ),
        start = Offset(shimmerOffset, 0f),
        end = Offset(shimmerOffset + 300f, 100f)
    )
}

@Composable
private fun DrawerHeader(
    userProfile: DrawerUserProfile?,
    isLoading: Boolean,
    onProfileClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Primary, PrimaryDark)
                )
            )
            .clickable { onProfileClick() }
            .padding(24.dp)
    ) {
        // =====================================================================
        // SMOOTH CROSSFADE between loading → loaded states (300ms)
        // Prevents the abrupt "flicker" when dashboard state transitions
        // from Loading → Success.
        //
        // PERFORMANCE: Crossfade uses hardware-accelerated alpha blending.
        // =====================================================================
        Crossfade(
            targetState = if (isLoading) "loading" else if (userProfile != null) "profile" else "empty",
            animationSpec = tween(durationMillis = 300),
            label = "drawer_header_crossfade"
        ) { state ->
        when (state) {
        "loading" -> {
            // Shimmer loading state — smooth animated gradient instead of static grey
            val shimmerBrush = drawerShimmerBrush()
            Column {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(shimmerBrush)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
            }
        }
        "profile" -> {
            // Profile loaded — show real data
            if (userProfile != null) {
            Column {
                // Profile Avatar
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    // First letter of name or phone
                    val initial = userProfile.name.firstOrNull()?.uppercase() 
                        ?: userProfile.phone.lastOrNull()?.toString() 
                        ?: "U"
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Secondary
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // User Name
                Text(
                    text = userProfile.name.ifEmpty { stringResource(R.string.set_your_name) },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Phone Number
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "+91 ${userProfile.phone}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                
                // Business Name (for transporter)
                if (userProfile.role == "transporter" && !userProfile.businessName.isNullOrEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = userProfile.businessName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // Role Badge
                Surface(
                    modifier = Modifier.padding(top = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (userProfile.role == "transporter") 
                                Icons.Default.LocalShipping else Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = userProfile.role.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        if (userProfile.isVerified) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = stringResource(R.string.cd_verified),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                
                // Edit Profile hint
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tap_edit_profile),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            } // end if (userProfile != null)
        }
        else -> {
            // "empty" — No profile data, not loading
            Column {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.not_logged_in),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        } // end when
        } // end Crossfade
    }
}

@Composable
private fun DrawerMenuItemRow(
    item: DrawerMenuItem,
    isSelected: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clickable { item.onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) PrimaryLight else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSelected) item.selectedIcon else item.icon,
                contentDescription = item.title,
                tint = if (isSelected) Secondary else TextSecondary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) Secondary else TextPrimary,
                modifier = Modifier.weight(1f)
            )
            
            // Badge
            if (item.badgeCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = Primary
                ) {
                    Text(
                        text = if (item.badgeCount > 99) "99+" else item.badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = OnPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerLogoutButton(onLogout: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable { onLogout() },
        shape = RoundedCornerShape(12.dp),
        color = Error.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Logout,
                contentDescription = stringResource(R.string.cd_logout),
                tint = Error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.logout),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Error
            )
        }
    }
}

/**
 * Helper function to create transporter menu items
 * NOTE: Broadcasts menu removed - broadcasts shown via overlay only
 * 
 * @param strings Map of localized strings. Required keys:
 *   - "dashboard", "my_fleet", "my_drivers", "trips", "settings"
 */
@Suppress("UNUSED_PARAMETER")
fun createTransporterMenuItems(
    onDashboard: () -> Unit,
    onFleet: () -> Unit,
    onDrivers: () -> Unit,
    onTrips: () -> Unit,
    onBroadcasts: () -> Unit,  // Kept for compatibility, but not used
    onSettings: () -> Unit,
    notificationCount: Int = 0,
    strings: Map<String, String> = emptyMap()
): List<DrawerMenuItem> = listOf(
    DrawerMenuItem(
        id = "dashboard",
        title = strings["dashboard"] ?: "Dashboard",
        icon = Icons.Outlined.Dashboard,
        selectedIcon = Icons.Filled.Dashboard,
        onClick = onDashboard
    ),
    DrawerMenuItem(
        id = "fleet",
        title = strings["my_fleet"] ?: "My Fleet",
        icon = Icons.Outlined.LocalShipping,
        selectedIcon = Icons.Filled.LocalShipping,
        onClick = onFleet
    ),
    DrawerMenuItem(
        id = "drivers",
        title = strings["my_drivers"] ?: "My Drivers",
        icon = Icons.Outlined.People,
        selectedIcon = Icons.Filled.People,
        onClick = onDrivers
    ),
    DrawerMenuItem(
        id = "trips",
        title = strings["trips"] ?: "Trips",
        icon = Icons.Outlined.Route,
        selectedIcon = Icons.Filled.Route,
        onClick = onTrips
    ),
    // Broadcasts menu item REMOVED - broadcasts shown via overlay
    DrawerMenuItem(
        id = "settings",
        title = strings["settings"] ?: "Settings",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Filled.Settings,
        onClick = onSettings
    )
)

/**
 * Helper function to create driver menu items
 * 
 * @param strings Map of localized strings. Required keys:
 *   - "dashboard", "trip_history", "earnings", "documents", "settings"
 */
@Suppress("UNUSED_PARAMETER")
fun createDriverMenuItems(
    onDashboard: () -> Unit,
    onTripHistory: () -> Unit,
    onEarnings: () -> Unit,
    onDocuments: () -> Unit,
    onSettings: () -> Unit,
    notificationCount: Int = 0,
    strings: Map<String, String> = emptyMap()
): List<DrawerMenuItem> = listOf(
    DrawerMenuItem(
        id = "dashboard",
        title = strings["dashboard"] ?: "Dashboard",
        icon = Icons.Outlined.Dashboard,
        selectedIcon = Icons.Filled.Dashboard,
        onClick = onDashboard
    ),
    DrawerMenuItem(
        id = "trips",
        title = strings["trip_history"] ?: "Trip History",
        icon = Icons.Outlined.History,
        selectedIcon = Icons.Filled.History,
        onClick = onTripHistory
    ),
    DrawerMenuItem(
        id = "earnings",
        title = strings["earnings"] ?: "Earnings",
        icon = Icons.Outlined.AccountBalanceWallet,
        selectedIcon = Icons.Filled.AccountBalanceWallet,
        onClick = onEarnings
    ),
    DrawerMenuItem(
        id = "documents",
        title = strings["documents"] ?: "Documents",
        icon = Icons.Outlined.Description,
        selectedIcon = Icons.Filled.Description,
        onClick = onDocuments
    ),
    DrawerMenuItem(
        id = "settings",
        title = strings["settings"] ?: "Settings",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Filled.Settings,
        onClick = onSettings
    )
)
