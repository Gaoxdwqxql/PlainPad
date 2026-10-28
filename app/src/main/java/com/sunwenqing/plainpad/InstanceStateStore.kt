package com.sunwenqing.plainpad

import android.net.Uri
import android.os.Bundle
import java.util.UUID

object InstanceStateStore {

    private const val KEY_NAMES = "doc_names"
    private const val KEY_URIS = "doc_uris"
    private const val KEY_ENCODINGS = "doc_encodings"
    private const val KEY_TEXTS = "doc_texts"
    private const val KEY_DIRTY_FLAGS = "doc_dirty_flags"
    private const val KEY_READONLY_FLAGS = "doc_readonly_flags"
    private const val KEY_SEL_STARTS = "doc_sel_starts"
    private const val KEY_SEL_ENDS = "doc_sel_ends"
    private const val KEY_SCROLLS = "doc_scrolls"
    private const val KEY_DRAFT_IDS = "doc_draft_ids"
    private const val KEY_CUR_INDEX = "cur_index"
    private const val KEY_UNTITLED_COUNTER = "untitled_counter"

    /**
     * Bundle 单个 key 不能超过 Binder 事务上限（约 1MB），
     * 这里保守限制单篇文本最多 100KB，超过只存空串。
     * 大文本恢复由 Prefs.saved_tabs 或从 URI 重新加载兜底。
     */
    private const val MAX_BUNDLE_TEXT_LENGTH = 100_000

    fun save(
        outState: Bundle,
        documents: List<OpenDocument>,
        currentIndex: Int,
        untitledCounter: Int
    ) {
        val names = ArrayList<String>()
        val uris = ArrayList<String>()
        val encodings = ArrayList<String>()
        val texts = ArrayList<String>()
        val dirtyFlags = ArrayList<Boolean>()
        val readonlyFlags = ArrayList<Boolean>()
        val selStarts = ArrayList<Int>()
        val selEnds = ArrayList<Int>()
        val scrolls = ArrayList<Int>()
        val draftIds = ArrayList<String>()

        for (d in documents) {
            names.add(d.displayName)
            uris.add(d.uri?.toString() ?: "")
            encodings.add(d.encoding)
            texts.add(if (d.text.length <= MAX_BUNDLE_TEXT_LENGTH) d.text else "")
            dirtyFlags.add(d.isDirty)
            readonlyFlags.add(d.isReadOnly)
            selStarts.add(d.selStart)
            selEnds.add(d.selEnd)
            scrolls.add(d.scrollY)
            draftIds.add(d.draftId)
        }

        outState.putStringArrayList(KEY_NAMES, names)
        outState.putStringArrayList(KEY_URIS, uris)
        outState.putStringArrayList(KEY_ENCODINGS, encodings)
        outState.putStringArrayList(KEY_TEXTS, texts)
        outState.putBooleanArray(KEY_DIRTY_FLAGS, dirtyFlags.toBooleanArray())
        outState.putBooleanArray(KEY_READONLY_FLAGS, readonlyFlags.toBooleanArray())
        outState.putIntegerArrayList(KEY_SEL_STARTS, selStarts)
        outState.putIntegerArrayList(KEY_SEL_ENDS, selEnds)
        outState.putIntegerArrayList(KEY_SCROLLS, scrolls)
        outState.putStringArrayList(KEY_DRAFT_IDS, draftIds)
        outState.putInt(KEY_CUR_INDEX, currentIndex)
        outState.putInt(KEY_UNTITLED_COUNTER, untitledCounter)
    }

    data class RestoredState(
        val documents: List<OpenDocument>,
        val currentIndex: Int,
        val untitledCounter: Int
    )

    fun restore(state: Bundle): RestoredState {
        val names = state.getStringArrayList(KEY_NAMES) ?: arrayListOf()
        val uris = state.getStringArrayList(KEY_URIS) ?: arrayListOf()
        val encodings = state.getStringArrayList(KEY_ENCODINGS) ?: arrayListOf()
        val texts = state.getStringArrayList(KEY_TEXTS) ?: arrayListOf()
        val dirtyFlags = state.getBooleanArray(KEY_DIRTY_FLAGS) ?: booleanArrayOf()
        val readonlyFlags = state.getBooleanArray(KEY_READONLY_FLAGS) ?: booleanArrayOf()
        val selStarts = state.getIntegerArrayList(KEY_SEL_STARTS) ?: arrayListOf()
        val selEnds = state.getIntegerArrayList(KEY_SEL_ENDS) ?: arrayListOf()
        val scrolls = state.getIntegerArrayList(KEY_SCROLLS) ?: arrayListOf()
        val draftIds = state.getStringArrayList(KEY_DRAFT_IDS) ?: arrayListOf()
        val curIdx = state.getInt(KEY_CUR_INDEX, 0)
        val counter = state.getInt(KEY_UNTITLED_COUNTER, 0)

        val docs = ArrayList<OpenDocument>(names.size)
        for (i in names.indices) {
            docs.add(
                OpenDocument(
                    displayName = names[i],
                    uri = uris.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let(Uri::parse),
                    encoding = encodings.getOrNull(i) ?: "UTF-8",
                    text = texts.getOrNull(i) ?: "",
                    isDirty = dirtyFlags.getOrNull(i) ?: false,
                    isReadOnly = readonlyFlags.getOrNull(i) ?: false,
                    selStart = selStarts.getOrNull(i) ?: 0,
                    selEnd = selEnds.getOrNull(i) ?: 0,
                    scrollY = scrolls.getOrNull(i) ?: 0,
                    draftId = draftIds.getOrNull(i)
                        ?.takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
                )
            )
        }
        return RestoredState(docs, curIdx, counter)
    }
}