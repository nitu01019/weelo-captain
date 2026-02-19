package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.api.DriverData
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
    val clickDebouncer = remember { ClickDebouncer(500L) }
    var vehicleNames by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // id → name
    var driverNames by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // id → name

    // Load vehicles and drivers from real API
    LaunchedEffect(Unit) {
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
            } else {
                Timber.w("CreateTrip: Failed to load vehicles ${vehicleResponse.code()}")
                errorMessage = "Failed to load vehicles"
            }

            if (driverResponse.isSuccessful && driverResponse.body()?.success == true) {
                driverResponse.body()?.data?.let { data ->
                    // name ?: phone ?: id — never display raw "null" in the picker
                    driverNames = data.drivers.mapNotNull { d ->
                        val id = d.id.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val label = d.name?.takeIf { it.isNotBlank() }
                            ?: d.phone?.takeIf { it.isNotBlank() }
                            ?: id
                        Pair(id, label)
                    }
                }
            } else {
                Timber.w("CreateTrip: Failed to load drivers ${driverResponse.code()}")
                // Show non-blocking warning — vehicle data still usable
                if (errorMessage.isBlank()) errorMessage = "Could not load drivers. You can still create a trip without assigning one."
            }

            Timber.d("CreateTrip: Loaded ${vehicleNames.size} vehicles, ${driverNames.size} drivers")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "CreateTrip: Failed to load data")
            errorMessage = "Failed to load form data"
        }
    }
    
    Column(Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(title = "Create Trip", onBackClick = onNavigateBack)
        
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Customer Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
            PrimaryTextField(
                value = customerName,
                onValueChange = { customerName = it; errorMessage = "" },
                label = "Customer Name * *",
                placeholder = "ABC Industries",
                leadingIcon = Icons.Default.Person
            )
            
            PrimaryTextField(
                value = customerMobile,
                onValueChange = { if (it.length <= 10) customerMobile = it },
                label = "Customer Mobile * *",
                placeholder = "10-digit number",
                leadingIcon = Icons.Default.Phone,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            
            Divider()
            Text("Trip Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
            PrimaryTextField(
                value = pickupAddress,
                onValueChange = { pickupAddress = it.trim() },
                label = "Pickup Address * *",
                placeholder = "Enter pickup location",
                leadingIcon = Icons.Default.LocationOn,
                maxLines = 2
            )
            
            PrimaryTextField(
                value = dropAddress,
                onValueChange = { dropAddress = it.trim() },
                label = "Drop Address * *",
                placeholder = "Enter drop location",
                leadingIcon = Icons.Default.LocationOn,
                maxLines = 2
            )
            
            PrimaryTextField(
                value = goodsType,
                onValueChange = { goodsType = it.trim() },
                label = "Goods Type *",
                placeholder = "Electronics, Furniture, etc.",
                leadingIcon = Icons.Default.Category
            )
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryTextField(
                    value = weight,
                    onValueChange = { weight = it.trim() },
                    label = "Weight *",
                    placeholder = "500 kg",
                    modifier = Modifier.weight(1f)
                )
                PrimaryTextField(
                    value = fare,
                    onValueChange = { fare = it.trim() },
                    label = "Fare *",
                    placeholder = "2500",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            
            Divider()
            Text("Select Vehicle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
            vehicleNames.take(3).forEach { vehicle ->
                SelectableCard(
                    title = vehicle.second,
                    subtitle = vehicle.first,
                    isSelected = selectedVehicleId == vehicle.first,
                    onClick = { selectedVehicleId = vehicle.first }
                )
            }
            
            Divider()
            Text("Select Driver", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
            driverNames.take(3).forEach { driver ->
                SelectableCard(
                    title = driver.second,
                    subtitle = driver.first,
                    isSelected = selectedDriverId == driver.first,
                    onClick = { selectedDriverId = driver.first }
                )
            }
            
            if (errorMessage.isNotEmpty()) {
                Text(errorMessage, color = Error, style = MaterialTheme.typography.bodySmall)
            }
        }
        
        Column(Modifier.padding(16.dp)) {
            PrimaryButton(
                text = "Create Trip",
                onClick = {
                    // Debounce to prevent rapid clicks
                    if (!clickDebouncer.canClick()) return@PrimaryButton
                    
                    // Validate inputs
                    val nameValidation = InputValidator.validateName(customerName)
                    if (!nameValidation.isValid) {
                        errorMessage = nameValidation.errorMessage!!
                        return@PrimaryButton
                    }
                    
                    val phoneValidation = InputValidator.validatePhoneNumber(customerMobile)
                    if (customerMobile.isNotEmpty() && !phoneValidation.isValid) {
                        errorMessage = phoneValidation.errorMessage!!
                        return@PrimaryButton
                    }
                    
                    when {
                        pickupAddress.isEmpty() -> errorMessage = "Enter pickup address"
                        dropAddress.isEmpty() -> errorMessage = "Enter drop address"
                        selectedVehicleId == null -> errorMessage = "Select a vehicle"
                        selectedDriverId == null -> errorMessage = "Select a driver"
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

@Composable
fun SelectableCard(title: String, subtitle: String, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(if (isSelected) PrimaryLight else White),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Primary) else null
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = Primary)
        }
    }
}
