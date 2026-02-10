package com.weelo.logistics.ui.transporter

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.data.api.ConfirmHoldRequest
import com.weelo.logistics.data.api.HoldTrucksRequest
import com.weelo.logistics.data.api.ReleaseHoldRequest
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "TruckHoldConfirm"
private const val HOLD_DURATION_SECONDS = 15

/**
 * =============================================================================
 * TRUCK HOLD CONFIRMATION SCREEN
 * =============================================================================
 * 
 * Shows countdown timer after transporter clicks ACCEPT.
 * 
 * FLOW:
 * 1. Auto-calls holdTrucks API
 * 2. Shows 15 second countdown
 * 3. User clicks CONFIRM → confirmHold API → Navigate to driver assignment
 * 4. User clicks CANCEL or timeout → releaseHold API → Go back
 * 
 * =============================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TruckHoldConfirmScreen(
    orderId: String,
    vehicleType: String,
    vehicleSubtype: String,
    quantity: Int,
    onConfirmed: (holdId: String, truckIds: List<String>) -> Unit,
    onCancelled: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State
    var holdId by remember { mutableStateOf<String?>(null) }
    var remainingSeconds by remember { mutableStateOf(HOLD_DURATION_SECONDS) }
    var isLoading by remember { mutableStateOf(true) }
    var isConfirming by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var holdSuccess by remember { mutableStateOf(false) }
    
    // Circular progress for countdown
    val progress = remainingSeconds.toFloat() / HOLD_DURATION_SECONDS
    
    // Color changes as time runs out
    val timerColor = when {
        remainingSeconds > 10 -> Success
        remainingSeconds > 5 -> Warning
        else -> Error
    }
    
    // Hold trucks on screen load
    LaunchedEffect(Unit) {
        timber.log.Timber.d("Holding $quantity $vehicleType $vehicleSubtype for order $orderId")
        
        try {
            val response = withContext(Dispatchers.IO) {
                RetrofitClient.truckHoldApi.holdTrucks(
                    HoldTrucksRequest(
                        orderId = orderId,
                        vehicleType = vehicleType,
                        vehicleSubtype = vehicleSubtype,
                        quantity = quantity
                    )
                )
            }
            
            if (response.isSuccessful && response.body()?.success == true) {
                holdId = response.body()?.data?.holdId
                holdSuccess = true
                timber.log.Timber.d("Hold success: $holdId")
            } else {
                val msg = response.body()?.error?.message ?: response.body()?.message ?: "Failed to hold trucks"
                errorMessage = msg
                timber.log.Timber.e("Hold failed: $msg")
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Hold exception")
            errorMessage = e.localizedMessage ?: "Network error"
        } finally {
            isLoading = false
        }
    }
    
    // Countdown timer
    LaunchedEffect(holdSuccess) {
        if (holdSuccess && holdId != null) {
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds--
            }
            
            // Timeout - release hold
            if (remainingSeconds <= 0 && holdId != null) {
                timber.log.Timber.d("Hold timeout, releasing")
                try {
                    withContext(Dispatchers.IO) {
                        RetrofitClient.truckHoldApi.releaseHold(
                            ReleaseHoldRequest(holdId!!)
                        )
                    }
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "Release failed")
                }
                Toast.makeText(context, "Time expired. Trucks released.", Toast.LENGTH_SHORT).show()
                onCancelled()
            }
        }
    }
    
    // Confirm function
    fun confirmHold() {
        if (holdId == null) return
        
        scope.launch {
            isConfirming = true
            
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.truckHoldApi.confirmHold(
                        ConfirmHoldRequest(holdId!!)
                    )
                }
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val truckIds = response.body()?.data?.assignedTrucks ?: emptyList()
                    timber.log.Timber.d("Confirmed! Assigned trucks: $truckIds")
                    Toast.makeText(context, "Trucks assigned! Now assign drivers.", Toast.LENGTH_SHORT).show()
                    onConfirmed(holdId!!, truckIds)
                } else {
                    val msg = response.body()?.error?.message ?: "Confirmation failed"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Confirm failed")
                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                isConfirming = false
            }
        }
    }
    
    // Cancel/release function
    fun cancelHold() {
        if (holdId == null) {
            onCancelled()
            return
        }
        
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    RetrofitClient.truckHoldApi.releaseHold(
                        ReleaseHoldRequest(holdId!!)
                    )
                }
                timber.log.Timber.d("Hold released")
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Release failed")
            }
            onCancelled()
        }
    }
    
    // UI
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirm Selection") },
                navigationIcon = {
                    IconButton(onClick = { cancelHold() }, enabled = !isConfirming) {
                        Icon(Icons.Default.Close, "Cancel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = White,
                    navigationIconContentColor = White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Surface),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Primary)
                        Spacer(Modifier.height(16.dp))
                        Text("Reserving trucks...", color = TextSecondary)
                    }
                }
                
                errorMessage != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Error,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = Error
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            errorMessage ?: "Error",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onCancelled) {
                            Text("Go Back")
                        }
                    }
                }
                
                holdSuccess -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Success Icon
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Success.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    null,
                                    modifier = Modifier.size(40.dp),
                                    tint = Success
                                )
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            
                            Text(
                                "Trucks Reserved!",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Spacer(Modifier.height(8.dp))
                            
                            // Truck info
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Primary.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.LocalShipping,
                                        null,
                                        tint = Primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "$quantity × ${vehicleType.uppercase()}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Primary
                                    )
                                    if (vehicleSubtype.isNotBlank()) {
                                        Text(
                                            " ($vehicleSubtype)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(32.dp))
                            
                            // Countdown Timer
                            Box(contentAlignment = Alignment.Center) {
                                // Background circle
                                CircularProgressIndicator(
                                    progress = 1f,
                                    modifier = Modifier.size(120.dp),
                                    strokeWidth = 8.dp,
                                    color = timerColor.copy(alpha = 0.2f)
                                )
                                
                                // Progress circle
                                CircularProgressIndicator(
                                    progress = progress,
                                    modifier = Modifier.size(120.dp),
                                    strokeWidth = 8.dp,
                                    color = timerColor
                                )
                                
                                // Timer text
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        remainingSeconds.toString(),
                                        style = MaterialTheme.typography.displayMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = timerColor
                                    )
                                    Text(
                                        "seconds",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            
                            Text(
                                "Confirm within time to assign trucks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(Modifier.height(32.dp))
                            
                            // Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Cancel Button
                                OutlinedButton(
                                    onClick = { cancelHold() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isConfirming
                                ) {
                                    Text("CANCEL")
                                }
                                
                                // Confirm Button
                                Button(
                                    onClick = { confirmHold() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Success),
                                    enabled = !isConfirming && remainingSeconds > 0
                                ) {
                                    if (isConfirming) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(Icons.Default.Check, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("CONFIRM", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
