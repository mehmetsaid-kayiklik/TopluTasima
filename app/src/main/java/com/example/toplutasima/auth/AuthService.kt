package com.example.toplutasima.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

object AuthService {

    private val auth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val uid: String
        get() = auth.currentUser?.uid
            ?: throw IllegalStateException("Kullanıcı giriş yapmamış")

    val isSignedIn: Boolean
        get() = auth.currentUser != null

    suspend fun signInWithGoogle(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        return result.user ?: throw Exception("Google girişi başarısız")
    }

    fun signOut() {
        auth.signOut()
    }
}
