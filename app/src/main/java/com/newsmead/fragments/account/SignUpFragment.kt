package com.newsmead.fragments.account

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.firebase.auth.FirebaseAuth
import com.newsmead.R
import com.newsmead.data.FirebaseHelper
import com.newsmead.logging.Analytics
import com.newsmead.logging.AppLog
import com.newsmead.research.ResearchSession

import com.newsmead.databinding.FragmentSignUpBinding

private const val TAG = "SignUpFragment"

class SignUpFragment: Fragment() {
    private lateinit var viewBinding: FragmentSignUpBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        this.viewBinding = FragmentSignUpBinding.inflate(inflater, container, false)
        this.auth = FirebaseAuth.getInstance()
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        )

        // Textfields to erase error messages
        this.viewBinding.etAccEmail.setOnFocusChangeListener { _, _ ->
            this.viewBinding.etAccEmail.error = null
        }

        this.viewBinding.etAccPassword.setOnFocusChangeListener { _, _ ->
            this.viewBinding.tilAccPassword.error = null
        }

        this.viewBinding.etAccConfirmPassword.setOnFocusChangeListener { _, _ ->
            this.viewBinding.tilAccConfirmPassword.error = null
        }

        // Buttons
        this.viewBinding.btnAccLog.setOnClickListener {
            // Goes to Log In Fragment
            goToLogIn(notice = null)
        }

        this.viewBinding.btnAccStart.setOnClickListener {
            // Temporarily disable button
            this.viewBinding.btnAccStart.isEnabled = false

            val name = this.viewBinding.etAccName.text.toString().trim()
            // Trimmed for the same reason as the log-in screen: autofill adds
            // a trailing space and Firebase rejects the address.
            val email = this.viewBinding.etAccEmail.text.toString().trim()
            val password = this.viewBinding.etAccPassword.text.toString()
            val confirmPassword = this.viewBinding.etAccConfirmPassword.text.toString()

            if (checkAccountErrors(email, password, confirmPassword)) {
                // Re-enable button
                this.viewBinding.btnAccStart.isEnabled = true
                return@setOnClickListener
            }

            // Create user with email and password
            this.auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity()) { task ->
                    if (!task.isSuccessful) {
                        // If sign up fails, say which failure it was. "Email
                        // already registered" in particular used to surface as
                        // a generic retry message.
                        val code = firebaseErrorCode(task.exception)
                        Analytics.signUp(success = false, errorCode = code)
                        ResearchSession.authEvent("sign_up", success = false, errorCode = code)
                        AppLog.w(TAG, "createUserWithEmail:failure code=$code", task.exception)
                        Toast.makeText(
                            requireActivity(),
                            signUpErrorMessage(requireActivity(), task.exception),
                            Toast.LENGTH_LONG
                        ).show()

                        // Re-enable button
                        this.viewBinding.btnAccStart.isEnabled = true
                        return@addOnCompleteListener
                    }

                    // Write the profile while the new account still holds its
                    // session, and only drop the session once that write has
                    // landed: Firestore rules key on request.auth, so a write
                    // still queued at signOut() would be rejected when it
                    // finally flushed, leaving an account with no lists.
                    Analytics.signUp(success = true)
                    ResearchSession.authEvent("sign_up", success = true)
                    AppLog.setAuthState("unverified")

                    FirebaseHelper.addUserToFireStore(requireActivity(), email, name) { written ->
                        if (!written) {
                            AppLog.w(TAG, "addUserToFireStore:failure")
                        }
                        sendVerificationThenFinish()
                    }
                }
        }

        return this.viewBinding.root
    }

    /**
     * Sends the verification link, then hands the user back to log in.
     *
     * A brand-new account is not verified yet, and both SplashActivity and
     * LogInFragment refuse unverified accounts. Letting this one session
     * through to MainActivity anyway meant a user got in exactly once and was
     * then locked out at the next launch with no explanation. Categories are
     * now collected on the first verified login instead.
     */
    private fun sendVerificationThenFinish() {
        val user = this.auth.currentUser
        if (user == null) {
            finishSignUp()
            return
        }
        user.sendEmailVerification().addOnCompleteListener { emailTask ->
            if (!emailTask.isSuccessful) {
                // The account itself exists — don't strand the user over this.
                AppLog.w(TAG, "sendEmailVerification:failure", emailTask.exception)
            }
            finishSignUp()
        }
    }

    private fun finishSignUp() {
        this.auth.signOut()
        if (!isAdded) return
        goToLogIn(notice = getString(R.string.verify_email_sent))
    }

    private fun goToLogIn(notice: String?) {
        val host = activity ?: return
        // Clear the Log In -> Create an Account entry so Back doesn't land
        // back on a sign-up form for an account that now exists.
        host.supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        host.supportFragmentManager.beginTransaction()
            .replace(R.id.flAccountContainer, LogInFragment.newInstance(notice))
            .commit()
    }

    /**
     * Provides all error checking for email, password, and confirm password
     * @param email The email entered by the user
     * @param password The password entered by the user
     * @param confirmPassword The confirm password entered by the user
     */
    private fun checkAccountErrors(email: String, password: String, confirmPassword: String): Boolean {
        if (email.isEmpty()) {
            this.viewBinding.etAccEmail.error = "Email is required"
            return true
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            this.viewBinding.etAccEmail.error = "Please provide valid email"
            return true
        }

        if (password.isEmpty()) {
            this.viewBinding.tilAccPassword.error = "Password is required"
            return true
        } else if (password.length < 6) {
            this.viewBinding.tilAccPassword.error = "Min password length should be 6 characters"
            return true
        }

        if (confirmPassword.isEmpty()) {
            // MUI Error
            this.viewBinding.tilAccConfirmPassword.error = "Confirm Password is required"
            return true
        } else if (password != confirmPassword) {
            this.viewBinding.tilAccConfirmPassword.error = "Password and Confirm Password does not match"
            return true
        }

        return false
    }
}
