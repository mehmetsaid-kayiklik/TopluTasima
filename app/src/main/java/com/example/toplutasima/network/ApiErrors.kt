package com.example.toplutasima.network

import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

class ApiRequestException(
    val provider: String,
    val endpoint: String,
    val requestId: String,
    val statusCode: Int? = null,
    val bodyPreview: String = "",
    override val cause: Throwable? = null
) : Exception(
    buildMessage(provider, endpoint, requestId, statusCode, bodyPreview, cause),
    cause
) {
    val isRateLimited: Boolean get() = statusCode == 429

    companion object {
        private fun buildMessage(
            provider: String,
            endpoint: String,
            requestId: String,
            statusCode: Int?,
            bodyPreview: String,
            cause: Throwable?
        ): String {
            val requestSuffix = "requestId=$requestId"
            return when {
                statusCode == 429 ->
                    "$provider kotasi asildi. Biraz bekleyip tekrar deneyin. ($requestSuffix)"
                statusCode != null ->
                    "$provider istegi basarisiz oldu: HTTP $statusCode ($endpoint, $requestSuffix)"
                cause is UnknownHostException ->
                    "Ag baglantisi yok veya $provider sunucusuna ulasilamiyor. ($requestSuffix)"
                cause is SocketTimeoutException ->
                    "$provider yanit vermedi, lutfen tekrar deneyin. ($requestSuffix)"
                cause is IOException ->
                    "$provider ag hatasi: ${cause.message ?: "bilinmeyen hata"} ($requestSuffix)"
                else ->
                    "$provider istegi basarisiz oldu: ${cause?.message ?: bodyPreview.ifBlank { "bilinmeyen hata" }} ($requestSuffix)"
            }
        }
    }
}

object ApiErrors {
    fun newRequestId(): String = UUID.randomUUID().toString()

    fun fromThrowable(
        provider: String,
        endpoint: String,
        requestId: String,
        throwable: Throwable
    ): ApiRequestException {
        if (throwable is ApiRequestException) return throwable
        if (throwable is HttpException) {
            val body = runCatching { throwable.response()?.errorBody()?.string().orEmpty() }
                .getOrDefault("")
            return fromHttpStatus(provider, endpoint, requestId, throwable.code(), body, throwable)
        }
        return ApiRequestException(
            provider = provider,
            endpoint = endpoint,
            requestId = requestId,
            cause = throwable
        )
    }

    fun fromHttpStatus(
        provider: String,
        endpoint: String,
        requestId: String,
        statusCode: Int,
        body: String,
        cause: Throwable? = null
    ): ApiRequestException {
        return ApiRequestException(
            provider = provider,
            endpoint = endpoint,
            requestId = requestId,
            statusCode = statusCode,
            bodyPreview = preview(body),
            cause = cause
        )
    }

    private fun preview(body: String): String =
        body.replace(Regex("\\s+"), " ").trim().take(240)
}
