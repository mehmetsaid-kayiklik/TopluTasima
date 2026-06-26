package com.example.toplutasima.network

import org.json.JSONObject
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
    val retryAfterSeconds: Long? = null,
    val bodyPreview: String = "",
    val errorCode: String? = null,
    override val cause: Throwable? = null
) : Exception(
    buildMessage(provider, endpoint, requestId, statusCode, retryAfterSeconds, bodyPreview, cause),
    cause
) {
    val isRateLimited: Boolean get() = statusCode == 429
    val isAccessDenied: Boolean get() = statusCode == 403
    val isEndpointUnsupported: Boolean get() = statusCode in setOf(404, 405, 501)
    val isSvcParamInvalid: Boolean get() = statusCode == 400 && errorCode == "SVC_PARAM"

    companion object {
        private fun buildMessage(
            provider: String,
            endpoint: String,
            requestId: String,
            statusCode: Int?,
            retryAfterSeconds: Long?,
            bodyPreview: String,
            cause: Throwable?
        ): String {
            val requestSuffix = "requestId=$requestId"
            val retrySuffix = retryAfterSeconds?.let { " $it saniye sonra tekrar deneyin." }.orEmpty()
            return when {
                statusCode == 429 ->
                    "$provider kotasi asildi.$retrySuffix ($requestSuffix)"
                statusCode == 403 ->
                    "$provider erisimi reddetti: HTTP 403. API anahtari eksik/gecersiz veya bu endpoint icin yetki yok. ($endpoint, $requestSuffix)"
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
            val retryAfter = throwable.response()?.headers()?.get("Retry-After")
            return fromHttpStatus(provider, endpoint, requestId, throwable.code(), body, throwable, retryAfter)
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
        cause: Throwable? = null,
        retryAfterHeader: String? = null
    ): ApiRequestException {
        return ApiRequestException(
            provider = provider,
            endpoint = endpoint,
            requestId = requestId,
            statusCode = statusCode,
            retryAfterSeconds = parseRetryAfterSeconds(retryAfterHeader),
            bodyPreview = preview(body),
            errorCode = parseErrorCode(body),
            cause = cause
        )
    }

    private fun parseRetryAfterSeconds(value: String?): Long? =
        value?.trim()?.toLongOrNull()?.takeIf { it >= 0 }

    private fun parseErrorCode(body: String): String? =
        runCatching { JSONObject(body).optString("errorCode").takeIf { it.isNotBlank() } }
            .getOrNull()

    private fun preview(body: String): String =
        body.replace(Regex("\\s+"), " ").trim().take(240)
}
