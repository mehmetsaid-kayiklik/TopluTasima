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
import com.example.toplutasima.transit.duplicate.TransitDuplicateCandidateUseCase
import com.example.toplutasima.transit.duplicate.TransitDuplicateDecisionStore
import com.example.toplutasima.transit.duplicate.TransitDuplicateMergeUseCase
import com.example.toplutasima.transit.duplicate.TransitDuplicateResolutionCoordinator
import com.example.toplutasima.transit.export.TransitExportUseCase
import com.example.toplutasima.transit.history.TransitChangeHistoryStore
import com.example.toplutasima.transit.history.TransitHistoryUndoEligibilityUseCase
import com.example.toplutasima.transit.history.TransitRecordDiffUseCase
import com.example.toplutasima.transit.insights.TransitInsightsEngine
import com.example.toplutasima.transit.sync.TransitSyncStatusStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Sprint features that are intentionally independent from PersonalTrip bindings. */
val transitFeatureModule = module {
    single { TransitRecordValidationUseCase() }
    single { TransitHealthCorrectionUseCase() }
    single { TransitRecordProvenanceStore() }
    single { TransitRecordProvenanceResolver() }
    single {
        TransitDuplicateCandidateUseCase(
            // The detector is shared with Sprint 2 health warnings. Disabling the
            // Sprint 3 resolution UI must not disable the existing health signal.
            enabled = TransitFeatureFlags.POST_SAVE_DATA_HEALTH
        )
    }
    single {
        TransitDuplicateMergeUseCase(
            validationUseCase = get(),
            provenanceResolver = get(),
            enabled = TransitFeatureFlags.POST_SAVE_DATA_HEALTH &&
                TransitFeatureFlags.TRANSIT_DUPLICATE_RESOLUTION
        )
    }
    single {
        TransitDuplicateResolutionCoordinator(
            enabled = TransitFeatureFlags.POST_SAVE_DATA_HEALTH &&
                TransitFeatureFlags.TRANSIT_DUPLICATE_RESOLUTION
        )
    }
    single {
        TransitDuplicateDecisionStore.create(
            androidContext(),
            TransitFeatureFlags.POST_SAVE_DATA_HEALTH &&
                TransitFeatureFlags.TRANSIT_DUPLICATE_RESOLUTION
        )
    }
    single {
        TransitChangeHistoryStore.create(
            androidContext(),
            TransitFeatureFlags.TRANSIT_CHANGE_HISTORY
        )
    }
    single { TransitRecordDiffUseCase() }
    single { TransitHistoryUndoEligibilityUseCase(get()) }
    single { TransitExportUseCase(enabled = TransitFeatureFlags.TRANSIT_EXPORT) }
    single { TransitSyncStatusStore.get(androidContext()) }
    single {
        TransitPostSaveHealthUseCase(
            validationUseCase = get(),
            correctionUseCase = get(),
            provenanceResolver = get(),
            duplicateCandidateUseCase = get()
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
    single { TransitInsightsEngine(summaryEngine = get()) }
    single<TransitSummaryDataSource> { LocalTransitSummaryDataSource(get()) }
}
