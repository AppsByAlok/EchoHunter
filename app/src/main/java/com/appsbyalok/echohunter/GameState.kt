package com.appsbyalok.echohunter

import android.os.Bundle
import kotlin.math.*

// Shared constants for colors
object GameColors {
    const val BG = 0xFF08080C.toInt()
    const val GRID = 0xFF141420.toInt()
    const val PULSE = 0xFF00FFFF.toInt()
    const val RED = 0xFFFF2A4D.toInt()
    const val YELLOW = 0xFFFFD700.toInt()
    const val TEXT = 0xFFEEEEEE.toInt()
    const val HP = 0xFF00FF7F.toInt()
    const val CLARITY = 0xFFFFFFFF.toInt()
    const val SHIELD = 0xFFAA00FF.toInt()
    const val OVERCLOCK = 0xFFFF5500.toInt()
    const val BOSS = 0xFFFF00FF.toInt()
    const val COOLANT = 0xFF00AAFF.toInt()
}

// Single Source of Truth for game data
class GameState {

    var modeStrategy: GameModeStrategy = EndlessMode()
    var gameMode = 0
        set(value) {
            field = value
            modeStrategy = when (value) {
                1 -> StoryMode()
                2 -> FirewallMode()
                else -> EndlessMode()
            }
        }

    // UI & App State
    var state = 5
    var difficulty = 0
    var stateTimer = 0f
    var nextStateAfterStory = 0
    var timeSinceStart = 0f

    // Player State
    var px = 0f; var py = 0f
    var targetPx = 0f; var targetPy = 0f
    var hp = 3; val maxHp = 3
    var isTouching = false

    // Abilities & Timers
    var pulse = false; var pulseR = 0f
    var cooldownTimer = 0f
    var visionClarity = 1.0f
    var shieldTimer = 0f
    var playerIframe = 0f

    var overclockMeter = 0f
    var overclockTimer = 0f
    var showOverclockTextTimer = 0f
    val isOverclocked: Boolean get() = overclockTimer > 0f

    // Stats & Progression
    var score = 0; var combo = 0
    var maxCombo = 0
    var wave = 1
    var comboBreakTimer = 0f
    var currentSector = 1
    var sectorTarget = 30

    // Camera & Environment
    var cameraX = 0f
    var baseWorldSpeed = 0f

    // Effects & Feedback Variables
    var damageFlash = 0f
    var sectorFlash = 0f
    var shakeAmount = 0f
    var empFlashTimer = 0f
    var timeScale = 1.0f
    var slowMoTimer = 0f

    // --- NEW: Hit-Stop Effect (Freezes game for micro-seconds on impact) ---
    var hitStopTimer = 0f

    // Interactive Ending Variables (Cinematic Core Merge)
    var isPerfectEnd = false
    var coreX = 0f
    var coreY = 0f
    var coreRadius = 0f
    var mergeTimer = 0f
    var whiteFlash = 0f

    // Visual Polish Variables (Cyberpunk Effects)
    var chromaticIntensity = 0f
    var shockwaveR = 0f
    var shockwaveX = 0f
    var shockwaveY = 0f
    var shockwaveActive = false

    // Boss Death Decompilation State
    var bossDeathTimer = 0f
    var bossDeathX = 0f
    var bossDeathY = 0f

    // Radar / Heartbeat Ping System
    var isEnemyNear = false
    var isEnemyVeryNear = false
    var radarPingTimer = 0f
    var heartbeatTimer = 0f

    // Firewall (Game Mode 2)
    var firewallWorldX = 0f
    var firewallOffset = -100f
    val obsCount = 4
    val obsX = FloatArray(obsCount)
    val obsGapY = FloatArray(obsCount)
    val obsGapSize = FloatArray(obsCount)
    val obsType = IntArray(obsCount)

    // Boss State
    var bossActive = false
    var bossHp = 0; var bossMaxHp = 0
    var bossX = -1000f; var bossY = -1000f
    var bossIframe = 0f
    var bossType = 0
    var bossVis = 1.0f // NEW: Boss visibility for hard mode dimming

    // Optimization Math variables
    var innerRSq = 0f
    var outerRSq = 0f
    var passiveAuraRadiusSq = 0f
    var fadeMultiplier = 1f

    fun saveState(b: Bundle) {
        b.putInt("state", state)
        b.putInt("difficulty", difficulty)
        b.putInt("score", score)
        b.putInt("gameMode", gameMode)
        b.putInt("hp", hp)
        b.putInt("nextStateAfterStory", nextStateAfterStory)
        b.putFloat("timeSinceStart", timeSinceStart)
        b.putInt("currentSector", currentSector)
        b.putInt("sectorTarget", sectorTarget)
        b.putInt("wave", wave)
        b.putFloat("cameraX", cameraX)
        b.putBoolean("bossActive", bossActive)
        b.putInt("bossType", bossType)
        b.putInt("bossHp", bossHp)
        b.putInt("bossMaxHp", bossMaxHp)
        b.putFloat("bossVis", bossVis)

        b.putBoolean("isPerfectEnd", isPerfectEnd)
        b.putFloat("coreX", coreX)
        b.putFloat("coreY", coreY)
        b.putFloat("mergeTimer", mergeTimer)
    }

    fun restoreState(b: Bundle) {
        state = b.getInt("state", 5)
        difficulty = b.getInt("difficulty", 0)
        score = b.getInt("score", 0)
        gameMode = b.getInt("gameMode", 0)
        hp = b.getInt("hp", 3)
        nextStateAfterStory = b.getInt("nextStateAfterStory", 0)
        timeSinceStart = b.getFloat("timeSinceStart", 0f)
        currentSector = b.getInt("currentSector", 1)
        sectorTarget = b.getInt("sectorTarget", 30)
        wave = b.getInt("wave", 1)
        cameraX = b.getFloat("cameraX", 0f)
        bossActive = b.getBoolean("bossActive", false)
        bossType = b.getInt("bossType", 0)
        bossHp = b.getInt("bossHp", 0)
        bossMaxHp = b.getInt("bossMaxHp", 0)
        bossVis = b.getFloat("bossVis", 1.0f)

        isPerfectEnd = b.getBoolean("isPerfectEnd", false)
        coreX = b.getFloat("coreX", 0f)
        coreY = b.getFloat("coreY", 0f)
        mergeTimer = b.getFloat("mergeTimer", 0f)
    }

    fun resetGame() {
        score = 0; combo = 0; hp = maxHp; wave = 1
        visionClarity = 1.0f; shieldTimer = 0f; playerIframe = 0f
        overclockMeter = 0f; overclockTimer = 0f
        cameraX = 0f; firewallWorldX = cameraX - 1000f
        currentSector = 1; sectorTarget = 30; bossActive = false
        empFlashTimer = 0f; comboBreakTimer = 0f

        StoryProtocol.popupTimer = 0f
        StoryProtocol.isGlitchActive = false
        StoryProtocol.areControlsInverted = false

        timeScale = 1.0f
        slowMoTimer = 0f
        hitStopTimer = 0f
        whiteFlash = 0f
        mergeTimer = 0f
        chromaticIntensity = 0f
        shockwaveActive = false
        bossDeathTimer = 0f
        bossVis = 1.0f
    }

    fun updateTimers(dt: Float, scale: Float) {
        if (playerIframe > 0f) playerIframe -= dt
        if (showOverclockTextTimer > 0f) showOverclockTextTimer -= dt
        if (comboBreakTimer > 0f) comboBreakTimer -= dt
        if (shieldTimer > 0f) shieldTimer -= dt
        if (bossIframe > 0f) bossIframe -= dt
        if (cooldownTimer > 0f) cooldownTimer -= dt
        if (empFlashTimer > 0f) empFlashTimer -= dt
        if (slowMoTimer > 0f) slowMoTimer -= dt
        if (bossDeathTimer > 0f) bossDeathTimer -= dt

        if (visionClarity < 1.0f) visionClarity = min(1.0f, visionClarity + 0.1f * dt)
        else if (visionClarity > 1.0f) visionClarity = max(1.0f, visionClarity - 0.1f * dt)

        if (chromaticIntensity > 0f) chromaticIntensity = max(0f, chromaticIntensity - dt * 2f)
        if (shockwaveActive) {
            shockwaveR += scale * 3f * dt
            if (shockwaveR > scale * 1.5f) shockwaveActive = false
        }

        if (overclockTimer > 0f) {
            overclockTimer -= dt
            overclockMeter = (overclockTimer / 5f) * 100f
            if (overclockTimer <= 0f) EchoAudioManager.playSound(android.media.ToneGenerator.TONE_CDMA_PIP, 100)
        } else if (overclockMeter > 0f) {
            val drainSpeed = if(difficulty == 0) 5f else 10f
            overclockMeter = max(0f, overclockMeter - drainSpeed * dt)
        }
    }

    fun updatePulseRadius(dt: Float, maxRad: Float) {
        if (pulse) {
            pulseR += maxRad * 2.5f * dt
            if (pulseR > maxRad) { pulse = false; pulseR = 0f }
        }
    }

    fun updatePlayerMovement(dt: Float, height: Float, scale: Float) {
        val targetWorldX = cameraX + targetPx
        val pdx = targetWorldX - px
        val pdy = targetPy - py
        val pDistSq = pdx * pdx + pdy * pdy
        val pSpeed = scale * (if (isOverclocked) 1.5f else 1.0f)
        val pSpeedDt = pSpeed * dt

        if (pDistSq > pSpeedDt * pSpeedDt) {
            val pDist = sqrt(pDistSq)
            px += (pdx / pDist) * pSpeedDt
            py += (pdy / pDist) * pSpeedDt
        } else {
            px = targetWorldX
            py = targetPy
        }

        val playerRadius = scale * 0.015f
        if (py < playerRadius) py = playerRadius
        if (py > height - playerRadius) py = height - playerRadius
    }

    fun updateCameraAndMovement(dt: Float, width: Float, scale: Float) {
        modeStrategy.updateCameraAndMovement(dt, this, width, scale)
    }

    fun updateVisibilityMath(scale: Float, maxRad: Float) {
        val passiveAuraRadius = scale * 0.12f
        passiveAuraRadiusSq = passiveAuraRadius * passiveAuraRadius

        // Hard mode mein enemies FAST disappear, Easy mein slow
        val baseFade = if (difficulty == 1) 0.65f else 0.85f
        fadeMultiplier = min(0.99f, baseFade + 0.16f * visionClarity)

        val echoThickness = maxRad * 0.05f
        if (pulse) {
            val innerR = max(0f, pulseR - echoThickness)
            val outerR = pulseR + echoThickness
            innerRSq = innerR * innerR
            outerRSq = outerR * outerR
        } else {
            innerRSq = 0f
            outerRSq = 0f
        }
    }

    fun getNextObstacleX(width: Float): Float {
        var maxX = cameraX + width
        for (j in 0 until obsCount) {
            if (obsX[j] > maxX) maxX = obsX[j]
        }
        return maxX + (width * 0.6f)
    }

    fun randomizeObstacle(i: Int, height: Float) {
        val diffMult = if (difficulty == 0) 0.8f else 1.0f
        val gapBase = height * 0.4f
        obsGapSize[i] = max(height * (if(difficulty==0) 0.35f else 0.25f), gapBase - (score * 0.002f * height * diffMult))
        obsGapY[i] = (height * 0.1f) + kotlin.random.Random.nextFloat() * (height * 0.8f - obsGapSize[i])

        val baseRedChance = if (score < 15) 0.0 else (score - 15) * 0.02
        val maxRedChance = if (difficulty == 0) 0.3 else 0.8
        val redChance = min(maxRedChance, baseRedChance)
        obsType[i] = if (kotlin.random.Random.nextDouble() < redChance) 1 else 0
    }
}