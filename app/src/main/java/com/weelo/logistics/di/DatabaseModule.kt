package com.weelo.logistics.di

import android.content.Context
import com.weelo.logistics.data.database.WeeloDatabase
import com.weelo.logistics.data.database.dao.ActiveTripDao
import com.weelo.logistics.data.database.dao.BufferedLocationDao
import com.weelo.logistics.data.database.dao.DriverProfileDao
import com.weelo.logistics.data.database.dao.TripDao
import com.weelo.logistics.data.database.dao.VehicleDao
import com.weelo.logistics.data.database.dao.WarningDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WeeloDatabase =
        WeeloDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideTripDao(database: WeeloDatabase): TripDao = database.tripDao()

    @Provides
    @Singleton
    fun provideDriverProfileDao(database: WeeloDatabase): DriverProfileDao = database.driverProfileDao()

    @Provides
    @Singleton
    fun provideWarningDao(database: WeeloDatabase): WarningDao = database.warningDao()

    @Provides
    @Singleton
    fun provideVehicleDao(database: WeeloDatabase): VehicleDao = database.vehicleDao()

    @Provides
    @Singleton
    fun provideActiveTripDao(database: WeeloDatabase): ActiveTripDao = database.activeTripDao()
}
