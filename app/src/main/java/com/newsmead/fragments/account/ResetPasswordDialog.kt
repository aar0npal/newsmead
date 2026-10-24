package com.newsmead.fragments.account

import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth
import com.newsmead.R
import com.newsmead.databinding.DialogResetPasswordBinding

/**
 * Prompts for an email address and sends a Firebase Auth password-reset link
 * to it. Always shows the same confirmation regardless of whether the email
 * matches an account, so the dialog can't be used to probe for registered
 * emails.
 */
class ResetPasswordDialog(
    context: Context,
    private val prefillEmail: String = "",
) {
    private val auth = FirebaseAuth.getInstance()
    private val builder = AlertDialog.Builder(context)
    private val binding = DialogResetPasswordBinding.inflate(LayoutInflater.from(context))

    init {
        binding.etResetPasswordEmail.setText(prefillEmail)

        builder.setView(binding.root)
            .setTitle(R.string.reset_password_title)
            .setPositiveButton(R.string.reset_password_send) { dialog, _ ->
                val email = binding.etResetPasswordEmail.text.toString().trim()
                if (android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    auth.sendPasswordResetEmail(email)
                    Toast.makeText(context, R.string.reset_password_sent, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, R.string.reset_password_invalid_email, Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.reset_password_cancel) { dialog, _ ->
                dialog.dismiss()
            }
    }

    fun show() {
        builder.create().show()
    }
}
