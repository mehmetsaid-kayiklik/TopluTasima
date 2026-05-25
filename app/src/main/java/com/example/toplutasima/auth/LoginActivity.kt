package com.example.toplutasima.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.example.toplutasima.MainActivity
import com.example.toplutasima.R
import com.example.toplutasima.ui.screens.login.LoginScreen
import com.example.toplutasima.ui.theme.TopluTasimaTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : ComponentActivity() {

    private var onGoogleResult: ((Boolean) -> Unit)? = null

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(Exception::class.java)
            val idToken = account.idToken ?: run {
                onGoogleResult?.invoke(false)
                return@registerForActivityResult
            }
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    AuthService.signInWithGoogle(idToken)
                    onGoogleResult?.invoke(true)
                } catch (e: Exception) {
                    onGoogleResult?.invoke(false)
                }
            }
        } catch (e: Exception) {
            onGoogleResult?.invoke(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (AuthService.isSignedIn) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContent {
            TopluTasimaTheme {
                LoginScreen(
                    onGoogleSignIn = { launchGoogleSignIn() }
                )
            }
        }
    }

    private fun launchGoogleSignIn() {
        onGoogleResult = { success ->
            if (success) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(this, gso)
        googleSignInLauncher.launch(client.signInIntent)
    }
}
