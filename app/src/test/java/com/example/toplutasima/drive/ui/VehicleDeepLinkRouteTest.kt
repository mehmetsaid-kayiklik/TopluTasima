package com.example.toplutasima.drive.ui

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VehicleDeepLinkRouteTest {
    @Test
    fun parsesStableVehicleIdOnly() {
        val result = VehicleDeepLinkRoute.parse(
            Uri.parse("toplutasima://drive/vehicle/vehicle-uuid")
        )
        assertEquals(
            VehicleDeepLinkRoute.ParseResult.Valid("vehicle-uuid"),
            result
        )
    }

    @Test
    fun rejectsWrongShapeQueryAndUnsafeId() {
        listOf(
            "https://drive/vehicle/id",
            "toplutasima://other/vehicle/id",
            "toplutasima://drive/vehicle/id/extra",
            "toplutasima://drive/vehicle/id?uid=secret",
            "toplutasima://drive/vehicle/%2F"
        ).forEach { raw ->
            assertSame(
                raw,
                VehicleDeepLinkRoute.ParseResult.Invalid,
                VehicleDeepLinkRoute.parse(Uri.parse(raw))
            )
        }
    }
}
