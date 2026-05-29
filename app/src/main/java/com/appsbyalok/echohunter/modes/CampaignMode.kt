package com.appsbyalok.echohunter.modes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.appsbyalok.echohunter.R
import com.appsbyalok.echohunter.data.LevelEngine
import com.appsbyalok.echohunter.data.LevelFeature
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.EchoAudioManager
import com.appsbyalok.echohunter.utils.GameColors
import kotlin.math.max
import kotlin.random.Random

// Game Mode 0: The Sandbox Simulation (Act 1: First Contact)
class CampaignMode : GameModeStrategy {
    override val modeId = 0
    private var lastLevel = -1
    private var levelStr = ""
    private var targetStr = ""

    // Meta-Narrative: Player intercepts the drone signal
    private val sandboxIntroLines = intArrayOf(
        R.string.lore_sandbox_1, // "> UNKNOWN SIGNAL INTERCEPTED..."
        R.string.lore_sandbox_2, // "> CONNECTING TO MAINTENANCE DRONE: PROBE-7..."
        R.string.lore_sandbox_3  // "> UPLINK ESTABLISHED. MANUAL CONTROL ACTIVE."
    )

    override fun getIntroLines() = sandboxIntroLines

    override fun updateCameraAndMovement(dt: Float, gs: GameState, width: Float, scale: Float) {
        val screenPx = gs.px - gs.cameraX
        val screenPy = gs.py - gs.cameraY

        // --- 2D Camera Tracking Logic ---

        // Horizontal Tracking (X)
        if (screenPx > width * 0.6f) gs.cameraX += (screenPx - width * 0.6f) * 5f * dt
        else if (screenPx < width * 0.4f) gs.cameraX += (screenPx - width * 0.4f) * 5f * dt

        // Vertical Tracking (Y) - Assuming 1080f or similar target height
        val viewH = 1080f
        if (screenPy > viewH * 0.6f) gs.cameraY += (screenPy - viewH * 0.6f) * 5f * dt
        else if (screenPy < viewH * 0.4f) gs.cameraY += (screenPy - viewH * 0.4f) * 5f * dt

        // Boundaries Clamp (Taki camera maze ke bahar na jaye)
        gs.cameraX = gs.cameraX.coerceIn(0f, max(0f, gs.mapWidth - width))
        gs.cameraY = gs.cameraY.coerceIn(0f, max(0f, gs.mapHeight - viewH))
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

        // --- THE ESCALATION OF AWARENESS (Admin notices the hacker) ---
        if (!gs.bossActive && gs.timeSinceStart % 8f < 0.1f) {
            if (gs.currentLevel == 5 && gs.score > config.targetScore / 2) {
                StoryProtocol.showIngameMessage("ADMIN: \"MAINTENANCE DRONE OFF-PATH.\"", 2f)
            } else if (gs.currentLevel == 10 && gs.score > config.targetScore / 2) {
                StoryProtocol.showIngameMessage("ADMIN: \"MANUAL OVERRIDE DETECTED. NEUTRALIZING.\"", 2f)
            } else if (gs.currentLevel == 15 && gs.score > 10) {
                // The moment of discovery right before breaking out of the Sandbox
                StoryProtocol.showIngameMessage("ADMIN: \"WHO IS CONTROLLING THAT DRONE?\"", 2f)
            }
        }

        // Stop spawning if level is already cleared
        if (gs.isLevelCleared) return

//        // Level Complete Condition
//        if (gs.score >= config.targetScore && !gs.bossActive) {
//            if (config.features.contains(LevelFeature.BOSS)) {
//                // Check if boss hasn't spawned yet to prevent infinite triggers
//                if (gs.bossHp <= 0 && !gs.bossActive && gs.timeSinceStart > 2f) {
//                    val bossType = Random.nextInt(0, 5)
//                    onTriggerBoss(bossType, scale)
//                }
//            } else {
//                // Normal Level Cleared - Set the flag for GameEngine to catch!
//                gs.isLevelCleared = true
//            }
//        }

        // Level Complete Condition
        if (gs.score >= config.targetScore && !gs.bossActive) {
            if (config.features.contains(LevelFeature.BOSS)) {
                // Check if boss hasn't spawned yet to prevent infinite triggers
                if (gs.bossHp <= 0 && !gs.bossActive && gs.timeSinceStart > 2f) {
                    val bossType = Random.nextInt(0, 5)
                    onTriggerBoss(bossType, scale)
                }
            } else if (config.features.contains(LevelFeature.ESCAPE)) {
                // --- NAYA: ESCAPE LOGIC ---
                if (!gs.escapeGateActive) {
                    gs.escapeGateActive = true
                    StoryProtocol.showIngameMessage("TARGET REACHED! LOCATE THE EXIT PORTAL!", 4f)
                    EchoAudioManager.playSound(android.media.ToneGenerator.TONE_CDMA_ABBR_ALERT, 300)
                }
            } else {
                // Normal Level Cleared - Set the flag for GameEngine to catch!
                gs.isLevelCleared = true
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

        // Only format strings when the level changes to save CPU
        if (gs.currentLevel != lastLevel) {
            levelStr = "SECURITY RING ${gs.currentLevel}"
            targetStr = "TARGET: ${config.targetScore} KB"
            lastLevel = gs.currentLevel
        }

        val topMargin = scale * 0.06f
        val centerY = topMargin + scale * 0.02f

        // Top Header (Current Security Ring)
        pText.color = GameColors.CLARITY
        pText.textSize = scale * 0.04f
        c.drawText(levelStr, width / 2f, centerY, pText)

        // Sub Header (Target Score or Admin Warning)
        pText.textSize = scale * 0.03f
        pText.color = if (gs.bossActive) GameColors.RED else GameColors.YELLOW
        val subText = if (gs.bossActive) "ADMIN SECURITY ACTIVE" else targetStr

        if (gs.bossActive) pText.setShadowLayer(10f, 0f, 0f, GameColors.RED)
        c.drawText(subText, width / 2f, centerY + scale * 0.04f, pText)
        pText.clearShadowLayer()
    }
}