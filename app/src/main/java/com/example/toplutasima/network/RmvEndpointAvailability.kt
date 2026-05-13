package com.example.toplutasima.network

import java.util.concurrent.ConcurrentHashMap

enum class EndpointSupportState { UNKNOWN, SUPPORTED, UNSUPPORTED }

object RmvEndpointAvailability {
    private val states = ConcurrentHashMap<String, EndpointSupportState>()

    fun state(endpoint: String): EndpointSupportState =
        states[endpoint] ?: EndpointSupportState.UNKNOWN

    fun isUnavailable(endpoint: String): Boolean =
        state(endpoint) == EndpointSupportState.UNSUPPORTED

    fun markSupported(endpoint: String) {
        states[endpoint] = EndpointSupportState.SUPPORTED
    }

    fun markUnsupported(endpoint: String) {
        states[endpoint] = EndpointSupportState.UNSUPPORTED
    }

    fun markFromException(endpoint: String, error: Throwable) {
        val apiError = error as? ApiRequestException ?: return
        if (apiError.isEndpointUnsupported) markUnsupported(endpoint)
    }

    fun clear() {
        states.clear()
    }
}
