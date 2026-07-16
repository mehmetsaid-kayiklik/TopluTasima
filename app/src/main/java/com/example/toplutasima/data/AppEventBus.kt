package com.example.toplutasima.data

import android.util.Log
import com.example.toplutasima.model.JourneyMatchCandidate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-içi event bus — deprecated LocalBroadcastManager yerine
 * coroutine-native SharedFlow kullanır.
 *
 * Yalnızca canlı process için çalışır. ViewModel sonradan başlarsa geçmiş
 * event'leri alamaz; bu yüzden ViewModel açılışta ilgili verileri
 * ayrıca DB/prefs'ten tazelemeli.
 */
object AppEventBus {

    private const val TAG = "AppEventBus"

    sealed class Event {
        /**
         * TransitActionWorker Firebase'e başarıyla yazdıktan sonra emit eder.
         * RmvLogViewModel bu event'i dinleyerek UI'ı senkronize eder.
         *
         * @param tripId     Güncellenen segmentin Firestore doc ID'si
         * @param isBoarding true → Bindim, false → İndim
         * @param timestamp  Kullanıcının bastığı andaki "HH:mm" değeri
         */
        data class TripSynced(
            val tripId: String,
            val isBoarding: Boolean,
            val timestamp: String
        ) : Event()

        data class JourneyMatchCompleted(
            val candidates: List<JourneyMatchCandidate>,
            val message: String
        ) : Event()
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    /** Worker veya Receiver'dan çağrılır; coroutine suspend gerektirmez. */
    fun emit(event: Event) {
        val eventType = event.javaClass.simpleName
        val hadActiveSubscriber = _events.subscriptionCount.value > 0
        val emitted = _events.tryEmit(event)
        if (!emitted) {
            Log.w(TAG, "Dropped event type=$eventType because the SharedFlow buffer is full")
        } else if (!hadActiveSubscriber && _events.subscriptionCount.value == 0) {
            Log.w(TAG, "Dropped event type=$eventType because there is no active subscriber")
        }
    }
}
