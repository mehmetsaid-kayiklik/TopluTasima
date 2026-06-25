package com.example.toplutasima.network.rmv

import com.example.toplutasima.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

private val json = Json {
    ignoreUnknownKeys = true   // RMV returns many fields we don't need
    isLenient = true           // Tolerate minor format quirks
    coerceInputValues = true   // Use defaults instead of failing on null mismatches
}

private val rmvHttpClient = OkHttpClient.Builder()
    .addInterceptor { chain ->
        chain.proceed(chain.request().withRmvAccessId(BuildConfig.RMV_ACCESS_ID))
    }
    .build()

internal fun okhttp3.Request.withRmvAccessId(accessId: String): okhttp3.Request {
    val cleanAccessId = accessId.trim()
    val hasAccessId = url.queryParameter("accessId").isNullOrBlank().not()
    val authenticatedUrl = when {
        cleanAccessId.isBlank() || hasAccessId -> url
        else -> url.newBuilder()
            .setQueryParameter("accessId", cleanAccessId)
            .build()
    }

    return newBuilder()
        .url(authenticatedUrl)
        .removeHeader("Authorization")
        .header("Accept", "application/json")
        .build()
}

internal val rmvRetrofit: Retrofit = Retrofit.Builder()
    .baseUrl("https://www.rmv.de/hapi/")
    .client(rmvHttpClient)
    .addConverterFactory(json.asConverterFactory("application/json; charset=utf-8".toMediaType()))
    .build()

interface RmvApi {
    @GET("location.name")
    suspend fun searchStops(
        @Query("input") input: String,
        @Query("type") type: String = "S",
        @Query("maxNo") maxNo: Int = 3,
        @Query("format") format: String = "json",
        @Query("requestId") requestId: String? = null
    ): RmvLocationResponse

    @GET("departureBoard")
    suspend fun getDepartures(
        @Query("id") stopId: String,
        @Query("date") date: String,
        @Query("time") time: String,
        @Query("format") format: String = "json",
        @Query("requestId") requestId: String? = null
    ): RmvDepartureBoardResponse

    @GET("trip")
    suspend fun getTrip(
        @Query("originId") originId: String,
        @Query("destId") destId: String,
        @Query("date") date: String,
        @Query("time") time: String,
        @Query("numF") numF: Int = 1,
        @Query("format") format: String = "json",
        @Query("requestId") requestId: String? = null
    ): kotlinx.serialization.json.JsonObject     // too polymorphic → parse manually

    @GET("journeyDetail")
    suspend fun getJourneyDetail(
        @Query("id") ref: String,
        @Query("poly") poly: String = "1",
        @Query("format") format: String = "json",
        @Query("requestId") requestId: String? = null
    ): kotlinx.serialization.json.JsonObject     // too polymorphic → parse manually

    @GET("location.nearbystops")
    suspend fun getNearbyStops(
        @Query("originCoordLat") lat: Double,     // decimal degrees (WGS84)
        @Query("originCoordLong") lon: Double,    // decimal degrees (WGS84)
        @Query("maxNo") maxNo: Int = 8,
        @Query("r") radiusMeters: Int = 500,      // meters
        @Query("type") type: String = "S",
        @Query("format") format: String = "json",
        @Query("requestId") requestId: String? = null
    ): kotlinx.serialization.json.JsonObject      // polymorphic → parse manually

    @GET("himsearch")
    suspend fun getTransitAlerts(
        @Query("line") line: String? = null,
        @Query("dateB") date: String? = null,
        @Query("format") format: String = "json",
        @Query("requestId") requestId: String? = null
    ): JsonObject

    @GET("trafficmessages/datex2")
    suspend fun getTrafficMessages(
        @Query("line") line: String? = null,
        @Query("format") format: String = "json",
        @Query("requestId") requestId: String? = null
    ): JsonObject

    @GET("location.search")
    suspend fun searchLocations(
        @Query("input") input: String,
        @Query("maxNo") maxNo: Int = 8,
        @Query("format") format: String = "json",
        @Query("requestId") requestId: String? = null
    ): JsonObject

    @GET("location.addresslookup")
    suspend fun lookupAddress(
        @Query("input") input: String,
        @Query("maxNo") maxNo: Int = 5,
        @Query("format") format: String = "json",
        @Query("requestId") requestId: String? = null
    ): JsonObject

    @POST("journeyTrackMatch")
    suspend fun matchJourneyTrack(
        @Body body: JsonObject,
        @Query("format") format: String = "json",
        @Query("requestId") requestId: String? = null
    ): JsonObject

}

val rmvApi: RmvApi = rmvRetrofit.create(RmvApi::class.java)
