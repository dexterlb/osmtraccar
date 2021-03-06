package org.qtrp.osmtraccar

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.qtrp.osmtraccar.databinding.DialogLoginBinding
import okhttp3.HttpUrl.Companion.toHttpUrl


class LoginDialogFragment(onLogin: (TraccarConnData) -> Unit, oldData: TraccarConnData?): DialogFragment() {
    val onLogin = onLogin
    val oldData = oldData

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            // Get the layout inflater
            val inflater = requireActivity().layoutInflater;
            val binding = DialogLoginBinding.inflate(inflater)

            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            builder.setView(binding.root)
                // Add action buttons
                .setPositiveButton("login",
                    DialogInterface.OnClickListener { dialog, _ ->
                        val url = try {
                            binding.url.text.toString().toHttpUrl()
                        } catch (e: Exception) {
                            return@OnClickListener
                        }
                        val data = TraccarConnData(
                            url = url,
                            email = binding.email.text.toString(),
                            pass = binding.password.text.toString(),
                        )

                        onLogin(data)
                        dialog.dismiss()
                    })
                .setNegativeButton("cancel",
                    DialogInterface.OnClickListener { dialog, _ ->
                        dialog.cancel()
                    })
            val result = builder.create()

            if (oldData != null) {
                binding.email.setText(oldData.email)
                binding.password.setText(oldData.pass)
                binding.url.setText(oldData.url.toString())
            }

            result
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}