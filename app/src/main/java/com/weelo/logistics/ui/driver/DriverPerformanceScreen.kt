package com.weelo.logistics.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*

/**
 * Driver Performance Screen - PRD-03 Compliant
 * Shows detailed performance metrics and analytics
 */
@Composable
fun DriverPerformanceScreen(@Suppress("UNUSED_PARAMETER") 
    driverId: String,
    onNavigateBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(title = "Performance", onBackClick = onNavigateBack)
        
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Overall Rating Card
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(PrimaryLight)
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("⭐", style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "4.7",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                    Text(
                        "Overall Rating",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Based on 156 trips",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            
            // Trip Statistics
            SectionCard("Trip Statistics") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatItem("Total", "156", Icons.Default.LocalShipping)
                    StatItem("Completed", "148", Icons.Default.CheckCircle)
                    StatItem("Cancelled", "8", Icons.Default.Cancel)
                }
            }
            
            // Performance Metrics
            SectionCard("Performance Metrics") {
                MetricRow("On-Time Delivery", "92%", Success)
                Divider()
                MetricRow("Average Trip Time", "2.5 hrs", Info)
                Divider()
                MetricRow("Distance Covered", "12,450 km", Secondary)
                Divider()
                MetricRow("Customer Satisfaction", "94%", Success)
            }
            
            // Monthly Trends
            SectionCard("Monthly Trends") {
                Text(
                    "Last 6 Months",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(16.dp))
                
                // Simple bar chart representation
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonthBar("Jan", 20, Primary)
                    MonthBar("Feb", 25, Primary)
                    MonthBar("Mar", 28, Primary)
                    MonthBar("Apr", 32, Success)
                    MonthBar("May", 30, Success)
                    MonthBar("Jun", 35, Success)
                }
            }
            
            // Recent Feedback
            SectionCard("Recent Feedback") {
                FeedbackItem(
                    rating = 5,
                    comment = "Excellent driver! On time and safe delivery.",
                    customer = "ABC Industries",
                    date = "2 days ago"
                )
                Divider(Modifier.padding(vertical = 12.dp))
                FeedbackItem(
                    rating = 4,
                    comment = "Good service, but could improve communication.",
                    customer = "XYZ Traders",
                    date = "5 days ago"
                )
            }
        }
    }
}

@Composable
fun RowScope.StatItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f)
    ) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
fun MetricRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun MonthBar(month: String, trips: Int, color: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            month,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(40.dp)
        )
        Box(
            Modifier
                .height(24.dp)
                .width((trips * 5).dp)
                .background(color, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "$trips",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun FeedbackItem(rating: Int, comment: String, customer: String, date: String) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row {
                repeat(rating) {
                    Text("⭐", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                date,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            comment,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "- $customer",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}
