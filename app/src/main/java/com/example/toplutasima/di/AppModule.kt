package com.example.toplutasima.di

import android.app.Application
import com.example.toplutasima.location.NearbyStopsManager
import com.example.toplutasima.repository.PersonalTripRepository
import com.example.toplutasima.repository.TripRepository
import com.example.toplutasima.usecase.TripPlanningUseCase
import com.example.toplutasima.viewmodel.BulkUpdateViewModel
import com.example.toplutasima.viewmodel.PersonalTripViewModel
import com.example.toplutasima.viewmodel.RecordsViewModel
import com.example.toplutasima.viewmodel.RmvLogViewModel
import com.example.toplutasima.viewmodel.SettingsViewModel
import com.example.toplutasima.viewmodel.SummaryViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Uygulamanın tek Koin modülü.
 *
 * – FirestoreService ve RmvApiService Kotlin `object` (global singleton) oldukları için
 *   buraya kaydetmeye gerek yok; TripRepository onlara doğrudan erişmeye devam eder.
 * – ViewModels, Koin'in `viewModel { }` bloğu ile tanımlanır; böylece
 *   Compose ekranlarında `koinViewModel()` ile inject edilebilirler.
 */
val appModule = module {

    // ── Repository ──────────────────────────────────────────────────────────
    single { TripRepository() }
    single { PersonalTripRepository() }       // Kişisel Araç — "personaltrips"

    // ── Use Case ────────────────────────────────────────────────────────────
    single { TripPlanningUseCase(get()) }

    // ── Location ────────────────────────────────────────────────────────────
    single { NearbyStopsManager(androidContext(), get()) }

    // ── ViewModels ──────────────────────────────────────────────────────────
    viewModel { RmvLogViewModel(get<Application>(), get(), get(), get()) }
    viewModel { SummaryViewModel(get<Application>(), get()) }
    viewModel { RecordsViewModel(get<Application>()) }
    viewModel { BulkUpdateViewModel(get<Application>()) }
    viewModel { SettingsViewModel(get<Application>()) }
    viewModel { PersonalTripViewModel(get<Application>(), get()) }  // Kişisel Araç
}
