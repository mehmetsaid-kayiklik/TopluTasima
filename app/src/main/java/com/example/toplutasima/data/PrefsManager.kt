package com.example.toplutasima.data

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.toplutasima.model.FavoriteStop
import com.example.toplutasima.model.ThemeMode
import com.example.toplutasima.model.UsageType
import com.example.toplutasima.network.FirestoreService
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * İniş hatırlatması tetikleyici türü.
 * LOCATION: Konuma dayalı (Proximity GPS)
 * TIME    : Saate dayalı (AlarmManager)
 * NONE    : Hatırlatma kapalı
 */
enum class TransitReminderType { LOCATION, TIME, NONE }

/**
 * Centralized SharedPreferences manager for favorites, theme, etc.
 * Follows the same singleton-with-observable-state pattern as LocaleManager.
 */
object PrefsManager {

    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ── Observable state ─────────────────────────────────────────────────────

    var favorites by mutableStateOf<List<FavoriteStop>>(emptyList())
        private set

    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    /** Kişisel biniş waypoint aralığı (saniye). 10 / 20 / 30 / 60 */
    var waypointIntervalSeconds by mutableStateOf(30)
        private set

    /** Toplu taşıma bildirimleri açık/kapalı */
    var transitNotificationsEnabled by mutableStateOf(true)
        private set

    /** Hatırlatma offset'i (dakika). 0 = tam iniş saatinde, negatif = önce, pozitif = sonra */
    var reminderOffsetMinutes by mutableStateOf(0)
        private set

    /** Material You dinamik tema desteği */
    var useMaterialYou by mutableStateOf(true)
        private set

    /**
     * İniş hatırlatma türü: LOCATION (GPS), TIME (alarm), NONE (kapalı).
     * Varsayılan TIME — mevcut davranışı korur.
     */
    var transitReminderType by mutableStateOf(TransitReminderType.TIME)
        private set

    // ── Init ─────────────────────────────────────────────────────────────────

    fun init(sharedPrefs: SharedPreferences) {
        prefs = sharedPrefs
        favorites = loadFavorites()
        themeMode = loadThemeMode()
        waypointIntervalSeconds = prefs.getInt("waypoint_interval_sec", 30)
        transitNotificationsEnabled = prefs.getBoolean("transit_notif_enabled", true)
        reminderOffsetMinutes = prefs.getInt("reminder_offset_min", 0)
        useMaterialYou = prefs.getBoolean("use_material_you", true)
        transitReminderType = loadTransitReminderType()
    }

    // ── Theme ────────────────────────────────────────────────────────────────

    fun changeThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun setMaterialYou(use: Boolean) {
        useMaterialYou = use
        prefs.edit().putBoolean("use_material_you", use).apply()
    }

    // ── Waypoint Aralığı ─────────────────────────────────────────────────────

    fun setWaypointInterval(seconds: Int) {
        waypointIntervalSeconds = seconds
        prefs.edit().putInt("waypoint_interval_sec", seconds).apply()
    }

    // ── Toplu Taşıma Bildirim Ayarları ───────────────────────────────────────

    fun changeTransitNotifications(enabled: Boolean) {
        transitNotificationsEnabled = enabled
        prefs.edit().putBoolean("transit_notif_enabled", enabled).apply()
    }

    fun changeReminderOffset(minutes: Int) {
        reminderOffsetMinutes = minutes
        prefs.edit().putInt("reminder_offset_min", minutes).apply()
    }

    fun changeTransitReminderType(type: TransitReminderType) {
        transitReminderType = type
        prefs.edit().putString("transit_reminder_type", type.name).apply()
    }

    private fun loadTransitReminderType(): TransitReminderType {
        val saved = prefs.getString("transit_reminder_type", TransitReminderType.TIME.name)
            ?: TransitReminderType.TIME.name
        return try { TransitReminderType.valueOf(saved) } catch (_: Exception) { TransitReminderType.TIME }
    }

    private fun loadThemeMode(): ThemeMode {
        val saved = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
        return try { ThemeMode.valueOf(saved) } catch (_: Exception) { ThemeMode.SYSTEM }
    }

    // ── Favorites CRUD ───────────────────────────────────────────────────────

    @OptIn(DelicateCoroutinesApi::class)
    fun addFavorite(stopId: String, stopName: String, label: String, usageType: UsageType): FavoriteStop {
        val fav = FavoriteStop(
            id = UUID.randomUUID().toString(),
            stopId = stopId,
            stopName = stopName,
            label = label,
            usageType = usageType
        )
        favorites = favorites + fav
        saveFavorites()
        // Fire-and-forget Firebase backup
        GlobalScope.launch(Dispatchers.IO) { FirestoreService.saveFavorite(fav) }
        return fav
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun removeFavorite(id: String) {
        favorites = favorites.filter { it.id != id }
        saveFavorites()
        GlobalScope.launch(Dispatchers.IO) { FirestoreService.deleteFavorite(id) }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun updateFavorite(id: String, label: String? = null, usageType: UsageType? = null) {
        favorites = favorites.map { fav ->
            if (fav.id == id) {
                fav.copy(
                    label = label ?: fav.label,
                    usageType = usageType ?: fav.usageType
                )
            } else fav
        }
        saveFavorites()
        // Sync updated favorite to Firebase
        val updated = favorites.find { it.id == id }
        if (updated != null) {
            GlobalScope.launch(Dispatchers.IO) { FirestoreService.saveFavorite(updated) }
        }
    }

    fun boardingFavorites(): List<FavoriteStop> =
        favorites.filter { it.usageType == UsageType.BOARDING || it.usageType == UsageType.BOTH }

    fun alightingFavorites(): List<FavoriteStop> =
        favorites.filter { it.usageType == UsageType.ALIGHTING || it.usageType == UsageType.BOTH }

    // ── Restore from Firebase (merge) ────────────────────────────────────────
    // Firebase'den tüm favorileri çeker, lokaldeki mevcut favorilerle birleştirir.
    // Aynı ID'ye sahip olanlar: Firebase versiyonu locale'i override eder.
    // Lokal-only favoriler korunur.
    suspend fun restoreFromFirebase(): Int {
        val remoteFavs = FirestoreService.fetchAllFavorites()
        if (remoteFavs.isEmpty()) return 0

        val localById = favorites.associateBy { it.id }.toMutableMap()
        var newCount = 0
        for (remote in remoteFavs) {
            if (!localById.containsKey(remote.id)) newCount++
            localById[remote.id] = remote
        }
        favorites = localById.values.toList()
        saveFavorites()
        return newCount
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private fun loadFavorites(): List<FavoriteStop> {
        val raw = prefs.getString("favorite_stops", null) ?: return emptyList()
        return try { json.decodeFromString<List<FavoriteStop>>(raw) } catch (_: Exception) { emptyList() }
    }

    private fun saveFavorites() {
        prefs.edit().putString("favorite_stops", json.encodeToString(favorites)).apply()
    }
}
