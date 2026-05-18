package com.example.toplutasima

import com.example.toplutasima.network.rmv.withRmvAccessId
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RmvRetrofitClientTest {
    @Test
    fun `rmv requests send access id as query parameter`() {
        val request = Request.Builder()
            .url("https://www.rmv.de/hapi/location.name?format=json")
            .header("Authorization", "Bearer stale")
            .build()

        val authenticated = request.withRmvAccessId(" abc.def ")

        assertEquals("json", authenticated.url.queryParameter("format"))
        assertEquals("abc.def", authenticated.url.queryParameter("accessId"))
        assertEquals("application/json", authenticated.header("Accept"))
        assertNull(authenticated.header("Authorization"))
    }

    @Test
    fun `existing access id is not overwritten`() {
        val request = Request.Builder()
            .url("https://www.rmv.de/hapi/departureBoard?accessId=existing&format=json")
            .build()

        val authenticated = request.withRmvAccessId("new")

        assertEquals("existing", authenticated.url.queryParameter("accessId"))
    }
}
