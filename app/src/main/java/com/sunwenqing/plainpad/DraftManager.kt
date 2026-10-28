package com.sunwenqing.plainpad

import android.content.Context
import org.json.JSONObject
import java.io.File

data class DraftInfo(
    val draftId: String,
    val displayName: String,
    val uri: String?,
    val encoding: String,
    val selStart: Int,
    val selEnd: Int,
    val scrollY: Int,
    val isReadOnly: Boolean,
    val savedAt: Long
)

/**
 * 草稿管理。每个标签对应两个文件：
 * - <draftId>.meta：JSON 元数据
 * - <draftId>.txt：正文
 *
 * 只在编辑防抖、切换标签、新建标签前写入。
 * 保存成功、关闭标签、onPause 完成时删除。
 */
object DraftManager {

    private const val META_EXT = ".meta"
    private const val TEXT_EXT = ".txt"

    fun draftsDir(context: Context): File =
        File(context.filesDir, "drafts").apply { mkdirs() }

    fun save(context: Context, doc: OpenDocument): Boolean {
        if (doc.isReadOnly) return false
        return try {
            val dir = draftsDir(context)
            val metaFile = File(dir, doc.draftId + META_EXT)
            val textFile = File(dir, doc.draftId + TEXT_EXT)

            val meta = JSONObject().apply {
                put("displayName", doc.displayName)
                put("uri", doc.uri?.toString() ?: "")
                put("encoding", doc.encoding)
                put("selStart", doc.selStart)
                put("selEnd", doc.selEnd)
                put("scrollY", doc.scrollY)
                put("isReadOnly", doc.isReadOnly)
                put("savedAt", System.currentTimeMillis())
            }

            metaFile.writeText(meta.toString(), Charsets.UTF_8)
            textFile.writeText(doc.text, Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun load(context: Context, draftId: String): Pair<DraftInfo, String>? {
        return try {
            val dir = draftsDir(context)
            val metaFile = File(dir, draftId + META_EXT)
            val textFile = File(dir, draftId + TEXT_EXT)
            if (!metaFile.exists() || !textFile.exists()) return null

            val o = JSONObject(metaFile.readText(Charsets.UTF_8))
            val info = DraftInfo(
                draftId = draftId,
                displayName = o.optString("displayName", "未命名.txt"),
                uri = o.optString("uri", "").takeIf { it.isNotEmpty() },
                encoding = o.optString("encoding", "UTF-8"),
                selStart = o.optInt("selStart", 0),
                selEnd = o.optInt("selEnd", 0),
                scrollY = o.optInt("scrollY", 0),
                isReadOnly = o.optBoolean("isReadOnly", false),
                savedAt = o.optLong("savedAt", 0L)
            )
            val text = textFile.readText(Charsets.UTF_8)
            info to text
        } catch (_: Exception) {
            null
        }
    }

    fun delete(context: Context, draftId: String): Boolean {
        return try {
            val dir = draftsDir(context)
            File(dir, draftId + META_EXT).delete()
            File(dir, draftId + TEXT_EXT).delete()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun listAll(context: Context): List<DraftInfo> {
        val dir = draftsDir(context)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.name.endsWith(META_EXT) }?.mapNotNull { f ->
            val draftId = f.name.removeSuffix(META_EXT)
            load(context, draftId)?.first
        } ?: emptyList()
    }

    fun clearAll(context: Context) {
        try {
            draftsDir(context).listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {}
    }
}