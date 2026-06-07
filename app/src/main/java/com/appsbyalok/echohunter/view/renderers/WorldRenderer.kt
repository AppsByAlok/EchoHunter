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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class WorldRenderer(
    private val context: Context,
    private val effectSys: EffectSystem,
    private val enemySys: EnemySystem,
) {
    private val p = Paint().apply { isAntiAlias = true }
    private val pGlow = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
    private val pDash =
        Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; color = 0x55FFFF00 }
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
        p.style = Paint.Style.STROKE
        p.color = if (gs.difficulty == 1) 0xFF441010.toInt() else 0xFF1A1C2E.toInt()
        p.strokeWidth = max(1f, scale * 0.002f)
        val gap = scale / 8.5f
        val parallaxX = gs.cameraX * 0.5f
        val parallaxY = gs.cameraY * 0.5f

        val gridOffsetX = -(parallaxX % gap)
        val gridOffsetY = -(parallaxY % gap)

        var i = -gap + gridOffsetX
        while (i < targetW + gap) {
            c.drawLine(i, 0f, i, targetH, p); i += gap
        }
        var j = -gap + gridOffsetY
        while (j < targetH + gap) {
            c.drawLine(0f, j, targetW, j, p); j += gap
        }
    }

    fun drawCRTOverlay(c: Canvas, gs: GameState, targetW: Float, targetH: Float) {
        p.style = Paint.Style.STROKE
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
                    1 -> { // 2D NEON WALL RENDERING
                        p.color = 0x2200FFFF
                        c.drawRect(drawX, drawY, drawX + ts, drawY + ts, p)

                        p.style = Paint.Style.STROKE
                        p.strokeWidth = scale * 0.005f
                        p.color = GameColors.PULSE
                        val m = ts * 0.1f
                        c.drawRect(drawX + m, drawY + m, drawX + ts - m, drawY + ts - m, p)
                        p.style = Paint.Style.FILL
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
        c.drawCircle(
            screenPlayerX,
            screenPlayerY,
            scale * 0.12f + sin(gs.timeSinceStart * 3f) * scale * 0.01f,
            p
        )

        gs.modeStrategy.drawModeSpecificWorld(c, gs, targetW, targetH, scale, p)

        if (gs.pulse) {
            val alpha = (255 * (1f - (gs.pulseR / (targetW * 0.75f)))).toInt()
            val colorGlow =
                if (StoryProtocol.isGlitchActive) GameColors.RED else if (gs.isOverclocked) GameColors.OVERCLOCK else if (gs.visionClarity > 0.3f) GameColors.PULSE else 0xFF006666.toInt()
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

        // --- FIX: DEFENSE mode, ESCAPE mode ya Story Core sabke liye call karenge ---
        val config = com.appsbyalok.echohunter.data.LevelEngine.getLevelConfig(gs.currentLevel)
        val isDefense =
            config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.DEFENSE) && gs.gameMode == 0
        val isEscape =
            config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.ESCAPE) && gs.gameMode == 0

        // Ab Escape level par bhi drawCore trigger hoga!
        if (isDefense || isEscape || gs.state == 8 || gs.state == 9) {
            drawCore(c, scale, gs, targetW, targetH, screenPlayerX, screenPlayerY)
        }

        if (gs.empMineActive) {
            val screenMineX = gs.empMineX - gs.cameraX
            val screenMineY = gs.empMineY - gs.cameraY
            p.style = Paint.Style.FILL
            p.color = GameColors.RED
            c.drawCircle(screenMineX, screenMineY, scale * 0.015f, p)

            p.style = Paint.Style.STROKE
            p.strokeWidth = scale * 0.003f
            p.color = GameColors.YELLOW
            val pulse = sin(gs.timeSinceStart * 10f) * scale * 0.015f
            c.drawCircle(screenMineX, screenMineY, scale * 0.03f + max(0f, pulse), p)
        }

        if (gs.isDecoyActive) {
            val screenDecoyX = gs.decoyX - gs.cameraX
            val screenDecoyY = gs.decoyY - gs.cameraY
            p.style = Paint.Style.STROKE
            p.strokeWidth = scale * 0.005f
            p.color = GameColors.PULSE
            val holoPulse = sin(gs.timeSinceStart * 20f) * scale * 0.005f
            c.drawCircle(screenDecoyX, screenDecoyY, scale * 0.02f + holoPulse, p)
            c.drawCircle(screenDecoyX, screenDecoyY, scale * 0.035f - holoPulse, p)
        }

        pGlow.color = GameColors.PULSE
        for (i in 0 until gs.maxSpikes) {
            if (gs.spikeActive[i]) {
                val sx = gs.spikeX[i] - gs.cameraX
                val sy = gs.spikeY[i] - gs.cameraY

                when (gs.spikeType[i]) {
                    2 -> { // SNIPER BEAM
                        pGlow.color = GameColors.RED
                        pGlow.strokeWidth = scale * 0.012f * (gs.spikeLife[i] / 0.6f)
                        c.drawLine(
                            sx,
                            sy,
                            sx - (gs.spikeVx[i] * 0.06f),
                            sy - (gs.spikeVy[i] * 0.06f),
                            pGlow
                        )
                    }

                    1 -> { // SHOTGUN SPREAD
                        pGlow.color = GameColors.OVERCLOCK
                        pGlow.strokeWidth = scale * 0.015f * (gs.spikeLife[i] / 0.4f)
                        c.drawLine(
                            sx,
                            sy,
                            sx - (gs.spikeVx[i] * 0.01f),
                            sy - (gs.spikeVy[i] * 0.01f),
                            pGlow
                        )
                    }

                    else -> { // NORMAL SPIKE
                        pGlow.color = GameColors.PULSE
                        pGlow.strokeWidth = scale * 0.008f * (gs.spikeLife[i] / 0.4f)
                        c.drawLine(
                            sx,
                            sy,
                            sx - (gs.spikeVx[i] * 0.02f),
                            sy - (gs.spikeVy[i] * 0.02f),
                            pGlow
                        )
                    }
                }
            }
        }

        // --- FIX: Enemies draw in Gameplay AND Core Merge states ---
        if (gs.state == 1 || gs.state == 8 || gs.state == 9) enemySys.drawEntities(c, gs, targetW, scale)

        effectSys.drawTrails(c, gs.cameraX, gs.cameraY, scale, currentPlayerColor)

        if (gs.isOverclocked) {
            effectSys.drawLightning(c, screenPlayerX, screenPlayerY, scale)
        }

        val shouldDrawPlayer = gs.playerIframe <= 0f || ((gs.timeSinceStart * 15).toInt() % 2 == 0)
        if (shouldDrawPlayer) {
            val playerRadius = scale * 0.015f
            val alpha = if (gs.isCamouflaged) 0x33 else 0xFF

            p.style = Paint.Style.FILL
            p.color = (alpha shl 24) or (currentPlayerColor and 0xFFFFFF)
            c.drawCircle(screenPlayerX, screenPlayerY, playerRadius, p)

            p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.003f
            if (gs.shieldTimer > 0f) {
                p.color = (alpha shl 24) or (GameColors.SHIELD and 0xFFFFFF)
                p.strokeWidth = scale * 0.006f
                c.drawCircle(
                    screenPlayerX,
                    screenPlayerY,
                    playerRadius * 3f + sin(gs.timeSinceStart * 10f) * scale * 0.005f,
                    p
                )
                p.strokeWidth = scale * 0.003f
            } else {
                p.color = (alpha shl 24) or (currentPlayerColor and 0xFFFFFF)
            }
            c.drawCircle(screenPlayerX, screenPlayerY, playerRadius * 2f, p)
        }

        effectSys.drawParticles(c, gs.cameraX, gs.cameraY, scale)
        effectSys.drawFloatingTexts(c, gs.cameraX, gs.cameraY, scale)

        // Arrow draw karne ke liye
        drawArrow(c, scale, gs, targetW, targetH)
    }

    private fun drawCore(
        c: Canvas,
        scale: Float,
        gs: GameState,
        targetW: Float,
        targetH: Float,
        screenPlayerX: Float,
        screenPlayerY: Float,
    ) {
        val screenCoreX = gs.coreX - gs.cameraX
        val screenCoreY = gs.coreY - gs.cameraY

        // --- 1. DETECT LEVEL FEATURES ---
        val config = com.appsbyalok.echohunter.data.LevelEngine.getLevelConfig(gs.currentLevel)
        val isDefense =
            config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.DEFENSE) && gs.gameMode == 0
        val isEscape = config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.ESCAPE) && gs.gameMode == 0

        // --- 2. SCREEN OFF-BOUNDS ARROW INDICATOR (STORY MODE GATEWAY ONLY) ---
        if (screenCoreX > targetW - scale * 0.1f && gs.state == 8) {
            val arrowX = targetW - scale * 0.06f
            val alpha = ((sin(gs.timeSinceStart * 10f) + 1f) / 2f * 155 + 100).toInt()

            p.color = (alpha shl 24) or 0xFFFF00
            p.style = Paint.Style.FILL

            arrowPath.reset()
            arrowPath.moveTo(arrowX - scale * 0.04f, screenCoreY - scale * 0.03f)
            arrowPath.lineTo(arrowX + scale * 0.02f, screenCoreY)
            arrowPath.lineTo(arrowX - scale * 0.04f, screenCoreY + scale * 0.03f)
            arrowPath.lineTo(arrowX - scale * 0.02f, screenCoreY)
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
            c.drawText(coreDistStr, arrowX - scale * 0.05f, screenCoreY + scale * 0.01f, pText)
            return // Arrow dikh raha hai toh baki base structure draw karne ki zaroorat nahi
        }

        // --- 3. PREMIUM CORE BASE RENDERING MATRIX ---
        if (gs.coreRadius > 0f) {

            when {
                // A. DEFENSE MODE VISUALS (Purple Pulsing Shield & HP Bar)
                isDefense && gs.state != 8 -> {
                    // Pulsing Shield Effect around the Core
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = scale * 0.015f
                    p.color = GameColors.SHIELD
                    c.drawCircle(
                        screenCoreX,
                        screenCoreY,
                        gs.coreRadius + (sin(gs.timeSinceStart * 10f) * scale * 0.02f),
                        p
                    )

                    // Core Base Body
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = scale * 0.01f
                    p.color = GameColors.YELLOW
                    c.drawCircle(screenCoreX, screenCoreY, gs.coreRadius, p)

                    p.style = Paint.Style.FILL
                    p.color = GameColors.CLARITY
                    c.drawCircle(screenCoreX, screenCoreY, gs.coreRadius * 0.4f, p)

                    // HEALTH BAR LAYER (Purely for Active Defense Levels)
                    p.style = Paint.Style.FILL
                    val hpBarW = scale * 0.18f
                    val hpBarH = scale * 0.02f
                    val hpY = screenCoreY - gs.coreRadius - scale * 0.05f

                    // HP Bar BG
                    p.color = 0xFF440000.toInt()
                    c.drawRect(
                        screenCoreX - hpBarW / 2,
                        hpY,
                        screenCoreX + hpBarW / 2,
                        hpY + hpBarH,
                        p
                    )

                    // HP Bar Color shifting
                    p.color = when {
                        gs.coreHp > gs.coreMaxHp * 0.5f -> GameColors.HP
                        gs.coreHp > gs.coreMaxHp * 0.25f -> GameColors.YELLOW
                        else -> GameColors.RED
                    }
                    val currentHpW = hpBarW * (max(0f, gs.coreHp.toFloat()) / gs.coreMaxHp)
                    c.drawRect(
                        screenCoreX - hpBarW / 2,
                        hpY,
                        screenCoreX - hpBarW / 2 + currentHpW,
                        hpY + hpBarH,
                        p
                    )

                    // Cyber Border Frame
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = scale * 0.004f
                    p.color = GameColors.TEXT
                    c.drawRect(
                        screenCoreX - hpBarW / 2,
                        hpY,
                        screenCoreX + hpBarW / 2,
                        hpY + hpBarH,
                        p
                    )
                }

                // B. ESCAPE MODE VISUALS (Exit Portal Portal Logic)
                isEscape && gs.state != 8 -> {
                    if (gs.escapeGateActive) {
                        // Active Portal (Green Neon Pulse)
                        p.style = Paint.Style.STROKE
                        p.strokeWidth = scale * 0.015f
                        p.color = GameColors.HP
                        c.drawCircle(
                            screenCoreX,
                            screenCoreY,
                            gs.coreRadius + (sin(gs.timeSinceStart * 10f) * scale * 0.02f),
                            p
                        )

                        p.style = Paint.Style.FILL
                        p.color = GameColors.PULSE
                        c.drawCircle(screenCoreX, screenCoreY, gs.coreRadius * 0.5f, p)
                    } else {
                        // Locked Portal (Dim Red / Inactive Structure)
                        p.style = Paint.Style.STROKE
                        p.strokeWidth = scale * 0.01f
                        p.color = 0xFF550000.toInt()
                        c.drawCircle(screenCoreX, screenCoreY, gs.coreRadius, p)

                        p.style = Paint.Style.FILL
                        p.color = 0xFF330000.toInt()
                        c.drawCircle(screenCoreX, screenCoreY, gs.coreRadius * 0.3f, p)
                    }
                }

                // C. STANDARD MODE CORE / STORY MODE MERGE CORE (Default Layout)
                else -> {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = scale * 0.015f
                    p.color = GameColors.YELLOW
                    c.drawCircle(
                        screenCoreX,
                        screenCoreY,
                        gs.coreRadius + sin(gs.timeSinceStart * 5f) * scale * 0.03f,
                        p
                    )

                    p.style = Paint.Style.FILL
                    p.color = GameColors.CLARITY
                    c.drawCircle(screenCoreX, screenCoreY, gs.coreRadius * 0.4f, p)
                }
            }

            // --- 5. DOTTED DATA EXTRACTOR TETHER (Story Mode Merges) ---
            if (gs.state == 8) {
                val dx = screenCoreX - screenPlayerX
                val dy = screenCoreY - screenPlayerY
                val dist = sqrt(dx * dx + dy * dy)

                if (dist > scale * 0.2f) {
                    pDash.style = Paint.Style.STROKE
                    pDash.strokeWidth = scale * 0.005f
                    c.drawLine(screenPlayerX, screenPlayerY, screenCoreX, screenCoreY, pDash)
                }
            }
        }
    }

    private fun drawArrow(c: Canvas, scale: Float, gs: GameState, targetW: Float, targetH: Float) {
        val config = com.appsbyalok.echohunter.data.LevelEngine.getLevelConfig(gs.currentLevel)
        var targetX = -1f
        var targetY = -1f
        var arrowColor = GameColors.HP
        var shouldDraw = false

        // 1. STORY MODE / BOSS MERGE
        if (gs.state == 8) {
            targetX = gs.coreX; targetY = gs.coreY
            arrowColor = GameColors.YELLOW
            shouldDraw = true
        }
        // 2. ESCAPE MODE (Portal Active hone par)
        else if (config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.ESCAPE) && gs.escapeGateActive) {
            targetX = gs.coreX; targetY = gs.coreY
            arrowColor = GameColors.HP
            shouldDraw = true
        }
        // 3. DEFENSE MODE (Core ko track karega)
        else if (config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.DEFENSE) && gs.gameMode == 0) {
            targetX = gs.coreX; targetY = gs.coreY
            arrowColor = GameColors.SHIELD // Neon Purple
            shouldDraw = true
        }
        // 4. ELIMINATION MODE (Sabse kareeb wale Red HVT ko track karega)
        else if (config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.ELIMINATION) && gs.gameMode == 0) {
            var minDist = Float.MAX_VALUE
            // Find the closest Type 3 enemy
            for (i in 0 until enemySys.n) {
                if (enemySys.vis[i] > 0.02f && enemySys.type[i] == 3) {
                    val dx = enemySys.ex[i] - gs.px
                    val dy = enemySys.ey[i] - gs.py
                    val distSq = dx * dx + dy * dy
                    if (distSq < minDist) {
                        minDist = distSq
                        targetX = enemySys.ex[i]
                        targetY = enemySys.ey[i]
                    }
                }
            }
            if (minDist != Float.MAX_VALUE) {
                arrowColor = GameColors.RED
                shouldDraw = true
            }
        }

        // AGAR KOI TARGET HAI, TOH ARROW DRAW KARO
        if (shouldDraw) {
            val dx = targetX - gs.px
            val dy = targetY - gs.py
            val distSq = dx * dx + dy * dy
            val hideRadius = min(targetW, targetH) * 0.4f

            // Sirf tab dikhao jab target player ki screen se door ho
            if (distSq > hideRadius * hideRadius) {
                val angle = kotlin.math.atan2(dy, dx)
                val arrowDist = min(targetW, targetH) * 0.35f // Screen ke center se doori

                val arrowScreenX = targetW / 2f + cos(angle) * arrowDist
                val arrowScreenY = targetH / 2f + sin(angle) * arrowDist

                val alpha = (abs(sin(gs.timeSinceStart * 5f)) * 155 + 100).toInt()

                p.style = Paint.Style.FILL
                p.color = (alpha shl 24) or (arrowColor and 0xFFFFFF)

                // Triangle Shape
                arrowPath.reset()
                arrowPath.moveTo(
                    arrowScreenX + cos(angle) * scale * 0.04f,
                    arrowScreenY + sin(angle) * scale * 0.04f
                )
                arrowPath.lineTo(
                    arrowScreenX + cos(angle + 2.5f) * scale * 0.03f,
                    arrowScreenY + sin(angle + 2.5f) * scale * 0.03f
                )
                arrowPath.lineTo(
                    arrowScreenX + cos(angle - 2.5f) * scale * 0.03f,
                    arrowScreenY + sin(angle - 2.5f) * scale * 0.03f
                )
                arrowPath.close()
                c.drawPath(arrowPath, p)

                // Distance Text
                pText.color = (alpha shl 24) or (arrowColor and 0xFFFFFF)
                pText.textSize = scale * 0.035f
                pText.textAlign = Paint.Align.CENTER
                val distStr = (sqrt(distSq) / scale * 10).toInt()
                c.drawText("$distStr M", arrowScreenX, arrowScreenY - scale * 0.04f, pText)
            }
        }
    }
}
