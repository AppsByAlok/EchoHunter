package com.appsbyalok.echohunter

import android.content.Context
import android.content.SharedPreferences

/**
 * SaveManager: Echo Hunter ka persistent memory hub.
 * for 120fps performance use .apply() so main thread block na ho.
 */

object SaveManager {
    private lateinit var prefs: SharedPreferences

    var totalData: Int = 0
        private set

    // --- New Score Tracking Variables ---
    var highScore: Int = 0
        private set
    var previousScore: Int = 0
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("EchoSaveInfo", Context.MODE_PRIVATE)
        totalData = prefs.getInt("totalData", 0)

        // Load the scores when app starts
        highScore = prefs.getInt("highScore", 0)
        previousScore = prefs.getInt("previousScore", 0)
    }

    fun addData(amount: Int) {
        totalData += amount
        prefs.edit().putInt("totalData", totalData).apply()
    }

    // --- New Function to Handle Run End ---
    fun saveRunResult(currentScore: Int) {
        previousScore = currentScore

        // Check if the current score beats the high score
        if (currentScore > highScore) {
            highScore = currentScore
        }

        // Save both to disk at the same time
        prefs.edit()
            .putInt("previousScore", previousScore)
            .putInt("highScore", highScore)
            .apply()
    }
}