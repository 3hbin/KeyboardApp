package com.example.keyboard.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.SoundEffectConstants
import com.example.keyboard.R
import com.example.keyboard.settings.Prefs

object Feedback {
    private var soundPool: SoundPool? = null
    private var clickId = 0
    private var loaded = false

    fun init(ctx: Context) {
        if (soundPool != null) return
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            soundPool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attrs)
                .build()
            clickId = soundPool!!.load(ctx, R.raw.click_sound, 1)
            soundPool!!.setOnLoadCompleteListener { _, _, status ->
                loaded = status == 0
            }
        } catch (_: Exception) {
            soundPool = null
            loaded = false
        }
    }

    fun keyPress(ctx: Context, prefs: Prefs) {
        if (prefs.haptic) {
            val ms = when (prefs.hapticStrength) { 1 -> 8L; 3 -> 28L; else -> 14L }
            try {
                val v = if (Build.VERSION.SDK_INT >= 31) {
                    (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                if (Build.VERSION.SDK_INT >= 26) {
                    val amp = when (prefs.hapticStrength) { 1 -> 40; 3 -> 120; else -> 70 }
                    v.vibrate(VibrationEffect.createOneShot(ms, amp.coerceIn(1, 255)))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(ms)
                }
            } catch (_: Exception) {}
        }
        if (prefs.sound) {
            try {
                if (soundPool == null) init(ctx)
                val vol = prefs.soundVolume.coerceIn(0f, 1f)
                if (loaded && clickId != 0) {
                    soundPool?.play(clickId, vol, vol, 1, 0, 1f)
                } else {
                    // Fallback system keypress
                    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    am.playSoundEffect(SoundEffectConstants.CLICK, vol)
                }
            } catch (_: Exception) {
                try {
                    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
                } catch (_: Exception) {}
            }
        }
    }

    fun release() {
        try { soundPool?.release() } catch (_: Exception) {}
        soundPool = null
        loaded = false
        clickId = 0
    }
}
