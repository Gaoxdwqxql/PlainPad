package com.sunwenqing.plainpad

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 自动保存控制器。
 * 负责三路触发：定时、编辑防抖、立即保存。
 */
class AutoSaveController(
    private val activity: MainActivity,
    private val prefs: Prefs,
    private val getCurrentDoc: () -> OpenDocument?,
    private val getContent: () -> String,
    private val isLargeFileMode: () -> Boolean,
    private val onSaved: (OpenDocument, String) -> Unit
) {

    private val intervalHandler = Handler(Looper.getMainLooper())
    private var intervalRunnable: Runnable? = null

    private val editHandler = Handler(Looper.getMainLooper())
    private var editRunnable: Runnable? = null
    private val EDIT_AUTO_SAVE_DELAY_MS = 2000L

    fun scheduleInterval() {
        cancelInterval()
        if (!prefs.autoSaveEnabled || prefs.autoSaveIntervalSec <= 0) return
        val r = object : Runnable {
            override fun run() {
                if (prefs.autoSaveEnabled && prefs.autoSaveIntervalSec > 0) {
                    autoSaveCurrent()
                    intervalHandler.postDelayed(this, prefs.autoSaveIntervalSec * 1000L)
                }
            }
        }
        intervalRunnable = r
        intervalHandler.postDelayed(r, prefs.autoSaveIntervalSec * 1000L)
    }

    fun cancelInterval() {
        intervalRunnable?.let { intervalHandler.removeCallbacks(it) }
        intervalRunnable = null
    }

    fun scheduleEditAutoSave() {
        if (!prefs.autoSaveEnabled || !prefs.autoSaveOnEdit || isLargeFileMode()) return
        val doc = getCurrentDoc() ?: return
        if (doc.uri == null || !doc.isDirty || doc.isReadOnly) return
        editRunnable?.let { editHandler.removeCallbacks(it) }
        val r = Runnable { autoSaveCurrent() }
        editRunnable = r
        editHandler.postDelayed(r, EDIT_AUTO_SAVE_DELAY_MS)
    }

    fun cancelEditAutoSave() {
        editRunnable?.let { editHandler.removeCallbacks(it) }
        editRunnable = null
    }

    fun autoSaveCurrent() {
        if (!prefs.autoSaveEnabled || isLargeFileMode()) return
        val doc = getCurrentDoc() ?: return
        if (doc.uri == null || !doc.isDirty || doc.isReadOnly) return
        val content = getContent()
        activity.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    EditorFileIO.save(activity.contentResolver, doc.uri!!, content, doc.encoding)
                }
                doc.text = content
                doc.isDirty = false
                prefs.lastSaveTime = System.currentTimeMillis()
                onSaved(doc, content)
            } catch (_: Exception) {}
        }
    }

    fun cancelAll() {
        cancelInterval()
        cancelEditAutoSave()
    }
}