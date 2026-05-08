package com.example.toplutasima.network.rmv

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

private val json = Json {
    ignoreUnknownKeys = true   // RMV returns many fields we don't need
    isLenient = true           // Tolerate minor format quirks
    coerceInputValues = true   // Use defaults instead of failing on null mismatches
}

internal val rmvRetrofit: Retrofit = Retrofit.Builder()
    .baseUrl("https://www.rmv.de/hapi/")
    .client(OkHttpClient())    // Reuse the same underlying OkHttp (no extra config needed here)
    .addConverterFactory(json.asConverterFactory("application/json; charset=utf-8".toMediaType()))
    .build()

interface RmvApi {
    @GET("location.name")
    suspend fun searchStops(
        @Query("accessId") accessId: String,
        @Query("input") input: String,
        @Query("type") type: String = "S",
        @Query("maxNo") maxNo: Int = 3,
        @Query("format") format: String = "json",
        @Query("requestId") requestId: String? = null
    ): RmvLocationResponse

    @GET("departureBoard")
    suspend fun getDepartures(
        @Query("accessId") accessId: String,
        @Query("id") stopId: String,
        @Query("date") date: String,
        @Query("time") time: String,
        @Query("format") format: String = "json",
        @Query("requestId") requestId: String? = null
    ): RmvDepartureBoardResponse

    @GET("trip")
    suspend fun getTrip(
        @Query("accessId") accessId: String,
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
        @Query("accessId") accessId: String,
        @Query("id") ref: String,
        @Query("format") format: String = "json",
        @Query("requestId") requestId: String? = null
    ): kotlinx.serialization.json.JsonObject     // too polymorphic → parse manually

    @GET("location.nearbystops")
    suspend fun getNearbyStops(
        @Query("accessId") accessId: String,
        @Query("originCoordLat") lat: Double,     // decimal degrees (WGS84)
        @Query("originCoordLong") lon: Double,    // decimal degrees (WGS84)
        @Query("maxNo") maxNo: Int = 8,
        @Query("r") radiusMeters: Int = 500,      // meters
        @Query("type") type: String = "S",
        @Query("format") format: String = "json",
        @Query("requestId") requestId: String? = null
    ): kotlinx.serialization.json.JsonObject      // polymorphic → parse manually
}

val rmvApi: RmvApi = rmvRetrofit.create(RmvApi::class.java)
