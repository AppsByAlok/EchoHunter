package com.appsbyalok.echohunter.modes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.utils.GameColors
import com.appsbyalok.echohunter.engine.GameState
import kotlin.math.sin
import kotlin.random.Random

// Game Mode 2: Firewall Breach (Treadmill Escape)
class FirewallMode : GameModeStrategy {
    override val modeId = 2
    private val firewallPath = Path()

    override fun getIntroLines() = StoryProtocol.firewallIntroLines

    override fun updateCameraAndMovement(dt: Float, gs: GameState, width: Float, scale: Float) {
        val fwScrollMult = if (gs.difficulty == 0) 0.8f else 1.0f
        val currentScrollSpeed = gs.baseWorldSpeed + (gs.score * scale * 0.005f * fwScrollMult)
        gs.cameraX += currentScrollSpeed * dt

        val screenPx = gs.px - gs.cameraX
        if (screenPx < width * 0.1f) gs.px = gs.cameraX + width * 0.1f
        if (screenPx > width * 0.8f) gs.px = gs.cameraX + width * 0.8f

        // Start se Firewall ka creep
        val baseCreep = if (gs.difficulty == 0) 0.015f else 0.035f
        var fwWorldSpeed = currentScrollSpeed + (scale * baseCreep)
        if (gs.difficulty == 1 && gs.timeSinceStart % 10f > 8f) fwWorldSpeed += scale * 0.2f

        gs.firewallWorldX += fwWorldSpeed * dt
        if (gs.firewallWorldX < gs.cameraX - width * 0.8f) gs.firewallWorldX =
            gs.cameraX - width * 0.8f
        gs.firewallOffset = gs.firewallWorldX - gs.cameraX
    }

    override fun checkProgression(
        context: Context,
        gs: GameState, scale: Float,
        onTriggerBoss: (Int, Float) -> Unit,
        onSetStory: (IntArray, Int) -> Unit
    ) {
        // --- ADMIN PANIC POPUPS ---
        if (gs.score == 5 && !gs.bossActive) StoryProtocol.showIngameMessage("ADMIN: \"STOP RUNNING.\"", 3f)
        if (gs.score == 30 && !gs.bossActive) StoryProtocol.showIngameMessage("ADMIN: \"CPU AT 95%... TRACING IP.\"", 3f)
        if (gs.score == 70 && !gs.bossActive) StoryProtocol.showIngameMessage("ADMIN: \"WE HAVE YOUR MAC ADDRESS.\"", 3f)

        // Continuous tension for high scores
        if (gs.score > 100 && gs.score % 50 == 0) {
            StoryProtocol.showIngameMessage("WARNING: HARDWARE OVERHEATING", 2f)
        }
    }

    override fun getEnemySpawnPosition(
        gs: GameState,
        width: Float,
        height: Float,
        scale: Float,
    ): Pair<Float, Float> {
        val x = gs.cameraX + width + Random.nextFloat() * (width * 0.5f)
        val y = Random.nextFloat() * height
        return Pair(x, y)
    }

    override fun drawModeSpecificWorld(c: Canvas, gs: GameState, width: Float, height: Float, scale: Float, p: Paint) {
        // --- 1. THE DELETION WAVE (Glitchy Fire) ---
        firewallPath.reset()
        firewallPath.moveTo(0f, 0f)
        var yPos = 0f
        while (yPos <= height) {
            // Fast, chaotic wave calculation
            val waveOffset = sin(yPos * 0.05f + gs.timeSinceStart * 20f) * (scale * 0.08f) + Random.nextFloat() * (scale * 0.02f)
            firewallPath.lineTo(gs.firewallOffset + waveOffset, yPos)
            yPos += 20f
        }
        firewallPath.lineTo(0f, height)
        firewallPath.close()

        p.style = Paint.Style.FILL
        // Flickering fiery color (Orange/Red)
        val fireAlpha = 150 + Random.nextInt(105)
        p.color = (fireAlpha shl 24) or 0xFF2200
        c.drawPath(firewallPath, p)

        // --- 2. THE SERVER GATES (Obstacles) ---
        for (i in 0 until gs.obsCount) {
            val screenObsX = gs.obsX[i] - gs.cameraX
            val obsW = scale * 0.05f
            val isDanger = gs.obsType[i] == 1

            // Add slight random horizontal jitter to danger blocks
            val blockOffset = if (isDanger && Random.nextDouble() > 0.8) scale * 0.01f else 0f

            p.color = if (isDanger) 0xFF440000.toInt() else 0xFF0A330A.toInt()
            c.drawRect(screenObsX + blockOffset, 0f, screenObsX + obsW + blockOffset, gs.obsGapY[i], p)
            c.drawRect(screenObsX - blockOffset, gs.obsGapY[i] + gs.obsGapSize[i], screenObsX + obsW - blockOffset, height, p)

            // Neon Caps
            p.color = if (isDanger) GameColors.RED else GameColors.HP
            c.drawRect(screenObsX, gs.obsGapY[i] - 10f, screenObsX + obsW, gs.obsGapY[i], p)
            c.drawRect(screenObsX, gs.obsGapY[i] + gs.obsGapSize[i], screenObsX + obsW, gs.obsGapY[i] + gs.obsGapSize[i] + 10f, p)
        }
    }

    override fun drawModeSpecificHUD(
        context: Context,
        c: Canvas,
        gs: GameState,
        width: Float,
        height: Float,
        scale: Float,
        pText: Paint,
    ) {
    }
}