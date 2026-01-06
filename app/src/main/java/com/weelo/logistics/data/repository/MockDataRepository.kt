package com.weelo.logistics.data.repository

import com.weelo.logistics.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Mock Data Repository - Provides sample data for UI testing
 * This simulates backend API calls with delay
 */
class MockDataRepository {
    
    // Current logged-in user (for demo)
    private var currentUser: User? = null
    
    // Mock data storage
    private val mockVehicles = mutableListOf<Vehicle>()
    private val mockDrivers = mutableListOf<Driver>()
    private val mockTrips = mutableListOf<Trip>()
    
    init {
        initializeMockData()
    }
    
    /**
     * Initialize with sample data
     */
    private fun initializeMockData() {
        // Sample vehicles - PRD-06 Compliant
        mockVehicles.addAll(
            listOf(
                Vehicle(
                    id = "v1",
                    transporterId = "t1",
                    category = VehicleCatalog.CONTAINER,
                    subtype = VehicleCatalog.CONTAINER_SUBTYPES[4], // 32 Feet Single Axle, 20 tons
                    vehicleNumber = "GJ-01-AB-1234",
                    model = "Tata Prima",
                    year = 2022,
                    status = VehicleStatus.AVAILABLE
                ),
                Vehicle(
                    id = "v2",
                    transporterId = "t1",
                    category = VehicleCatalog.LCV,
                    subtype = VehicleCatalog.LCV_SUBTYPES[0], // 14 Feet Open, 2.5 tons
                    vehicleNumber = "GJ-05-CD-5678",
                    model = "Eicher Pro",
                    year = 2023,
                    status = VehicleStatus.IN_TRANSIT,
                    assignedDriverId = "d1"
                ),
                Vehicle(
                    id = "v3",
                    transporterId = "t1",
                    category = VehicleCatalog.MINI_PICKUP,
                    subtype = VehicleCatalog.MINI_PICKUP_SUBTYPES[1], // Tata Ace, 0.75 tons
                    vehicleNumber = "MH-12-EF-9012",
                    model = "Tata Ace Gold",
                    year = 2021,
                    status = VehicleStatus.AVAILABLE
                ),
                Vehicle(
                    id = "v4",
                    transporterId = "t1",
                    category = VehicleCatalog.TRAILER,
                    subtype = VehicleCatalog.TRAILER_SUBTYPES[7], // 32-35 Ton
                    vehicleNumber = "RJ-14-GH-3456",
                    model = "Ashok Leyland",
                    year = 2020,
                    status = VehicleStatus.MAINTENANCE
                ),
                Vehicle(
                    id = "v5",
                    transporterId = "t1",
                    category = VehicleCatalog.TIPPER,
                    subtype = VehicleCatalog.TIPPER_SUBTYPES[3], // 20-24 Ton
                    vehicleNumber = "UP-16-IJ-7890",
                    model = "Mahindra",
                    year = 2022,
                    status = VehicleStatus.AVAILABLE
                )
            )
        )
        
        // Sample drivers
        mockDrivers.addAll(
            listOf(
                Driver(
                    id = "d1",
                    name = "Rajesh Kumar",
                    mobileNumber = "9876543210",
                    licenseNumber = "DL1420110012345",
                    transporterId = "t1",
                    assignedVehicleId = "v2",
                    isAvailable = false,
                    rating = 4.5f,
                    totalTrips = 156,
                    status = DriverStatus.ON_TRIP
                ),
                Driver(
                    id = "d2",
                    name = "Suresh Sharma",
                    mobileNumber = "9876543211",
                    licenseNumber = "DL1420110012346",
                    transporterId = "t1",
                    isAvailable = true,
                    rating = 4.8f,
                    totalTrips = 203,
                    status = DriverStatus.ACTIVE
                ),
                Driver(
                    id = "d3",
                    name = "Mohan Singh",
                    mobileNumber = "9876543212",
                    licenseNumber = "DL1420110012347",
                    transporterId = "t1",
                    isAvailable = true,
                    rating = 4.2f,
                    totalTrips = 89,
                    status = DriverStatus.ACTIVE
                )
            )
        )
        
        // Sample trips
        mockTrips.addAll(
            listOf(
                Trip(
                    id = "trip1",
                    transporterId = "t1",
                    vehicleId = "v2",
                    driverId = "d1",
                    pickupLocation = Location(
                        latitude = 28.7041,
                        longitude = 77.1025,
                        address = "Connaught Place, New Delhi"
                    ),
                    dropLocation = Location(
                        latitude = 28.5355,
                        longitude = 77.3910,
                        address = "Sector 18, Noida"
                    ),
                    distance = 25.5,
                    estimatedDuration = 45,
                    status = TripStatus.IN_PROGRESS,
                    customerName = "ABC Industries",
                    customerMobile = "9999999999",
                    goodsType = "Electronics",
                    weight = "500 kg",
                    fare = 2500.0,
                    startedAt = System.currentTimeMillis() - 1800000 // 30 min ago
                ),
                Trip(
                    id = "trip2",
                    transporterId = "t1",
                    vehicleId = "v1",
                    driverId = null,
                    pickupLocation = Location(
                        latitude = 28.6139,
                        longitude = 77.2090,
                        address = "Lajpat Nagar, New Delhi"
                    ),
                    dropLocation = Location(
                        latitude = 28.4595,
                        longitude = 77.0266,
                        address = "Gurgaon Cyber City"
                    ),
                    distance = 18.0,
                    estimatedDuration = 35,
                    status = TripStatus.PENDING,
                    customerName = "XYZ Traders",
                    customerMobile = "9999999998",
                    goodsType = "Furniture",
                    weight = "300 kg",
                    fare = 1800.0
                ),
                Trip(
                    id = "trip3",
                    transporterId = "t1",
                    vehicleId = "v2",
                    driverId = "d1",
                    pickupLocation = Location(
                        latitude = 28.6692,
                        longitude = 77.4538,
                        address = "Indirapuram, Ghaziabad"
                    ),
                    dropLocation = Location(
                        latitude = 28.5244,
                        longitude = 77.1855,
                        address = "Hauz Khas, New Delhi"
                    ),
                    distance = 22.0,
                    estimatedDuration = 40,
                    status = TripStatus.COMPLETED,
                    customerName = "PQR Logistics",
                    customerMobile = "9999999997",
                    goodsType = "Groceries",
                    weight = "200 kg",
                    fare = 2000.0,
                    startedAt = System.currentTimeMillis() - 7200000, // 2 hours ago
                    completedAt = System.currentTimeMillis() - 3600000 // 1 hour ago
                )
            )
        )
    }
    
    // ============ User Authentication ============
    
    suspend fun login(mobileNumber: String, password: String): Result<User> {
        
        // Mock login - accept any mobile with password "123456"
        return if (password == "123456") {
            val user = User(
                id = "u1",
                name = "Demo User",
                mobileNumber = mobileNumber,
                email = "demo@weelo.com",
                roles = listOf(UserRole.TRANSPORTER, UserRole.DRIVER) // Both roles for demo
            )
            currentUser = user
            Result.success(user)
        } else {
            Result.failure(Exception("Invalid credentials"))
        }
    }
    
    suspend fun signup(name: String, mobileNumber: String, password: String, role: UserRole): Result<User> {
        
        val user = User(
            id = "u_${System.currentTimeMillis()}",
            name = name,
            mobileNumber = mobileNumber,
            roles = listOf(role)
        )
        currentUser = user
        return Result.success(user)
    }
    
    fun getCurrentUser(): User? = currentUser
    
    // ============ Transporter Dashboard ============
    
    suspend fun getTransporterDashboard(transporterId: String): Result<TransporterDashboard> {
        
        val activeVehicles = mockVehicles.count { it.status == VehicleStatus.AVAILABLE || it.status == VehicleStatus.IN_TRANSIT }
        val activeDrivers = mockDrivers.count { it.status == DriverStatus.ACTIVE || it.status == DriverStatus.ON_TRIP }
        val activeTrips = mockTrips.count { it.status == TripStatus.IN_PROGRESS }
        val todayRevenue = mockTrips.filter { it.status == TripStatus.COMPLETED }.sumOf { it.fare }
        
        val dashboard = TransporterDashboard(
            totalVehicles = mockVehicles.size,
            activeVehicles = activeVehicles,
            totalDrivers = mockDrivers.size,
            activeDrivers = activeDrivers,
            activeTrips = activeTrips,
            todayRevenue = todayRevenue,
            todayTrips = mockTrips.filter { it.status == TripStatus.COMPLETED }.size,
            completedTrips = mockTrips.filter { it.status == TripStatus.COMPLETED }.size,
            recentTrips = mockTrips.take(5)
        )
        
        return Result.success(dashboard)
    }
    
    // ============ Driver Dashboard ============
    
    suspend fun getDriverDashboard(driverId: String): Result<DriverDashboard> {
        
        val driver = mockDrivers.find { it.id == driverId }
        val activeTrip = mockTrips.find { it.driverId == driverId && it.status == TripStatus.IN_PROGRESS }
        val pendingTrips = mockTrips.filter { it.driverId == driverId && it.status == TripStatus.ASSIGNED }
        val completedTrips = mockTrips.filter { it.driverId == driverId && it.status == TripStatus.COMPLETED }
        
        val dashboard = DriverDashboard(
            isAvailable = driver?.isAvailable ?: false,
            activeTrip = activeTrip,
            todayTrips = completedTrips.size,
            todayEarnings = completedTrips.sumOf { it.fare },
            todayDistance = completedTrips.sumOf { it.distance },
            weekEarnings = completedTrips.sumOf { it.fare } * 5, // Mock weekly data
            monthEarnings = completedTrips.sumOf { it.fare } * 20, // Mock monthly data
            rating = driver?.rating ?: 0f,
            totalTrips = driver?.totalTrips ?: 0,
            pendingTrips = pendingTrips
        )
        
        return Result.success(dashboard)
    }
    
    // ============ Vehicle Management ============
    
    suspend fun getVehicles(transporterId: String): Result<List<Vehicle>> {
        return Result.success(mockVehicles.filter { it.transporterId == transporterId })
    }
    
    suspend fun addVehicle(vehicle: Vehicle): Result<Vehicle> {
        mockVehicles.add(vehicle)
        return Result.success(vehicle)
    }
    
    suspend fun updateVehicle(vehicle: Vehicle): Result<Vehicle> {
        val index = mockVehicles.indexOfFirst { it.id == vehicle.id }
        if (index != -1) {
            mockVehicles[index] = vehicle
            return Result.success(vehicle)
        }
        return Result.failure(Exception("Vehicle not found"))
    }
    
    suspend fun deleteVehicle(vehicleId: String): Result<Unit> {
        mockVehicles.removeIf { it.id == vehicleId }
        return Result.success(Unit)
    }
    
    // ============ Driver Management ============
    
    suspend fun getDrivers(transporterId: String): Result<List<Driver>> {
        return Result.success(mockDrivers.filter { it.transporterId == transporterId })
    }
    
    suspend fun addDriver(driver: Driver): Result<Driver> {
        mockDrivers.add(driver)
        return Result.success(driver)
    }
    
    suspend fun updateDriver(driver: Driver): Result<Driver> {
        val index = mockDrivers.indexOfFirst { it.id == driver.id }
        if (index != -1) {
            mockDrivers[index] = driver
            return Result.success(driver)
        }
        return Result.failure(Exception("Driver not found"))
    }
    
    suspend fun deleteDriver(driverId: String): Result<Unit> {
        mockDrivers.removeIf { it.id == driverId }
        return Result.success(Unit)
    }
    
    // ============ Trip Management ============
    
    suspend fun getTrips(transporterId: String): Result<List<Trip>> {
        return Result.success(mockTrips.filter { it.transporterId == transporterId })
    }
    
    suspend fun getTripsByDriver(driverId: String): Result<List<Trip>> {
        return Result.success(mockTrips.filter { it.driverId == driverId })
    }
    
    suspend fun createTrip(trip: Trip): Result<Trip> {
        mockTrips.add(trip)
        return Result.success(trip)
    }
    
    suspend fun updateTripStatus(tripId: String, status: TripStatus): Result<Trip> {
        val index = mockTrips.indexOfFirst { it.id == tripId }
        if (index != -1) {
            val updatedTrip = mockTrips[index].copy(
                status = status,
                startedAt = if (status == TripStatus.IN_PROGRESS) System.currentTimeMillis() else mockTrips[index].startedAt,
                completedAt = if (status == TripStatus.COMPLETED) System.currentTimeMillis() else mockTrips[index].completedAt
            )
            mockTrips[index] = updatedTrip
            return Result.success(updatedTrip)
        }
        return Result.failure(Exception("Trip not found"))
    }
    
    suspend fun acceptTrip(tripId: String, driverId: String): Result<Trip> {
        return updateTripStatus(tripId, TripStatus.ACCEPTED)
    }
    
    suspend fun rejectTrip(tripId: String, driverId: String): Result<Trip> {
        return updateTripStatus(tripId, TripStatus.REJECTED)
    }
    
    // ============ Real-time tracking (Flow) ============
    
    fun trackTrip(tripId: String): Flow<TripTracking> = flow {
        // Simulate real-time location updates
        while (true) {
            val trip = mockTrips.find { it.id == tripId }
            if (trip != null && trip.status == TripStatus.IN_PROGRESS) {
                emit(
                    TripTracking(
                        tripId = tripId,
                        driverId = trip.driverId ?: "",
                        currentLocation = trip.pickupLocation, // Mock: use pickup location
                        speed = (40..80).random().toFloat(),
                        heading = (0..360).random().toFloat()
                    )
                )
            }
        }
    }
    
    // ============ BROADCAST SYSTEM - Mock Methods ============
    
    /**
     * Get all active broadcasts for transporter
     * BACKEND: Fetch broadcasts via WebSocket or API
     */
    suspend fun getMockBroadcasts(): List<BroadcastTrip> {
        delay(500) // Simulate network delay
        return listOf(
            BroadcastTrip(
                broadcastId = "bc1",
                customerId = "c1",
                customerName = "Reliance Industries",
                customerMobile = "9876543210",
                pickupLocation = Location(28.7041, 77.1025, "Connaught Place, New Delhi", "New Delhi", "Delhi", "110001"),
                dropLocation = Location(19.0760, 72.8777, "Andheri, Mumbai", "Mumbai", "Maharashtra", "400053"),
                distance = 1420.0,
                estimatedDuration = 1200,
                totalTrucksNeeded = 10,
                trucksFilledSoFar = 3,
                vehicleType = VehicleCatalog.CONTAINER,
                goodsType = "Industrial Equipment",
                weight = "25 tons",
                farePerTruck = 85000.0,
                totalFare = 850000.0,
                status = BroadcastStatus.ACTIVE,
                isUrgent = true
            ),
            BroadcastTrip(
                broadcastId = "bc2",
                customerId = "c2",
                customerName = "Amazon Logistics",
                customerMobile = "9876543211",
                pickupLocation = Location(28.5355, 77.3910, "Sector 18, Noida", "Noida", "UP", "201301"),
                dropLocation = Location(28.4595, 77.0266, "Cyber City, Gurgaon", "Gurgaon", "Haryana", "122002"),
                distance = 45.0,
                estimatedDuration = 90,
                totalTrucksNeeded = 5,
                trucksFilledSoFar = 0,
                vehicleType = VehicleCatalog.LCV,
                goodsType = "E-commerce Packages",
                weight = "2 tons",
                farePerTruck = 3500.0,
                totalFare = 17500.0,
                status = BroadcastStatus.ACTIVE,
                isUrgent = false
            ),
            BroadcastTrip(
                broadcastId = "bc3",
                customerId = "c3",
                customerName = "Adani Group",
                customerMobile = "9876543212",
                pickupLocation = Location(23.0225, 72.5714, "Ahmedabad Port", "Ahmedabad", "Gujarat", "380001"),
                dropLocation = Location(28.7041, 77.1025, "Delhi NCR", "Delhi", "Delhi", "110001"),
                distance = 950.0,
                estimatedDuration = 840,
                totalTrucksNeeded = 15,
                trucksFilledSoFar = 8,
                vehicleType = VehicleCatalog.TRAILER,
                goodsType = "Construction Materials",
                weight = "35 tons",
                farePerTruck = 65000.0,
                totalFare = 975000.0,
                status = BroadcastStatus.PARTIALLY_FILLED,
                isUrgent = false
            )
        )
    }
    
    /**
     * Get broadcast by ID
     */
    suspend fun getMockBroadcastById(broadcastId: String): BroadcastTrip? {
        delay(300)
        return getMockBroadcasts().find { it.broadcastId == broadcastId }
    }
    
    /**
     * Get available vehicles for transporter
     * BACKEND: Filter vehicles with status = AVAILABLE
     */
    suspend fun getMockAvailableVehicles(transporterId: String): List<Vehicle> {
        delay(300)
        return mockVehicles.filter { 
            it.transporterId == transporterId && it.status == VehicleStatus.AVAILABLE 
        }
    }
    
    /**
     * Get vehicles by IDs
     */
    suspend fun getMockVehiclesByIds(vehicleIds: List<String>): List<Vehicle> {
        delay(200)
        return mockVehicles.filter { it.id in vehicleIds }
    }
    
    /**
     * Get available drivers for transporter
     * BACKEND: Filter drivers with status = ACTIVE and not on trip
     */
    suspend fun getMockAvailableDrivers(transporterId: String): List<Driver> {
        delay(300)
        return mockDrivers.filter { 
            it.transporterId == transporterId && 
            it.status == DriverStatus.ACTIVE &&
            it.isAvailable
        }
    }
    
    /**
     * Get assignment details with driver statuses
     */
    suspend fun getMockAssignmentDetails(assignmentId: String): TripAssignment {
        delay(400)
        return TripAssignment(
            assignmentId = assignmentId,
            broadcastId = "bc1",
            transporterId = "t1",
            trucksTaken = 3,
            assignments = listOf(
                DriverTruckAssignment(
                    driverId = "d1",
                    driverName = "Rajesh Kumar",
                    vehicleId = "v1",
                    vehicleNumber = "GJ-01-AB-1234",
                    status = DriverResponseStatus.ACCEPTED
                ),
                DriverTruckAssignment(
                    driverId = "d2",
                    driverName = "Suresh Sharma",
                    vehicleId = "v3",
                    vehicleNumber = "MH-12-EF-9012",
                    status = DriverResponseStatus.PENDING
                ),
                DriverTruckAssignment(
                    driverId = "d3",
                    driverName = "Mohan Singh",
                    vehicleId = "v5",
                    vehicleNumber = "UP-16-IJ-7890",
                    status = DriverResponseStatus.DECLINED
                )
            ),
            pickupLocation = Location(28.7041, 77.1025, "Connaught Place, New Delhi", "New Delhi", "Delhi", "110001"),
            dropLocation = Location(19.0760, 72.8777, "Andheri, Mumbai", "Mumbai", "Maharashtra", "400053"),
            distance = 1420.0,
            farePerTruck = 85000.0,
            goodsType = "Industrial Equipment"
        )
    }
    
    /**
     * Get driver notifications
     * BACKEND: Fetch via FCM or API
     */
    suspend fun getMockDriverNotifications(driverId: String): List<DriverNotification> {
        delay(300)
        return listOf(
            DriverNotification(
                notificationId = "n1",
                assignmentId = "a1",
                driverId = driverId,
                pickupAddress = "Connaught Place, New Delhi",
                dropAddress = "Andheri, Mumbai",
                distance = 1420.0,
                estimatedDuration = 1200,
                fare = 85000.0,
                goodsType = "Industrial Equipment",
                receivedAt = System.currentTimeMillis() - 300000, // 5 min ago
                status = NotificationStatus.PENDING_RESPONSE,
                expiryTime = System.currentTimeMillis() + 300000 // Expires in 5 min
            ),
            DriverNotification(
                notificationId = "n2",
                assignmentId = "a2",
                driverId = driverId,
                pickupAddress = "Sector 18, Noida",
                dropAddress = "Cyber City, Gurgaon",
                distance = 45.0,
                estimatedDuration = 90,
                fare = 3500.0,
                goodsType = "E-commerce Packages",
                receivedAt = System.currentTimeMillis() - 600000, // 10 min ago
                status = NotificationStatus.PENDING_RESPONSE
            ),
            DriverNotification(
                notificationId = "n3",
                assignmentId = "a3",
                driverId = driverId,
                pickupAddress = "Lajpat Nagar, Delhi",
                dropAddress = "Faridabad",
                distance = 35.0,
                estimatedDuration = 60,
                fare = 2500.0,
                goodsType = "Furniture",
                receivedAt = System.currentTimeMillis() - 7200000, // 2 hours ago
                status = NotificationStatus.ACCEPTED,
                isRead = true
            )
        )
    }
    
    /**
     * Get notification by ID
     */
    suspend fun getMockNotificationById(notificationId: String): DriverNotification? {
        delay(200)
        return getMockDriverNotifications("d1").find { it.notificationId == notificationId }
    }
    
    /**
     * Get trip by ID
     */
    suspend fun getMockTripById(tripId: String): Trip? {
        delay(200)
        return mockTrips.find { it.id == tripId }
    }
    
    /**
     * Get live tracking data for driver
     * BACKEND: Continuously update GPS coordinates
     */
    suspend fun getMockLiveTracking(driverId: String): LiveTripTracking {
        delay(100)
        return LiveTripTracking(
            trackingId = "track_${System.currentTimeMillis()}",
            assignmentId = "a1",
            driverId = driverId,
            vehicleId = "v1",
            currentLocation = Location(
                latitude = 28.7041 + (Math.random() * 0.01),
                longitude = 77.1025 + (Math.random() * 0.01),
                address = "Near India Gate, Delhi",
                city = "Delhi",
                state = "Delhi"
            ),
            currentLatitude = 28.7041 + (Math.random() * 0.01),
            currentLongitude = 77.1025 + (Math.random() * 0.01),
            tripStatus = TripStatus.IN_PROGRESS,
            startedAt = System.currentTimeMillis() - 1800000,
            currentSpeed = (40..80).random().toFloat(),
            heading = (0..360).random().toFloat(),
            isLocationSharing = true
        )
    }
}
