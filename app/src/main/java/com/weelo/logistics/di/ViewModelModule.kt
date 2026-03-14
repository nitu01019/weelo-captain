package com.weelo.logistics.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/**
 * ViewModelModule - Hilt module for ViewModel scoping.
 *
 * ViewModels annotated with @HiltViewModel are automatically
 * provided by Hilt — no manual @Provides needed here.
 *
 * Usage in ViewModel:
 *   @HiltViewModel
 *   class MyViewModel @Inject constructor(
 *       private val repo: TripRepository
 *   ) : ViewModel()
 *
 * Usage in Activity/Fragment:
 *   val viewModel: MyViewModel by viewModels()
 */
@Module
@InstallIn(ViewModelComponent::class)
object ViewModelModule
