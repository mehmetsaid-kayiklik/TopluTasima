package com.example.toplutasima

import com.example.toplutasima.network.rmv.RmvApi
import com.example.toplutasima.network.rmv.withRmvAccessId
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.Header
import retrofit2.http.Headers

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
            .addHeader("Authorization", "Bearer stale")
            .addHeader("Authorization", "Bearer older")
            .build()

        val authenticated = request.withRmvAccessId("new")

        assertEquals("existing", authenticated.url.queryParameter("accessId"))
        assertTrue(authenticated.headers("Authorization").isEmpty())
    }

    @Test
    fun `rmv retrofit methods do not declare authorization headers`() {
        RmvApi::class.java.declaredMethods.forEach { method ->
            val methodHeaders = method.getAnnotation(Headers::class.java)?.value.orEmpty()
            assertFalse(
                "${method.name} must not declare Authorization header",
                methodHeaders.any { it.startsWith("Authorization", ignoreCase = true) }
            )

            method.parameterAnnotations.flatten().forEach { annotation ->
                if (annotation is Header) {
                    assertFalse(
                        "${method.name} must not accept Authorization header",
                        annotation.value.equals("Authorization", ignoreCase = true)
                    )
                }
            }
        }
    }
}
