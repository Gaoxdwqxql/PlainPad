package com.sunwenqing.plainpad

import android.net.Uri
import java.util.UUID

/** 一个打开的文档（对应一个标签页） */
data class OpenDocument(
    var uri: Uri? = null,
    var displayName: String = "",
    var encoding: String = "UTF-8",
    var text: String = "",
    var selStart: Int = 0,
    var selEnd: Int = 0,
    var scrollY: Int = 0,
    var isDirty: Boolean = false,
    var isReadOnly: Boolean = false,
    /** 保存时是否写入 BOM。打开带 BOM 的文件后为 true，用于原样保留。 */
    var hasBom: Boolean = false,
    /** 书签：记录的是行首字符偏移，编辑时会随文本增删调整 */
    var bookmarks: List<Int> = emptyList(),
    var draftId: String = UUID.randomUUID().toString()
)