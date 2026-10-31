package com.sunwenqing.plainpad.early

import android.content.Context
import androidx.core.content.edit

class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("plainpad_prefs", Context.MODE_PRIVATE)

    var fontSize: Int
        get() = sp.getInt(KEY_FONT, 16)
        set(v) = sp.edit { putInt(KEY_FONT, v) }

    var wrap: Boolean
        get() = sp.getBoolean(KEY_WRAP, true)
        set(v) = sp.edit { putBoolean(KEY_WRAP, v) }

    private companion object {
        const val KEY_FONT = "font_size"
        const val KEY_WRAP = "wrap"
    }
}