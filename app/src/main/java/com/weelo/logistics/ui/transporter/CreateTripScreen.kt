package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.weelo.logistics.R
import com.weelo.logistics.data.model.Trip
import com.weelo.logistics.data.model.Location
import com.weelo.logistics.data.model.TripStatus
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.utils.ClickDebouncer
import com.weelo.logistics.utils.InputValidator
import com.weelo.logistics.utils.DataSanitizer
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun CreateTripScreen(
    onNavigateBack: () -> Unit,
    onTripCreated: () -> Unit
) {
    var customerName by remember { mutableStateOf("") }
    var customerMobile by remember { mutableStateOf("") }
    var pickupAddress by remember { mutableStateOf("") }
    var dropAddress by remember { mutableStateOf("") }
    var goodsType by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var fare by remember { mutableStateOf("") }
    var selectedVehicleId by remember { mutableStateOf<String?>(null) }
    var selectedDriverId by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clickDebouncer = remember { ClickDebouncer(500L) }
    var vehicleNames by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // id → name
    var driverNames by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // id → name
    var isLoadingVehicleOptions by remember { mutableStateOf(true) }
    var isLoadingDriverOptions by remember { mutableStateOf(true) }
    var vehicleLoadMessage by remember { mutableStateOf<String?>(null) }
    var driverLoadMessage by remember { mutableStateOf<String?>(null) }

    // Load vehicles and drivers from real API
    LaunchedEffect(Unit) {
        isLoadingVehicleOptions = true
        isLoadingDriverOptions = true
        vehicleLoadMessage = null
        driverLoadMessage = null
        try {
            val vehicleResponse = RetrofitClient.vehicleApi.getVehicles()
            val driverResponse = RetrofitClient.driverApi.getDriverList()

            if (vehicleResponse.isSuccessful && vehicleResponse.body()?.success == true) {
                val vehicleList = vehicleResponse.body()?.data?.vehicles
                vehicleNames = vehicleList?.mapNotNull { v ->
                    val id = v.id.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val number = v.vehicleNumber.takeIf { it.isNotBlank() } ?: id
                    Pair(id, number)
                } ?: emptyList()
                vehicleLoadMessage = null
            } else {
                Timber.w("CreateTrip: Failed to load vehicles ${vehicleResponse.code()}")
                vehicleLoadMessage = "Failed to load vehicles"
                errorMessage = "Failed to load vehicles"
            }
            isLoadingVehicleOptions = false

            if (driverResponse.isSuccessful && driverResponse.body()?.success == true) {
                driverResponse.body()?.data?.let { data ->
                    // name ?: phone ?: id — never display raw "null" in the picker
                    driverNames = data.drivers.mapNotNull { d ->
                        val id = d.id.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val label = d.name?.takeIf { it.isNotBlank() }
                            ?: d.phone.takeIf { it.isNotBlank() }
                            ?: id
                        Pair(id, label)
                    }
                }
                driverLoadMessage = null
            } else {
                Timber.w("CreateTrip: Failed to load drivers ${driverResponse.code()}")
                driverLoadMessage = "Could not load drivers. You can still create a trip without assigning one."
                // Show non-blocking warning — vehicle data still usable
                if (errorMessage.isBlank()) errorMessage = "Could not load drivers. You can still create a trip without assigning one."
            }
            isLoadingDriverOptions = false

            Timber.d("CreateTrip: Loaded ${vehicleNames.size} vehicles, ${driverNames.size} drivers")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "CreateTrip: Failed to load data")
            vehicleLoadMessage = "Failed to load form data"
            driverLoadMessage = "Failed to load form data"
            isLoadingVehicleOptions = false
            isLoadingDriverOptions = false
            errorMessage = "Failed to load form data"
        }
    }
    
    val screenConfig = rememberScreenConfig()
    val contentMaxWidth = if (screenConfig.isLandscape) 720.dp else 640.dp
    val showGlobalErrorBanner = errorMessage.isNotEmpty() &&
        errorMessage != vehicleLoadMessage &&
        errorMessage != driverLoadMessage

    Column(Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(title = "Create Trip", onBackClick = onNavigateBack)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = contentMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MediaHeaderCard(
                    title = "Create Trip",
                    subtitle = "Add customer details, route, vehicle, and an optional driver assignment.",
                    mediaSpec = CardMediaSpec(
                        artwork = CardArtwork.DETAIL_TRIP,
                        headerHeight = if (screenConfig.isLandscape) 108.dp else 124.dp
                    ),
                    trailingHeaderContent = {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = White.copy(alpha = 0.94f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Route, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                                Text("Trip", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                            }
                        }
                    }
                )

                SectionCard("Customer Details") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrimaryTextField(
                            value = customerName,
                            onValueChange = { customerName = it; errorMessage = "" },
                            label = "Customer Name *",
                            placeholder = "ABC Industries",
                            leadingIcon = Icons.Default.Person
                        )

                        PrimaryTextField(
                            value = customerMobile,
                            onValueChange = {
                                if (it.length <= 10) {
                                    customerMobile = it
                                    errorMessage = ""
                                }
                            },
                            label = "Customer Mobile *",
                            placeholder = "10-digit number",
                            leadingIcon = Icons.Default.Phone,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                    }
                }

                SectionCard("Trip Details") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrimaryTextField(
                            value = pickupAddress,
                            onValueChange = { pickupAddress = it.trim(); errorMessage = "" },
                            label = "Pickup Address *",
                            placeholder = "Enter pickup location",
                            leadingIcon = Icons.Default.LocationOn,
                            maxLines = 2
                        )

                        PrimaryTextField(
                            value = dropAddress,
                            onValueChange = { dropAddress = it.trim(); errorMessage = "" },
                            label = "Drop Address *",
                            placeholder = "Enter drop location",
                            leadingIcon = Icons.Default.LocationOn,
                            maxLines = 2
                        )

                        PrimaryTextField(
                            value = goodsType,
                            onValueChange = { goodsType = it.trim(); errorMessage = "" },
                            label = "Goods Type *",
                            placeholder = "Electronics, Furniture, etc.",
                            leadingIcon = Icons.Default.Category
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PrimaryTextField(
                                value = weight,
                                onValueChange = { weight = it.trim(); errorMessage = "" },
                                label = "Weight *",
                                placeholder = "500 kg",
                                modifier = Modifier.weight(1f)
                            )
                            PrimaryTextField(
                                value = fare,
                                onValueChange = { fare = it.trim(); errorMessage = "" },
                                label = "Fare *",
                                placeholder = "2500",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                SectionCard("Select Vehicle") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (vehicleLoadMessage != null) {
                            InlineInfoBannerCard(
                                title = "Vehicle list unavailable",
                                subtitle = vehicleLoadMessage ?: "Failed to load vehicles",
                                icon = Icons.Default.ErrorOutline,
                                iconTint = Error,
                                containerColor = ErrorLight
                            )
                        } else {
                            InlineInfoBannerCard(
                                title = "Required",
                                subtitle = "Select one vehicle to create the trip.",
                                icon = Icons.Default.LocalShipping,
                                iconTint = Primary,
                                containerColor = SurfaceVariant
                            )
                        }

                        when {
                            isLoadingVehicleOptions -> {
                                SectionSkeletonBlock(rowCount = 2, titleLineWidthFraction = 0.34f)
                                SectionSkeletonBlock(rowCount = 2, titleLineWidthFraction = 0.38f)
                            }
                            vehicleNames.isEmpty() -> {
                                InlineSectionEmptyState(
                                    spec = noAvailabilityEmptyStateSpec(
                                        artwork = EmptyStateArtwork.CREATE_TRIP_NO_VEHICLES,
                                        title = "No vehicles available",
                                        subtitle = "Add a vehicle to your fleet before creating a trip."
                                    ),
                                    layoutStyle = EmptyStateLayoutStyle.CARD_COMPACT
                                )
                            }
                            else -> {
                                vehicleNames.forEach { vehicle ->
                                    SelectableCard(
                                        title = vehicle.second,
                                        subtitle = vehicle.first,
                                        isSelected = selectedVehicleId == vehicle.first,
                                        onClick = { selectedVehicleId = vehicle.first; errorMessage = "" },
                                        leadingIcon = Icons.Default.LocalShipping
                                    )
                                }
                            }
                        }
                    }
                }

                SectionCard("Select Driver (Optional)") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (driverLoadMessage != null) {
                            InlineInfoBannerCard(
                                title = "Driver list unavailable",
                                subtitle = driverLoadMessage ?: "Could not load drivers. You can create the trip without assigning one.",
                                icon = Icons.Default.WarningAmber,
                                iconTint = Warning,
                                containerColor = WarningLight
                            )
                        } else {
                            InlineInfoBannerCard(
                                title = "Optional",
                                subtitle = "You can assign a driver now or later.",
                                icon = Icons.Default.Person,
                                iconTint = Primary,
                                containerColor = SurfaceVariant
                            )
                        }

                        when {
                            isLoadingDriverOptions -> {
                                SectionSkeletonBlock(rowCount = 2, titleLineWidthFraction = 0.34f, showLeadingAvatar = true)
                            }
                            driverNames.isEmpty() -> {
                                InlineSectionEmptyState(
                                    spec = noAvailabilityEmptyStateSpec(
                                        artwork = EmptyStateArtwork.CREATE_TRIP_NO_DRIVERS,
                                        title = "No drivers available",
                                        subtitle = "Create the trip now and assign a driver later."
                                    ),
                                    layoutStyle = EmptyStateLayoutStyle.CARD_COMPACT
                                )
                            }
                            else -> {
                                driverNames.forEach { driver ->
                                    SelectableCard(
                                        title = driver.second,
                                        subtitle = driver.first,
                                        isSelected = selectedDriverId == driver.first,
                                        onClick = { selectedDriverId = driver.first; errorMessage = "" },
                                        leadingIcon = Icons.Default.Person
                                    )
                                }
                            }
                        }
                    }
                }

                if (showGlobalErrorBanner) {
                    InlineInfoBannerCard(
                        title = "Fix the form",
                        subtitle = errorMessage,
                        icon = Icons.Default.ErrorOutline,
                        iconTint = Error,
                        containerColor = ErrorLight
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(White),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = contentMaxWidth)
                    .padding(16.dp)
            ) {
            PrimaryButton(
                text = "Create Trip",
                onClick = {
                    // Debounce to prevent rapid clicks
                    if (!clickDebouncer.canClick()) return@PrimaryButton
                    
                    // Validate inputs
                    val nameValidation = InputValidator.validateName(customerName)
                    if (!nameValidation.isValid) {
                        errorMessage = nameValidation.errorMessage ?: context.getString(R.string.error_invalid_customer_name)
                        return@PrimaryButton
                    }
                    
                    val phoneValidation = InputValidator.validatePhoneNumber(customerMobile)
                    if (customerMobile.isNotEmpty() && !phoneValidation.isValid) {
                        errorMessage = phoneValidation.errorMessage ?: context.getString(R.string.error_invalid_phone)
                        return@PrimaryButton
                    }
                    
                    when {
                        pickupAddress.isEmpty() -> errorMessage = "Enter pickup address"
                        dropAddress.isEmpty() -> errorMessage = "Enter drop address"
                        selectedVehicleId == null -> errorMessage = "Select a vehicle"
                        // Driver is optional — transporter can assign later
                        else -> {
                            isLoading = true
                            scope.launch {
                                try {
                                    // Sanitize user inputs
                                    // Validate fare — must be a positive number
                                    val parsedFare = fare.toDoubleOrNull()
                                    if (parsedFare == null || parsedFare <= 0.0) {
                                        errorMessage = "Please enter a valid fare amount"
                                        isLoading = false
                                        return@launch
                                    }

                                    val vehicleId = selectedVehicleId ?: run {
                                        errorMessage = "Please select a vehicle"
                                        isLoading = false
                                        return@launch
                                    }

                                    val trip = Trip(
                                        id = "trip_${System.currentTimeMillis()}",
                                        transporterId = "t1",
                                        vehicleId = vehicleId,
                                        driverId = selectedDriverId,
                                        pickupLocation = Location(0.0, 0.0, DataSanitizer.sanitizeForApi(pickupAddress) ?: ""),
                                        dropLocation = Location(0.0, 0.0, DataSanitizer.sanitizeForApi(dropAddress) ?: ""),
                                        customerName = DataSanitizer.sanitizeForApi(customerName) ?: "",
                                        customerMobile = customerMobile,
                                        goodsType = DataSanitizer.sanitizeForApi(goodsType) ?: "",
                                        weight = weight,
                                        fare = parsedFare,
                                        status = TripStatus.ASSIGNED
                                    )
                                    // TODO: Replace with real API call when endpoint is ready
                                    // val response = RetrofitClient.tripApi.createTrip(trip)
                                    // if (!response.isSuccessful) { errorMessage = "Failed: ${response.code()}"; return@launch }
                                    Timber.d("CreateTrip: Trip staged locally - $trip")
                                    // Only navigate on success (not before API call)
                                    onTripCreated()
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    Timber.e(e, "CreateTrip: Failed to create trip")
                                    errorMessage = "Failed to create trip"
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    }
                },
                isLoading = isLoading,
                enabled = !isLoading
            )
            }
        }
    }
}

@Composable
fun SelectableCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.CheckCircle
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(if (isSelected) PrimaryLight else White),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Primary) else androidx.compose.foundation.BorderStroke(1.dp, Divider),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = if (isSelected) Primary.copy(alpha = 0.12f) else SurfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (isSelected) Primary else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, null, tint = Primary)
            } else {
                Icon(Icons.Default.ChevronRight, null, tint = TextDisabled)
            }
        }
    }
}
