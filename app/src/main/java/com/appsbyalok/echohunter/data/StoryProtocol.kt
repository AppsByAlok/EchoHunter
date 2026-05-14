package com.appsbyalok.echohunter.data

import com.appsbyalok.echohunter.R
import kotlin.random.Random

object StoryProtocol {

    var currentPopupRes: Int = 0
    var currentPopupText: String? = null
    var currentPopupArg: Int? = null
    var popupTimer: Float = 0f
    var isGlitchActive: Boolean = false
    var areControlsInverted: Boolean = false

    fun showIngameMessage(resId: Int, duration: Float = 3f) {
        currentPopupRes = resId
        currentPopupText = null
        currentPopupArg = null
        popupTimer = duration
    }

    fun showIngameMessage(text: String, duration: Float = 3f) {
        currentPopupRes = 0
        currentPopupText = text
        popupTimer = duration
    }

    fun update(dt: Float) {
        if (popupTimer > 0f) popupTimer -= dt
        if (bossIntroTimer > 0f) bossIntroTimer -= dt
    }

    var bossIntroTimer: Float = 0f
    var currentBossNameRes: Int = 0

    fun startBossIntro(bossType: Int) {
        bossIntroTimer = 4f
        currentBossNameRes = when (bossType) {
            0 -> R.string.boss_0
            1 -> R.string.boss_1
            2 -> R.string.boss_2
            3 -> R.string.boss_3
            else -> R.string.boss_omega
        }
    }

    // --- Story Line Arrays ---
    val storyIntroLines = intArrayOf(
        R.string.story_main_1, R.string.story_main_2,
        R.string.story_main_3, R.string.story_main_4
    )
    val storyMidLines = intArrayOf(
        R.string.story_mid_1, R.string.story_mid_2, R.string.story_mid_3
    )
    val storyPerfectEnding = intArrayOf(
        R.string.story_perfect_1, R.string.story_perfect_2,
        R.string.story_perfect_3, R.string.story_perfect_4, R.string.story_perfect_5
    )
    val storyNeutralEnding = intArrayOf(
        R.string.story_neutral_1, R.string.story_neutral_2,
        R.string.story_neutral_3, R.string.story_neutral_4
    )
    val firewallIntroLines = intArrayOf(
        R.string.story_firewall_1, R.string.story_firewall_2,
        R.string.story_firewall_3, R.string.story_firewall_4
    )
    val firewallPopups = intArrayOf(
        R.string.popup_firewall_1, R.string.popup_firewall_2,
        R.string.popup_firewall_3, R.string.popup_firewall_4
    )
    val badEndingLines = intArrayOf(
        R.string.story_bad_1, R.string.story_bad_2, R.string.story_bad_3
    )

    fun triggerRandomGlitch(score: Int, gameMode: Int, difficulty: Int) {
        if (score > 30 && Random.nextDouble() < 0.05) {
            isGlitchActive = true
            showIngameMessage("ADMIN: \"I FOUND YOUR IP.\"", 2f)
        } else {
            isGlitchActive = false
            areControlsInverted = false
        }

        // Inverse Controls Glitch (Admin tries to mess with the Hacker's controller)
        val isAPT = SaveManager.unlockedStoryStreak >= 3 || gameMode == 1
        val invertedChance = if (difficulty == 1 || isAPT) 0.15 else 0.02

        if (isGlitchActive && Random.nextDouble() < invertedChance) {
            areControlsInverted = true
            showIngameMessage("UPLINK INTERFERENCE: CONTROLS REVERSED", 2f)
        }
    }
}