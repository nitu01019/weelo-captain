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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.R
import com.weelo.logistics.data.model.*
// MockDataRepository removed — all data from real API
import com.weelo.logistics.ui.components.CardArtwork
import com.weelo.logistics.ui.components.CardMediaSpec
import com.weelo.logistics.ui.components.IllustrationCanvas
import com.weelo.logistics.ui.components.InlineInfoBannerCard
import com.weelo.logistics.ui.components.MediaHeaderCard
import com.weelo.logistics.ui.components.PrimaryButton
import com.weelo.logistics.ui.components.PrimaryTextField
import com.weelo.logistics.ui.components.PrimaryTopBar
import com.weelo.logistics.ui.components.rememberScreenConfig
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
    val screenConfig = rememberScreenConfig()
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
                onComplete = { _ ->
                    // Vehicles saved to backend successfully
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
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (screenConfig.isLandscape) 48.dp else 24.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val successContentWidth = if (screenConfig.isLandscape) 560.dp else 460.dp
                    Column(
                        modifier = Modifier
                            .widthIn(max = successContentWidth)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        MediaHeaderCard(
                            title = "Vehicles Added Successfully",
                            subtitle = "Your fleet has been updated and is ready for trip assignments.",
                            mediaSpec = CardMediaSpec(
                                artwork = CardArtwork.DETAIL_VEHICLE,
                                headerHeight = if (screenConfig.isLandscape) 108.dp else 124.dp
                            ),
                            trailingHeaderContent = {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = White.copy(alpha = 0.94f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(18.dp))
                                        Text("Saved", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                                    }
                                }
                            }
                        )

                        InlineInfoBannerCard(
                            title = "Fleet updated",
                            subtitle = "${selectedSubtypes.values.sum()} vehicle(s) were added to your fleet.",
                            icon = Icons.Default.LocalShipping,
                            iconTint = Primary,
                            containerColor = SurfaceVariant
                        )

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
}


@Composable
fun StepIndicator(currentStep: Int) {
    val screenConfig = rememberScreenConfig()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(White)
            .padding(horizontal = if (screenConfig.isLandscape) 24.dp else 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepDot(step = 1, currentStep = currentStep, label = "Category")
        StepLine(isActive = currentStep > 1)
        StepDot(step = 2, currentStep = currentStep, label = "Subtype")
        StepLine(isActive = currentStep > 2)
        StepDot(step = 3, currentStep = currentStep, label = "Details")
    }
}

@Composable
fun RowScope.StepDot(step: Int, currentStep: Int, label: String) {
    val screenConfig = rememberScreenConfig()
    val dotSize = if (screenConfig.isLandscape) 28.dp else 32.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f)
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .background(
                    color = if (step <= currentStep) Primary else Divider,
                    shape = CircleShape
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
            color = if (step <= currentStep) Primary else TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = if (screenConfig.isLandscape) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
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
    val screenConfig = rememberScreenConfig()
    // OPTIMIZATION: Cache vehicle types list - never changes
    val vehicleTypes = remember { VehicleTypeCatalog.getAllVehicleTypes() }
    var showComingSoonDialog by remember { mutableStateOf(false) }
    var selectedTypeName by remember { mutableStateOf("") }
    
    // OPTIMIZATION: Stable lambda references to prevent recomposition
    val onDismissDialog = remember { { showComingSoonDialog = false } }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        MediaHeaderCard(
            title = "Select Vehicle Type",
            subtitle = "Choose the primary vehicle class for your fleet onboarding.",
            mediaSpec = CardMediaSpec(
                artwork = CardArtwork.ADD_VEHICLE_TYPE_SELECTOR,
                headerHeight = if (screenConfig.isLandscape) 120.dp else 136.dp,
                placement = com.weelo.logistics.ui.components.CardArtworkPlacement.TOP_BLEED,
                contentScale = ContentScale.Fit,
                containerColor = IllustrationCanvas,
                showInsetFrame = false,
                fitContentPadding = 2.dp,
                enableImageFadeIn = true,
                imageFadeDurationMs = 170
            ),
            trailingHeaderContent = {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = White.copy(alpha = 0.94f)
                ) {
                    Text(
                        text = "Step 1",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyVerticalGrid(
            modifier = Modifier.weight(1f),
            columns = GridCells.Adaptive(minSize = if (screenConfig.isLandscape) 180.dp else 150.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
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
            onDismissRequest = onDismissDialog,
            title = { Text("Coming Soon") },
            text = { Text("$selectedTypeName onboarding is coming soon! Currently, only Truck is available.") },
            confirmButton = {
                TextButton(onClick = onDismissDialog) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun VehicleTypeCard(vehicleType: VehicleType, onClick: () -> Unit) {
    // Get the appropriate icon based on vehicle type
    val icon = when (vehicleType.iconName) {
        "truck" -> Icons.Default.LocalShipping
        "tractor" -> Icons.Default.Agriculture
        "jcb" -> Icons.Default.Construction
        "tempo" -> Icons.Default.LocalShipping
        else -> Icons.Default.LocalShipping
    }
    
    // Icon colors
    val iconColor = when (vehicleType.iconName) {
        "truck" -> Primary
        "tractor" -> Color(0xFF4CAF50) // Green for agriculture
        "jcb" -> Color(0xFFFF9800) // Orange for construction
        "tempo" -> Color(0xFF2196F3) // Blue for tempo
        else -> Primary
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = if (vehicleType.isAvailable) 4.dp else 1.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (vehicleType.isAvailable) 
                White
            else 
                Color(0xFFF5F5F5) // Gray for coming soon
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                if (vehicleType.isAvailable) iconColor.copy(alpha = 0.18f) else Color.Gray.copy(alpha = 0.12f),
                                White
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!vehicleType.isAvailable) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = White.copy(alpha = 0.9f)
                    ) {
                        Text(
                            text = "Soon",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
                
                // Icon with background circle
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(
                            color = if (vehicleType.isAvailable)
                                iconColor.copy(alpha = 0.12f)
                            else
                                Color.Gray.copy(alpha = 0.10f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = vehicleType.name,
                        modifier = Modifier.size(28.dp),
                        tint = if (vehicleType.isAvailable) iconColor else Color.Gray
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Text(
                    text = vehicleType.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (vehicleType.isAvailable) TextPrimary else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = vehicleType.category,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = vehicleType.description.ifBlank { "Vehicle onboarding option" },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SelectCategoryStep(
    onCategorySelected: (TruckCategory) -> Unit,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit
) {
    val screenConfig = rememberScreenConfig()
    // onBack will be used when back navigation from category selection is implemented
    // Performance: Use remember with key to cache categories
    val categories = remember(Unit) { VehicleCatalog.getAllCategories() }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        MediaHeaderCard(
            title = "Select Truck Category",
            subtitle = "Choose the category to view available subtypes and capacities.",
            mediaSpec = CardMediaSpec(
                artwork = CardArtwork.ADD_VEHICLE_TRUCK_CATEGORY,
                headerHeight = if (screenConfig.isLandscape) 120.dp else 136.dp,
                placement = com.weelo.logistics.ui.components.CardArtworkPlacement.TOP_BLEED,
                contentScale = ContentScale.Fit,
                containerColor = IllustrationCanvas,
                showInsetFrame = false,
                fitContentPadding = 2.dp,
                enableImageFadeIn = true,
                imageFadeDurationMs = 170
            ),
            trailingHeaderContent = {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = White.copy(alpha = 0.94f)
                ) {
                    Text(
                        text = "${categories.size} categories",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyVerticalGrid(
            modifier = Modifier.weight(1f),
            columns = GridCells.Adaptive(minSize = if (screenConfig.isLandscape) 190.dp else 160.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
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
        "container" -> 1.42f
        "lcv" -> 1.36f
        "bulker" -> 1.34f
        "trailer" -> 1.24f
        "tipper", "dumper", "others" -> 1.30f
        "tanker" -> 1.28f
        "mini" -> 1.28f
        "open" -> 1.22f
        else -> 1.18f
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Divider)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
                    .background(SurfaceVariant)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = category.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(scaleFactor),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.08f))
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
                                color = TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
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
            "container" -> 1.28f
            "mini" -> 1.20f
            "open" -> 1.20f
            "trailer" -> 1.12f
            "dumper" -> 1.12f
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
        shadowElevation = 7.dp, // Reduced shadow for lower GPU cost on long lists
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
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${subtype.capacityTons} Ton",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

// =============================================================================
// NOTE: EnterDetailsStep was removed - replaced by VehicleDataEntryScreen
// which provides better UX with backend integration, batch saving, and
// proper hierarchical data structure.
// =============================================================================
