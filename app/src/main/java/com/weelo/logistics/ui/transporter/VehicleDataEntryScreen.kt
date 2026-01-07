package com.weelo.logistics.ui.transporter

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.request.CachePolicy
import kotlinx.coroutines.delay
import com.weelo.logistics.data.model.TruckCategory
import com.weelo.logistics.data.model.TruckSubtype
import com.weelo.logistics.ui.components.PrimaryButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.filled.ArrowBack
import com.weelo.logistics.ui.theme.*

/**
 * Data class for individual vehicle entry
 * Structure: Category → Subtype → Individual Vehicle
 */
data class VehicleEntry(
    val categoryId: String,           // e.g., "lcv"
    val categoryName: String,          // e.g., "LCV"
    val subtypeId: String,             // e.g., "19_open"
    val subtypeName: String,           // e.g., "19 Feet Open"
    val intermediateType: String? = null, // e.g., "open" for LCV Open
    var vehicleNumber: String = "",    // e.g., "HR-55-A-1234"
    var manufacturer: String = "",     // e.g., "Tata", "Mahindra"
    var model: String = "",            // e.g., "LPT 1613"
    var year: String = "",             // e.g., "2020"
    var photoUri: Uri? = null          // Vehicle photo
)

/**
 * Screen for entering individual vehicle details
 * Shows scrollable cards for each vehicle
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
    // Create list of vehicle entries based on quantities
    val vehicleEntries = remember {
        mutableStateListOf<VehicleEntry>().apply {
            selectedSubtypes.forEach { (subtype, count) ->
                repeat(count) {
                    add(
                        VehicleEntry(
                            categoryId = category.id,
                            categoryName = category.name,
                            subtypeId = subtype.id,
                            subtypeName = subtype.name,
                            intermediateType = intermediateType
                        )
                    )
                }
            }
        }
    }
    
    val totalVehicles = vehicleEntries.size
    var currentVehicleIndex by remember { mutableStateOf(0) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Vehicle Details ($currentVehicleIndex/$totalVehicles)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            
            // Header
            item {
                Column {
                    Text(
                        text = "Enter details for each vehicle",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${category.name} - ${intermediateType?.replaceFirstChar { it.uppercase() } ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
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
                    onFocus = { currentVehicleIndex = index + 1 }
                )
            }
            
            // Save button
            item {
                // Use derivedStateOf for better performance
                val allFilled by remember {
                    derivedStateOf {
                        vehicleEntries.all {
                            it.vehicleNumber.isNotBlank() &&
                            it.manufacturer.isNotBlank() &&
                            it.year.isNotBlank()
                        }
                    }
                }
                
                PrimaryButton(
                    text = "Save All Vehicles ($totalVehicles)",
                    onClick = { onComplete(vehicleEntries.toList()) },
                    enabled = allFilled,
                    modifier = Modifier.fillMaxWidth()
                )
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
    onFocus: () -> Unit
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
                singleLine = true
            )
            
            Spacer(Modifier.height(12.dp))
            
            // Manufacturer Dropdown
            var expandedManufacturer by remember { mutableStateOf(false) }
            val manufacturers = remember { listOf("Tata", "Ashok Leyland", "Mahindra", "Eicher", "BharatBenz", "Other") }
            
            ExposedDropdownMenuBox(
                expanded = expandedManufacturer,
                onExpandedChange = { expandedManufacturer = it }
            ) {
                OutlinedTextField(
                    value = manufacturer,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Manufacturer *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedManufacturer) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
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
                singleLine = true
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
                singleLine = true
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
