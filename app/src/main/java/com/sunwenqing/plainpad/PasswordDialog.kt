package com.sunwenqing.plainpad

import android.app.Activity
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sunwenqing.plainpad.databinding.DialogPasswordBinding

/** 加密/解密用的密钥输入框；requireConfirm 时显示第二遍确认。 */
object PasswordDialog {

    fun show(
        activity: Activity,
        title: CharSequence,
        subtitle: CharSequence,
        requireConfirm: Boolean,
        onSubmit: (CharArray) -> Unit
    ) {
        val b = DialogPasswordBinding.inflate(activity.layoutInflater)
        b.tvPasswordTitle.text = title
        b.tvPasswordSubtitle.text = subtitle
        b.etPasswordConfirm.visibility = if (requireConfirm) android.view.View.VISIBLE
        else android.view.View.GONE

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(b.root)
            .create()

        b.btnPasswordCancel.setOnClickListener { dialog.dismiss() }
        b.btnPasswordConfirm.setOnClickListener {
            val password = b.etPassword.text?.toString().orEmpty()
            val confirm = b.etPasswordConfirm.text?.toString().orEmpty()
            when {
                password.isEmpty() -> showError(activity, b, R.string.crypto_password_empty)
                requireConfirm && password != confirm ->
                    showError(activity, b, R.string.crypto_password_mismatch)
                else -> {
                    dialog.dismiss()
                    onSubmit(password.toCharArray())
                }
            }
        }

        dialog.setOnShowListener {
            b.etPassword.requestFocus()
        }
        dialog.show()
    }

    private fun showError(activity: Activity, b: DialogPasswordBinding, res: Int) {
        b.tvPasswordError.setText(res)
        b.tvPasswordError.visibility = android.view.View.VISIBLE
        Toast.makeText(activity, res, Toast.LENGTH_SHORT).show()
    }
}
