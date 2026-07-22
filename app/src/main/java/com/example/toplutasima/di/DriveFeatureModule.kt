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
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/** Dependency boundary for the optional, transit-independent drive core. */
val driveFeatureModule = module {
    single {
        WorkManagerDriveSyncScheduler(context = androidContext())
    }
    single<DriveSyncWorkScheduler> { get<WorkManagerDriveSyncScheduler>() }
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
            )
        )
    }
    single {
        val app = androidApplication() as TopluTasimaApp
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
            currentUserId = CurrentUserProvider::currentUserIdOrNull
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
}
