package com.newsmead.fragments.account

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.newsmead.activities.MainActivity
import com.newsmead.R
import com.newsmead.data.FirebaseHelper
import com.newsmead.data.PreloadedData
import com.newsmead.logging.Analytics
import com.newsmead.logging.AppLog
import com.newsmead.research.ResearchSession

import com.newsmead.databinding.FragmentLogInBinding
import kotlinx.coroutines.launch

private const val TAG = "LogInFragment"

/**
 * A simple [Fragment] subclass.
 * Use the [LogInFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class LogInFragment : Fragment() {
    private lateinit var viewBinding: FragmentLogInBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        // Initialize viewBinding for LogInFragment
        this.viewBinding = FragmentLogInBinding.inflate(inflater, container, false)
        this.auth = FirebaseAuth.getInstance()
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        )

        // Explains why the user is back here, e.g. after being signed out at
        // launch over an unverified email. Shown once, not on every re-create.
        if (savedInstanceState == null) {
            arguments?.getString(ARG_NOTICE)?.let { notice ->
                Toast.makeText(requireActivity(), notice, Toast.LENGTH_LONG).show()
            }
        }

        // Listener for text fields to clear error
        this.viewBinding.etAccLogEmail.setOnFocusChangeListener { _, _ ->
            this.viewBinding.tilAccLogEmail.error = null
        }

        this.viewBinding.etAccLogPassword.setOnFocusChangeListener { _, _ ->
            this.viewBinding.tilAccLogPassword.error = null
        }


        // Buttons
        this.viewBinding.tvForgotPassword.setOnClickListener {
            val email = this.viewBinding.etAccLogEmail.text.toString().trim()
            ResetPasswordDialog(requireContext(), email).show()
        }
        this.viewBinding.btnAccCreate.setOnClickListener {
            val signUpFragment = SignUpFragment()
            val transaction = parentFragmentManager.beginTransaction()
            transaction.replace(R.id.flAccountContainer, signUpFragment)
            transaction.addToBackStack(null)
            transaction.commit()
        }
        this.viewBinding.btnAccLogIn.setOnClickListener {

            // Log In Validation. Trimmed because keyboard autofill routinely
            // appends a space, which Firebase then rejects as a bad email.
            val email = this.viewBinding.etAccLogEmail.text.toString().trim()
            val password = this.viewBinding.etAccLogPassword.text.toString()

            if (checkAccountErrors(email, password)) {
                return@setOnClickListener
            }

            // Log In User
            setFormEnabled(false)
            this.auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity()) { task ->
                    if (!task.isSuccessful) {
                        val code = firebaseErrorCode(task.exception)
                        AppLog.w(TAG, "signIn:failure code=$code", task.exception)
                        Analytics.login(success = false, errorCode = code)
                        ResearchSession.authEvent("log_in", success = false, errorCode = code)
                        setFormEnabled(true)
                        Toast.makeText(
                            requireActivity(),
                            signInErrorMessage(requireActivity(), task.exception),
                            Toast.LENGTH_LONG
                        ).show()
                        return@addOnCompleteListener
                    }

                    val user = this.auth.currentUser
                    if (user != null && !user.isEmailVerified) {
                        Analytics.verificationBlocked()
                        ResearchSession.authEvent("verification_blocked", success = false)
                        sendVerificationThenSignOut()
                    } else {
                        Analytics.login(success = true)
                        ResearchSession.authEvent("log_in", success = true)
                        AppLog.setAuthState("verified")
                        // Sign in success, update UI with the signed-in user's information
                        successfulLogIn()
                    }
                }

        }

        // Inflate the layout for this fragment
        return this.viewBinding.root
    }

    /**
     * Issues a fresh verification link and only then drops the session.
     * Signing out first could invalidate the token the send needs, so the
     * email the user was told to expect sometimes never left.
     */
    private fun sendVerificationThenSignOut() {
        val user = this.auth.currentUser ?: return
        user.sendEmailVerification().addOnCompleteListener { emailTask ->
            if (!emailTask.isSuccessful) {
                AppLog.w(TAG, "sendEmailVerification:failure", emailTask.exception)
            }
            this.auth.signOut()

            val host = activity ?: return@addOnCompleteListener
            if (!isAdded) return@addOnCompleteListener
            setFormEnabled(true)
            Toast.makeText(
                host,
                getString(R.string.verify_email_before_login),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Goes to MainActivity while clearing all other activities, or into
     * onboarding first if this account never picked its categories.
     */
    private fun successfulLogIn() {
        lifecycleScope.launch {
            val host = activity ?: return@launch

            // Warming the saved-lists cache is a convenience, not a login
            // requirement. This used to throw straight out of the coroutine
            // and crash the app whenever Firestore was unreachable.
            try {
                PreloadedData.updateSavedData(FirebaseHelper.getListsAndArticles(host))
            } catch (exception: Exception) {
                AppLog.w(TAG, "preload:failure", exception)
            }

            val onboarded = try {
                FirebaseHelper.hasCompletedOnboarding()
            } catch (exception: Exception) {
                // Don't make the user redo onboarding just because a read failed.
                AppLog.w(TAG, "onboardingCheck:failure", exception)
                true
            }

            if (!isAdded || host.isFinishing) return@launch

            if (!onboarded) {
                setFormEnabled(true)
                host.supportFragmentManager.beginTransaction()
                    .replace(R.id.flAccountContainer, OnboardingFragment())
                    .commit()
                return@launch
            }

            val intent = Intent(host, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            host.startActivity(intent)
            host.finish()
        }
    }

    /**
     * Guards against a second sign-in being fired by a double tap while the
     * first is still in flight.
     */
    private fun setFormEnabled(enabled: Boolean) {
        if (!isAdded) return
        this.viewBinding.btnAccLogIn.isEnabled = enabled
        this.viewBinding.btnAccCreate.isEnabled = enabled
    }

    /**
     * Provides all error checking for email, password, and confirm password
     * @param email The email entered by the user
     * @param password The password entered by the user
     */
    private fun checkAccountErrors(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            this.viewBinding.tilAccLogEmail.error = "Email is required"
            return true
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            this.viewBinding.tilAccLogEmail.error = "Please provide valid email"
            return true
        }

        if (password.isEmpty()) {
            this.viewBinding.tilAccLogPassword.error = "Password is required"
            return true
        } else if (password.length < 6) {
            this.viewBinding.tilAccLogPassword.error = "Min password length should be 6 characters"
            return true
        }

        return false
    }

    companion object {
        private const val ARG_NOTICE = "notice"

        /**
         * @param notice One-off message explaining how the user got here,
         *               or null to open the screen with no explanation.
         */
        fun newInstance(notice: String?): LogInFragment = LogInFragment().apply {
            if (notice != null) arguments = bundleOf(ARG_NOTICE to notice)
        }
    }
}
