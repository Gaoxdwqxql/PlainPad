package com.sunwenqing.plainpad

import android.content.Context
import android.graphics.Typeface
import java.io.File

object FontHelper {

    fun typefaceForKey(context: Context, prefs: Prefs, key: String): Typeface {
        if (key.startsWith(FontManager.CUSTOM_PREFIX)) {
            if (FontManager.isUriKey(key)) {
                val uri = FontManager.uriOfKey(key)
                if (uri != null) {
                    val tf = FontManager.loadTypefaceFromUri(context, uri)
                    if (tf != null) return tf
                }
                return Typeface.MONOSPACE
            }

            val fileName = FontManager.fileNameOfKey(key)
            if (fileName != null) {
                val file = prefs.fontFile(fileName)
                if (file.exists()) {
                    try {
                        return Typeface.createFromFile(file)
                    } catch (_: Exception) {}
                }
                return Typeface.MONOSPACE
            }
        }

        if (key == "custom") {
            val oldPath = prefs.customFontPath
            if (oldPath.isNotEmpty()) {
                val f = File(oldPath)
                if (f.exists()) {
                    try {
                        return Typeface.createFromFile(f)
                    } catch (_: Exception) {}
                }
            }
            return Typeface.MONOSPACE
        }

        return Typeface.create(key, Typeface.NORMAL)
    }

    fun getTypeface(context: Context, prefs: Prefs): Typeface =
        typefaceForKey(context, prefs, prefs.editorFontFamily)
}