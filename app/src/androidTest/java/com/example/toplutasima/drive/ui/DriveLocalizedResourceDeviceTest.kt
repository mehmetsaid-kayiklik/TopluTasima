package com.example.toplutasima.drive.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.toplutasima.R
import com.example.toplutasima.drive.model.DriveTripPurpose
import com.example.toplutasima.drive.model.VehicleFuelType
import com.example.toplutasima.ui.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DriveLocalizedResourceDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun fuelLabelsFollowAppLanguageInsteadOfHostConfiguration() {
        assertEquals(R.string.drive_fuel_electric, VehicleFuelType.ELECTRIC.labelResource(AppLanguage.TR))
        assertEquals(
            "Elektrik",
            context.getString(VehicleFuelType.ELECTRIC.labelResource(AppLanguage.TR))
        )
        assertEquals(
            "Elektrisch",
            context.getString(VehicleFuelType.ELECTRIC.labelResource(AppLanguage.DE))
        )
        assertEquals(
            "Electric",
            context.getString(VehicleFuelType.ELECTRIC.labelResource(AppLanguage.EN))
        )
    }

    @Test
    fun purposeLabelsFollowAppLanguageThroughExplicitResourceMapping() {
        assertEquals(
            R.string.drive_purpose_personal,
            DriveTripPurpose.PERSONAL.labelResource(AppLanguage.TR)
        )
        assertEquals(
            "Kişisel",
            context.getString(DriveTripPurpose.PERSONAL.labelResource(AppLanguage.TR))
        )
        assertEquals(
            "Privat",
            context.getString(DriveTripPurpose.PERSONAL.labelResource(AppLanguage.DE))
        )
        assertEquals(
            "Personal",
            context.getString(DriveTripPurpose.PERSONAL.labelResource(AppLanguage.EN))
        )
    }

    @Test
    fun unknownLabelsRemainExplicitInEveryAppLanguage() {
        assertEquals(
            "Bilinmiyor",
            context.getString(VehicleFuelType.UNKNOWN.labelResource(AppLanguage.TR))
        )
        assertEquals(
            "Unbekannt",
            context.getString(VehicleFuelType.UNKNOWN.labelResource(AppLanguage.DE))
        )
        assertEquals(
            "Unknown",
            context.getString(VehicleFuelType.UNKNOWN.labelResource(AppLanguage.EN))
        )
    }
}
