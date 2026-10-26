package com.sunwenqing.plainpad

import android.graphics.Typeface
import java.io.File

object FontHelper {

    fun typefaceForKey(prefs: Prefs, key: String): Typeface {
        if (key.startsWith(FontManager.CUSTOM_PREFIX)) {
            val fileName = key.removePrefix(FontManager.CUSTOM_PREFIX)
            val file = prefs.fontFile(fileName)
            if (file.exists()) {
                try {
                    return Typeface.createFromFile(file)
                } catch (_: Exception) {
                    // fall through
                }
            }
            return Typeface.MONOSPACE
        }

        // 兼容旧版 "custom" + customFontPath
        if (key == "custom") {
            val oldPath = prefs.customFontPath
            if (oldPath.isNotEmpty()) {
                val f = File(oldPath)
                if (f.exists()) {
                    try {
                        return Typeface.createFromFile(f)
                    } catch (_: Exception) {
                        // fall through
                    }
                }
            }
            return Typeface.MONOSPACE
        }

        return Typeface.create(key, Typeface.NORMAL)
    }

    fun getTypeface(prefs: Prefs): Typeface =
        typefaceForKey(prefs, prefs.editorFontFamily)
}