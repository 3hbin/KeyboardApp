package com.example.keyboard.clipboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ClipItem(val text: String, val time: Long, val pinned: Boolean = false)

class ClipboardStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("clip_store", Context.MODE_PRIVATE)
    private val max = 20

    fun add(text: String) {
        if (text.isBlank()) return
        val list = all().toMutableList().filter { it.text != text }.toMutableList()
        list.add(0, ClipItem(text, System.currentTimeMillis(), false))
        while (list.size > max) {
            val idx = list.indexOfLast { !it.pinned }
            if (idx >= 0) list.removeAt(idx) else break
        }
        save(list)
    }

    fun all(): List<ClipItem> {
        val raw = sp.getString("items", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            ClipItem(o.getString("t"), o.getLong("ts"), o.optBoolean("p", false))
        }
    }

    fun pin(text: String, pinned: Boolean) {
        save(all().map { if (it.text == text) it.copy(pinned = pinned) else it })
    }

    fun clearUnpinned() {
        save(all().filter { it.pinned })
    }

    private fun save(list: List<ClipItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("t", it.text).put("ts", it.time).put("p", it.pinned))
        }
        sp.edit().putString("items", arr.toString()).apply()
    }
}
