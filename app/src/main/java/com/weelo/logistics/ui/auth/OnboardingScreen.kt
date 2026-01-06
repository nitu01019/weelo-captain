package com.weelo.logistics.ui.auth

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
                text = "Skip",
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
                        emoji = "🚛",
                        title = "Manage Your Fleet",
                        description = "Add vehicles, assign drivers, and track deliveries in real-time. Complete control at your fingertips."
                    )
                    1 -> OnboardingPageData(
                        emoji = "📱",
                        title = "Accept Trips Instantly",
                        description = "Get trip requests, navigate easily, and earn more. Drive smart with GPS navigation."
                    )
                    else -> OnboardingPageData(
                        emoji = "🔄",
                        title = "One App, All Roles",
                        description = "Be a transporter, driver, or both. Switch between roles anytime with a single tap."
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
                    text = "Next",
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                )
            } else {
                PrimaryButton(
                    text = "Get Started",
                    onClick = onComplete
                )
            }
        }
    }
}

data class OnboardingPageData(
    val emoji: String,
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
        Text(
            text = page.emoji,
            fontSize = 100.sp
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
