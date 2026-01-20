package com.weelo.logistics.ui.transporter

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.weelo.logistics.data.model.TruckCategory
import com.weelo.logistics.data.model.TruckSubtype
import com.weelo.logistics.data.model.VehicleCatalog
import com.weelo.logistics.data.repository.VehicleRegistrationEntry
import com.weelo.logistics.data.repository.VehicleRepository
import com.weelo.logistics.data.repository.VehicleResult
import com.weelo.logistics.ui.components.PrimaryButton
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// =============================================================================
// VEHICLE DATA ENTRY SCREEN
// =============================================================================
//
// This screen handles entering details for multiple vehicles at once.
// 
// FLOW:
// 1. User selects Vehicle Type (Truck) → Category (Open/Container) → Subtypes (17ft x 2, 19ft x 1)
// 2. This screen shows N cards where N = total count of all subtypes selected
// 3. User fills details for each vehicle
// 4. On "Save All", vehicles are sent to backend in batch
// 5. On success, navigate back and refresh fleet list
//
// DATA HIERARCHY:
// VehicleType (Truck) 
//   └── Category (Open Truck)
//       └── Subtype (17 Feet)
//           └── Vehicle (HR-55-A-1234)
//
// BACKEND INTEGRATION:
// - POST /api/v1/vehicles - Register each vehicle
// - Future: POST /api/v1/vehicles/batch - Batch registration
// =============================================================================

/**
 * Data class for individual vehicle entry
 * Structure: VehicleType → Category → Subtype → Individual Vehicle
 */
data class VehicleEntry(
    val vehicleType: String = "truck",  // Main type: truck, tractor, jcb, tempo
    val categoryId: String,             // e.g., "open", "container", "lcv"
    val categoryName: String,           // e.g., "Open Truck", "Container", "LCV"
    val subtypeId: String,              // e.g., "17_feet", "19_open"
    val subtypeName: String,            // e.g., "17 Feet", "19 Feet Open"
    val capacityTons: Double = 0.0,     // Capacity in tons from subtype
    val intermediateType: String? = null, // e.g., "open" for LCV Open, "container" for LCV Container
    var vehicleNumber: String = "",     // e.g., "HR-55-A-1234"
    var manufacturer: String = "",      // e.g., "Tata", "Mahindra"
    var model: String = "",             // e.g., "LPT 1613"
    var year: String = "",              // e.g., "2020"
    var photoUri: Uri? = null           // Vehicle photo (optional)
) {
    /**
     * Convert to registration entry for API
     */
    fun toRegistrationEntry(): VehicleRegistrationEntry {
        return VehicleRegistrationEntry(
            vehicleType = vehicleType,
            category = categoryId,
            categoryName = categoryName,
            subtype = subtypeId,
            subtypeName = subtypeName,
            intermediateType = intermediateType,
            vehicleNumber = vehicleNumber,
            manufacturer = manufacturer,
            model = model.takeIf { it.isNotBlank() },
            year = year.toIntOrNull(),
            photoUri = photoUri,
            capacityTons = capacityTons
        )
    }
    
    /**
     * Check if entry has all required fields filled
     */
    fun isValid(): Boolean {
        return vehicleNumber.isNotBlank() &&
               manufacturer.isNotBlank() &&
               year.isNotBlank() &&
               year.length == 4 &&
               year.toIntOrNull() != null
    }
}

/**
 * Screen for entering individual vehicle details
 * Shows scrollable cards for each vehicle
 * 
 * SAVES TO BACKEND with proper hierarchy:
 * VehicleType (truck) → Category (open/container) → Subtype (17ft/19ft) → Vehicle
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDataEntryScreen(
    category: TruckCategory,
    intermediateType: String?,
    selectedSubtypes: Map<TruckSubtype, Int>,
    onComplete: (List<VehicleEntry>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { VehicleRepository.getInstance(context) }
    
    // UI State
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successCount by remember { mutableStateOf(0) }
    var failedVehicles by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Create list of vehicle entries based on quantities
    // Each subtype with count creates that many vehicle entry slots
    val vehicleEntries = remember {
        mutableStateListOf<VehicleEntry>().apply {
            selectedSubtypes.forEach { (subtype, count) ->
                repeat(count) {
                    add(
                        VehicleEntry(
                            vehicleType = "truck", // Main vehicle type
                            categoryId = category.id,
                            categoryName = category.name,
                            subtypeId = subtype.id,
                            subtypeName = subtype.name,
                            capacityTons = subtype.capacityTons,
                            intermediateType = intermediateType
                        )
                    )
                }
            }
        }
    }
    
    val totalVehicles = vehicleEntries.size
    var currentVehicleIndex by remember { mutableStateOf(0) }
    
    // Show error dialog
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Registration Error") },
            text = { 
                Column {
                    Text(errorMessage ?: "Unknown error occurred")
                    if (failedVehicles.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Failed vehicles:", fontWeight = FontWeight.Bold)
                        failedVehicles.forEach { vehicle ->
                            Text("• $vehicle", style = MaterialTheme.typography.bodySmall)
                        }
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
                title = { Text("Add Vehicle Details ($currentVehicleIndex/$totalVehicles)") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isSubmitting) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            
            // Header with hierarchy info
            item {
                Column {
                    Text(
                        text = "Enter details for each vehicle",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Truck → ${category.name}${intermediateType?.let { " → ${it.replaceFirstChar { c -> c.uppercase() }}" } ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Data will be saved with full hierarchy for easy search",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            
            // Vehicle entry cards with key for smooth scrolling
            itemsIndexed(
                items = vehicleEntries,
                key = { idx, veh -> "${veh.categoryId}-${veh.subtypeId}-$idx" },
                contentType = { _, _ -> "VehicleCard" }
            ) { index, vehicle ->
                VehicleEntryCard(
                    index = index + 1,
                    total = totalVehicles,
                    vehicle = vehicle,
                    onVehicleUpdate = { updated ->
                        vehicleEntries[index] = updated
                    },
                    onFocus = { currentVehicleIndex = index + 1 },
                    enabled = !isSubmitting
                )
            }
            
            // Save button with loading state
            item {
                // Use derivedStateOf for better performance
                val allFilled by remember {
                    derivedStateOf {
                        vehicleEntries.all {
                            it.vehicleNumber.isNotBlank() &&
                            it.manufacturer.isNotBlank() &&
                            it.year.isNotBlank() &&
                            it.year.length == 4
                        }
                    }
                }
                
                Column {
                    if (isSubmitting) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Saving vehicles to server...",
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
                                failedVehicles = emptyList()
                                
                                try {
                                    // Convert to registration entries
                                    val registrationEntries = vehicleEntries.map { it.toRegistrationEntry() }
                                    
                                    // Call backend API through repository
                                    val result = repository.registerVehiclesBatch(registrationEntries)
                                    
                                    when (result) {
                                        is VehicleResult.Success -> {
                                            // Success! Navigate back
                                            successCount = result.data.size
                                            onComplete(vehicleEntries.toList())
                                        }
                                        is VehicleResult.Error -> {
                                            errorMessage = result.message
                                        }
                                        is VehicleResult.Loading -> {
                                            // Should not happen here, but handle gracefully
                                        }
                                    }
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Failed to save vehicles"
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        },
                        enabled = allFilled && !isSubmitting,
                        isLoading = isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/**
 * Card for entering individual vehicle details
 */
@Composable
fun VehicleEntryCard(
    index: Int,
    total: Int,
    vehicle: VehicleEntry,
    onVehicleUpdate: (VehicleEntry) -> Unit,
    onFocus: () -> Unit,
    enabled: Boolean = true
) {
    // Separate local state for smooth input
    var vehicleNumber by remember { mutableStateOf(vehicle.vehicleNumber) }
    var manufacturer by remember { mutableStateOf(vehicle.manufacturer) }
    var model by remember { mutableStateOf(vehicle.model) }
    var year by remember { mutableStateOf(vehicle.year) }
    var photoUri by remember { mutableStateOf(vehicle.photoUri) }
    
    // Debounced update - only notify parent after 300ms
    LaunchedEffect(vehicleNumber, manufacturer, model, year, photoUri) {
        delay(300)
        onVehicleUpdate(
            vehicle.copy(
                vehicleNumber = vehicleNumber,
                manufacturer = manufacturer,
                model = model,
                year = year,
                photoUri = photoUri
            )
        )
    }
    
    // Photo picker
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            photoUri = it
        }
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
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = Primary
                )
                Text(
                    text = vehicle.subtypeName,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Vehicle Number
            OutlinedTextField(
                value = vehicleNumber,
                onValueChange = {
                    vehicleNumber = it.uppercase()
                    onFocus()
                },
                label = { Text("Vehicle Number *") },
                placeholder = { Text("HR-55-A-1234") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled
            )
            
            Spacer(Modifier.height(12.dp))
            
            // Manufacturer Dropdown
            var expandedManufacturer by remember { mutableStateOf(false) }
            val manufacturers = remember { listOf("Tata", "Ashok Leyland", "Mahindra", "Eicher", "BharatBenz", "Other") }
            
            ExposedDropdownMenuBox(
                expanded = expandedManufacturer && enabled,
                onExpandedChange = { if (enabled) expandedManufacturer = it }
            ) {
                OutlinedTextField(
                    value = manufacturer,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Manufacturer *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedManufacturer) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    enabled = enabled
                )
                ExposedDropdownMenu(
                    expanded = expandedManufacturer,
                    onDismissRequest = { expandedManufacturer = false }
                ) {
                    manufacturers.forEach { mfr ->
                        DropdownMenuItem(
                            text = { Text(mfr) },
                            onClick = {
                                manufacturer = mfr
                                expandedManufacturer = false
                                onFocus()
                            }
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Model (optional)
            OutlinedTextField(
                value = model,
                onValueChange = {
                    model = it.trim()
                },
                label = { Text("Model (Optional)") },
                placeholder = { Text("LPT 1613") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled
            )
            
            Spacer(Modifier.height(12.dp))
            
            // Year
            OutlinedTextField(
                value = year,
                onValueChange = {
                    if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                        year = it.trim()
                        onFocus()
                    }
                },
                label = { Text("Year *") },
                placeholder = { Text("2020") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Photo upload with optimized loading
            if (photoUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photoUri)
                        .crossfade(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = "Vehicle photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(8.dp))
            }
            
            OutlinedButton(
                onClick = { photoLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (photoUri == null) Icons.Default.Add else Icons.Default.CameraAlt,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(if (photoUri == null) "Add Photo (Optional)" else "Change Photo")
            }
        }
    }
}
