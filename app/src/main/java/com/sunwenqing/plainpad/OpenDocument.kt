package com.sunwenqing.plainpad

import android.net.Uri

/** 一个打开的文档（对应一个标签页） */
data class OpenDocument(
    var uri: Uri? = null,
    var displayName: String = "",
    var encoding: String = "UTF-8",
    var text: String = "",
    var selStart: Int = 0,
    var selEnd: Int = 0,
    var scrollY: Int = 0,
    var isDirty: Boolean = false
)