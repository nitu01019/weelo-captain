package com.weelo.logistics.ui.transporter

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.R
import com.weelo.logistics.data.model.*
import com.weelo.logistics.data.repository.MockDataRepository
import com.weelo.logistics.ui.components.PrimaryButton
import com.weelo.logistics.ui.components.PrimaryTextField
import com.weelo.logistics.ui.components.PrimaryTopBar
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.material.ripple.rememberRipple

/**
 * Add Vehicle Screen - PRD-02 & PRD-06 Compliant
 * Multi-step: Vehicle Type → Category → Subtype → Details
 * 
 * STATE MANAGEMENT:
 * - Always starts FRESH (no persistence by default)
 * - User can explicitly save as DRAFT
 * - User can load existing DRAFT
 * - On exit without save: state is CLEARED
 */
@Composable
fun AddVehicleScreen(
    onNavigateBack: () -> Unit,
    onVehicleAdded: () -> Unit
) {
    // STATE: Always starts fresh - NO persistence
    var currentStep by remember { mutableStateOf(0) }
    var selectedVehicleType by remember { mutableStateOf<VehicleType?>(null) }
    var selectedCategory by remember { mutableStateOf<TruckCategory?>(null) }
    var selectedSubtypes by remember { mutableStateOf<Map<TruckSubtype, Int>>(emptyMap()) }
    var intermediateSelection by remember { mutableStateOf<String?>(null) }
    
    // Draft dialog states
    // showDraftDialog will be used when draft loading UI is implemented
    @Suppress("UNUSED_VARIABLE")
    var showDraftDialog by remember { mutableStateOf(false) }
    var showSaveDraftDialog by remember { mutableStateOf(false) }
    
    // Handle back button - CLEAR state and exit
    val handleBack = {
        if (currentStep > 0) {
            currentStep -= 1
        } else {
            // CLEAR all state when exiting
            currentStep = 0
            selectedVehicleType = null
            selectedCategory = null
            selectedSubtypes = emptyMap()
            intermediateSelection = null
            onNavigateBack()
        }
    }
    
    // Handle Android system back button
    BackHandler {
        handleBack()
    }
    
    // Draft Save Dialog
    if (showSaveDraftDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDraftDialog = false },
            title = { Text("Save as Draft?") },
            text = { 
                Text("Save your current progress and continue later. You can access saved drafts from the Add Vehicle screen.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // TODO: Implement draft save with ViewModel
                        showSaveDraftDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text("Save Draft")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDraftDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        // Top Bar with Draft Button
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 4.dp,
            color = White
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = handleBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                
                Text(
                    text = "Add Vehicle",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                
                // Save Draft Button (only show after step 1)
                if (currentStep > 1 && selectedCategory != null) {
                    TextButton(
                        onClick = { showSaveDraftDialog = true }
                    ) {
                        Text(
                            text = "Save Draft",
                            color = Primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        
        // Step Indicator (only show when past vehicle type selection)
        if (currentStep > 0) {
            StepIndicator(currentStep = currentStep)
        }
        
        when (currentStep) {
            0 -> SelectVehicleTypeStep(
                onVehicleTypeSelected = { vehicleType ->
                    selectedVehicleType = vehicleType
                    if (vehicleType.isAvailable) {
                        currentStep = 1
                    }
                }
            )
            1 -> SelectCategoryStep(
                onCategorySelected = { category ->
                    selectedCategory = category
                    currentStep = 2
                },
                onBack = { currentStep = 0 }
            )
            2 -> {
                SelectSubtypeStep(
                    category = selectedCategory!!,
                    initialSelection = selectedSubtypes,
                    intermediateType = intermediateSelection,
                    onProceed = { selections, intermediate ->
                        selectedSubtypes = selections
                        intermediateSelection = intermediate
                        currentStep = 3
                    },
                    onBack = { currentStep = 1 },
                    onBackPressed = { hasIntermediate ->
                        !hasIntermediate
                    }
                )
            }
            3 -> VehicleDataEntryScreen(
                category = selectedCategory!!,
                intermediateType = intermediateSelection,
                selectedSubtypes = selectedSubtypes,
                onComplete = { vehicleEntries ->
                    // vehicleEntries will be used for backend submission when API integration is complete
                    // TODO: Save to backend
                    @Suppress("UNUSED_VARIABLE")
                    val unused = vehicleEntries
                    // For now, just go to next step
                    currentStep = 4
                },
                onBack = { currentStep = 2 }
            )
            4 -> {
                // Success screen - Vehicle added
                // CLEAR state after successful completion
                LaunchedEffect(Unit) {
                    // State will be cleared on back navigation
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Success,
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "Vehicles Added Successfully!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${selectedSubtypes.values.sum()} vehicles have been added to your fleet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(32.dp))
                    PrimaryButton(
                        text = "Done",
                        onClick = {
                            // CLEAR all state before navigating back
                            currentStep = 0
                            selectedVehicleType = null
                            selectedCategory = null
                            selectedSubtypes = emptyMap()
                            intermediateSelection = null
                            onVehicleAdded()
                            onNavigateBack()
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun StepIndicator(currentStep: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(White)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepDot(step = 1, currentStep = currentStep, label = "Category *")
        StepLine(isActive = currentStep > 1)
        StepDot(step = 2, currentStep = currentStep, label = "Type *")
        StepLine(isActive = currentStep > 2)
        StepDot(step = 3, currentStep = currentStep, label = "Details *")
    }
}

@Composable
fun RowScope.StepDot(step: Int, currentStep: Int, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = if (step <= currentStep) Primary else Divider,
                    shape = androidx.compose.foundation.shape.CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step.toString(),
                color = White,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (step <= currentStep) Primary else TextSecondary
        )
    }
}

@Composable
fun RowScope.StepLine(isActive: Boolean) {
    Divider(
        modifier = Modifier
            .weight(0.5f)
            .padding(horizontal = 8.dp),
        color = if (isActive) Primary else Divider,
        thickness = 2.dp
    )
}

@Composable
fun SelectVehicleTypeStep(onVehicleTypeSelected: (VehicleType) -> Unit) {
    val vehicleTypes = remember { VehicleTypeCatalog.getAllVehicleTypes() }
    var showComingSoonDialog by remember { mutableStateOf(false) }
    var selectedTypeName by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Select Vehicle Type",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Choose the type of vehicle you want to add to your fleet",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = vehicleTypes,
                key = { it.id }
            ) { vehicleType ->
                VehicleTypeCard(
                    vehicleType = vehicleType,
                    onClick = {
                        if (vehicleType.isAvailable) {
                            onVehicleTypeSelected(vehicleType)
                        } else {
                            selectedTypeName = vehicleType.name
                            showComingSoonDialog = true
                        }
                    }
                )
            }
        }
    }
    
    // Coming Soon Dialog
    if (showComingSoonDialog) {
        AlertDialog(
            onDismissRequest = { showComingSoonDialog = false },
            title = { Text("Coming Soon") },
            text = { Text("$selectedTypeName onboarding is coming soon! Currently, only Truck is available.") },
            confirmButton = {
                TextButton(onClick = { showComingSoonDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun VehicleTypeCard(vehicleType: VehicleType, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (vehicleType.isAvailable) White else androidx.compose.ui.graphics.Color(0xFFF5F5F5)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = vehicleType.icon,
                    fontSize = 64.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = vehicleType.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (vehicleType.isAvailable) TextPrimary else TextSecondary
                )
                if (!vehicleType.isAvailable) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Coming Soon",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun SelectCategoryStep(
    onCategorySelected: (TruckCategory) -> Unit,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit
) {
    // onBack will be used when back navigation from category selection is implemented
    // Performance: Use remember with key to cache categories
    val categories = remember(Unit) { VehicleCatalog.getAllCategories() }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Select Truck Category",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Choose the type of truck you want to add",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(2), // 2 columns for larger, popped-out cards
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = categories,
                key = { it.id }
            ) { category ->
                CategoryCard(
                    category = category,
                    onClick = { onCategorySelected(category) }
                )
            }
        }
    }
}

@Composable
fun CategoryCard(category: TruckCategory, onClick: () -> Unit) {
    // Map category to representative vehicle image - PROPER MAPPING (9 categories)
    val imageRes = when (category.id.lowercase()) {
        "open" -> R.drawable.vehicle_open          // Open Truck
        "container" -> R.drawable.vehicle_container // Container
        "lcv" -> R.drawable.vehicle_lcv            // LCV
        "mini" -> R.drawable.vehicle_mini          // Mini/Pickup
        "trailer" -> R.drawable.vehicle_trailer    // Trailer
        "tipper" -> R.drawable.vehicle_tipper      // Tipper
        "tanker" -> R.drawable.vehicle_tanker      // Tanker
        "dumper" -> R.drawable.vehicle_dumper      // Dumper
        "bulker" -> R.drawable.vehicle_bulker      // Bulker
        else -> R.drawable.vehicle_open
    }

    // Granular Scaling based on user request ("Increase all very little except Tanker, Mini, Open")
    val scaleFactor = when (category.id.lowercase()) {
        "container" -> 1.6f       // Increased
        "lcv" -> 1.52f            // Increased
        "bulker" -> 1.48f         // Increased (removed haulage)
        "trailer" -> 1.35f        // Increased (was 1.25)
        "tipper", "dumper", "others" -> 1.45f // Increased
        "tanker" -> 1.45f         // Excluded from increase (kept same)
        "mini" -> 1.42f           // Excluded from increase (kept same)
        "open" -> 1.35f           // Excluded from increase (kept same)
        else -> 1.3f              // Default
    }

    // Glassy Gradient Brush
    val glassBrush = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFFFFF),
            Color(0xFFF0F4F8),
            Color(0xFFE1E8ED)
        ),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    // RIPPLE FIX: Use MutableInteractionSource with Light Blue Ripple
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.1f),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        shape = RoundedCornerShape(24.dp), 
        colors = CardDefaults.cardColors(containerColor = Color.Transparent), 
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFFFFF))
    ) {
        // Parent Box for layering
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Layer 1: Glassy Background
            Box(
                 modifier = Modifier
                     .fillMaxSize()
                     .background(glassBrush)
            )
            
            // Layer 2: Maximized Image
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = category.name,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scaleFactor),
                contentScale = ContentScale.Crop
            )
            
            // Layer 3: Interaction Overlay (Ripple on Top)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = androidx.compose.material.ripple.rememberRipple(
                            bounded = true,
                            color = Color(0xFF64B5F6) // Light Blue Ripple
                        ),
                        onClick = onClick
                    )
            )
        }
    }
}

@Composable
fun SelectSubtypeStep(
    category: TruckCategory,
    initialSelection: Map<TruckSubtype, Int>,
    @Suppress("UNUSED_PARAMETER") intermediateType: String?,
    onProceed: (Map<TruckSubtype, Int>, String?) -> Unit,
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onBackPressed: ((Boolean) -> Boolean)? = null
) {
    // intermediateType and onBackPressed will be used for advanced navigation when draft loading is implemented
    val subtypes = remember(category.id) { VehicleCatalog.getSubtypesForCategory(category.id) }
    
    // Special handling for LCV and Mini - need intermediate selection
    var intermediateSelection by remember { mutableStateOf<String?>(null) }
    
    // Local state for selections
    var selections by remember { mutableStateOf(initialSelection) }
    
    val totalCount = selections.values.sum()
    
    // For LCV and Mini, first show type selection buttons
    val needsIntermediateStep = category.id.lowercase() in listOf("lcv", "mini")
    val showSubtypes = !needsIntermediateStep || intermediateSelection != null
    
    // Handle back button for intermediate step
    BackHandler(enabled = showSubtypes && needsIntermediateStep && intermediateSelection != null) {
        intermediateSelection = null
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // PERFORMANCE FIX: Use LazyColumn instead of Column with verticalScroll
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                item(key = "header") {
                    Column {
                        Text(
                            text = "Select ${category.name} Type",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (!showSubtypes) {
                            // Show intermediate selection for LCV or Mini
                            Text(
                                text = if (category.id.lowercase() == "lcv") 
                                    "Choose LCV type first" 
                                else 
                                    "Choose vehicle type first",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        } else {
                            Text(
                                text = "Add quantities for the trucks you have.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
                
                // Intermediate type buttons
                if (!showSubtypes) {
                    item(key = "spacer_intermediate") {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    if (category.id.lowercase() == "lcv") {
                        // LCV Open button
                        item(key = "lcv_open") {
                            IntermediateTypeButton(
                                title = "LCV Open",
                                subtitle = "14 to 24 Feet",
                                onClick = { intermediateSelection = "open" }
                            )
                        }
                        
                        // LCV Container button
                        item(key = "lcv_container") {
                            IntermediateTypeButton(
                                title = "LCV Container",
                                subtitle = "14 to 32 Feet SXL",
                                onClick = { intermediateSelection = "container" }
                            )
                        }
                    } else if (category.id.lowercase() == "mini") {
                        // Pickup Truck - Dost button
                        item(key = "mini_dost") {
                            IntermediateTypeButton(
                                title = "Pickup Truck - Dost",
                                subtitle = "1.5 - 2 Ton Capacity",
                                onClick = { intermediateSelection = "dost" }
                            )
                        }
                        
                        // Mini Truck - Tata Ace button
                        item(key = "mini_ace") {
                            IntermediateTypeButton(
                                title = "Mini Truck - Tata Ace",
                                subtitle = "0.75 - 1 Ton Capacity",
                                onClick = { intermediateSelection = "ace" }
                            )
                        }
                    }
                } else {
                    // Spacer after header
                    item(key = "spacer_subtypes") {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    // Filter subtypes based on intermediate selection
                    val filteredSubtypes = if (needsIntermediateStep) {
                        when {
                            category.id.lowercase() == "lcv" && intermediateSelection == "open" ->
                                subtypes.filter { it.id.contains("open", ignoreCase = true) }
                            category.id.lowercase() == "lcv" && intermediateSelection == "container" ->
                                subtypes.filter { it.id.contains("container", ignoreCase = true) }
                            category.id.lowercase() == "mini" && intermediateSelection == "dost" ->
                                subtypes.filter { it.id == "dost" }
                            category.id.lowercase() == "mini" && intermediateSelection == "ace" ->
                                subtypes.filter { it.id == "ace" }
                            else -> subtypes
                        }
                    } else {
                        subtypes
                    }
                    
                    // Vehicle subtype items - PERFORMANCE: Use items() instead of forEach
                    items(
                        items = filteredSubtypes,
                        key = { subtype -> subtype.id }
                    ) { subtype ->
                        val count = selections[subtype] ?: 0
                        SubtypeCounterItem(
                            category = category,
                            subtype = subtype,
                            count = count,
                            onCountChange = { newCount ->
                                val newSelections = selections.toMutableMap()
                                if (newCount > 0) {
                                    newSelections[subtype] = newCount
                                } else {
                                    newSelections.remove(subtype)
                                }
                                selections = newSelections
                            }
                        )
                    }
                }
                
                // Extra space for bottom bar
                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
        
        // Proceed Button / Bottom Bar
        if (totalCount > 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                shadowElevation = 16.dp,
                color = White
            ) {
                 Column(modifier = Modifier.padding(16.dp)) {
                     PrimaryButton(
                         text = "Proceed ($totalCount Vehicles)",
                         onClick = { onProceed(selections, intermediateSelection) }
                     )
                 }
            }
        } else {
             // Show Back button if nothing selected
             Box(
                 modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
             ) {
                TextButton(
                    onClick = {
                        // If showing subtypes (intermediate selection made), go back to intermediate
                        if (showSubtypes && needsIntermediateStep) {
                            intermediateSelection = null
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (showSubtypes && needsIntermediateStep) 
                            "Back to Type Selection" 
                        else 
                            "Back to Categories"
                    )
                }
             }
        }
    }
}

@Composable
fun IntermediateTypeButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    // RIPPLE FIX: Add interactionSource for instant ripple
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        interactionSource = interactionSource  // RIPPLE FIX: Add interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Select",
                tint = Primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun SubtypeCounterItem(
    category: TruckCategory,
    subtype: TruckSubtype, 
    count: Int, 
    onCountChange: (Int) -> Unit
) {
    // Map category to detail image resource (NEW images from user)
    val imageRes = remember(category.id) {
        when (category.id.lowercase()) {
            "container" -> R.drawable.vehicle_container_detail
            "tanker" -> R.drawable.vehicle_tanker_detail
            "tipper" -> R.drawable.vehicle_tipper_detail
            "bulker" -> R.drawable.vehicle_bulker_detail
            "trailer" -> R.drawable.vehicle_trailer_detail
            "mini" -> R.drawable.vehicle_mini_detail
            "lcv" -> R.drawable.vehicle_lcv_detail
            "dumper" -> R.drawable.vehicle_dumper_detail
            "open" -> R.drawable.vehicle_open  // No new image provided for open
            else -> null
        }
    }
    
    // PERFORMANCE FIX: Use remember for static values
    val imageSize = remember(category.id) {
        when (category.id.lowercase()) {
            "lcv" -> 80.dp
            else -> 110.dp
        }
    }
    
    val contentScaleFactor = remember(category.id) {
        when (category.id.lowercase()) {
            "container" -> 1.7f
            "mini" -> 1.5f
            "open" -> 1.5f
            "trailer" -> 1.3f
            "dumper" -> 1.3f
            else -> 1.0f
        }
    }
    
    val imagePadding = remember(category.id) {
        when (category.id.lowercase()) {
            "lcv" -> 4.dp
            "container" -> 0.dp
            "mini", "open" -> 0.dp
            "trailer", "dumper" -> 1.dp
            else -> 2.dp
        }
    }
    
    val verticalOffset = remember(category.id) {
        when (category.id.lowercase()) {
            "lcv" -> 18.dp
            else -> 0.dp
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 12.dp, // Shadow for "pop out"
        tonalElevation = 0.dp,   // 0.dp to prevent "Orange" tint interaction with Primary color
        shape = RoundedCornerShape(20.dp),
        color = Color.White,     // Pure White
        border = if (count > 0) androidx.compose.foundation.BorderStroke(2.dp, Primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),  // Slightly more padding
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Vehicle Image with category-specific sizing and positioning
            if (imageRes != null) {
                Surface(
                    modifier = Modifier
                        .size(imageSize)
                        .offset(y = verticalOffset),
                    shape = RoundedCornerShape(12.dp),
                    color = Surface,
                    shadowElevation = 0.dp
                ) {
                    Image(
                        painter = painterResource(id = imageRes),
                        contentDescription = subtype.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(imagePadding)
                            .scale(contentScaleFactor),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subtype.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${subtype.capacityTons} Ton",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            
            // Quantity Controls - Capsule Style with INSTANT RIPPLE FIX
            if (count == 0) {
                 // Zero State - Just the Plus Button with instant ripple
                 val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                 
                 Box(
                     modifier = Modifier
                         .size(36.dp)
                         .background(Primary, androidx.compose.foundation.shape.CircleShape)
                         .clickable(
                             interactionSource = interactionSource,
                             indication = androidx.compose.material.ripple.rememberRipple(
                                 bounded = true,
                                 radius = 18.dp,
                                 color = White
                             ),
                             onClick = { onCountChange(count + 1) }
                         ),
                     contentAlignment = Alignment.Center
                 ) {
                     Icon(
                         imageVector = Icons.Default.Add,
                         contentDescription = "Add",
                         tint = White,
                         modifier = Modifier.size(20.dp)
                     )
                 }
            } else {
                 // Active State - Capsule [ -  Count  + ]
                 Row(
                     verticalAlignment = Alignment.CenterVertically,
                     horizontalArrangement = Arrangement.SpaceBetween,
                     modifier = Modifier
                         .height(40.dp)
                         .background(Color(0xFFF5F5F5), RoundedCornerShape(50))
                         .border(1.dp, Primary, RoundedCornerShape(50))
                         .padding(horizontal = 4.dp)
                 ) {
                    // Minus Button with instant ripple
                    val minusInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(
                                interactionSource = minusInteractionSource,
                                indication = androidx.compose.material.ripple.rememberRipple(
                                    bounded = true,
                                    radius = 16.dp,
                                    color = Primary
                                ),
                                onClick = { onCountChange(count - 1) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                         Text("-", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary)
                    }
                    
                    // Count Text
                     Text(
                         text = "$count",
                         style = MaterialTheme.typography.titleMedium,
                         fontWeight = FontWeight.Bold,
                         modifier = Modifier.padding(horizontal = 12.dp)
                     )
                     
                    // Plus Button with instant ripple
                    val plusInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Primary, androidx.compose.foundation.shape.CircleShape)
                            .clickable(
                                interactionSource = plusInteractionSource,
                                indication = androidx.compose.material.ripple.rememberRipple(
                                    bounded = true,
                                    radius = 16.dp,
                                    color = White
                                ),
                                onClick = { onCountChange(count + 1) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                         Icon(
                             imageVector = Icons.Default.Add,
                             contentDescription = "Add",
                             tint = White,
                             modifier = Modifier.size(20.dp)
                         )
                    }
                 }
            }
        }
    }
}

@Composable
fun EnterDetailsStep(
    category: TruckCategory,
    selectedSubtypes: Map<TruckSubtype, Int>,
    onBack: () -> Unit,
    onAllVehiclesAdded: () -> Unit
) {
    // Flatten selection map to a list of subtypes (e.g., [19ft, 19ft, 20ft])
    val vehiclesToDo = remember(selectedSubtypes) {
        val list = mutableListOf<TruckSubtype>()
        selectedSubtypes.forEach { (subtype, count) ->
            repeat(count) { list.add(subtype) }
        }
        list
    }
    
    // Track current index in the list
    var currentIndex by remember { mutableStateOf(0) }
    
    // Store collected details: Map<Index, VehicleData>
    // Just minimal data needed: Number, Model, Year
    data class PartialVehicleData(val number: String, val model: String, val year: String)
    val collectedData = remember { mutableStateMapOf<Int, PartialVehicleData>() }
    
    val currentSubtype = vehiclesToDo.getOrNull(currentIndex)
    
    if (currentSubtype == null) {
        // Should not happen if map was not empty
        onBack()
        return
    }
    
    // Form State for CURRENT vehicle
    // Initialize with existing data if we are going back/forth (optional, currently resetting on next)
    // To persist data when going 'Back' within step 3, we would read from collectedData.
    var vehicleNumber by remember(currentIndex) { mutableStateOf(collectedData[currentIndex]?.number ?: "") }
    var model by remember(currentIndex) { mutableStateOf(collectedData[currentIndex]?.model ?: "") }
    var year by remember(currentIndex) { mutableStateOf(collectedData[currentIndex]?.year ?: "") }
    var errorMessage by remember(currentIndex) { mutableStateOf("") }
    
    var isSubmitting by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val repository = remember { MockDataRepository() }
    // TODO: Connect to real repository from backend
    
    // Progress Label
    val totalVehicles = vehiclesToDo.size
    val currentNumber = currentIndex + 1
    
    BackHandler {
        if (currentIndex > 0) {
            currentIndex -= 1
        } else {
            onBack()
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Vehicle Details ($currentNumber of $totalVehicles)",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${category.name} - ${currentSubtype.name}",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            PrimaryTextField(
                value = vehicleNumber,
                onValueChange = {
                    vehicleNumber = it.uppercase()
                    errorMessage = ""
                },
                label = "Vehicle Number * *",
                placeholder = "GJ-01-AB-1234",
                isError = errorMessage.isNotEmpty(),
                errorMessage = errorMessage
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            PrimaryTextField(
                value = model,
                onValueChange = { model = it.trim() },
                label = "Model (Optional) *",
                placeholder = "Tata, Mahindra, Ashok Leyland, etc."
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            PrimaryTextField(
                value = year,
                onValueChange = { if (it.length <= 4) year = it },
                label = "Year (Optional) *",
                placeholder = "2023",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            val isLast = currentNumber == totalVehicles
            val buttonText = if (isLast) "Submit All Vehicles" else "Next Vehicle"
            
            PrimaryButton(
                text = buttonText,
                onClick = {
                    when {
                        vehicleNumber.isEmpty() -> errorMessage = "Please enter vehicle number"
                        !vehicleNumber.matches(Regex("[A-Z]{2}-\\d{2}-[A-Z]{2}-\\d{4}")) -> 
                            errorMessage = "Invalid format. Use: GJ-01-AB-1234"
                        else -> {
                            // Save current data
                            collectedData[currentIndex] = PartialVehicleData(vehicleNumber, model, year)
                            
                            if (isLast) {
                                // Submit EVERYTHING
                                isSubmitting = true
                                scope.launch {
                                    // Iterate and save all
                                    collectedData.forEach { (index, data) ->
                                        val subtype = vehiclesToDo[index]
                                        val vehicle = Vehicle(
                                            id = "v_${System.currentTimeMillis()}_$index",
                                            transporterId = "t1",
                                            category = category,
                                            subtype = subtype,
                                            vehicleNumber = data.number,
                                            model = data.model.takeIf { it.isNotEmpty() },
                                            year = data.year.toIntOrNull(),
                                            status = VehicleStatus.AVAILABLE
                                        )
                                        repository.addVehicle(vehicle)
                                    }
                                    onAllVehiclesAdded()
                                }
                            } else {
                                // Go to next
                                currentIndex += 1
                            }
                        }
                    }
                },
                isLoading = isSubmitting,
                enabled = !isSubmitting
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
