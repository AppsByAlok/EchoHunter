package com.appsbyalok.echohunter.utils

import android.media.AudioManager
import android.media.ToneGenerator

// Singleton object for handling audio globally
object EchoAudioManager {
    private var tone: ToneGenerator? = null
    private var lastToneTime = 0L

    // Initialize once in your app/activity lifecycle
    fun init() {
        if (tone == null) {
            tone = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        }
    }

    // Play sound with a small cooldown to prevent overlapping audio glitches
    fun playSound(toneType: Int, duration: Int) {
        if (!com.appsbyalok.echohunter.data.SaveManager.isSoundEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastToneTime > 40) {
            try {
                tone?.startTone(toneType, duration)
                lastToneTime = now
            } catch (_: Exception) {
                // Ignore audio errors to prevent crashes
            }
        }
    }

    /**
     * Specialized fire sound that "pitch shifts" based on weapon damage.
     * Higher damage = Higher DTMF frequency for a sharper "upgraded" feel.
     */
    fun playFireSound(damage: Float, weaponType: Int) {
        if (!com.appsbyalok.echohunter.data.SaveManager.isSoundEnabled) return

        // Map damage (typical range 1.0 to 25.0) to DTMF tones for pseudo-pitch shifting
        // We use DTMF tones because they offer distinct, recognizable frequency variations
        val toneIndex = (damage / 2.0f).toInt().coerceIn(1, 12)
        val toneType = when (toneIndex) {
            1 -> ToneGenerator.TONE_DTMF_1
            2 -> ToneGenerator.TONE_DTMF_2
            3 -> ToneGenerator.TONE_DTMF_3
            4 -> ToneGenerator.TONE_DTMF_4
            5 -> ToneGenerator.TONE_DTMF_5
            6 -> ToneGenerator.TONE_DTMF_6
            7 -> ToneGenerator.TONE_DTMF_7
            8 -> ToneGenerator.TONE_DTMF_8
            9 -> ToneGenerator.TONE_DTMF_9
            10 -> ToneGenerator.TONE_DTMF_S // *
            11 -> ToneGenerator.TONE_DTMF_0
            else -> ToneGenerator.TONE_DTMF_P // #
        }

        // Adjust duration based on weapon type for better tactile feel
        val duration = when (weaponType) {
            1 -> 35 // Shotgun - snappy
            2 -> 80 // Sniper - heavier resonance
            else -> 45 // Standard - balanced
        }

        playSound(toneType, duration)
    }

    // Release resources when game is closed
    fun release() {
        tone?.release()
        tone = null
    }
}