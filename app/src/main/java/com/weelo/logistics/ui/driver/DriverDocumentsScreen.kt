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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.R
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*

/**
 * Driver Documents Screen - PRD-03 Compliant
 * Upload and manage driver documents
 */
@Composable
fun DriverDocumentsScreen(@Suppress("UNUSED_PARAMETER") 
    driverId: String,
    onNavigateBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(title = stringResource(R.string.my_documents), onBackClick = onNavigateBack)
        
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(SecondaryLight)
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, tint = Secondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.upload_documents_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            
            // Driving License
            DocumentCard(
                title = stringResource(R.string.driving_license),
                subtitle = "DL1420110012345",
                status = stringResource(R.string.doc_verified),
                statusColor = Success,
                icon = Icons.Default.Badge,
                onView = { /* TODO */ },
                onUpload = { /* TODO */ }
            )
            
            // Aadhar Card
            DocumentCard(
                title = stringResource(R.string.aadhar_card),
                subtitle = "XXXX XXXX 5678",
                status = stringResource(R.string.doc_pending),
                statusColor = Warning,
                icon = Icons.Default.CreditCard,
                onView = null,
                onUpload = { /* TODO */ }
            )
            
            // PAN Card
            DocumentCard(
                title = stringResource(R.string.pan_card),
                subtitle = "ABCDE1234F",
                status = stringResource(R.string.doc_not_uploaded),
                statusColor = Error,
                icon = Icons.Default.CreditCard,
                onView = null,
                onUpload = { /* TODO */ }
            )
            
            // Vehicle RC
            DocumentCard(
                title = stringResource(R.string.vehicle_rc_optional),
                subtitle = "GJ-01-AB-1234",
                status = stringResource(R.string.doc_verified),
                statusColor = Success,
                icon = Icons.Default.Description,
                onView = { /* TODO */ },
                onUpload = { /* TODO */ }
            )
            
            // Insurance
            DocumentCard(
                title = stringResource(R.string.vehicle_insurance_optional),
                subtitle = stringResource(R.string.valid_till_format, "31/12/2024"),
                status = stringResource(R.string.doc_verified),
                statusColor = Success,
                icon = Icons.Default.Shield,
                onView = { /* TODO */ },
                onUpload = { /* TODO */ }
            )
            
            Divider()
            
            Text(
                stringResource(R.string.documents_note),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun DocumentCard(
    title: String,
    subtitle: String,
    status: String,
    @Suppress("UNUSED_PARAMETER") statusColor: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onView: (() -> Unit)?,
    onUpload: () -> Unit,
    chipStatus: ChipStatus = when {
        statusColor == Success -> ChipStatus.COMPLETED
        statusColor == Warning -> ChipStatus.PENDING
        else -> ChipStatus.CANCELLED
    }
) {
    // statusColor determines chip status type
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(Surface, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = Primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                
                StatusChip(
                    text = status,
                    status = chipStatus
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onView != null) {
                    SecondaryButton(
                        text = stringResource(R.string.view_button),
                        onClick = onView,
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(
                    onClick = onUpload,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (onView == null) Primary else Secondary
                    )
                ) {
                    Icon(Icons.Default.Upload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (onView == null) stringResource(R.string.upload_button) else stringResource(R.string.reupload_button))
                }
            }
        }
    }
}
