package com.example.toplutasima.di

import com.example.toplutasima.usecase.transit.TransitRecordValidationUseCase
import com.example.toplutasima.usecase.transit.TransitHealthCorrectionUseCase
import com.example.toplutasima.usecase.transit.TransitPostSaveHealthUseCase
import com.example.toplutasima.transit.provenance.TransitRecordProvenanceResolver
import com.example.toplutasima.transit.provenance.TransitRecordProvenanceStore
import com.example.toplutasima.transit.summary.TransitSummaryEngine
import com.example.toplutasima.transit.summary.TransitSummaryHealthAssessor
import com.example.toplutasima.transit.summary.TransitSummaryHealthAssessorAdapter
import com.example.toplutasima.viewmodel.summary.LocalTransitSummaryDataSource
import com.example.toplutasima.viewmodel.summary.TransitSummaryDataSource
import com.example.toplutasima.transit.TransitFeatureFlags
import org.koin.dsl.module

/** Sprint features that are intentionally independent from PersonalTrip bindings. */
val transitFeatureModule = module {
    single { TransitRecordValidationUseCase() }
    single { TransitHealthCorrectionUseCase() }
    single { TransitRecordProvenanceStore() }
    single { TransitRecordProvenanceResolver() }
    single {
        TransitPostSaveHealthUseCase(
            validationUseCase = get(),
            correctionUseCase = get(),
            provenanceResolver = get()
        )
    }
    single<TransitSummaryHealthAssessor> {
        TransitSummaryHealthAssessorAdapter(
            healthUseCase = get(),
            provenanceStore = get(),
            provenanceEnabled = TransitFeatureFlags.PROVENANCE_BADGES
        )
    }
    single {
        TransitSummaryEngine(
            healthAssessor = if (TransitFeatureFlags.POST_SAVE_DATA_HEALTH) get() else null
        )
    }
    single<TransitSummaryDataSource> { LocalTransitSummaryDataSource(get()) }
}
