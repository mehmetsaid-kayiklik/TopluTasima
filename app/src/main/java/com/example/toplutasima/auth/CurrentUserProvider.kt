package com.example.toplutasima.auth

import com.google.firebase.auth.FirebaseAuth

object CurrentUserProvider {
    fun currentUserIdOrNull(): String? =
        FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotBlank() }

    fun requireUserId(): String =
        currentUserIdOrNull() ?: error("Kullanıcı oturumu yok")
}
