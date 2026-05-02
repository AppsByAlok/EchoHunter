package com.appsbyalok.echohunter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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

        if (!gs.isTouching) gs.targetPx = gs.px - gs.cameraX

        // Start se Firewall ka creep
        val baseCreep = if (gs.difficulty == 0) 0.015f else 0.035f
        var fwWorldSpeed = currentScrollSpeed + (scale * baseCreep)
        if (gs.difficulty == 1 && gs.timeSinceStart % 10f > 8f) fwWorldSpeed += scale * 0.2f

        gs.firewallWorldX += fwWorldSpeed * dt
        if (gs.firewallWorldX < gs.cameraX - width * 0.8f) gs.firewallWorldX = gs.cameraX - width * 0.8f
        gs.firewallOffset = gs.firewallWorldX - gs.cameraX
    }

    override fun checkProgression(
        context: Context,
        gs: GameState, scale: Float,
        onTriggerBoss: (Int, Float) -> Unit,
        onSetStory: (IntArray, Int) -> Unit
    ) {
        if (gs.score > 0 && gs.score % 100 == 0) {
            StoryProtocol.showIngameMessage(StoryProtocol.firewallPopups[Random.nextInt(StoryProtocol.firewallPopups.size)])
        }
    }

    override fun getEnemySpawnPosition(gs: GameState, width: Float, height: Float, scale: Float): Pair<Float, Float> {
        // Firewall mode mein enemies only aage (right side) se spawn hoti hain
        val x = gs.cameraX + width + Random.nextFloat() * (width * 0.5f)
        val y = Random.nextFloat() * height
        return Pair(x, y)
    }

    override fun drawModeSpecificWorld(c: Canvas, gs: GameState, width: Float, height: Float, scale: Float, p: Paint) {
        // Drawing the red wavy Firewall
        firewallPath.reset()
        firewallPath.moveTo(0f, 0f)
        var yPos = 0f
        while (yPos <= height) {
            val waveOffset = sin(yPos * 0.02f + gs.timeSinceStart * 10f) * (scale * 0.05f)
            firewallPath.lineTo(gs.firewallOffset + waveOffset, yPos)
            yPos += 20f
        }
        firewallPath.lineTo(0f, height)
        firewallPath.close()

        p.style = Paint.Style.FILL
        p.color = 0xAAFF0000.toInt()
        c.drawPath(firewallPath, p)

        // Draw Obstacles (Pillars)
        for (i in 0 until gs.obsCount) {
            val screenObsX = gs.obsX[i] - gs.cameraX
            val obsW = scale * 0.05f
            val isDanger = gs.obsType[i] == 1

            p.color = if (isDanger) (if (gs.difficulty == 1) 0xFF330A0A.toInt() else GameColors.GRID) else 0xFF0A330A.toInt()
            c.drawRect(screenObsX, 0f, screenObsX + obsW, gs.obsGapY[i], p)
            c.drawRect(screenObsX, gs.obsGapY[i] + gs.obsGapSize[i], screenObsX + obsW, height, p)

            p.color = if (isDanger) GameColors.RED else GameColors.HP
            c.drawRect(screenObsX, gs.obsGapY[i] - 10f, screenObsX + obsW, gs.obsGapY[i], p)
            c.drawRect(screenObsX, gs.obsGapY[i] + gs.obsGapSize[i], screenObsX + obsW, gs.obsGapY[i] + gs.obsGapSize[i] + 10f, p)
        }
    }

    override fun drawModeSpecificHUD(context: Context, c: Canvas, gs: GameState, width: Float, height: Float, scale: Float, pText: Paint) {
        // Firewall mode uses minimal HUD (score/health handled by common HUD logic)
    }
}