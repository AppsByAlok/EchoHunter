package com.appsbyalok.echohunter.view.renderers

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.appsbyalok.echohunter.R
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.systems.EffectSystem
import com.appsbyalok.echohunter.systems.EnemySystem
import com.appsbyalok.echohunter.utils.GameColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class WorldRenderer(
    private val context: Context,
    private val effectSys: EffectSystem,
    private val enemySys: EnemySystem
) {
    private val p = Paint().apply { isAntiAlias = true }
    private val pGlow = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
    private val pDash = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; color = 0x55FFFF00 }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val arrowPath = Path()
    private var lastCoreDist = -1
    private var coreDistStr = ""

    fun updateDashEffect(scale: Float) {
        pDash.pathEffect = DashPathEffect(floatArrayOf(scale * 0.05f, scale * 0.05f), 0f)
    }

    fun drawGrid(c: Canvas, scale: Float, gs: GameState, targetW: Float, targetH: Float) {
        p.color = if (gs.difficulty == 1) 0xFF330A0A.toInt() else GameColors.GRID
        p.strokeWidth = 2f
        val gap = scale / 8f
        val parallaxX = gs.cameraX * 0.5f
        val parallaxY = gs.cameraY * 0.5f

        val gridOffsetX = -(parallaxX % gap)
        val gridOffsetY = -(parallaxY % gap)

        var i = -gap + gridOffsetX
        while (i < targetW + gap) { c.drawLine(i, 0f, i, targetH, p); i += gap }
        var j = -gap + gridOffsetY
        while (j < targetH + gap) { c.drawLine(0f, j, targetW, j, p); j += gap }
    }

    fun drawCRTOverlay(c: Canvas, gs: GameState, targetW: Float, targetH: Float) {
        p.color = 0x22000000
        p.strokeWidth = 2f
        var yLine = (gs.timeSinceStart * 20f) % 8f
        while (yLine < targetH) {
            c.drawLine(0f, yLine, targetW, yLine, p)
            yLine += 8f
        }
    }

    fun drawMaze(c: Canvas, gs: GameState, scale: Float, targetW: Float, targetH: Float) {
        val grid = gs.gridMap ?: return
        val ts = gs.tileSize

        p.style = Paint.Style.FILL

        for (x in grid.indices) {
            for (y in grid[x].indices) {
                val drawX = x * ts - gs.cameraX
                val drawY = y * ts - gs.cameraY

                // Culling (Don't draw if outside screen)
                if (drawX < -ts || drawX > targetW || drawY < -ts || drawY > targetH) continue

                when (grid[x][y]) {
                    1 -> { // 2D NEON WALL RENDERING (Matches perfect collision)
                        // Inner fill
                        p.color = 0x2200FFFF.toInt() // Faint cyan background
                        c.drawRect(drawX, drawY, drawX + ts, drawY + ts, p)

                        // Glowing cyber border
                        p.style = Paint.Style.STROKE
                        p.strokeWidth = scale * 0.005f
                        p.color = GameColors.PULSE
                        val m = ts * 0.1f
                        c.drawRect(drawX + m, drawY + m, drawX + ts - m, drawY + ts - m, p)
                        p.style = Paint.Style.FILL // Reset
                    }
                    2 -> { // DESTINATION / CORE
                        p.color = GameColors.YELLOW
                        p.setShadowLayer(20f, 0f, 0f, GameColors.YELLOW)
                        val hover = sin(gs.timeSinceStart * 4f) * (scale * 0.01f)
                        c.drawCircle(drawX + ts/2, drawY + ts/2 + hover, ts * 0.3f, p)
                        p.clearShadowLayer()
                    }
                }
            }
        }
    }

    fun drawGamePlay(c: Canvas, scale: Float, gs: GameState, targetW: Float, targetH: Float) {
        val currentPlayerColor = if (gs.isOverclocked) GameColors.OVERCLOCK else GameColors.PULSE
        val screenPlayerX = gs.px - gs.cameraX
        val screenPlayerY = gs.py - gs.cameraY

        p.style = Paint.Style.FILL; p.color = 0x1A00FFFF
        c.drawCircle(screenPlayerX, screenPlayerY, scale * 0.12f + sin(gs.timeSinceStart * 3f) * scale * 0.01f, p)

        gs.modeStrategy.drawModeSpecificWorld(c, gs, targetW, targetH, scale, p)

        if (gs.pulse) {
            val alpha = (255 * (1f - (gs.pulseR / (targetW * 0.75f)))).toInt()
            val colorGlow = if (StoryProtocol.isGlitchActive) GameColors.RED else if (gs.isOverclocked) GameColors.OVERCLOCK else if (gs.visionClarity > 0.3f) GameColors.PULSE else 0xFF006666.toInt()
            pGlow.color = (max(0, alpha) shl 24) or (colorGlow and 0xFFFFFF)
            pGlow.strokeWidth = scale * 0.008f
            c.drawCircle(screenPlayerX, screenPlayerY, gs.pulseR, pGlow)
        }

        if (gs.shockwaveActive) {
            val screenShockX = gs.shockwaveX - gs.cameraX
            val screenShockY = gs.shockwaveY - gs.cameraY
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(2f, scale * 0.05f * (1f - (gs.shockwaveR / (scale * 1.5f))))
            p.color = GameColors.CLARITY
            c.drawCircle(screenShockX, screenShockY, gs.shockwaveR, p)
            p.style = Paint.Style.FILL
            p.color = 0x22FFFFFF
            c.drawCircle(screenShockX, screenShockY, gs.shockwaveR, p)
        }

        if (gs.state == 8 || gs.state == 9) {
            drawCore(c, scale, gs, targetW, screenPlayerX, screenPlayerY)
        }

        pGlow.color = GameColors.PULSE
        for (i in 0 until gs.maxSpikes) {
            if (gs.spikeActive[i]) {
                val sx = gs.spikeX[i] - gs.cameraX
                val sy = gs.spikeY[i] - gs.cameraY

                pGlow.strokeWidth = scale * 0.008f * (gs.spikeLife[i] / 0.4f)
                c.drawLine(sx, sy, sx - (gs.spikeVx[i] * 0.02f), sy - (gs.spikeVy[i] * 0.02f), pGlow)
            }
        }

        if (gs.state == 1) enemySys.drawEntities(c, gs, targetW, scale)

        effectSys.drawTrails(c, gs.cameraX, gs.cameraY, scale, currentPlayerColor)

        if (gs.isOverclocked) {
            effectSys.drawLightning(c, screenPlayerX, screenPlayerY, scale)
        }

        val shouldDrawPlayer = gs.playerIframe <= 0f || ((gs.timeSinceStart * 15).toInt() % 2 == 0)
        if (shouldDrawPlayer) {
            val playerRadius = scale * 0.015f
            p.style = Paint.Style.FILL; p.color = currentPlayerColor
            c.drawCircle(screenPlayerX, screenPlayerY, playerRadius, p)

            p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.003f
            if (gs.shieldTimer > 0f) {
                p.color = GameColors.SHIELD; p.strokeWidth = scale * 0.006f
                c.drawCircle(screenPlayerX, screenPlayerY, playerRadius * 3f + sin(gs.timeSinceStart * 10f) * scale * 0.005f, p)
                p.strokeWidth = scale * 0.003f
            } else p.color = currentPlayerColor
            c.drawCircle(screenPlayerX, screenPlayerY, playerRadius * 2f, p)
        }

        effectSys.drawParticles(c, gs.cameraX, gs.cameraY, scale)
        effectSys.drawFloatingTexts(c, gs.cameraX, gs.cameraY, scale)
    }

    private fun drawCore(c: Canvas, scale: Float, gs: GameState, targetW: Float, screenPlayerX: Float, screenPlayerY: Float) {
        val screenCoreX = gs.coreX - gs.cameraX
        val screenCoreY = gs.coreY - gs.cameraY

        if (screenCoreX > targetW - scale * 0.1f && gs.state == 8) {
            val arrowX = targetW - scale * 0.06f
            val arrowY = screenCoreY

            val alpha = ((sin(gs.timeSinceStart * 10f) + 1f) / 2f * 155 + 100).toInt()
            p.color = (alpha shl 24) or 0xFFFF00
            p.style = Paint.Style.FILL

            arrowPath.reset()
            arrowPath.moveTo(arrowX - scale * 0.04f, arrowY - scale * 0.03f)
            arrowPath.lineTo(arrowX + scale * 0.02f, arrowY)
            arrowPath.lineTo(arrowX - scale * 0.04f, arrowY + scale * 0.03f)
            arrowPath.lineTo(arrowX - scale * 0.02f, arrowY)
            arrowPath.close()
            c.drawPath(arrowPath, p)

            pText.color = (alpha shl 24) or 0xFFFF00
            pText.textSize = scale * 0.035f
            pText.textAlign = Paint.Align.RIGHT

            val dist = ((screenCoreX - targetW) / scale * 10).toInt()
            if (dist != lastCoreDist) {
                lastCoreDist = dist
                coreDistStr = context.getString(R.string.ui_core_signal, dist)
            }
            c.drawText(coreDistStr, arrowX - scale * 0.05f, arrowY + scale * 0.01f, pText)
        } else {
            p.style = Paint.Style.STROKE
            p.strokeWidth = scale * 0.015f
            p.color = GameColors.YELLOW
            c.drawCircle(screenCoreX, screenCoreY, gs.coreRadius + sin(gs.timeSinceStart * 5f) * scale * 0.03f, p)

            p.style = Paint.Style.FILL
            p.color = GameColors.CLARITY
            c.drawCircle(screenCoreX, screenCoreY, gs.coreRadius * 0.4f, p)

            if (gs.state == 8) {
                val dx = screenCoreX - screenPlayerX
                val dy = screenCoreY - screenPlayerY
                val dist = sqrt(dx*dx + dy*dy)

                if (dist > scale * 0.2f) {
                    pDash.strokeWidth = scale * 0.005f
                    c.drawLine(screenPlayerX, screenPlayerY, screenCoreX, screenCoreY, pDash)
                }
            }
        }
    }
}