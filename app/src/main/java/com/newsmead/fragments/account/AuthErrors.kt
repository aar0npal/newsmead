package com.newsmead.fragments.account

import android.content.Context
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.newsmead.R

/**
 * Turns a FirebaseAuth failure into something the user can act on.
 *
 * Both screens used to collapse every failure into a single message, so a
 * dropped connection read as "wrong password" and an already-registered email
 * read as "please try again" — the two most common real failures were the two
 * the user was told nothing about.
 */

/**
 * Best available machine-readable code for a FirebaseAuth failure.
 *
 * Typed exceptions carry `errorCode` directly, but the reCAPTCHA-wrapped
 * sign-in path does not throw one: a wrong password arrives as a plain
 * `FirebaseException` whose message is
 * `"An internal error has occurred. [ INVALID_LOGIN_CREDENTIALS ]"`.
 * Verified on-device — without the bracket fallback the single most common
 * failure in the app reports as "FirebaseException" and shows the generic
 * message, which is the exact problem this file exists to fix.
 */
internal fun firebaseErrorCode(exception: Exception?): String? {
    if (exception == null) return null
    (exception as? FirebaseAuthException)?.errorCode?.let { return it }
    BRACKETED_CODE.find(exception.message.orEmpty())?.groupValues?.get(1)?.let { return it }
    return exception.javaClass.simpleName
}

internal fun signInErrorMessage(context: Context, exception: Exception?): String {
    when (exception) {
        is FirebaseNetworkException -> return context.getString(R.string.auth_error_network)
        is FirebaseAuthInvalidUserException ->
            return if (exception.errorCode == CODE_USER_DISABLED) {
                context.getString(R.string.auth_error_disabled)
            } else {
                context.getString(R.string.auth_error_no_account)
            }
        // Projects with email-enumeration protection report an unknown email
        // this way too, so this stays deliberately vague about which half was
        // wrong.
        is FirebaseAuthInvalidCredentialsException ->
            return context.getString(R.string.auth_error_bad_credentials)
    }

    return when (firebaseErrorCode(exception)) {
        CODE_INVALID_LOGIN_CREDENTIALS, CODE_INVALID_PASSWORD, CODE_WRONG_PASSWORD ->
            context.getString(R.string.auth_error_bad_credentials)
        CODE_EMAIL_NOT_FOUND, CODE_USER_NOT_FOUND ->
            context.getString(R.string.auth_error_no_account)
        CODE_USER_DISABLED -> context.getString(R.string.auth_error_disabled)
        CODE_TOO_MANY_ATTEMPTS -> context.getString(R.string.auth_error_too_many_attempts)
        CODE_NETWORK_FAILED -> context.getString(R.string.auth_error_network)
        else -> context.getString(R.string.auth_error_sign_in_generic)
    }
}

internal fun signUpErrorMessage(context: Context, exception: Exception?): String {
    when (exception) {
        is FirebaseNetworkException -> return context.getString(R.string.auth_error_network)
        is FirebaseAuthUserCollisionException ->
            return context.getString(R.string.auth_error_email_taken)
        // Must be checked before FirebaseAuthInvalidCredentialsException,
        // which it extends.
        is FirebaseAuthWeakPasswordException ->
            return exception.reason ?: context.getString(R.string.auth_error_weak_password)
        is FirebaseAuthInvalidCredentialsException ->
            return context.getString(R.string.auth_error_invalid_email)
    }

    return when (firebaseErrorCode(exception)) {
        CODE_EMAIL_EXISTS -> context.getString(R.string.auth_error_email_taken)
        CODE_WEAK_PASSWORD -> context.getString(R.string.auth_error_weak_password)
        CODE_INVALID_EMAIL -> context.getString(R.string.auth_error_invalid_email)
        CODE_TOO_MANY_ATTEMPTS -> context.getString(R.string.auth_error_too_many_attempts)
        CODE_NETWORK_FAILED -> context.getString(R.string.auth_error_network)
        else -> context.getString(R.string.auth_error_sign_up_generic)
    }
}

private val BRACKETED_CODE = Regex("""\[\s*([A-Z_]+)\s*]""")

private const val CODE_INVALID_LOGIN_CREDENTIALS = "INVALID_LOGIN_CREDENTIALS"
private const val CODE_INVALID_PASSWORD = "INVALID_PASSWORD"
private const val CODE_WRONG_PASSWORD = "ERROR_WRONG_PASSWORD"
private const val CODE_EMAIL_NOT_FOUND = "EMAIL_NOT_FOUND"
private const val CODE_USER_NOT_FOUND = "ERROR_USER_NOT_FOUND"
private const val CODE_USER_DISABLED = "ERROR_USER_DISABLED"
private const val CODE_EMAIL_EXISTS = "EMAIL_EXISTS"
private const val CODE_WEAK_PASSWORD = "WEAK_PASSWORD"
private const val CODE_INVALID_EMAIL = "INVALID_EMAIL"
private const val CODE_TOO_MANY_ATTEMPTS = "TOO_MANY_ATTEMPTS_TRY_LATER"
private const val CODE_NETWORK_FAILED = "NETWORK_REQUEST_FAILED"
