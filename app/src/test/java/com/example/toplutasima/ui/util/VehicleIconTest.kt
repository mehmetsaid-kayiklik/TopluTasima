package com.example.toplutasima.ui.util

import com.example.toplutasima.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleIconTest {

    @Test
    fun `vehicleIcon returns bus emoji for bus key`() {
        assertEquals("🚌", vehicleIcon(VehicleType.BUS.key))
    }

    @Test
    fun `vehicleIcon returns train emoji for sbahn key`() {
        assertEquals("🚆", vehicleIcon(VehicleType.SBAHN.key))
    }

    @Test
    fun `vehicleIcon returns metro emoji for ubahn key`() {
        assertEquals("🚇", vehicleIcon(VehicleType.UBAHN.key))
    }

    @Test
    fun `vehicleIcon returns locomotive emoji for regional rail key`() {
        assertEquals("🚂", vehicleIcon(VehicleType.RERB.key))
    }

    @Test
    fun `vehicleIcon returns high speed train emoji for long distance rail key`() {
        assertEquals("🚄", vehicleIcon(VehicleType.FERNZUG.key))
    }

    @Test
    fun `vehicleIcon returns tram emoji for tram key`() {
        assertEquals("🚋", vehicleIcon(VehicleType.STRASSENBAHN.key))
    }

    @Test
    fun `vehicleIcon falls back to bus emoji for unknown key`() {
        assertEquals("🚌", vehicleIcon("unknown"))
    }

    @Test
    fun `vehicleIcon falls back to bus emoji for blank key`() {
        assertEquals("🚌", vehicleIcon(""))
    }
}
