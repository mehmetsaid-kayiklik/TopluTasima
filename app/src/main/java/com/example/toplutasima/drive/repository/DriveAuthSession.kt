package com.example.toplutasima.drive.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Emits only the authenticated UID needed to switch UID-scoped Room queries safely. */
object DriveAuthSession {
    fun authenticatedUidChanges(
        auth: FirebaseAuth = FirebaseAuth.getInstance()
    ): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid?.takeIf(String::isNotBlank))
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()
}
