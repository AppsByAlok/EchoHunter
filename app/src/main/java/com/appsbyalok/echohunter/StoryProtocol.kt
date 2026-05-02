package com.appsbyalok.echohunter

import kotlin.random.Random

object StoryProtocol {

    // --- In Game popup system ---
    var currentPopupRes: Int = 0
    var currentPopupArg: Int? = null
    var popupTimer: Float = 0f
    var isGlitchActive: Boolean = false
    var areControlsInverted: Boolean = false

    fun showIngameMessage(resId: Int, duration: Float = 3f) {
        currentPopupRes = resId
        currentPopupArg = null
        popupTimer = duration
    }

    fun update(dt: Float) {
        if (popupTimer > 0f) {
            popupTimer -= dt
        }
        if (bossIntroTimer > 0f) {
            bossIntroTimer -= dt
        }
    }

    // --- Cinematic Intro for Boss ---
    var bossIntroTimer: Float = 0f
    var currentBossNameRes: Int = 0

    fun startBossIntro(bossType: Int) {
        currentBossNameRes = when(bossType) {
            0 -> R.string.boss_0
            1 -> R.string.boss_1
            2 -> R.string.boss_2
            3 -> R.string.boss_3
            else -> R.string.boss_omega
        }
        bossIntroTimer = 2.5f // 2.5 सेकंड के लिए गेम फ्रीज होगा
    }

    // --- Story Lines for each Mode ---

    // 1. ENDLESS WAVES (Survival)
    val endlessIntroLines = intArrayOf(
        R.string.story_endless_1,
        R.string.story_endless_2,
        R.string.story_endless_3,
        R.string.story_endless_4
    )
    val endlessPopups = intArrayOf(
        R.string.popup_endless_1,
        R.string.popup_endless_2,
        R.string.popup_endless_3,
        R.string.popup_endless_4
    )

    // 2. STORY MODE (Mainframe Salvation - Canon Story)
    val storyIntroLines = intArrayOf(
        R.string.story_main_1,
        R.string.story_main_2,
        R.string.story_main_3,
        R.string.story_main_4
    )
    val storyMidLines = intArrayOf(
        R.string.story_mid_1,
        R.string.story_mid_2,
        R.string.story_mid_3
    )
    // Dark Twist Endings
    val storyPerfectEnding = intArrayOf(
        R.string.story_perfect_1,
        R.string.story_perfect_2,
        R.string.story_perfect_3,
        R.string.story_perfect_4,
        R.string.story_perfect_5
    )
    val storyNeutralEnding = intArrayOf(
        R.string.story_neutral_1,
        R.string.story_neutral_2,
        R.string.story_neutral_3,
        R.string.story_neutral_4
    )

    // 3. FIREWALL BREACH (Escape Protocol)
    val firewallIntroLines = intArrayOf(
        R.string.story_firewall_1,
        R.string.story_firewall_2,
        R.string.story_firewall_3,
        R.string.story_firewall_4
    )
    val firewallPopups = intArrayOf(
        R.string.popup_firewall_1,
        R.string.popup_firewall_2,
        R.string.popup_firewall_3,
        R.string.popup_firewall_4
    )

    val badEndingLines = intArrayOf(
        R.string.story_bad_1,
        R.string.story_bad_2,
        R.string.story_bad_3
    )

    // Function to trigger random glitches during the game (now checks difficulty)
    fun triggerRandomGlitch(score: Int, gameMode: Int, difficulty: Int) {
        if (score > 30 && Random.nextDouble() < 0.05) {
            isGlitchActive = true
            showIngameMessage(R.string.msg_glitch, 2f)
        } else {
            isGlitchActive = false
            areControlsInverted = false
        }

        // Invert controls during the story (HARD MODE ONLY)
        if (difficulty == 1 && gameMode == 1 && score > 100 && Random.nextDouble() < 0.1) {
            areControlsInverted = true
            showIngameMessage(R.string.msg_fighting_back, 3f)
        }
    }
}