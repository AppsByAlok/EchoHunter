package com.appsbyalok.echohunter.modes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.appsbyalok.echohunter.R
import com.appsbyalok.echohunter.data.LevelEngine
import com.appsbyalok.echohunter.data.LevelFeature
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.GameColors
import kotlin.math.max
import kotlin.random.Random

// Game Mode 0: The Sandbox Simulation (Act 1: First Contact)
class CampaignMode : GameModeStrategy {
    override val modeId = 0
    private var lastLevel = -1
    private var levelStr = ""

    private var bossSpawnedForLevel = -1

    // Meta-Narrative: Player intercepts the drone signal
    private val sandboxIntroLines = intArrayOf(
        R.string.lore_sandbox_1, // "> UNKNOWN SIGNAL INTERCEPTED..."
        R.string.lore_sandbox_2, // "> CONNECTING TO MAINTENANCE DRONE: PROBE-7..."
        R.string.lore_sandbox_3  // "> UPLINK ESTABLISHED. MANUAL CONTROL ACTIVE."
    )

    override fun getIntroLines() = sandboxIntroLines

    override fun updateCameraAndMovement(dt: Float, gs: GameState, width: Float, height: Float, scale: Float) {
        val screenPx = gs.px - gs.cameraX
        val screenPy = gs.py - gs.cameraY

        // --- Smooth 2D Camera Tracking Logic ---
        val lerpFactor = 5f * dt

        // Horizontal Tracking (X) - 40/60 window
        if (screenPx > width * 0.6f) gs.cameraX += (screenPx - width * 0.6f) * lerpFactor
        else if (screenPx < width * 0.4f) gs.cameraX += (screenPx - width * 0.4f) * lerpFactor

        // Vertical Tracking (Y) - Portrait Safe
        if (screenPy > height * 0.6f) gs.cameraY += (screenPy - height * 0.6f) * lerpFactor
        else if (screenPy < height * 0.4f) gs.cameraY += (screenPy - height * 0.4f) * lerpFactor

        // Boundaries Clamp (Uses dynamic height now)
        gs.cameraX = gs.cameraX.coerceIn(0f, max(0f, gs.mapWidth - width))
        gs.cameraY = gs.cameraY.coerceIn(0f, max(0f, gs.mapHeight - height))
    }

    override fun checkProgression(
        context: Context,
        gs: GameState, scale: Float,
        onTriggerBoss: (Int, Float) -> Unit,
        onSetStory: (IntArray, Int) -> Unit
    ) {
        if (gs.currentLevel == Int.MAX_VALUE) {
            StoryProtocol.showIngameMessage(R.string.lore_system_overflow, 10f)
            gs.score = 0
            return
        }

        val config = LevelEngine.getLevelConfig(gs.currentLevel)

        // Stop spawning if level is already cleared
        if (gs.isLevelCleared) return

        // --- MODULAR PROGRESSION ---
        // Use the objective's trigger logic to spawn the boss
        if (gs.activeObjective.isBossTriggerReady(gs)) {
            if (config.features.contains(LevelFeature.BOSS) && !gs.bossActive && bossSpawnedForLevel != gs.currentLevel) {
                bossSpawnedForLevel = gs.currentLevel
                val bossType = Random.nextInt(0, 5)
                onTriggerBoss(bossType, scale)
            }
        }
    }

    override fun getEnemySpawnPosition(gs: GameState, width: Float, height: Float, scale: Float): Pair<Float, Float> {
        val x = if (Random.nextBoolean()) gs.cameraX + Random.nextFloat() * width else gs.cameraX + if (Random.nextBoolean()) -scale * 0.1f else width + scale * 0.1f
        val y = if (Random.nextBoolean()) -scale * 0.1f else height + scale * 0.1f
        return Pair(x, y)
    }

    override fun drawModeSpecificWorld(c: Canvas, gs: GameState, width: Float, height: Float, scale: Float, p: Paint) {
        // Can be expanded to include specific lore-based background graphics
    }

    override fun drawModeSpecificHUD(context: Context, c: Canvas, gs: GameState, width: Float, height: Float, scale: Float, pText: Paint) {
        val config = LevelEngine.getLevelConfig(gs.currentLevel)

        // Level text logic
        if (gs.currentLevel != lastLevel) {
            levelStr = "SECURITY RING ${gs.currentLevel}"
            lastLevel = gs.currentLevel
        }

        val topMargin = scale * 0.06f
        val centerY = topMargin + scale * 0.02f

        // Top Header
        pText.color = GameColors.CLARITY
        pText.textSize = scale * 0.04f
        c.drawText(levelStr, width / 2f, centerY, pText)

        // --- NAYA: DYNAMIC SUB-HEADER (TARGETS) ---
        pText.textSize = scale * 0.03f
        pText.color = if (gs.bossActive) GameColors.RED else GameColors.YELLOW

        val subText = when {
            gs.bossActive -> "ADMIN SECURITY ACTIVE"
            gs.escapeGateActive -> "EXIT PORTAL ACTIVE"
            config.features.contains(LevelFeature.ELIMINATION) -> "TARGETS: ${gs.elimTargetsKilled} / ${gs.elimTargetsRequired}"
            else -> "TARGET: ${config.targetScore} KB"
        }

        if (gs.bossActive) pText.setShadowLayer(10f, 0f, 0f, GameColors.RED)
        c.drawText(subText, width / 2f, centerY + scale * 0.04f, pText)
        pText.clearShadowLayer()
    }
}