package com.weelo.logistics.ui.components

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            text = "Weelo Captain v1.0.0",
            style = MaterialTheme.typography.bodySmall,
            color = TextDisabled,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.CenterHorizontally)
        )
    }
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
                    colors = listOf(Primary, Primary.copy(alpha = 0.8f))
                )
            )
            .clickable { onProfileClick() }
            .padding(24.dp)
    ) {
        if (isLoading) {
            // Loading state
            Column {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }
        } else if (userProfile != null) {
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
                        color = Primary
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // User Name
                Text(
                    text = userProfile.name.ifEmpty { "Set your name" },
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
                                contentDescription = "Verified",
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
                        text = "Tap to edit profile",
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
        } else {
            // No profile - prompt to login
            Column {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Not logged in",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
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
        color = if (isSelected) Primary.copy(alpha = 0.1f) else Color.Transparent
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
                tint = if (isSelected) Primary else TextSecondary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) Primary else TextPrimary,
                modifier = Modifier.weight(1f)
            )
            
            // Badge
            if (item.badgeCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = Error
                ) {
                    Text(
                        text = if (item.badgeCount > 99) "99+" else item.badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
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
                contentDescription = "Logout",
                tint = Error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Logout",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Error
            )
        }
    }
}

/**
 * Helper function to create transporter menu items
 */
fun createTransporterMenuItems(
    onDashboard: () -> Unit,
    onFleet: () -> Unit,
    onDrivers: () -> Unit,
    onTrips: () -> Unit,
    onBroadcasts: () -> Unit,
    onSettings: () -> Unit,
    notificationCount: Int = 0
): List<DrawerMenuItem> = listOf(
    DrawerMenuItem(
        id = "dashboard",
        title = "Dashboard",
        icon = Icons.Outlined.Dashboard,
        selectedIcon = Icons.Filled.Dashboard,
        onClick = onDashboard
    ),
    DrawerMenuItem(
        id = "fleet",
        title = "My Fleet",
        icon = Icons.Outlined.LocalShipping,
        selectedIcon = Icons.Filled.LocalShipping,
        onClick = onFleet
    ),
    DrawerMenuItem(
        id = "drivers",
        title = "My Drivers",
        icon = Icons.Outlined.People,
        selectedIcon = Icons.Filled.People,
        onClick = onDrivers
    ),
    DrawerMenuItem(
        id = "trips",
        title = "Trips",
        icon = Icons.Outlined.Route,
        selectedIcon = Icons.Filled.Route,
        onClick = onTrips
    ),
    DrawerMenuItem(
        id = "broadcasts",
        title = "Broadcasts",
        icon = Icons.Outlined.Campaign,
        selectedIcon = Icons.Filled.Campaign,
        badgeCount = notificationCount,
        onClick = onBroadcasts
    ),
    DrawerMenuItem(
        id = "settings",
        title = "Settings",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Filled.Settings,
        onClick = onSettings
    )
)

/**
 * Helper function to create driver menu items
 */
fun createDriverMenuItems(
    onDashboard: () -> Unit,
    onTripHistory: () -> Unit,
    onEarnings: () -> Unit,
    onDocuments: () -> Unit,
    onSettings: () -> Unit,
    notificationCount: Int = 0
): List<DrawerMenuItem> = listOf(
    DrawerMenuItem(
        id = "dashboard",
        title = "Dashboard",
        icon = Icons.Outlined.Dashboard,
        selectedIcon = Icons.Filled.Dashboard,
        onClick = onDashboard
    ),
    DrawerMenuItem(
        id = "trips",
        title = "Trip History",
        icon = Icons.Outlined.History,
        selectedIcon = Icons.Filled.History,
        onClick = onTripHistory
    ),
    DrawerMenuItem(
        id = "earnings",
        title = "Earnings",
        icon = Icons.Outlined.AccountBalanceWallet,
        selectedIcon = Icons.Filled.AccountBalanceWallet,
        onClick = onEarnings
    ),
    DrawerMenuItem(
        id = "documents",
        title = "Documents",
        icon = Icons.Outlined.Description,
        selectedIcon = Icons.Filled.Description,
        onClick = onDocuments
    ),
    DrawerMenuItem(
        id = "settings",
        title = "Settings",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Filled.Settings,
        onClick = onSettings
    )
)
