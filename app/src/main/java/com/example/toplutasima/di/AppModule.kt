package com.example.toplutasima.di

import com.example.toplutasima.TopluTasimaApp
import com.example.toplutasima.data.repository.LocalTripRepository
import com.example.toplutasima.data.repository.ProfileSyncRepository
import com.example.toplutasima.location.NearbyStopsManager
import com.example.toplutasima.network.firestore.FirestoreFavoriteDataSource
import com.example.toplutasima.network.firestore.FirestoreMigrationService
import com.example.toplutasima.network.firestore.FirestoreTripRemoteDataSource
import com.example.toplutasima.repository.PersonalTripRepository
import com.example.toplutasima.repository.RmvTripRepository
import com.example.toplutasima.repository.TransitRecordRepository
import com.example.toplutasima.repository.TripProfileLinkRepository
import com.example.toplutasima.repository.TripRecordMapper
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.usecase.JourneyMatchUseCase
import com.example.toplutasima.usecase.ManualEntryUseCase
import com.example.toplutasima.usecase.RecordSaveUseCase
import com.example.toplutasima.usecase.RmvMesafeBackfillUseCase
import com.example.toplutasima.usecase.StopSelectionUseCase
import com.example.toplutasima.usecase.TripPlanningUseCase
import com.example.toplutasima.viewmodel.BulkUpdateViewModel
import com.example.toplutasima.viewmodel.PersonalTripViewModel
import com.example.toplutasima.viewmodel.RecordsViewModel
import com.example.toplutasima.viewmodel.RmvLogViewModel
import com.example.toplutasima.viewmodel.SettingsViewModel
import com.example.toplutasima.viewmodel.SummaryViewModel
import com.example.toplutasima.worker.TopluTasimaWorkerFactory
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Uygulamanın tek Koin modülü.
 */
val appModule = module {

    // ── Remote Data Sources ─────────────────────────────────────────────────
    single { FirestoreTripRemoteDataSource() }
    single { FirestoreMigrationService() }
    single { FirestoreFavoriteDataSource() }

    // ── Repository ──────────────────────────────────────────────────────────
    single { RmvTripRepository() }
    single { TripProfileLinkRepository(androidContext()) }
    single {
        TransitRecordRepository(
            appContext = androidContext(),
            profileLinkRepository = get(),
            recordMapper = TripRecordMapper,
            tripRemoteDataSource = get()
        )
    }
    single {
        LocalTripRepository(
            androidContext(),
            (androidApplication() as TopluTasimaApp).database.tripDao(),
            get()
        )
    }
    single { PersonalTripRepository() }       // Kişisel Araç — "personaltrips"
    single { ProfileSyncRepository(androidContext()) }
    single { TopluTasimaWorkerFactory() }

    // ── Use Case ────────────────────────────────────────────────────────────
    single { TripPlanningUseCase(get()) }
    single { StopSelectionUseCase(get(), PrefsManager) }
    single { JourneyMatchUseCase() }
    single { RecordSaveUseCase(get(), get()) }
    single { ManualEntryUseCase() }
    single { RmvMesafeBackfillUseCase(get()) }

    // ── Location ────────────────────────────────────────────────────────────
    single { NearbyStopsManager(androidContext(), get()) }

    // ── ViewModels ──────────────────────────────────────────────────────────
    viewModel {
        RmvLogViewModel(
            application = androidApplication(),
            stopSelectionUseCase = get(),
            journeyMatchUseCase = get(),
            recordSaveUseCase = get(),
            manualEntryUseCase = get(),
            rmvTripRepository = get(),
            transitRecordRepository = get(),
            tripProfileLinkRepository = get(),
            tripPlanner = get(),
            nearbyManager = get(),
            profileSyncRepository = get()
        )
    }
    viewModel { SummaryViewModel(androidApplication()) }
    viewModel { RecordsViewModel(androidApplication(), get()) }
    viewModel { BulkUpdateViewModel(androidApplication(), get()) }
    viewModel { SettingsViewModel(androidApplication(), get()) }
    viewModel { PersonalTripViewModel(androidApplication(), get()) }  // Kişisel Araç
}
