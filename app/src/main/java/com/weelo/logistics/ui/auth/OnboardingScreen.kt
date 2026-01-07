package com.weelo.logistics.ui.auth

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.ui.components.PrimaryButton
import com.weelo.logistics.ui.components.WeeloTextButton
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Onboarding Screen - 3-page introduction to the app
 * Shows key features and benefits
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Skip button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
                WeeloTextButton(
                text = androidx.compose.ui.res.stringResource(com.weelo.logistics.R.string.skip),
                onClick = onComplete
            )
        }
        
        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            OnboardingPage(
                page = when (page) {
                    0 -> OnboardingPageData(
                        icon = Icons.Default.LocalShipping,
                        title = androidx.compose.ui.res.stringResource(com.weelo.logistics.R.string.onboarding_title_1),
                        description = androidx.compose.ui.res.stringResource(com.weelo.logistics.R.string.onboarding_desc_1)
                    )
                    1 -> OnboardingPageData(
                        icon = Icons.Default.PhoneAndroid,
                        title = androidx.compose.ui.res.stringResource(com.weelo.logistics.R.string.onboarding_title_2),
                        description = androidx.compose.ui.res.stringResource(com.weelo.logistics.R.string.onboarding_desc_2)
                    )
                    else -> OnboardingPageData(
                        icon = Icons.Default.Refresh,
                        title = androidx.compose.ui.res.stringResource(com.weelo.logistics.R.string.onboarding_title_3),
                        description = androidx.compose.ui.res.stringResource(com.weelo.logistics.R.string.onboarding_desc_3)
                    )
                }
            )
        }
        
        // Page indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size(if (pagerState.currentPage == index) 24.dp else 8.dp, 8.dp)
                        .background(
                            color = if (pagerState.currentPage == index) Primary else Divider,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                        )
                )
                if (index < 2) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
        
        // Action buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (pagerState.currentPage < 2) {
                PrimaryButton(
                    text = androidx.compose.ui.res.stringResource(com.weelo.logistics.R.string.next),
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                )
            } else {
                PrimaryButton(
                    text = androidx.compose.ui.res.stringResource(com.weelo.logistics.R.string.get_started),
                    onClick = onComplete
                )
            }
        }
    }
}

data class OnboardingPageData(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val description: String
)

@Composable
fun OnboardingPage(page: OnboardingPageData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = page.icon,
            contentDescription = page.title,
            modifier = Modifier.size(100.dp),
            tint = Primary
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}
