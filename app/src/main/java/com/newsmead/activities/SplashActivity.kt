package com.newsmead.activities

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.newsmead.data.StudyConfig
import com.newsmead.R
import com.newsmead.data.PreloadedData
import com.newsmead.databinding.ActivitySplashBinding

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var auth : FirebaseAuth
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installSplashScreen()

        // Initialize viewBinding for SplashActivity
        val viewBinding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        auth = Firebase.auth
        if (StudyConfig.BYPASS_LOGIN) {
            navigateToMainActivity()
            return
        }
        val user = auth.currentUser
        if (user == null) {
            // Never signed in on this device: go straight to sign-up, as before.
            goToAccount(signUp = true, notice = null)
            return
        }

        // isEmailVerified is read off the locally cached token, so a user who
        // clicked the link in a browser still reads as unverified here and was
        // signed out forever. Refresh the record before judging them.
        user.reload().addOnCompleteListener { task ->
            val refreshed = auth.currentUser
            val verified = when {
                refreshed == null -> false
                task.isSuccessful -> refreshed.isEmailVerified
                // Offline: trust the cached flag rather than locking out a user
                // who verified long ago and simply has no signal right now.
                task.exception is FirebaseNetworkException -> refreshed.isEmailVerified
                else -> false
            }

            if (verified) {
                navigateToMainActivity()
                return@addOnCompleteListener
            }

            if (refreshed != null) auth.signOut()

            // NewsMeadApplication preloads saved lists for whoever was signed
            // in when the process started. Drop it, or the next account opens
            // onto the previous one's articles.
            PreloadedData.clearData()

            goToAccount(
                signUp = false,
                notice = getString(R.string.verify_email_before_login)
            )
        }
    }

    /**
     * @param signUp true when there is no account yet, so the account screen
     *               should open on sign-up rather than log-in.
     * @param notice Explanation for why the user landed here, or null.
     */
    private fun goToAccount(signUp: Boolean, notice: String?) {
        handler.postDelayed({
            val intent = Intent(this, AccountActivity::class.java)
            if (signUp) intent.putExtra("initial_fragment", "sign_up")
            if (notice != null) intent.putExtra("notice", notice)
            startActivity(intent)
            finish()
        }, SPLASH_DELAY_MS)
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        // The old bare Handler().postDelayed() went on to fire into a destroyed
        // activity if the user left during the splash.
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val SPLASH_DELAY_MS = 2000L
    }
}
