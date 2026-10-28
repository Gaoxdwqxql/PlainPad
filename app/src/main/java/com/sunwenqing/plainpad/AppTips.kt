package com.sunwenqing.plainpad

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar

object AppTips {

    fun showShort(activity: Activity, message: String) =
        show(activity.findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT)

    fun showShort(activity: Activity, @StringRes resId: Int) =
        showShort(activity, activity.getString(resId))

    fun showLong(activity: Activity, message: String) =
        show(activity.findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)

    fun showLong(activity: Activity, @StringRes resId: Int) =
        showLong(activity, activity.getString(resId))

    fun showShort(anchor: View, message: String) =
        show(anchor, message, Snackbar.LENGTH_SHORT)

    fun showShort(anchor: View, @StringRes resId: Int) =
        showShort(anchor, anchor.context.getString(resId))

    fun showLong(anchor: View, message: String) =
        show(anchor, message, Snackbar.LENGTH_LONG)

    fun showLong(anchor: View, @StringRes resId: Int) =
        showLong(anchor, anchor.context.getString(resId))

    private fun show(anchor: View?, message: String, duration: Int) {
        if (anchor == null) return
        val snackbar = Snackbar.make(anchor, message, duration)
        val view = snackbar.view
        val ctx = view.context
        val density = ctx.resources.displayMetrics.density

        val bgColor = resolveAttr(
            view,
            com.google.android.material.R.attr.colorOnSurface,
            0xFF303030.toInt()
        )
        val fgColor = resolveAttr(
            view,
            com.google.android.material.R.attr.colorSurface,
            0xFFFFFFFF.toInt()
        )

        view.background = GradientDrawable().apply {
            cornerRadius = 12f * density
            setColor(bgColor)
        }

        view.findViewById<TextView>(
            com.google.android.material.R.id.snackbar_text
        )?.setTextColor(fgColor)

        snackbar.show()
    }

    private fun resolveAttr(view: View, attr: Int, fallback: Int): Int {
        val tv = TypedValue()
        return if (view.context.theme.resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) ContextCompat.getColor(view.context, tv.resourceId)
            else tv.data
        } else {
            fallback
        }
    }
}