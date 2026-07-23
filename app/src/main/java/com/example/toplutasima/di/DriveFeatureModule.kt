package com.example.toplutasima.di

import com.example.toplutasima.TopluTasimaApp
import com.example.toplutasima.auth.CurrentUserProvider
import com.example.toplutasima.drive.repository.DriveSyncRepository
import com.example.toplutasima.drive.repository.DriveAdvancedRepository
import com.example.toplutasima.drive.repository.DriveSyncWorkScheduler
import com.example.toplutasima.drive.repository.DriveTripRepository
import com.example.toplutasima.drive.repository.DriveVehicleRepository
import com.example.toplutasima.drive.repository.DriveAuthSession
import com.example.toplutasima.drive.repository.OfflineFirstDriveRepository
import com.example.toplutasima.drive.sync.RoomDriveSyncRepository
import com.example.toplutasima.drive.sync.DriveAccountScopeManager
import com.example.toplutasima.drive.sync.WorkManagerDriveSyncScheduler
import com.example.toplutasima.drive.ui.DriveViewModel
import com.example.toplutasima.drive.ui.VehicleAssignmentViewModel
import com.example.toplutasima.drive.assignment.OfflineFirstVehicleAssignmentRepository
import com.example.toplutasima.drive.assignment.RoomVehicleAssignmentSyncCoordinator
import com.example.toplutasima.drive.assignment.VehicleAssignmentRepository
import com.example.toplutasima.drive.assignment.VehiclePersonDirectoryRefresher
import com.example.toplutasima.drive.photo.AndroidVehiclePhotoPreparer
import com.example.toplutasima.drive.photo.FirebaseVehiclePhotoRemoteDataSource
import com.example.toplutasima.drive.photo.OfflineFirstVehiclePhotoRepository
import com.example.toplutasima.drive.photo.RoomVehiclePhotoSyncCoordinator
import com.example.toplutasima.drive.photo.VehiclePhotoFileStore
import com.example.toplutasima.drive.photo.VehiclePhotoPreparer
import com.example.toplutasima.drive.photo.VehiclePhotoRemoteDataSource
import com.example.toplutasima.drive.photo.VehiclePhotoRepository
import com.example.toplutasima.drive.photo.VehiclePhotoSyncScheduler
import com.example.toplutasima.drive.photo.WorkManagerVehiclePhotoSyncScheduler
import com.example.toplutasima.drive.ui.VehiclePhotoViewModel
import com.example.toplutasima.drive.ui.VehicleLedgerViewModel
import com.example.toplutasima.drive.DriveFeatureFlags
import com.example.toplutasima.drive.ledger.VehicleLedgerSyncCoordinator
import com.example.toplutasima.drive.ledger.FirestoreVehicleLedgerRemoteDataSource
import com.example.toplutasima.drive.ledger.OfflineFirstVehicleLedgerRepository
import com.example.toplutasima.drive.ledger.RoomVehicleLedgerSyncCoordinator
import com.example.toplutasima.drive.ledger.VehicleLedgerRemoteDataSource
import com.example.toplutasima.drive.ledger.VehicleLedgerRepository
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/** Dependency boundary for the optional, transit-independent drive core. */
val driveFeatureModule = module {
    single { VehiclePhotoFileStore(androidContext()) }
    single<VehiclePhotoSyncScheduler> { WorkManagerVehiclePhotoSyncScheduler(androidContext()) }
    single<VehiclePhotoPreparer> { AndroidVehiclePhotoPreparer(androidContext(), get()) }
    single<VehiclePhotoRemoteDataSource> {
        FirebaseVehiclePhotoRemoteDataSource(fileStore = get())
    }
    single {
        RoomVehiclePhotoSyncCoordinator(
            database = (androidApplication() as TopluTasimaApp).database,
            remote = get(),
            fileStore = get(),
            currentUserId = CurrentUserProvider::currentUserIdOrNull
        )
    }
    single<VehiclePhotoRepository> {
        OfflineFirstVehiclePhotoRepository(
            database = (androidApplication() as TopluTasimaApp).database,
            currentUserId = CurrentUserProvider::currentUserIdOrNull,
            authenticatedUidChanges = DriveAuthSession.authenticatedUidChanges(),
            preparer = get(),
            remote = get(),
            fileStore = get(),
            scheduler = get()
        )
    }
    single {
        WorkManagerDriveSyncScheduler(context = androidContext())
    }
    single<DriveSyncWorkScheduler> { get<WorkManagerDriveSyncScheduler>() }
    single<VehicleLedgerRemoteDataSource> { FirestoreVehicleLedgerRemoteDataSource() }
    single {
        RoomVehicleLedgerSyncCoordinator(
            database = (androidApplication() as TopluTasimaApp).database,
            remote = get(),
            currentUserId = CurrentUserProvider::currentUserIdOrNull
        )
    }
    single<VehicleLedgerRepository> {
        OfflineFirstVehicleLedgerRepository(
            database = (androidApplication() as TopluTasimaApp).database,
            currentUserId = CurrentUserProvider::currentUserIdOrNull,
            authenticatedUidChanges = DriveAuthSession.authenticatedUidChanges(),
            syncScheduler = get()
        )
    }
    single {
        OfflineFirstDriveRepository(
            database = (androidApplication() as TopluTasimaApp).database,
            authenticatedUid = CurrentUserProvider::currentUserIdOrNull,
            authenticatedUidChanges = DriveAuthSession.authenticatedUidChanges(),
            syncScheduler = get(),
            vehicleAssignmentRepository = get()
        )
    }
    single<DriveVehicleRepository> { get<OfflineFirstDriveRepository>() }
    single<DriveTripRepository> { get<OfflineFirstDriveRepository>() }
    single<DriveAdvancedRepository> { get<OfflineFirstDriveRepository>() }
    single {
        VehiclePersonDirectoryRefresher(
            database = (androidApplication() as TopluTasimaApp).database,
            profileSyncRepository = get()
        )
    }
    single {
        OfflineFirstVehicleAssignmentRepository(
            database = (androidApplication() as TopluTasimaApp).database,
            currentUserId = CurrentUserProvider::currentUserIdOrNull,
            authenticatedUidChanges = DriveAuthSession.authenticatedUidChanges(),
            syncScheduler = get(),
            directoryRefresher = get<VehiclePersonDirectoryRefresher>()::refresh
        )
    }
    single<VehicleAssignmentRepository> { get<OfflineFirstVehicleAssignmentRepository>() }
    single<DriveSyncRepository> {
        val database = (androidApplication() as TopluTasimaApp).database
        RoomDriveSyncRepository(
            database = database,
            assignmentSyncCoordinator = RoomVehicleAssignmentSyncCoordinator(
                database = database,
                directoryRefresher = get()
            ),
            photoPullCoordinator = get<RoomVehiclePhotoSyncCoordinator>(),
            ledgerSyncCoordinator = if (DriveFeatureFlags.DRIVE_VEHICLE_LEDGER) {
                get<RoomVehicleLedgerSyncCoordinator>()
            } else {
                VehicleLedgerSyncCoordinator.NoOp
            }
        )
    }
    single {
        val app = androidApplication() as TopluTasimaApp
        val photoScheduler = get<VehiclePhotoSyncScheduler>()
        val photoFileStore = get<VehiclePhotoFileStore>()
        // App startup also happens while signed out (including instrumented tests). Keep the
        // database-backed repository lazy until an authenticated owner actually needs replay.
        val photoRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            get<VehiclePhotoRepository>()
        }
        val ledgerRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            get<VehicleLedgerRepository>()
        }
        DriveAccountScopeManager(
            databaseProvider = {
                if (CurrentUserProvider.currentUserIdOrNull() == null) {
                    app.databaseIfInitialized()
                } else {
                    app.database
                }
            },
            onAuthenticatedUserChanged =
                get<WorkManagerDriveSyncScheduler>()::onAuthenticatedUserChanged,
            currentUserId = CurrentUserProvider::currentUserIdOrNull,
            onPhotoScopeChanged = { ownerUid ->
                photoScheduler.onAuthenticatedUserChanged(ownerUid)
                photoFileStore.clearOutside(ownerUid)
                if (ownerUid != null) photoRepository.schedulePendingOperations()
                if (ownerUid != null && DriveFeatureFlags.DRIVE_VEHICLE_LEDGER) {
                    ledgerRepository.runLegacyOdometerBackfill()
                    ledgerRepository.schedulePendingSync()
                }
            }
        )
    }
    viewModel {
        DriveViewModel(
            vehicleRepository = get(),
            tripRepository = get(),
            advancedRepository = get()
        )
    }
    viewModel { VehicleAssignmentViewModel(repository = get()) }
    viewModel { VehiclePhotoViewModel(repository = get()) }
    viewModel { VehicleLedgerViewModel(repository = get()) }
}
