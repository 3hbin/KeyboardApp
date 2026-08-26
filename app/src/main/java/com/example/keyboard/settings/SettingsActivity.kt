package com.example.keyboard.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.example.keyboard.R
import com.example.keyboard.input.VietnameseEngine

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbarSettings)
            .setNavigationOnClickListener { finish() }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val ctx = preferenceManager.context
            val screen = preferenceManager.createPreferenceScreen(ctx)
            val prefs = Prefs(ctx)

            screen.addPreference(ListPreference(ctx).apply {
                key = "pref_input_mode"; title = "Bộ gõ tiếng Việt"
                entries = arrayOf("Telex", "VNI", "Tắt"); entryValues = arrayOf("TELEX", "VNI", "OFF")
                setDefaultValue("TELEX")
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                setOnPreferenceChangeListener { _, n ->
                    prefs.inputMode = when (n as String) {
                        "VNI" -> VietnameseEngine.Mode.VNI
                        "OFF" -> VietnameseEngine.Mode.OFF
                        else -> VietnameseEngine.Mode.TELEX
                    }; true
                }
            })
            screen.addPreference(ListPreference(ctx).apply {
                key = "pref_height"; title = "Chiều cao bàn phím"
                entries = arrayOf("Thấp", "Vừa", "Cao"); entryValues = arrayOf("0", "1", "2")
                setDefaultValue("1")
                setOnPreferenceChangeListener { _, n -> prefs.heightLevel = (n as String).toInt(); true }
            })
            screen.addPreference(ListPreference(ctx).apply {
                key = "pref_one_hand"; title = "Gõ một tay"
                entries = arrayOf("Tắt", "Trái", "Phải"); entryValues = arrayOf("off", "left", "right")
                setDefaultValue("off")
                setOnPreferenceChangeListener { _, n -> prefs.oneHanded = n as String; true }
            })
            screen.addPreference(SwitchPreferenceCompat(ctx).apply {
                key = "pref_num_row"; title = "Hàng số cố định"; setDefaultValue(true)
                setOnPreferenceChangeListener { _, n -> prefs.showNumberRow = n as Boolean; true }
            })
            screen.addPreference(SwitchPreferenceCompat(ctx).apply {
                key = "pref_haptic"; title = "Rung khi bấm"; setDefaultValue(true)
                setOnPreferenceChangeListener { _, n -> prefs.haptic = n as Boolean; true }
            })
            screen.addPreference(ListPreference(ctx).apply {
                key = "pref_haptic_str"; title = "Mức rung"
                entries = arrayOf("Nhẹ", "Vừa", "Mạnh"); entryValues = arrayOf("1", "2", "3")
                setDefaultValue("2")
                setOnPreferenceChangeListener { _, n -> prefs.hapticStrength = (n as String).toInt(); true }
            })
            screen.addPreference(SwitchPreferenceCompat(ctx).apply {
                key = "pref_sound"; title = "Âm thanh phím (SoundPool)"; setDefaultValue(true)
                setOnPreferenceChangeListener { _, n -> prefs.sound = n as Boolean; true }
            })
            screen.addPreference(SwitchPreferenceCompat(ctx).apply {
                key = "pref_auto_cap"; title = "Tự viết hoa đầu câu"; setDefaultValue(true)
                setOnPreferenceChangeListener { _, n -> prefs.autoCapitalize = n as Boolean; true }
            })
            screen.addPreference(ListPreference(ctx).apply {
                key = "pref_bg_opacity"; title = "Độ mờ lớp phủ nền"
                entries = arrayOf("30%", "50%", "70%", "85%", "100%")
                entryValues = arrayOf("0.3", "0.5", "0.7", "0.85", "1.0")
                setDefaultValue("0.85")
                setOnPreferenceChangeListener { _, n -> prefs.bgOpacity = (n as String).toFloat(); true }
            })
            preferenceScreen = screen
        }
    }
}
