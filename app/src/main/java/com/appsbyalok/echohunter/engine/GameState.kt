package com.appsbyalok.echohunter.engine

import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.data.UpgradeSystem
import com.appsbyalok.echohunter.modes.CampaignMode
import com.appsbyalok.echohunter.modes.GameModeStrategy
import com.appsbyalok.echohunter.modes.IGameObjective
import com.appsbyalok.echohunter.modes.StandardObjective
import com.appsbyalok.echohunter.modes.StoryMode
import com.appsbyalok.echohunter.utils.EchoAudioManager
import kotlin.math.max
import kotlin.math.min

class GameState {
    var activeObjective: IGameObjective = StandardObjective() // Current goal the player needs to fulfill
    var modeStrategy: GameModeStrategy = CampaignMode() // Logic handler for the active game mode
    var gameMode = 0 // Identifier for current game mode (0: Campaign/Archives, 1: Story)
        set(value) {
            field = value
            modeStrategy = when (value) {
                1 -> StoryMode()
                else -> CampaignMode()
            }
        }

    var levelStartTime = 0f // Timestamp of when the current level started

    var state = 5 // Current engine state (0: Menu, 1: Playing, 2: Pause, 3: Help, 4: GameOver Story, 5: Intro Story, 6: Ending Story, 7: Mid-story, 8: Core Merge, 9: Perfect End Zoom, 10: Decompiler, 11: Archives, 12: Victory, 13: Arsenal, 14: Nano-OS)
    var difficulty = 0 // Selected difficulty level (0: Normal/Recruit, 1: Hard/Elite)
    var stateTimer = 0f // General timer for state-specific durations
    var nextStateAfterStory = 0 // Target state to transition to after a story sequence
    var timeSinceStart = 0f // Total elapsed time since the game session began

    var isRotationWarning = false // Flag to show rotation-related UI warnings
    var selectedStoryAct = 0 // Index of the currently selected story chapter


    // --- GLOBAL SNACK BAR / TOAST SYSTEM ---
    var globalMessage = "" // Current message text to display in the global snackbar
    var globalMessageTimer = 0f // Remaining duration for the global message display

    fun showGlobalMessage(msg: String, duration: Float = 2f) {
        Log.d("TAG", "showGlobalMessage called: $msg")
        globalMessage = msg
        globalMessageTimer = duration
    }

    // --- NAYA: CENTRALIZED UI COORDINATES (100% RESPONSIVE MATCH) ---
    var uiBtnRadius = 0f // Hitbox radius for on-screen control buttons
    var uiAtkX = 0f // Screen X coordinate for the Attack button
    var uiAtkY = 0f // Screen Y coordinate for the Attack button
    var uiOvrX = 0f // Screen X coordinate for the Overclock button
    var uiOvrY = 0f // Screen Y coordinate for the Overclock button
    var uiTrapX = 0f // Screen X coordinate for the Trap button
    var uiTrapY = 0f // Screen Y coordinate for the Trap button
    var uiPulseX = 0f // Screen X coordinate for the Pulse/Sonar button
    var uiPulseY = 0f // Screen Y coordinate for the Pulse/Sonar button
    var uiPauseX = 0f // Screen X coordinate for the Pause button
    var uiPauseY = 0f // Screen Y coordinate for the Pause button

    // --- AUTOPILOT & DOUBLE TAP ---
    var isAutoPilotActive = false // Whether the AI is currently controlling player movement
    var autoPilotTimer = 0f // Duration or cooldown tracking for autopilot mode
    var isAutoFireLocked = false // If the weapon is set to fire automatically
    var isAutoSonarLocked = false // If sonar pulses are triggered automatically
    var isSonarPressed = false // Current input state of the sonar button


    // NAYA: MOD MENU FLAGS
    var modGodMode = false // Cheat flag for player invincibility
    var modInfiniteOvr = false // Cheat flag for unlimited overclock meter
    var modFullVisibility = false // Cheat flag to remove visibility restrictions (fog of war)

    // --- VISUAL DEBUGGER VARIABLES ---
    var lastTouchX = -100f // Last recorded raw touch X coordinate for debugging
    var lastTouchY = -100f // Last recorded raw touch Y coordinate for debugging

    var gridMap: Array<IntArray>? = null // 2D layout representing walls and walkable areas
    var tileSize = 100f // Size of each grid cell in world units
    var mapWidth = 0f // Total width of the current map
    var mapHeight = 0f // Total height of the current map

    var px = 0f // Player's current world X position
    var py = 0f // Player's current world Y position

    var joyBaseX = 0f // Static X position of the joystick base
    var joyBaseY = 0f // Static Y position of the joystick base
    var joyKnobX = 0f // Current X position of the interactive joystick knob
    var joyKnobY = 0f // Current Y position of the interactive joystick knob
    var joyDirX = 0f // Normalized X direction vector from the joystick
    var joyDirY = 0f // Normalized Y direction vector from the joystick
    var isJoyActive = false // Whether the user is currently interacting with the joystick
    var lastFacingX = 1f // The horizontal direction the player last moved towards
    var lastFacingY = 0f // The vertical direction the player last moved towards

    val maxSpikes = 12 // Maximum number of active projectiles (spikes) allowed
    val spikeX = FloatArray(maxSpikes) // X positions of projectiles
    val spikeY = FloatArray(maxSpikes) // Y positions of projectiles
    val spikeVx = FloatArray(maxSpikes) // X velocities of projectiles
    val spikeVy = FloatArray(maxSpikes) // Y velocities of projectiles
    val spikeLife = FloatArray(maxSpikes) // Remaining life/duration of projectiles
    val spikeActive = BooleanArray(maxSpikes) // Active status of projectile slots
    val spikeType = IntArray(maxSpikes) // Type identifier for different projectile effects

    var currentWeapon = 1 // Index of equipped weapon (0: Blaster, 1: Shotgun, 2: Sniper)
    var currentTrap = 2   // Index of selected trap (0: Camouflage, 1: Decoy, 2: EMP Mine)

    var defenseTimer = 0f // Countdown for the current defense objective phase
    var maxDefenseTimer = 0f // Total duration defined for the defense phase
    var coreHp = 10 // Current health of the core being defended
    var coreMaxHp = 10 // Maximum possible health of the core
    var defWaveCurrent = 1 // Current wave number in defense mode
    var defWaveMax = 1 // Total waves to survive in defense mode
    var defWaveState = 0 // Defense phase state (0: Buffer, 1: Active, 2: Cooldown)
    var defWaveTimer = 0f // Timer for defense wave transitions
    var defEnemiesToSpawn = 0 // Number of enemies remaining to spawn in the current wave
    var defEnemiesAlive = 0 // Number of active enemies currently in the defense arena

    var escapeGateActive = false // Whether the level exit portal is currently available

    var isAttackPressed = false // Input state for the attack command
    var isOverclockPressed = false // Input state for the overclock command
    var isTrapPressed = false // Input state for the trap command

    var attackCooldown = 0f // Time remaining before the next attack can be performed
    var trapCooldownTimer = 0f // Time remaining before the next trap can be deployed

    var globalSonarAlert = false // Flag for high-priority sonar-detected threats
    var localAttackAlert = false // Flag for immediate proximity threats

    var isCamouflaged = false // Whether the player is currently hidden from enemies
    var camoTimer = 0f // Remaining duration for the camouflage effect
    var isDecoyActive = false // Whether a decoy hologram is currently active
    var decoyX = 0f // World X position of the active decoy
    var decoyY = 0f // World Y position of the active decoy
    var decoyTimer = 0f // Remaining duration for the decoy effect
    var empMineActive = false // Whether an EMP mine has been deployed
    var empMineX = 0f // World X position of the EMP mine
    var empMineY = 0f // World Y position of the EMP mine

    var hp = 3 // Current health points of the player
    val maxHp: Int get() = 3 + UpgradeSystem.getBonusMaxHp() // Derived maximum health including upgrades
    var isTouching = false // General flag for any screen touch interaction

    var pulse = false // Whether a sonar pulse is currently propagating
    var pulseR = 0f // Current radius of the expanding sonar pulse
    var cooldownTimer = 0f // General purpose timer for ability cooldowns
    var visionClarity = 1.0f // Visual factor affecting how much of the map is visible
    var shieldTimer = 0f // Remaining duration of active invulnerability shield
    var playerIframe = 0f // Temporary invincibility period after being hit

    var overclockMeter = 0f // Current charge percentage of the overclock ability
    var overclockTimer = 0f // Remaining duration of the active overclock state
    var showOverclockTextTimer = 0f // Timer for displaying the "OVERCLOCK" UI announcement
    val isOverclocked: Boolean get() = overclockTimer > 0f // Derived status of being in overclock mode

    var score = 0 // Player's total accumulated score
    var combo = 0 // Current streak of consecutive hits or actions
    var wave = 1 // Current wave number in survival or campaign phases

    var currentLevel = 1 // Index of the level being played
    var collectedDataKB = 0L // Amount of narrative "data" resource collected
    var isLevelCleared = false // Flag indicating if the level objective is complete
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

    var comboBreakTimer = 0f // Grace period before the combo counter resets
    var currentSector = 1 // Current subsection of the level (e.g., Sector 1 of 3)
    var sectorTarget = 30 // Objective target required to clear the current sector

    var cameraX = 0f // Current X offset of the game camera
    var cameraY = 0f // Current Y offset of the game camera

    var damageFlash = 0f // Visual effect timer for screen flash when taking damage
    var sectorFlash = 0f // Visual effect timer for sector transitions
    var shakeAmount = 0f // Intensity of the camera shake effect
    var empFlashTimer = 0f // Timer for the visual flash triggered by EMPs
    var timeScale = 1.0f // Global speed multiplier for game logic (e.g., 0.5f for half-speed)
    var slowMoTimer = 0f // Remaining duration for slow-motion effects

    var hitStopTimer = 0f // Duration to freeze the game momentarily for impact feedback

    var isPerfectEnd = false // Flag if the level was completed without taking damage
    var coreX = 0f // Target X position for the end-level core sequence
    var coreY = 0f // Target Y position for the end-level core sequence
    var coreRadius = 0f // Visual radius of the end-level core
    var mergeTimer = 0f // Timer for the level-completion "merging" cinematic
    var whiteFlash = 0f // Intensity of the screen-clearing white flash effect

    var chromaticIntensity = 0f // Intensity of the chromatic aberration post-processing effect
    var shockwaveR = 0f // Current radius of a visual shockwave effect
    var shockwaveX = 0f // World X origin of a shockwave
    var shockwaveY = 0f // World Y origin of a shockwave
    var shockwaveActive = false // Whether a shockwave effect is currently being rendered

    var bossDeathTimer = 0f // Timer for the boss's defeat cinematic
    var bossDeathX = 0f // World X position where the boss was defeated
    var bossDeathY = 0f // World Y position where the boss was defeated
    var isBossRage = false // Whether the boss is in its high-intensity "rage" phase

    var isEnemyNear = false // Proximity flag for general enemy presence
    var isEnemyVeryNear = false // Proximity flag for immediate enemy threats
    var radarPingTimer = 0f // Timer for the periodic radar UI pulse
    var heartbeatTimer = 0f // Timer for the proximity-based audio "heartbeat" effect

    var bossActive = false // Whether a boss is currently present in the level
    var bossHp = 0 // Current health points of the boss
    var bossMaxHp = 0 // Maximum possible health of the boss
    var bossX = -1000f // World X position of the boss
    var bossY = -1000f // World Y position of the boss
    var bossIframe = 0f // Boss's temporary invulnerability period
    var bossType = 0 // Identifier for the type of boss encountered
    var bossVis = 1.0f // Visual alpha/visibility factor for the boss

    var innerRSq = 0f // Squared inner radius for optimized shader visibility calculations
    var outerRSq = 0f // Squared outer radius for optimized shader visibility calculations
    var passiveAuraRadiusSq = 0f // Squared radius of the player's permanent visibility light
    var fadeMultiplier = 1f // Overall ambient darkness factor applied to the scene

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

            // FIX: Ensure player is strictly clamped to Map Bounds
            px = max(playerRadius, min(px, mapWidth - playerRadius))
            py = max(playerRadius, min(py, mapHeight - playerRadius))
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

    fun updateCameraAndMovement(dt: Float, width: Float, height: Float, scale: Float) {
        modeStrategy.updateCameraAndMovement(dt, this, width, height, scale)
    }

    fun updateVisibilityMath(scale: Float, maxRad: Float) {
        val passiveAuraRadius = if (modFullVisibility) scale * 100f else scale * 0.12f
        passiveAuraRadiusSq = passiveAuraRadius * passiveAuraRadius

        val baseFade = if (difficulty == 1) 0.65f else 0.85f
        fadeMultiplier = if (modFullVisibility) 1.0f else min(0.99f, baseFade + 0.16f * visionClarity)

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
