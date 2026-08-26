package com.example.keyboard.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.keyboard.input.VietnameseEngine

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE)

    var inputMode: VietnameseEngine.Mode
        get() = VietnameseEngine.Mode.entries.getOrElse(sp.getInt("input_mode", 1)) { VietnameseEngine.Mode.TELEX }
        set(v) = sp.edit { putInt("input_mode", v.ordinal) }

    var layout: String
        get() = sp.getString("layout", "QWERTY") ?: "QWERTY"
        set(v) = sp.edit { putString("layout", v) }

    var heightLevel: Int
        get() = sp.getInt("height", 1)
        set(v) = sp.edit { putInt("height", v) }

    var fontSizeSp: Float
        get() = sp.getFloat("font_size", 18f)
        set(v) = sp.edit { putFloat("font_size", v) }

    var showNumberRow: Boolean
        get() = sp.getBoolean("number_row", true)
        set(v) = sp.edit { putBoolean("number_row", v) }

    var showKeyBorder: Boolean
        get() = sp.getBoolean("key_border", false)
        set(v) = sp.edit { putBoolean("key_border", v) }

    var haptic: Boolean
        get() = sp.getBoolean("haptic", true)
        set(v) = sp.edit { putBoolean("haptic", v) }

    var hapticStrength: Int
        get() = sp.getInt("haptic_strength", 2)
        set(v) = sp.edit { putInt("haptic_strength", v) }

    var sound: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) = sp.edit { putBoolean("sound", v) }

    var soundVolume: Float
        get() = sp.getFloat("sound_vol", 0.7f)
        set(v) = sp.edit { putFloat("sound_vol", v) }

    var keyPreview: Boolean
        get() = sp.getBoolean("key_preview", true)
        set(v) = sp.edit { putBoolean("key_preview", v) }

    var autoCapitalize: Boolean
        get() = sp.getBoolean("auto_cap", true)
        set(v) = sp.edit { putBoolean("auto_cap", v) }

    var autoSpace: Boolean
        get() = sp.getBoolean("auto_space", true)
        set(v) = sp.edit { putBoolean("auto_space", v) }

    var themeMode: String
        get() = sp.getString("theme_mode", "system") ?: "system"
        set(v) = sp.edit { putString("theme_mode", v) }

    var colorPreset: String
        get() = sp.getString("color_preset", "default") ?: "default"
        set(v) = sp.edit { putString("color_preset", v) }

    var bgImageUri: String
        get() = sp.getString("bg_uri", "") ?: ""
        set(v) = sp.edit { putString("bg_uri", v) }

    var bgOpacity: Float
        get() = sp.getFloat("bg_opacity", 0.85f)
        set(v) = sp.edit { putFloat("bg_opacity", v) }

    var oneHanded: String // off / left / right
        get() = sp.getString("one_hand", "off") ?: "off"
        set(v) = sp.edit { putString("one_hand", v) }

    var floating: Boolean
        get() = sp.getBoolean("floating", false)
        set(v) = sp.edit { putBoolean("floating", v) }

    var showArrowKeys: Boolean
        get() = sp.getBoolean("arrows", false)
        set(v) = sp.edit { putBoolean("arrows", v) }

    var typingCountToday: Int
        get() = sp.getInt("type_count_" + today(), 0)
        set(v) = sp.edit { putInt("type_count_" + today(), v) }

    var recentEmojis: String
        get() = sp.getString("recent_emoji", "") ?: ""
        set(v) = sp.edit { putString("recent_emoji", v) }

    fun addRecentEmoji(e: String) {
        val list = recentEmojis.split("|").filter { it.isNotBlank() && it != e }.toMutableList()
        list.add(0, e)
        recentEmojis = list.take(24).joinToString("|")
    }

    private fun today(): String {
        val c = java.util.Calendar.getInstance()
        return "${c.get(java.util.Calendar.YEAR)}${c.get(java.util.Calendar.DAY_OF_YEAR)}"
    }

    fun getShortcuts(): Map<String, String> {
        val raw = sp.getString("shortcuts", "dc=Địa chỉ: |sdt=SĐT: |email=Email: ") ?: ""
        return raw.split("|").mapNotNull {
            val p = it.split("=", limit = 2)
            if (p.size == 2) p[0].trim() to p[1].trim() else null
        }.toMap()
    }
}
