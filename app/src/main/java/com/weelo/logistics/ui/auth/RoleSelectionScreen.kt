package com.weelo.logistics.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.weelo.logistics.R
import com.weelo.logistics.ui.components.CardArtwork
import com.weelo.logistics.ui.components.InlineInfoBannerCard
import com.weelo.logistics.ui.components.MediaHeaderCard
import com.weelo.logistics.ui.components.PrimaryButton
import com.weelo.logistics.ui.components.bannerGeneratedArtSpec
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.ui.components.rememberScreenConfig
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/**
 * Role Selection Screen - PRD-01 Compliant
 * Simple 2-card selection: Transporter or Driver
 * Added guide icon with dialog to explain roles
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoleSelectionScreen(
    onRoleSelected: (String) -> Unit
) {
    var showGuideDialog by remember { mutableStateOf(false) }
    val screenConfig = rememberScreenConfig()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val portraitPagerHeight = (screenConfig.screenWidth * (2f / 3f))
        .coerceAtLeast(224.dp)
        .coerceAtMost(if (screenConfig.screenHeight < 700.dp) 252.dp else 286.dp)
    val pagerHeight = when {
        screenConfig.isLandscape -> 208.dp
        else -> portraitPagerHeight
    }
    val sheetMinHeight = if (screenConfig.isLandscape) 280.dp else 360.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF113C95))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Spacer(modifier = Modifier.height(if (screenConfig.isLandscape) 4.dp else 6.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(pagerHeight)
        ) {
            page ->
            val drawable = if (page == 0) {
                R.drawable.auth_role_carousel_vehicles
            } else {
                R.drawable.auth_role_carousel_team
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(Color(0xFF113C95)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = drawable),
                    contentDescription = if (page == 0) {
                        stringResource(R.string.auth_role_cd_fleet_illustration)
                    } else {
                        stringResource(R.string.auth_role_cd_team_illustration)
                    },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (screenConfig.isLandscape) 2.dp else 0.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(2) { index ->
                val isActive = pagerState.currentPage == index
                val dotWidth by animateDpAsState(
                    targetValue = if (isActive) 24.dp else 8.dp,
                    animationSpec = tween(180),
                    label = "rolePagerDotWidth"
                )
                val dotColor by animateColorAsState(
                    targetValue = if (isActive) Primary else Color.White.copy(alpha = 0.35f),
                    animationSpec = tween(180),
                    label = "rolePagerDotColor"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = dotWidth, height = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(dotColor)
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
            color = Surface,
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = sheetMinHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (screenConfig.isLandscape) 20.dp else 24.dp)
                    .padding(top = 18.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.auth_role_choose_to_continue),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }

                    IconButton(
                        onClick = { showGuideDialog = true },
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color.White, CircleShape)
                            .border(1.dp, Divider, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HelpOutline,
                            contentDescription = stringResource(R.string.auth_role_guide_cd),
                            tint = Primary
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { onRoleSelected("TRANSPORTER") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Divider),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = TextPrimary
                        )
                    ) {
                        Icon(Icons.Default.Business, contentDescription = null, tint = Primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.auth_role_transporter),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start
                        )
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = TextSecondary)
                    }

                    OutlinedButton(
                        onClick = { onRoleSelected("DRIVER") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Divider),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = TextPrimary
                        )
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Secondary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.auth_role_driver),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start
                        )
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = TextSecondary)
                    }
                }

                InlineInfoBannerCard(
                    title = stringResource(R.string.auth_role_access_title),
                    subtitle = stringResource(R.string.auth_role_access_subtitle),
                    icon = Icons.Default.Info,
                    iconTint = Primary
                )

                Spacer(modifier = Modifier.height(if (screenConfig.isLandscape) 10.dp else 18.dp))
            }
        }
    }

    if (showGuideDialog) {
        RoleGuideDialog(onDismiss = { showGuideDialog = false })
    }
}

/**
 * Role Guide Dialog - Explains the difference between roles
 * Modern card-based design with icons and clear descriptions
 */
@Composable
fun RoleGuideDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.auth_role_dialog_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.auth_role_cd_close),
                            tint = TextSecondary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))

                InlineInfoBannerCard(
                    title = stringResource(R.string.auth_role_quick_guide_title),
                    subtitle = stringResource(R.string.auth_role_quick_guide_subtitle),
                    icon = Icons.Default.Info,
                    iconTint = Primary
                )

                Spacer(modifier = Modifier.height(20.dp))
                
                // Transporter Section
                GuideRoleItem(
                    icon = Icons.Default.Business,
                    iconColor = Primary,
                    title = stringResource(R.string.auth_role_transporter),
                    points = listOf(
                        stringResource(R.string.auth_role_transporter_point_1),
                        stringResource(R.string.auth_role_transporter_point_2),
                        stringResource(R.string.auth_role_transporter_point_3),
                        stringResource(R.string.auth_role_transporter_point_4)
                    )
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Divider(color = Divider, thickness = 1.dp)
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Driver Section
                GuideRoleItem(
                    icon = Icons.Default.AccountCircle,
                    iconColor = Secondary,
                    title = stringResource(R.string.auth_role_driver),
                    points = listOf(
                        stringResource(R.string.auth_role_driver_point_1),
                        stringResource(R.string.auth_role_driver_point_2),
                        stringResource(R.string.auth_role_driver_point_3),
                        stringResource(R.string.auth_role_driver_point_4)
                    )
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Got It Button
                PrimaryButton(
                    text = stringResource(R.string.auth_role_got_it),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Individual role item in guide dialog with icon and bullet points
 */
@Composable
fun GuideRoleItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    points: List<String>
) {
    Column {
        // Title with Icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                iconColor.copy(alpha = 0.2f),
                                iconColor.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Bullet Points
        points.forEach { point ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .offset(y = 6.dp)
                        .background(iconColor, CircleShape)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = point,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
