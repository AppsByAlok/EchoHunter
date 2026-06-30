package com.appsbyalok.echohunter.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale
import kotlin.math.max

object SaveManager {
    private lateinit var prefs: SharedPreferences

    var dataCoinsKB: Long = 0L
        private set

    var totalData: Long = 0L
        private set

    var maxCampaignLevel: Int = 1
        private set

    var isAutoNextLevelEnabled: Boolean = false
        private set

    // --- GAME SETTINGS ---
    var isSoundEnabled: Boolean = true
        private set
    var isVibrationEnabled: Boolean = true
        private set
    var isEffectsEnabled: Boolean = true
        private set

    fun setAutoNextLevel(enabled: Boolean) {
        isAutoNextLevelEnabled = enabled
        prefs.edit().putBoolean("isAutoNextLevelEnabled", isAutoNextLevelEnabled).apply()
    }

    fun setSoundEnabled(enabled: Boolean) {
        isSoundEnabled = enabled
        prefs.edit().putBoolean("isSoundEnabled", isSoundEnabled).apply()
    }

    fun setVibrationEnabled(enabled: Boolean) {
        isVibrationEnabled = enabled
        prefs.edit().putBoolean("isVibrationEnabled", isVibrationEnabled).apply()
    }

    fun setEffectsEnabled(enabled: Boolean) {
        isEffectsEnabled = enabled
        prefs.edit().putBoolean("isEffectsEnabled", isEffectsEnabled).apply()
    }
    // --- NAYA STREAK SYSTEM (ROGUELITE) ---
    var currentStoryStreak: Int = 0
        private set
    var unlockedStoryStreak: Int = 0
        private set

    var currentHardStreak: Int = 0
        private set
    var unlockedHardStreak: Int = 0
        private set

    val isStoryModeUnlocked: Boolean
        get() = maxCampaignLevel > 15

    // Hard Mode unlocks permanently after clearing all 3 Acts (Streak >= 3)
    val isHardModeUnlocked: Boolean
        get() = unlockedStoryStreak >= 3

    val isManualAimUnlocked: Boolean
        get() = unlockedStoryStreak >= 1 || maxCampaignLevel > 20

    var highScore: Long = 0L
        private set
    var previousScore: Long = 0L
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("EchoSaveInfo", Context.MODE_PRIVATE)

        dataCoinsKB = getLongSafe("dataCoinsKB")
        totalData = getLongSafe("totalData")
        maxCampaignLevel = prefs.getInt("maxCampaignLevel", 1)
        isAutoNextLevelEnabled = prefs.getBoolean("isAutoNextLevelEnabled", false)
        isSoundEnabled = prefs.getBoolean("isSoundEnabled", true)
        isVibrationEnabled = prefs.getBoolean("isVibrationEnabled", true)
        isEffectsEnabled = prefs.getBoolean("isEffectsEnabled", true)

        // Load Streaks
        currentStoryStreak = prefs.getInt("currStory", 0)
        unlockedStoryStreak = prefs.getInt("unlockStory", 0)
        currentHardStreak = prefs.getInt("currHard", 0)
        unlockedHardStreak = prefs.getInt("unlockHard", 0)

        highScore = getLongSafe("highScore")
        previousScore = getLongSafe("previousScore")
    }

    /**
     * Safely retrieves a Long value from SharedPreferences.
     * If the value was previously stored as an Int (migration), it converts and saves it as a Long.
     */
    private fun getLongSafe(key: String, defaultValue: Long = 0L): Long {
        return try {
            // Case 1: Value is already Long (Normal flow)
            prefs.getLong(key, defaultValue)
        } catch (_: Exception) {
            try {
                // Case 2: Value is Int (Legacy data)
                val legacyValue = prefs.getInt(key, defaultValue.toInt())
                val migratedValue = legacyValue.toLong()
                
                // Auto-migrate: overwrite with Long for next time
                prefs.edit().putLong(key, migratedValue).apply()
                migratedValue
            } catch (_: Exception) {
                // Case 3: Value is something else or corrupted
                defaultValue
            }
        }
    }

    fun addData(kbAmount: Long) {
        dataCoinsKB += kbAmount
        totalData = (dataCoinsKB / 1024L)
        prefs.edit()
            .putLong("dataCoinsKB", dataCoinsKB)
            .putLong("totalData", totalData)
            .apply()
    }

    fun spendData(kbCost: Long): Boolean {
        if (dataCoinsKB >= kbCost) {
            dataCoinsKB -= kbCost
            totalData = (dataCoinsKB / 1024L)
            prefs.edit()
                .putLong("dataCoinsKB", dataCoinsKB)
                .putLong("totalData", totalData)
                .apply()
            return true
        }
        return false
    }

    fun updateCampaignProgress(levelCleared: Int) {
        if (levelCleared >= maxCampaignLevel) {
            maxCampaignLevel = max(maxCampaignLevel, levelCleared + 1)
            prefs.edit().putInt("maxCampaignLevel", maxCampaignLevel).apply()
        }
    }

    // --- STREAK PROGRESSION LOGIC ---
    fun updateStoryStreak(won: Boolean, isHardMode: Boolean, playedAct: Int) {
        if (won) {
            if (!isHardMode) {
                // If they played the exact Act they needed to beat
                if (playedAct == currentStoryStreak) {
                    currentStoryStreak++
                    if (currentStoryStreak > unlockedStoryStreak) {
                        unlockedStoryStreak = currentStoryStreak
                    }
                }
            } else {
                if (playedAct == currentHardStreak) {
                    currentHardStreak++
                    if (currentHardStreak > unlockedHardStreak) {
                        unlockedHardStreak = currentHardStreak
                    }
                }
            }
        } else {
            // Loss resets current streak back to 0, but Unlocked stays safe forever!
            if (!isHardMode) currentStoryStreak = 0
            else currentHardStreak = 0
        }

        prefs.edit()
            .putInt("currStory", currentStoryStreak)
            .putInt("unlockStory", unlockedStoryStreak)
            .putInt("currHard", currentHardStreak)
            .putInt("unlockHard", unlockedHardStreak)
            .apply()
    }

    fun saveRunResult(currentScore: Long) {
        previousScore = currentScore
        if (currentScore > highScore) {
            highScore = currentScore
        }
        prefs.edit()
            .putLong("previousScore", previousScore)
            .putLong("highScore", highScore)
            .apply()
    }

    fun formatDataString(kb: Long): String {
        if (kb < 1024L) return "$kb KB"
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US,"%.1f MB", mb)
        val gb = mb / 1024.0
        if (gb < 1024.0) return String.format(Locale.US,"%.2f GB", gb)
        val tb = gb / 1024.0
        if (tb < 1024.0) return String.format(Locale.US,"%.2f TB", tb)
        val pb = tb / 1024.0
        return String.format(Locale.US,"%.2f PB", pb)
    }

    fun debugSetLevel(level: Int) {
        maxCampaignLevel = max(1, level)
        prefs.edit().putInt("maxCampaignLevel", maxCampaignLevel).apply()
    }

    fun debugModifyStoryStreak(delta: Int) {
        unlockedStoryStreak = max(0, unlockedStoryStreak + delta)
        prefs.edit().putInt("unlockStory", unlockedStoryStreak).apply()
    }

    fun debugUnlockAll() {
        maxCampaignLevel = 200
        unlockedStoryStreak = 3
        unlockedHardStreak = 3
        dataCoinsKB = 999_999_999_999L // ~1 PB (Petabyte)
        totalData = (dataCoinsKB / 1024L)
        
        prefs.edit()
            .putInt("maxCampaignLevel", maxCampaignLevel)
            .putInt("unlockStory", unlockedStoryStreak)
            .putInt("unlockHard", unlockedHardStreak)
            .putLong("dataCoinsKB", dataCoinsKB)
            .putLong("totalData", totalData)
            .apply()
        
        UpgradeSystem.debugMaxAll()
    }

    fun clearAllData() {
        dataCoinsKB = 0L
        totalData = 0L
        maxCampaignLevel = 1
        isAutoNextLevelEnabled = false
        currentStoryStreak = 0
        unlockedStoryStreak = 0
        currentHardStreak = 0
        unlockedHardStreak = 0
        highScore = 0L
        previousScore = 0L

        prefs.edit().clear().apply()
        UpgradeSystem.clearAllData()
    }
}