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
    /** 用户手动锁定的编辑状态，和文件本身的只读属性无关 */
    var isLocked: Boolean = false,
    /** 超大文件走按需读取：不为 null 时 text 为空，内容从数据源取 */
    var chunkedSource: ChunkedTextSource? = null,
    /** 这次文档是否原本走按需读取；重建/重启后据此重新打开数据源 */
    var wasChunked: Boolean = false,
    /** 保存时是否写入 BOM。打开带 BOM 的文件后为 true，用于原样保留。 */
    var hasBom: Boolean = false,
    /** 书签：记录的是行首字符偏移，编辑时会随文本增删调整 */
    var bookmarks: List<Int> = emptyList(),
    /** 是否按 PlainPad 密钥格式加密保存 */
    var encrypted: Boolean = false,
    /** 当前会话的加密密钥，只存在内存里，不写入磁盘 */
    var password: CharArray? = null,
    var draftId: String = UUID.randomUUID().toString()
)