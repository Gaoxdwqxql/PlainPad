package com.sunwenqing.plainpad

import android.graphics.Typeface
import java.io.File

object FontHelper {
    /**
     * 根据偏好设置获取 Typeface
     */
    fun getTypeface(prefs: Prefs): Typeface {
        val customPath = prefs.customFontPath
        if (customPath.isNotEmpty()) {
            try {
                val file = File(customPath)
                if (file.exists()) {
                    return Typeface.createFromFile(file)
                }
            } catch (_: Exception) {
                // 加载失败退回默认
            }
        }
        return Typeface.create(prefs.editorFontFamily, Typeface.NORMAL)
    }
}