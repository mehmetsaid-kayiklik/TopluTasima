package com.example.toplutasima.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.toplutasima.MainActivity
import com.example.toplutasima.R
import com.example.toplutasima.ui.screens.login.LoginScreen
import com.example.toplutasima.ui.theme.TopluTasimaTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : ComponentActivity() {

    companion object {
        private const val TAG = "LoginActivity"
    }

    private var onGoogleResult: ((Boolean) -> Unit)? = null

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                reportGoogleFailure(
                    message = "Google ID token alinamadi. Firebase OAuth ayarlarini kontrol et."
                )
                return@registerForActivityResult
            }
            lifecycleScope.launch(Dispatchers.Main) {
                try {
                    AuthService.signInWithGoogle(idToken)
                    onGoogleResult?.invoke(true)
                } catch (e: Exception) {
                    reportGoogleFailure(
                        message = "Firebase Google girisi basarisiz. Firebase Auth ayarlarini kontrol et.",
                        throwable = e
                    )
                }
            }
        } catch (e: ApiException) {
            val status = GoogleSignInStatusCodes.getStatusCodeString(e.statusCode)
            reportGoogleFailure(
                message = "Google girisi basarisiz: $status (${e.statusCode})",
                throwable = e
            )
        } catch (e: Exception) {
            reportGoogleFailure(
                message = "Google girisi basarisiz.",
                throwable = e
            )
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

    private fun reportGoogleFailure(message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.e(TAG, message)
        } else {
            Log.e(TAG, message, throwable)
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        onGoogleResult?.invoke(false)
    }
}
