package com.weelo.logistics.di

import com.weelo.logistics.data.database.dao.ActiveTripDao
import com.weelo.logistics.data.database.dao.DriverProfileDao
import com.weelo.logistics.data.database.dao.TripDao
import com.weelo.logistics.data.database.dao.VehicleDao
import com.weelo.logistics.data.database.dao.WarningDao
import com.weelo.logistics.data.database.repositories.DriverProfileRepository
import com.weelo.logistics.data.database.repositories.TripRepository
import com.weelo.logistics.data.database.repositories.VehicleRepository
import com.weelo.logistics.data.database.repositories.WarningRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideTripRepository(
        dao: ActiveTripDao,
        tripDao: TripDao
    ): TripRepository = TripRepository(tripDao, dao)

    @Provides
    @Singleton
    fun provideDriverProfileRepository(dao: DriverProfileDao): DriverProfileRepository =
        DriverProfileRepository(dao)

    @Provides
    @Singleton
    fun provideWarningRepository(dao: WarningDao): WarningRepository =
        WarningRepository(dao)

    @Provides
    @Singleton
    fun provideVehicleRepository(dao: VehicleDao): VehicleRepository =
        VehicleRepository(dao)
}
