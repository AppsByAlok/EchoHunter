package com.appsbyalok.echohunter.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale
import kotlin.math.max

object SaveManager {
    private lateinit var prefs: SharedPreferences

    var dataCoinsKB: Long = 0L
        private set

    var totalData: Int = 0
        private set

    var maxCampaignLevel: Int = 1
        private set

    var isAutoNextLevelEnabled: Boolean = false
        private set

    fun setAutoNextLevel(enabled: Boolean) {
        isAutoNextLevelEnabled = enabled
        prefs.edit().putBoolean("isAutoNextLevelEnabled", isAutoNextLevelEnabled).apply()
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

    var highScore: Int = 0
        private set
    var previousScore: Int = 0
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("EchoSaveInfo", Context.MODE_PRIVATE)

        dataCoinsKB = prefs.getLong("dataCoinsKB", 0L)
        totalData = prefs.getInt("totalData", 0)
        maxCampaignLevel = prefs.getInt("maxCampaignLevel", 1)
        isAutoNextLevelEnabled = prefs.getBoolean("isAutoNextLevelEnabled", false)

        // Load Streaks
        currentStoryStreak = prefs.getInt("currStory", 0)
        unlockedStoryStreak = prefs.getInt("unlockStory", 0)
        currentHardStreak = prefs.getInt("currHard", 0)
        unlockedHardStreak = prefs.getInt("unlockHard", 0)

        highScore = prefs.getInt("highScore", 0)
        previousScore = prefs.getInt("previousScore", 0)
    }

    fun addData(kbAmount: Long) {
        dataCoinsKB += kbAmount
        totalData = (dataCoinsKB / 1024L).toInt()
        prefs.edit()
            .putLong("dataCoinsKB", dataCoinsKB)
            .putInt("totalData", totalData)
            .apply()
    }

    fun spendData(kbCost: Long): Boolean {
        if (dataCoinsKB >= kbCost) {
            dataCoinsKB -= kbCost
            totalData = (dataCoinsKB / 1024L).toInt()
            prefs.edit()
                .putLong("dataCoinsKB", dataCoinsKB)
                .putInt("totalData", totalData)
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

    fun saveRunResult(currentScore: Int) {
        previousScore = currentScore
        if (currentScore > highScore) {
            highScore = currentScore
        }
        prefs.edit()
            .putInt("previousScore", previousScore)
            .putInt("highScore", highScore)
            .apply()
    }

    fun formatDataString(kb: Long): String {
        if (kb < 1024L) return "${kb}KB"
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US,"%.1fMB", mb)
        val gb = mb / 1024.0
        if (gb < 1024.0) return String.format(Locale.US,"%.2fGB", gb)
        val tb = gb / 1024.0
        return String.format(Locale.US,"%.2fTB", tb)
    }

    fun debugSetLevel(level: Int) {
        maxCampaignLevel = max(1, level)
        prefs.edit().putInt("maxCampaignLevel", maxCampaignLevel).apply()
    }

    fun clearAllData() {
        dataCoinsKB = 0L
        totalData = 0
        maxCampaignLevel = 1
        isAutoNextLevelEnabled = false
        currentStoryStreak = 0
        unlockedStoryStreak = 0
        currentHardStreak = 0
        unlockedHardStreak = 0
        highScore = 0
        previousScore = 0

        // 1. SharedPreferences of SaveManager clear karein
        prefs.edit().clear().apply()

        // 2. Upgrade System ko link karke uski storage ko bhi wipe karein
        UpgradeSystem.clearAllData()
    }
}