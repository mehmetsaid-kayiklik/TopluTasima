package com.example.toplutasima.data

import android.content.SharedPreferences
import com.example.toplutasima.model.FavoriteStop
import com.example.toplutasima.model.UsageType
import com.example.toplutasima.network.firestore.FavoriteRemoteDataSource
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PrefsManagerFavoriteSyncTest {

    @Test
    fun `favorite sync failures are reported without uncaught exceptions`() {
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val uncaught = CopyOnWriteArrayList<Throwable>()
        val logged = CopyOnWriteArrayList<String>()
        val exceptionHandler = CoroutineExceptionHandler { _, throwable -> uncaught += throwable }
        val scope = CoroutineScope(SupervisorJob() + dispatcher + exceptionHandler)

        try {
            PrefsManager.init(inMemorySharedPreferences(), scope)
            PrefsManager.configureFavoriteSyncForTesting(
                dataSource = ThrowingFavoriteRemoteDataSource(),
                errorLogger = { message, _ -> logged += message }
            )

            val favorite = PrefsManager.addFavorite(
                stopId = "stop-1",
                stopName = "Central Station",
                label = "Home",
                usageType = UsageType.BOTH
            )
            assertTrue(PrefsManager.favorites.any { it.id == favorite.id })
            assertFailure(FavoriteSyncOperation.ADD, favorite.id)

            PrefsManager.clearFavoriteSyncFailure()
            PrefsManager.updateFavorite(favorite.id, label = "Updated Home")
            assertEquals("Updated Home", PrefsManager.favorites.single { it.id == favorite.id }.label)
            assertFailure(FavoriteSyncOperation.UPDATE, favorite.id)

            PrefsManager.clearFavoriteSyncFailure()
            PrefsManager.removeFavorite(favorite.id)
            assertTrue(PrefsManager.favorites.none { it.id == favorite.id })
            assertFailure(FavoriteSyncOperation.DELETE, favorite.id)

            assertTrue("No favorite failure should escape the launch body", uncaught.isEmpty())
            assertEquals(3, logged.size)
        } finally {
            PrefsManager.resetFavoriteSyncAfterTesting()
            scope.cancel()
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    private fun assertFailure(
        expectedOperation: FavoriteSyncOperation,
        expectedFavoriteId: String
    ) {
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (System.nanoTime() < deadlineNanos) {
            val failure = PrefsManager.favoriteSyncFailure.value
            if (failure?.operation == expectedOperation) {
                assertEquals(expectedFavoriteId, failure.favoriteId)
                assertTrue(failure.message.contains("simulated", ignoreCase = true))
                return
            }
            Thread.sleep(10)
        }
        fail("Expected $expectedOperation favorite sync failure")
    }

    private class ThrowingFavoriteRemoteDataSource : FavoriteRemoteDataSource {
        override suspend fun saveFavorite(fav: FavoriteStop): Nothing {
            throw IOException("Simulated favorite save failure")
        }

        override suspend fun deleteFavorite(favId: String): Nothing {
            throw IOException("Simulated favorite delete failure")
        }

        override suspend fun fetchAllFavorites(): List<FavoriteStop> = emptyList()
    }

    private fun inMemorySharedPreferences(): SharedPreferences {
        val values = ConcurrentHashMap<String, Any>()
        lateinit var editor: SharedPreferences.Editor

        editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { proxy, method, args ->
            val key = args?.getOrNull(0) as? String
            when (method.name) {
                "putString", "putStringSet", "putInt", "putLong", "putFloat", "putBoolean" -> {
                    if (key != null) {
                        values[key] = args.getOrNull(1) ?: return@newProxyInstance proxy
                    }
                    proxy
                }
                "remove" -> {
                    if (key != null) values.remove(key)
                    proxy
                }
                "clear" -> {
                    values.clear()
                    proxy
                }
                "commit" -> true
                "apply" -> null
                else -> proxy
            }
        } as SharedPreferences.Editor

        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            val key = args?.getOrNull(0) as? String
            val defaultValue = args?.getOrNull(1)
            when (method.name) {
                "getAll" -> values.toMap()
                "getString", "getStringSet", "getInt", "getLong", "getFloat", "getBoolean" ->
                    values[key] ?: defaultValue
                "contains" -> key != null && values.containsKey(key)
                "edit" -> editor
                "registerOnSharedPreferenceChangeListener",
                "unregisterOnSharedPreferenceChangeListener" -> null
                else -> null
            }
        } as SharedPreferences
    }
}
