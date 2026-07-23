package com.example.toplutasima.drive.ui

import com.example.toplutasima.drive.model.DriveOdometerSource
import com.example.toplutasima.drive.model.DriveSyncState
import com.example.toplutasima.drive.model.DriveTrip
import com.example.toplutasima.drive.model.DriveTripPurpose
import com.example.toplutasima.drive.model.DriveVehicle
import com.example.toplutasima.drive.model.DriveVehicleOverview
import com.example.toplutasima.drive.model.DriveVehicleSummary
import com.example.toplutasima.drive.model.VehicleFuelType
import com.example.toplutasima.drive.model.VehicleTransmissionType
import com.example.toplutasima.drive.model.VehicleBodyType
import com.example.toplutasima.drive.model.DriveFieldProvenance
import com.example.toplutasima.drive.model.DriveHealthIssue
import com.example.toplutasima.drive.model.DriveSyncReceipt
import com.example.toplutasima.drive.model.DriveVehicleListCriteria
import com.example.toplutasima.drive.validation.DriveDecimalParseResult
import com.example.toplutasima.drive.validation.DriveDecimalParser
import java.time.Instant

sealed interface DriveDestination {
    data object VehicleList : DriveDestination
    data class VehicleDetail(val vehicleId: String) : DriveDestination
    data class VehicleEditor(val vehicleId: String?) : DriveDestination
    data class TripEditor(
        val vehicleId: String,
        val tripId: String?
    ) : DriveDestination
}

enum class DriveFormError {
    REQUIRED,
    INVALID_NUMBER,
    INVALID_DATE,
    INVALID_TIME,
    INVALID_CODE,
    DISPLAY_NAME_REQUIRED,
    MODEL_YEAR_OUT_OF_RANGE,
    NEGATIVE_ODOMETER,
    CURRENT_ODOMETER_BEFORE_INITIAL,
    VEHICLE_REQUIRED,
    VEHICLE_NOT_FOUND,
    END_BEFORE_START,
    NEGATIVE_DISTANCE,
    END_ODOMETER_BEFORE_START,
    DISTANCE_REQUIRED,
    DISTANCE_ODOMETER_MISMATCH
}

enum class DriveUiMessage {
    AUTHENTICATION_REQUIRED,
    OWNERSHIP_MISMATCH,
    RECORD_NOT_FOUND,
    RECORD_DELETED,
    DATABASE_FAILURE,
    SCHEDULING_FAILURE,
    UNKNOWN_FAILURE,
    VEHICLE_SAVED,
    TRIP_SAVED,
    VEHICLE_DELETED,
    TRIP_DELETED,
    BULK_VEHICLES_DELETED
}

enum class DriveNoticeKind {
    SUCCESS,
    ERROR
}

data class DriveUiNotice(
    val kind: DriveNoticeKind,
    val message: DriveUiMessage
)

data class DriveVehicleFormState(
    val editingVehicleId: String? = null,
    val displayName: String = "",
    val brand: String = "",
    val model: String = "",
    val licensePlate: String = "",
    val modelYear: String = "",
    val fuelType: VehicleFuelType = VehicleFuelType.UNKNOWN,
    val initialOdometerKm: String = "",
    val currentOdometerKm: String = "",
    val assignedPersonId: String? = null,
    val notes: String = "",
    val countryCode: String = "",
    val transmissionType: VehicleTransmissionType = VehicleTransmissionType.UNKNOWN,
    val bodyType: VehicleBodyType = VehicleBodyType.UNKNOWN,
    val color: String = "",
    val vin: String = "",
    val engineDisplacementCc: String = "",
    val enginePowerKw: String = "",
    val purchaseDate: String = "",
    val purchasePrice: String = "",
    val currencyCode: String = "",
    val trimLevel: String = "",
    val engineCode: String = "",
    val registrationDate: String = "",
    val inspectionDueDate: String = "",
    val insuranceDueDate: String = "",
    val tireSize: String = "",
    val fieldErrors: Map<String, DriveFormError> = emptyMap()
)

data class DriveTripFormState(
    val editingTripId: String? = null,
    val vehicleId: String = "",
    val startedDate: String = "",
    val startedTime: String = "",
    val endedDate: String = "",
    val endedTime: String = "",
    val startOdometerKm: String = "",
    val endOdometerKm: String = "",
    val distanceKm: String = "",
    val purpose: DriveTripPurpose = DriveTripPurpose.UNCLASSIFIED,
    val startLocationName: String = "",
    val endLocationName: String = "",
    val notes: String = "",
    val originalStartedAt: Instant? = null,
    val originalEndedAt: Instant? = null,
    val fieldErrors: Map<String, DriveFormError> = emptyMap()
) {
    val calculatedDistanceKm: Double?
        get() {
            val start = (DriveDecimalParser.parse(startOdometerKm) as? DriveDecimalParseResult.Valid)
                ?.value ?: return null
            val end = (DriveDecimalParser.parse(endOdometerKm) as? DriveDecimalParseResult.Valid)
                ?.value ?: return null
            return (end - start).takeIf { it >= 0.0 }
        }
}

sealed interface DriveDeletePrompt {
    data class Vehicle(
        val vehicleId: String,
        val activeTripCount: Int
    ) : DriveDeletePrompt

    data class Trip(
        val tripId: String,
        val vehicleId: String
    ) : DriveDeletePrompt

    data class BulkVehicles(
        val vehicleIds: Set<String>
    ) : DriveDeletePrompt
}

data class DriveUiState(
    val featureEnabled: Boolean = true,
    val destination: DriveDestination = DriveDestination.VehicleList,
    val vehicles: List<DriveVehicleOverview> = emptyList(),
    val totalVehicleCount: Int = 0,
    val listCriteria: DriveVehicleListCriteria = DriveVehicleListCriteria(),
    val selectionMode: Boolean = false,
    val selectedVehicleIds: Set<String> = emptySet(),
    val vehiclesLoading: Boolean = true,
    val vehiclesError: DriveUiMessage? = null,
    val selectedVehicle: DriveVehicle? = null,
    val selectedVehicleSummary: DriveVehicleSummary? = null,
    val selectedVehicleTrips: List<DriveTrip> = emptyList(),
    val selectedVehicleProvenance: List<DriveFieldProvenance> = emptyList(),
    val healthIssues: List<DriveHealthIssue> = emptyList(),
    val syncReceipts: List<DriveSyncReceipt> = emptyList(),
    val detailLoading: Boolean = false,
    val detailError: DriveUiMessage? = null,
    val externalVehicleRequest: Boolean = false,
    val vehicleForm: DriveVehicleFormState? = null,
    val tripForm: DriveTripFormState? = null,
    val deletePrompt: DriveDeletePrompt? = null,
    val isMutating: Boolean = false,
    val notice: DriveUiNotice? = null
)

internal fun DriveVehicleSummary?.displayedOdometerSource(): DriveOdometerSource =
    this?.displayedOdometerSource ?: DriveOdometerSource.UNAVAILABLE

internal fun DriveVehicle.syncNeedsAttention(): Boolean =
    syncState == DriveSyncState.RETRYABLE_ERROR || syncState == DriveSyncState.PERMANENT_ERROR

object DriveUiTestTags {
    const val ROOT = "drive_root"
    const val VEHICLE_LIST = "drive_vehicle_list"
    const val VEHICLE_EMPTY = "drive_vehicle_empty"
    const val VEHICLE_CARD = "drive_vehicle_card"
    const val ADD_VEHICLE = "drive_add_vehicle"
    const val VEHICLE_DETAIL = "drive_vehicle_detail"
    const val EDIT_VEHICLE = "drive_edit_vehicle"
    const val DELETE_VEHICLE = "drive_delete_vehicle"
    const val ADD_TRIP = "drive_add_trip"
    const val TRIP_LIST = "drive_trip_list"
    const val TRIP_CARD = "drive_trip_card"
    const val VEHICLE_EDITOR = "drive_vehicle_editor"
    const val VEHICLE_NAME_INPUT = "drive_vehicle_name_input"
    const val TRIP_EDITOR = "drive_trip_editor"
    const val SAVE_FORM = "drive_save_form"
    const val DELETE_CONFIRMATION = "drive_delete_confirmation"
    const val SEARCH = "drive_vehicle_search"
    const val FILTER = "drive_vehicle_filter"
    const val SORT = "drive_vehicle_sort"
    const val SELECTION = "drive_vehicle_selection"
    const val BULK_DELETE = "drive_vehicle_bulk_delete"
    const val PHOTO_SECTION = "drive_vehicle_photo_section"
    const val PHOTO_EMPTY = "drive_vehicle_photo_empty"
    const val PHOTO_ADD = "drive_vehicle_photo_add"
    const val PHOTO_RETRY = "drive_vehicle_photo_retry"
    const val PHOTO_PRIMARY = "drive_vehicle_photo_primary"
}
