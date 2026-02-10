package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.api.RegisterVehicleRequest
import com.weelo.logistics.data.model.TruckCategory
import com.weelo.logistics.data.model.TruckSubtype
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.ui.components.PrimaryButton
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "VehicleDataEntry"

/**
 * Data class for vehicle entry form
 */
data class VehicleFormEntry(
    val categoryId: String,
    val categoryName: String,
    val subtypeId: String,
    val subtypeName: String,
    val capacityTons: Double,
    var vehicleNumber: String = "",
    var manufacturer: String = "",
    var model: String = "",
    var year: String = ""
) {
    fun isValid(): Boolean {
        return vehicleNumber.isNotBlank() &&
               vehicleNumber.length >= 6 &&
               manufacturer.isNotBlank() &&
               year.length == 4 &&
               year.toIntOrNull() != null
    }
    
    fun toApiRequest(): RegisterVehicleRequest {
        return RegisterVehicleRequest(
            vehicleNumber = vehicleNumber.uppercase().trim(),
            vehicleType = categoryId,
            vehicleSubtype = subtypeName,
            capacity = "${capacityTons} Ton",
            model = if (model.isNotBlank()) "$manufacturer $model" else manufacturer,
            year = year.toIntOrNull()
        )
    }
}

/**
 * VehicleDataEntryScreen - Enter details and save vehicles to backend
 * 
 * Saves to: POST /api/v1/vehicles
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDataEntryScreen(
    category: TruckCategory,
    intermediateType: String?,
    selectedSubtypes: Map<TruckSubtype, Int>,
    onComplete: (List<VehicleFormEntry>) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    // State
    var isSubmitting by remember { mutableStateOf(false) }
    var currentSavingIndex by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var savedCount by remember { mutableStateOf(0) }
    var failedVehicles by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Create form entries from selected subtypes
    val vehicleEntries = remember {
        mutableStateListOf<VehicleFormEntry>().apply {
            selectedSubtypes.forEach { (subtype, count) ->
                repeat(count) {
                    add(
                        VehicleFormEntry(
                            categoryId = category.id,
                            categoryName = category.name,
                            subtypeId = subtype.id,
                            subtypeName = subtype.name,
                            capacityTons = subtype.capacityTons
                        )
                    )
                }
            }
        }
    }
    
    val totalVehicles = vehicleEntries.size
    val allValid by remember {
        derivedStateOf { vehicleEntries.all { it.isValid() } }
    }
    
    // Error dialog
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            icon = { Icon(Icons.Default.Error, null, tint = Error) },
            title = { Text("Registration Error") },
            text = {
                Column {
                    Text(errorMessage ?: "Unknown error")
                    if (failedVehicles.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Failed vehicles:", fontWeight = FontWeight.Bold)
                        failedVehicles.forEach { v ->
                            Text("• $v", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (savedCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "$savedCount vehicles saved successfully",
                            color = Success,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (isSubmitting) "Saving ${currentSavingIndex}/$totalVehicles..." 
                        else "Add Vehicle Details"
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isSubmitting) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Enter details for $totalVehicles vehicle${if (totalVehicles > 1) "s" else ""}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${category.name}${intermediateType?.let { " • ${it.replaceFirstChar { c -> c.uppercase() }}" } ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
            
            // Vehicle entry cards
            itemsIndexed(
                items = vehicleEntries,
                key = { idx, entry -> "${entry.subtypeId}-$idx" }
            ) { index, entry ->
                VehicleEntryFormCard(
                    index = index + 1,
                    total = totalVehicles,
                    entry = entry,
                    onUpdate = { updated -> vehicleEntries[index] = updated },
                    enabled = !isSubmitting
                )
            }
            
            // Save button
            item {
                Column {
                    if (isSubmitting) {
                        LinearProgressIndicator(
                            progress = currentSavingIndex.toFloat() / totalVehicles,
                            modifier = Modifier.fillMaxWidth(),
                            color = Primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Saving vehicle $currentSavingIndex of $totalVehicles...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    
                    PrimaryButton(
                        text = if (isSubmitting) "Saving..." else "Save All Vehicles ($totalVehicles)",
                        onClick = {
                            scope.launch {
                                isSubmitting = true
                                errorMessage = null
                                savedCount = 0
                                failedVehicles = emptyList()
                                val failed = mutableListOf<String>()
                                
                                try {
                                    vehicleEntries.forEachIndexed { index, entry ->
                                        currentSavingIndex = index + 1
                                        
                                        try {
                                            val request = entry.toApiRequest()
                                            timber.log.Timber.d("Saving vehicle (upsert): ${request.vehicleNumber}")
                                            
                                            // Use UPSERT endpoint - creates new or updates existing
                                            val response = withContext(Dispatchers.IO) {
                                                RetrofitClient.vehicleApi.upsertVehicle(request)
                                            }
                                            
                                            if (response.isSuccessful && response.body()?.success == true) {
                                                savedCount++
                                                val action = if (response.body()?.data?.isNew == true) "registered" else "updated"
                                                timber.log.Timber.d("Vehicle ${request.vehicleNumber} $action successfully")
                                            } else {
                                                // Only fails if vehicle is owned by someone else
                                                val errorCode = response.body()?.error?.code
                                                val errMsg = when (errorCode) {
                                                    "VEHICLE_EXISTS" -> "Registered by another transporter"
                                                    else -> response.body()?.error?.message 
                                                        ?: response.errorBody()?.string()
                                                        ?: "Unknown error"
                                                }
                                                timber.log.Timber.e("Failed to save ${request.vehicleNumber}: $errMsg")
                                                failed.add("${request.vehicleNumber}: $errMsg")
                                            }
                                        } catch (e: Exception) {
                                            timber.log.Timber.e(e, "Exception saving vehicle")
                                            failed.add("${entry.vehicleNumber}: ${e.localizedMessage}")
                                        }
                                    }
                                    
                                    if (failed.isEmpty()) {
                                        // All saved successfully
                                        onComplete(vehicleEntries.toList())
                                    } else {
                                        // Some failed
                                        failedVehicles = failed
                                        errorMessage = if (savedCount > 0) {
                                            "$savedCount saved, ${failed.size} failed"
                                        } else {
                                            "Failed to save vehicles"
                                        }
                                    }
                                } catch (e: Exception) {
                                    timber.log.Timber.e(e, "Error in save process")
                                    errorMessage = e.localizedMessage ?: "Unknown error"
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        },
                        enabled = allValid && !isSubmitting,
                        isLoading = isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/**
 * Form card for entering vehicle details
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleEntryFormCard(
    index: Int,
    total: Int,
    entry: VehicleFormEntry,
    onUpdate: (VehicleFormEntry) -> Unit,
    enabled: Boolean
) {
    // Local state for smooth input
    var vehicleNumber by remember { mutableStateOf(entry.vehicleNumber) }
    var manufacturer by remember { mutableStateOf(entry.manufacturer) }
    var model by remember { mutableStateOf(entry.model) }
    var year by remember { mutableStateOf(entry.year) }
    
    // Update parent on change
    LaunchedEffect(vehicleNumber, manufacturer, model, year) {
        onUpdate(
            entry.copy(
                vehicleNumber = vehicleNumber,
                manufacturer = manufacturer,
                model = model,
                year = year
            )
        )
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vehicle $index of $total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Surface
                ) {
                    Text(
                        text = entry.subtypeName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Vehicle Number
            OutlinedTextField(
                value = vehicleNumber,
                onValueChange = { vehicleNumber = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' } },
                label = { Text("Vehicle Number *") },
                placeholder = { Text("MH12AB1234") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
                isError = vehicleNumber.isNotBlank() && vehicleNumber.length < 6
            )
            
            Spacer(Modifier.height(12.dp))
            
            // Manufacturer Dropdown
            var expandedMfr by remember { mutableStateOf(false) }
            val manufacturers = listOf("Tata", "Ashok Leyland", "Mahindra", "Eicher", "BharatBenz", "Volvo", "Scania", "Other")
            
            ExposedDropdownMenuBox(
                expanded = expandedMfr && enabled,
                onExpandedChange = { if (enabled) expandedMfr = it }
            ) {
                OutlinedTextField(
                    value = manufacturer,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Manufacturer *") },
                    placeholder = { Text("Select manufacturer") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMfr) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    enabled = enabled
                )
                ExposedDropdownMenu(
                    expanded = expandedMfr,
                    onDismissRequest = { expandedMfr = false }
                ) {
                    manufacturers.forEach { mfr ->
                        DropdownMenuItem(
                            text = { Text(mfr) },
                            onClick = {
                                manufacturer = mfr
                                expandedMfr = false
                            }
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Model (optional)
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model (Optional)") },
                placeholder = { Text("e.g., Prima 4928") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled
            )
            
            Spacer(Modifier.height(12.dp))
            
            // Year
            OutlinedTextField(
                value = year,
                onValueChange = { 
                    if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                        year = it
                    }
                },
                label = { Text("Manufacturing Year *") },
                placeholder = { Text("2023") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
                isError = year.isNotBlank() && (year.length != 4 || year.toIntOrNull() == null)
            )
            
            // Capacity info
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Capacity: ${entry.capacityTons} Ton",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
