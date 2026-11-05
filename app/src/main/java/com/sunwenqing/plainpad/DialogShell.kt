package com.sunwenqing.plainpad

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sunwenqing.plainpad.databinding.DialogShellBinding

/**
 * 长弹窗的统一外壳：固定头部、可滚动内容、固定底部动作条。
 *
 * 解决的问题：内容一长，底部按钮会被顶到屏幕外，要么看不见、要么得先滚到底。
 * 这里把弹窗高度限制在屏幕 78% 以内，超出部分由内容区自己滚动，按钮永远固定可见。
 *
 * 用法：
 * ```
 * val shell = DialogShell.build(activity, R.string.stats_title)
 * shell.shellContent.addView(contentView)
 * shell.shellFooter.addView(copyButton)
 * val dialog = DialogShell.show(activity, shell)
 * ```
 */
object DialogShell {

    private const val MAX_HEIGHT_RATIO = 0.78f
    private const val WIDTH_RATIO = 0.92f

    fun build(activity: Activity, @StringRes titleRes: Int): DialogShellBinding =
        build(activity, activity.getString(titleRes))

    fun build(activity: Activity, title: CharSequence): DialogShellBinding {
        val b = DialogShellBinding.inflate(activity.layoutInflater)
        b.tvShellTitle.text = title
        return b
    }

    fun show(
        activity: Activity,
        shell: DialogShellBinding,
        widthRatio: Float = WIDTH_RATIO
    ): androidx.appcompat.app.AlertDialog {
        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(shell.root)
            .create()

        shell.btnShellClose.setOnClickListener { dialog.dismiss() }

        dialog.show()

        val metrics = activity.resources.displayMetrics
        dialog.window?.setLayout(
            (metrics.widthPixels * widthRatio).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // 内容超出上限时才把滚动区定高，否则保持 wrap_content，短内容不会留白
        shell.root.post {
            val density = metrics.density
            val maxContentHeight = (metrics.heightPixels * MAX_HEIGHT_RATIO).toInt() -
                    shell.shellHeader.height - shell.shellFooter.height - (24 * density).toInt()

            val contentHeight = shell.shellContent.height
            if (maxContentHeight > 0 && contentHeight > maxContentHeight) {
                val lp = shell.shellScroll.layoutParams as ViewGroup.MarginLayoutParams
                lp.height = maxContentHeight
                shell.shellScroll.layoutParams = lp
            }
        }

        return dialog
    }

    /** 往底部动作条里加一个文字按钮 */
    fun addFooterButton(
        activity: Activity,
        shell: DialogShellBinding,
        @StringRes textRes: Int,
        onClick: () -> Unit
    ) {
        val btn = android.view.LayoutInflater.from(activity)
            .inflate(R.layout.dialog_shell_button, shell.shellFooter, false)
                as com.google.android.material.button.MaterialButton
        btn.text = activity.getString(textRes)
        btn.setOnClickListener { onClick() }
        shell.shellFooter.addView(btn)
    }

    /** 往内容区加一个视图 */
    fun addContent(shell: DialogShellBinding, view: View) {
        shell.shellContent.addView(view)
    }
}
