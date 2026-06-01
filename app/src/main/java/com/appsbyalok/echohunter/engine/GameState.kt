package com.appsbyalok.echohunter.engine

import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.data.UpgradeSystem
import com.appsbyalok.echohunter.modes.CampaignMode
import com.appsbyalok.echohunter.modes.GameModeStrategy
import com.appsbyalok.echohunter.modes.StoryMode
import com.appsbyalok.echohunter.utils.EchoAudioManager
import kotlin.math.max
import kotlin.math.min

class GameState {

    var modeStrategy: GameModeStrategy = CampaignMode()
    var gameMode = 0
        set(value) {
            field = value
            modeStrategy = when (value) {
                1 -> StoryMode()
                else -> CampaignMode()
            }
        }

    var levelStartTime = 0f

    var state = 5
    var difficulty = 0
    var stateTimer = 0f
    var nextStateAfterStory = 0
    var timeSinceStart = 0f

    var isRotationWarning = false
    var selectedStoryAct = 0


    // --- GLOBAL SNACK BAR / TOAST SYSTEM ---
    var globalMessage = ""
    var globalMessageTimer = 0f

    fun showGlobalMessage(msg: String, duration: Float = 2f) {
        Log.d("TAG", "showGlobalMessage called: $msg")
        globalMessage = msg
        globalMessageTimer = duration
    }

    // --- NAYA: CENTRALIZED UI COORDINATES (100% RESPONSIVE MATCH) ---
    var uiBtnRadius = 0f
    var uiAtkX = 0f
    var uiAtkY = 0f
    var uiOvrX = 0f
    var uiOvrY = 0f
    var uiTrapX = 0f
    var uiTrapY = 0f
    var uiPulseX = 0f
    var uiPulseY = 0f
    var uiPauseX = 0f
    var uiPauseY = 0f

    // --- AUTOPILOT & DOUBLE TAP ---
    var isAutoPilotActive = false
    var autoPilotTimer = 0f
    var isAutoFireLocked = false
    var isAutoSonarLocked = false
    var isSonarPressed = false


    // NAYA: MOD MENU FLAGS
    var modGodMode = false
    var modInfiniteOvr = false

    // --- VISUAL DEBUGGER VARIABLES ---
    var showDebugHitboxes = false
    var lastTouchX = -100f
    var lastTouchY = -100f

    var gridMap: Array<IntArray>? = null
    var tileSize = 100f
    var mapWidth = 0f
    var mapHeight = 0f

    var px = 0f
    var py = 0f

    var joyBaseX = 0f
    var joyBaseY = 0f
    var joyKnobX = 0f
    var joyKnobY = 0f
    var joyDirX = 0f
    var joyDirY = 0f
    var isJoyActive = false
    var lastFacingX = 1f
    var lastFacingY = 0f

    val maxSpikes = 12
    val spikeX = FloatArray(maxSpikes)
    val spikeY = FloatArray(maxSpikes)
    val spikeVx = FloatArray(maxSpikes)
    val spikeVy = FloatArray(maxSpikes)
    val spikeLife = FloatArray(maxSpikes)
    val spikeActive = BooleanArray(maxSpikes)
    val spikeType = IntArray(maxSpikes)

    var currentWeapon = 1
    var currentTrap = 2   // 2 = EMP Mine, 1 = Decoy Hologram, 0 = Camouflage

    var defenseTimer = 0f
    var maxDefenseTimer = 0f
    var coreHp = 10
    var coreMaxHp = 10

    var escapeGateActive = false

    var isAttackPressed = false
    var isOverclockPressed = false
    var isTrapPressed = false

    var attackCooldown = 0f
    var trapCooldownTimer = 0f

    var globalSonarAlert = false
    var localAttackAlert = false

    var isCamouflaged = false
    var camoTimer = 0f
    var isDecoyActive = false
    var decoyX = 0f
    var decoyY = 0f
    var decoyTimer = 0f
    var empMineActive = false
    var empMineX = 0f
    var empMineY = 0f

    var hp = 3
    val maxHp: Int get() = 3 + UpgradeSystem.getBonusMaxHp()
    var isTouching = false

    var pulse = false
    var pulseR = 0f
    var cooldownTimer = 0f
    var visionClarity = 1.0f
    var shieldTimer = 0f
    var playerIframe = 0f

    var overclockMeter = 0f
    var overclockTimer = 0f
    var showOverclockTextTimer = 0f
    val isOverclocked: Boolean get() = overclockTimer > 0f

    var score = 0
    var combo = 0
    var wave = 1

    var currentLevel = 1
    var collectedDataKB = 0L
    var isLevelCleared = false
        set(value) {
            if (value && !field) {
                val totalDurationSeconds = timeSinceStart - levelStartTime
                val minutes = (totalDurationSeconds / 60).toInt()
                val seconds = (totalDurationSeconds % 60).toInt()

                val pilotType = if (isAutoPilotActive) "AUTOPILOT" else "MANUAL_PLAYER"
                val targetScore =
                    com.appsbyalok.echohunter.data.LevelEngine.getLevelConfig(currentLevel).targetScore

                // Android Studio Logcat logs
                Log.d(
                    "ECHO_HUNTER_PERF",
                    ">>> LEVEL $currentLevel CLEARED BY [$pilotType] | TIME ELAPSED: ${minutes}m ${seconds}s (${totalDurationSeconds} seconds) <<<"
                )
                Log.d(
                    "ECHO_HUNTER_PERF",
                    "Current Score: $score, Max Score: $targetScore, Combo: $combo, Wave: $wave, Sector: $currentSector, Current Level: $currentLevel , Current Time: ${System.currentTimeMillis()}"
                )
            }
            field = value
        }

    var comboBreakTimer = 0f
    var currentSector = 1
    var sectorTarget = 30

    var cameraX = 0f
    var cameraY = 0f
    var baseWorldSpeed = 0f

    var damageFlash = 0f
    var sectorFlash = 0f
    var shakeAmount = 0f
    var empFlashTimer = 0f
    var timeScale = 1.0f
    var slowMoTimer = 0f

    var hitStopTimer = 0f

    var isPerfectEnd = false
    var coreX = 0f
    var coreY = 0f
    var coreRadius = 0f
    var mergeTimer = 0f
    var whiteFlash = 0f

    var chromaticIntensity = 0f
    var shockwaveR = 0f
    var shockwaveX = 0f
    var shockwaveY = 0f
    var shockwaveActive = false

    var bossDeathTimer = 0f
    var bossDeathX = 0f
    var bossDeathY = 0f
    var isBossRage = false

    var isEnemyNear = false
    var isEnemyVeryNear = false
    var radarPingTimer = 0f
    var heartbeatTimer = 0f

    var bossActive = false
    var bossHp = 0
    var bossMaxHp = 0
    var bossX = -1000f
    var bossY = -1000f
    var bossIframe = 0f
    var bossType = 0
    var bossVis = 1.0f

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
        b.putFloat("cameraY", cameraY)
        b.putBoolean("bossActive", bossActive)
        b.putInt("bossType", bossType)
        b.putInt("bossHp", bossHp)
        b.putInt("bossMaxHp", bossMaxHp)
        b.putFloat("bossVis", bossVis)
        b.putInt("currentLevel", currentLevel)
        b.putLong("collectedDataKB", collectedDataKB)
        b.putBoolean("isPerfectEnd", isPerfectEnd)
        b.putFloat("coreX", coreX)
        b.putFloat("coreY", coreY)
        b.putFloat("mergeTimer", mergeTimer)
        b.putInt("coreHp", coreHp)
        b.putInt("coreMaxHp", coreMaxHp)
        b.putBoolean("escapeGateActive", escapeGateActive)
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
        cameraY = b.getFloat("cameraY", 0f)
        bossActive = b.getBoolean("bossActive", false)
        bossType = b.getInt("bossType", 0)
        bossHp = b.getInt("bossHp", 0)
        bossMaxHp = b.getInt("bossMaxHp", 0)
        bossVis = b.getFloat("bossVis", 1.0f)
        currentLevel = b.getInt("currentLevel", 1)
        collectedDataKB = b.getLong("collectedDataKB", 0L)
        isPerfectEnd = b.getBoolean("isPerfectEnd", false)
        coreX = b.getFloat("coreX", 0f)
        coreY = b.getFloat("coreY", 0f)
        mergeTimer = b.getFloat("mergeTimer", 0f)
        coreHp = b.getInt("coreHp", 10)
        coreMaxHp = b.getInt("coreMaxHp", 10)
        escapeGateActive = b.getBoolean("escapeGateActive", false)
    }

    fun resetGame() {
        score = 0; combo = 0; wave = 1
        hp = maxHp
        collectedDataKB = 0L
        isLevelCleared = false

        visionClarity = 1.0f; shieldTimer = 0f; playerIframe = 0f
        overclockMeter = 0f; overclockTimer = 0f
        cameraX = 0f
        cameraY = 0f
        currentSector = 1; sectorTarget = 30; bossActive = false
        empFlashTimer = 0f; comboBreakTimer = 0f

        attackCooldown = 0f
        trapCooldownTimer = 0f
        isCamouflaged = false
        isDecoyActive = false
        empMineActive = false

        for (i in 0 until maxSpikes) spikeActive[i] = false

        StoryProtocol.popupTimer = 0f
        StoryProtocol.isGlitchActive = false
        StoryProtocol.areControlsInverted = false
        isRotationWarning = false

        coreHp = 10
        coreMaxHp = 10

        escapeGateActive = false

        timeScale = 1.0f
        slowMoTimer = 0f
        hitStopTimer = 0f
        whiteFlash = 0f
        mergeTimer = 0f
        chromaticIntensity = 0f
        shockwaveActive = false
        bossDeathTimer = 0f
        bossVis = 1.0f

        // FIX: RESET JOYSTICK
        isJoyActive = false
        joyDirX = 0f
        joyDirY = 0f
        joyBaseX = 0f
        joyBaseY = 0f
        joyKnobX = 0f
        joyKnobY = 0f
        isAttackPressed = false
        isOverclockPressed = false
        isTrapPressed = false
        isSonarPressed = false

        isAutoFireLocked = false
        isAutoSonarLocked = false

        levelStartTime = timeSinceStart
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
        if (attackCooldown > 0f) attackCooldown -= dt
        if (defenseTimer > 0f) defenseTimer -= dt

        if (visionClarity < 1.0f) visionClarity = min(1.0f, visionClarity + 0.1f * dt)
        else if (visionClarity > 1.0f) visionClarity = max(1.0f, visionClarity - 0.1f * dt)

        if (chromaticIntensity > 0f) chromaticIntensity = max(0f, chromaticIntensity - dt * 2f)
        if (shockwaveActive) {
            shockwaveR += scale * 3f * dt
            if (shockwaveR > scale * 1.5f) shockwaveActive = false
        }

        if (overclockTimer > 0f) {
            overclockTimer -= dt
            val maxOcTime = 5f + UpgradeSystem.getBonusOverclockTime()
            overclockMeter = (overclockTimer / maxOcTime) * 100f
            if (overclockTimer <= 0f) EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_PIP, 100)
        } else if (overclockMeter > 0f && overclockMeter < 100f) {
            val drainSpeed = if (difficulty == 0) 5f else 10f
            overclockMeter = max(0f, overclockMeter - drainSpeed * dt)
        }
    }

    fun updatePulseRadius(dt: Float, maxRad: Float) {
        if (pulse) {
            pulseR += maxRad * 2.5f * dt
            if (pulseR > maxRad) {
                pulse = false; pulseR = 0f
            }
        }
    }

    fun updatePlayerMovement(dt: Float, width: Float, height: Float, scale: Float) {
        val baseSpeed = scale * (if (isOverclocked) 1.2f else 0.8f)
        val pSpeed = baseSpeed * UpgradeSystem.getSpeedMultiplier()

        if (joyDirX != 0f || joyDirY != 0f) {
            lastFacingX = joyDirX
            lastFacingY = joyDirY
        }

        var vx = joyDirX * pSpeed * dt
        var vy = joyDirY * pSpeed * dt

        if (StoryProtocol.areControlsInverted) {
            vx = -vx
            vy = -vy
        }

        val playerRadius = scale * 0.015f

        if (gridMap != null) {
            val nextPx = px + vx
            if (!isCollidingWithWall(nextPx, py, playerRadius)) px = nextPx

            val nextPy = py + vy
            if (!isCollidingWithWall(px, nextPy, playerRadius)) py = nextPy

            cameraX += ((px - width / 2f) - cameraX) * 5f * dt
            cameraY += ((py - height / 2f) - cameraY) * 5f * dt

            cameraX = max(0f, min(cameraX, mapWidth - width))
            cameraY = max(0f, min(cameraY, mapHeight - height))
        } else {
            px += vx
            py += vy
            if (px < cameraX + playerRadius) px = cameraX + playerRadius
            if (px > cameraX + width - playerRadius) px = cameraX + width - playerRadius
            if (py < cameraY + playerRadius) py = cameraY + playerRadius
            if (py > cameraY + height - playerRadius) py = cameraY + height - playerRadius
        }
    }

    private fun isCollidingWithWall(cx: Float, cy: Float, radius: Float): Boolean {
        val grid = gridMap ?: return false
        val ts = tileSize
        val hitbox = radius * 0.6f

        val left = ((cx - hitbox) / ts).toInt()
        val right = ((cx + hitbox) / ts).toInt()
        val top = ((cy - hitbox) / ts).toInt()
        val bottom = ((cy + hitbox) / ts).toInt()

        for (x in left..right) {
            for (y in top..bottom) {
                if (x < 0 || x >= grid.size || y < 0 || y >= grid[0].size) return true
                if (grid[x][y] == 1) return true
            }
        }
        return false
    }

    fun updateCameraAndMovement(dt: Float, width: Float, scale: Float) {
        modeStrategy.updateCameraAndMovement(dt, this, width, scale)
    }

    fun updateVisibilityMath(scale: Float, maxRad: Float) {
        val passiveAuraRadius = scale * 0.12f
        passiveAuraRadiusSq = passiveAuraRadius * passiveAuraRadius

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
}