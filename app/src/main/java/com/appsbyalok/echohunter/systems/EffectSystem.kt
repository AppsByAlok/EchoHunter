package com.appsbyalok.echohunter.systems

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.appsbyalok.echohunter.utils.GameColors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Handles purely visual features like particles, player trails, and floating text
class EffectSystem {
    private val p = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val pGlow = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val path = Path()

    companion object {
        private const val TWO_PI = 6.2831855f
    }

    private val pn = 128
    private val pxA = FloatArray(pn); private val pyA = FloatArray(pn)
    private val pvxA = FloatArray(pn); private val pvyA = FloatArray(pn)
    private val pLife = FloatArray(pn)
    private val pInitialLife = FloatArray(pn)
    private val pColor = IntArray(pn)

    private val trailLength = 15
    private val trailX = FloatArray(trailLength); private val trailY = FloatArray(trailLength)
    private var trailIdx = 0

    private val ftn = 10
    private val ftX = FloatArray(ftn); private val ftY = FloatArray(ftn)
    private val ftLife = FloatArray(ftn); private val ftColor = IntArray(ftn)
    private val ftStr = Array(ftn) { "" }

    // --- SONAR PING EFFECTS ---
    private val spn = 20
    private val spX = FloatArray(spn); private val spY = FloatArray(spn)
    private val spLife = FloatArray(spn)
    private val spColor = IntArray(spn)

    private val arcN = 8
    private val arcStartX = FloatArray(arcN); private val arcStartY = FloatArray(arcN)
    private val arcEndX = FloatArray(arcN); private val arcEndY = FloatArray(arcN)
    private val arcLife = FloatArray(arcN)

    fun reset() {
        trailIdx = 0
        for (i in 0 until trailLength) { trailX[i] = 0f; trailY[i] = 0f }
        for (i in 0 until pn) { pLife[i] = 0f }
        for (i in 0 until ftn) { ftLife[i] = 0f }
        for (i in 0 until spn) { spLife[i] = 0f }
        for (i in 0 until arcN) { arcLife[i] = 0f }
    }

    fun recordTrail(px: Float, py: Float) {
        trailX[trailIdx] = px; trailY[trailIdx] = py
        trailIdx = (trailIdx + 1) % trailLength
    }

    fun spawnParticles(x: Float, y: Float, colorMode: Int, scale: Float, count: Int = defaultParticleCount(colorMode)) {
        var spawned = 0
        for (i in 0 until pn) {
            if (pLife[i] <= 0) {
                pxA[i] = x; pyA[i] = y
                val angle = Random.nextFloat() * TWO_PI
                val speed = scale * 0.5f + Random.nextFloat() * (scale * 1.0f)
                pvxA[i] = cos(angle) * speed
                pvyA[i] = sin(angle) * speed
                
                pInitialLife[i] = particleLifetime(colorMode)
                pLife[i] = pInitialLife[i]
                pColor[i] = particleColor(colorMode)
                spawned++
                if (spawned >= count) break
            }
        }
    }

    private fun defaultParticleCount(colorMode: Int) = when (colorMode) {
        3 -> 6 // Boss impact
        4, 6 -> 8 // Critical hit / EMP
        8, 15 -> 10 // Menu connection / repair confirmation
        else -> 4
    }

    private fun particleLifetime(colorMode: Int) = when (colorMode) {
        3 -> 0.85f
        4, 6 -> 0.7f
        8, 15 -> 0.8f
        else -> 0.55f
    }

    private fun particleColor(colorMode: Int) = when (colorMode) {
        0 -> GameColors.PULSE
        1 -> GameColors.RED
        2 -> GameColors.SHIELD
        3 -> GameColors.BOSS
        4 -> GameColors.YELLOW
        6, 8 -> GameColors.CLARITY
        15 -> GameColors.HP
        else -> GameColors.PULSE
    }

    fun spawnFloatingText(x: Float, y: Float, value: Long, color: Int, overrideStr: String? = null) {
        for (i in 0 until ftn) {
            if (ftLife[i] <= 0f) {
                ftX[i] = x; ftY[i] = y; ftLife[i] = 1f; ftColor[i] = color
                ftStr[i] = overrideStr ?: "+$value"
                break
            }
        }
    }

    fun spawnAlertPulse(x: Float, y: Float, color: Int) {
        spawnSonarPing(x, y, color)
    }

    fun spawnSonarPing(x: Float, y: Float, color: Int) {
        for (i in 0 until spn) {
            if (spLife[i] <= 0f) {
                spX[i] = x; spY[i] = y; spLife[i] = 1.0f; spColor[i] = color
                break
            }
        }
    }

    fun spawnElectricArc(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        for (i in 0 until arcN) {
            if (arcLife[i] <= 0f) {
                arcStartX[i] = fromX; arcStartY[i] = fromY
                arcEndX[i] = toX; arcEndY[i] = toY
                arcLife[i] = 0.18f
                return
            }
        }
    }

    fun update(dt: Float, scale: Float) {
        for (i in 0 until pn) {
            if (pLife[i] > 0) {
                pxA[i] += pvxA[i] * dt
                pyA[i] += pvyA[i] * dt
                pLife[i] -= 1f * dt
            }
        }
        for (i in 0 until ftn) {
            if (ftLife[i] > 0f) {
                ftY[i] -= scale * 0.1f * dt
                ftLife[i] -= 1f * dt
            }
        }
        for (i in 0 until spn) {
            if (spLife[i] > 0f) {
                spLife[i] -= 1.5f * dt
            }
        }
        for (i in 0 until arcN) if (arcLife[i] > 0f) arcLife[i] -= dt
    }

    // FIXED: CameraY is now properly used to align trails
    fun drawTrails(c: Canvas, cameraX: Float, cameraY: Float, scale: Float, currentPlayerColor: Int) {
        p.style = Paint.Style.FILL
        for (i in 0 until trailLength) {
            val tx = trailX[(trailIdx + i) % trailLength] - cameraX
            val ty = trailY[(trailIdx + i) % trailLength] - cameraY
            if (tx + cameraX != 0f || ty + cameraY != 0f) {
                val isOverclocked = currentPlayerColor == GameColors.OVERCLOCK
                val trailSize = if (isOverclocked) scale * 0.02f else scale * 0.012f
                p.color = ((255 * (i.toFloat() / trailLength)).toInt() shl 24) or (currentPlayerColor and 0xFFFFFF)
                
                // OPTIMIZATION: Simplified trail rendering
                val sz = trailSize * (i.toFloat() / trailLength)
                c.drawRect(tx - sz, ty - sz, tx + sz, ty + sz, p)
            }
        }
    }

    // FIXED: Optimized single-pass rendering
    fun drawParticles(c: Canvas, cameraX: Float, cameraY: Float, scale: Float) {
        p.style = Paint.Style.FILL

        for (i in 0 until pn) {
            if (pLife[i] > 0) {
                val alphaAlpha = (pLife[i] / pInitialLife[i]).coerceIn(0f, 1f)
                p.color = ((alphaAlpha * 255).toInt() shl 24) or (pColor[i] and 0xFFFFFF)

                val sz = alphaAlpha * (scale * 0.008f)
                val sx = pxA[i] - cameraX
                val sy = pyA[i] - cameraY
                
                c.drawRect(sx - sz, sy - sz, sx + sz, sy + sz, p)
            }
        }
    }

    // FIXED: Text renders relative to camera
    fun drawFloatingTexts(c: Canvas, cameraX: Float, cameraY: Float, scale: Float) {
        pText.textSize = scale * 0.04f
        val offset = scale * 0.005f

        for (i in 0 until ftn) {
            if (ftLife[i] > 0f) {
                val currentAlpha = (ftLife[i] * 255).toInt()
                val screenX = ftX[i] - cameraX
                val screenY = ftY[i] - cameraY

                pText.color = Color.BLACK
                pText.alpha = currentAlpha / 2
                c.drawText(ftStr[i], screenX + offset, screenY + offset, pText)

                pText.color = ftColor[i]
                pText.alpha = currentAlpha
                c.drawText(ftStr[i], screenX, screenY, pText)
            }
        }
    }

    fun drawSonarPings(c: Canvas, cameraX: Float, cameraY: Float, scale: Float) {
        pGlow.style = Paint.Style.STROKE
        for (i in 0 until spn) {
            if (spLife[i] > 0f) {
                val life = spLife[i]
                val alpha = (life * 255).toInt()
                pGlow.color = (alpha shl 24) or (spColor[i] and 0xFFFFFF)
                pGlow.strokeWidth = scale * 0.005f * life
                
                val r = (1f - life) * scale * 0.12f
                c.drawCircle(spX[i] - cameraX, spY[i] - cameraY, r, pGlow)
            }
        }
    }

    fun drawElectricArcs(c: Canvas, cameraX: Float, cameraY: Float, scale: Float) {
        pGlow.style = Paint.Style.STROKE
        for (i in 0 until arcN) {
            if (arcLife[i] <= 0f) continue
            val life = (arcLife[i] / 0.18f).coerceIn(0f, 1f)
            val sx = arcStartX[i] - cameraX; val sy = arcStartY[i] - cameraY
            val ex = arcEndX[i] - cameraX; val ey = arcEndY[i] - cameraY
            val dx = ex - sx; val dy = ey - sy
            val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val px = -dy / length; val py = dx / length
            pGlow.color = ((life * 255).toInt() shl 24) or (GameColors.CLARITY and 0xFFFFFF)
            pGlow.strokeWidth = scale * 0.006f * life
            path.reset(); path.moveTo(sx, sy)
            for (step in 1..4) {
                val t = step / 5f
                val wobble = sin((step * 7f) + life * 12f) * scale * 0.025f
                path.lineTo(sx + dx * t + px * wobble, sy + dy * t + py * wobble)
            }
            path.lineTo(ex, ey)
            c.drawPath(path, pGlow)
        }
    }

    fun drawLightning(c: Canvas, startX: Float, startY: Float, scale: Float) {
        pGlow.color = GameColors.OVERCLOCK
        pGlow.strokeWidth = scale * 0.003f
        path.reset()
        val branches = Random.nextInt(2, 5)
        repeat(branches) {
            var currX = startX
            var currY = startY
            var angle = Random.nextFloat() * TWO_PI
            path.moveTo(currX, currY)
            val segments = Random.nextInt(3, 6)
            repeat(segments) {
                currX += cos(angle) * scale * 0.04f
                currY += sin(angle) * scale * 0.04f
                angle += (Random.nextFloat() - 0.5f) * 2f
                path.lineTo(currX, currY)
            }
        }
        c.drawPath(path, pGlow)
    }
}
