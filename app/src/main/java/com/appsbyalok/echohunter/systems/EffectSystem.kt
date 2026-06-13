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

    private val pn = 40
    private val pxA = FloatArray(pn); private val pyA = FloatArray(pn)
    private val pvxA = FloatArray(pn); private val pvyA = FloatArray(pn)
    private val pLife = FloatArray(pn)

    private val trailLength = 15
    private val trailX = FloatArray(trailLength); private val trailY = FloatArray(trailLength)
    private var trailIdx = 0

    private val ftn = 10
    private val ftX = FloatArray(ftn); private val ftY = FloatArray(ftn)
    private val ftLife = FloatArray(ftn); private val ftColor = IntArray(ftn)
    private val ftStr = Array(ftn) { "" }

    fun reset() {
        for (i in 0 until trailLength) { trailX[i] = 0f; trailY[i] = 0f }
        for (i in 0 until pn) { pLife[i] = 0f }
        for (i in 0 until ftn) { ftLife[i] = 0f }
    }

    fun recordTrail(px: Float, py: Float) {
        trailX[trailIdx] = px; trailY[trailIdx] = py
        trailIdx = (trailIdx + 1) % trailLength
    }

    fun spawnParticles(x: Float, y: Float, colorMode: Int, scale: Float) {
        for (i in 0 until pn) {
            if (pLife[i] <= 0) {
                pxA[i] = x; pyA[i] = y
                val angle = Random.nextFloat() * TWO_PI
                val speed = scale * 0.5f + Random.nextFloat() * (scale * 1.0f)
                pvxA[i] = cos(angle) * speed
                pvyA[i] = sin(angle) * speed
                
                // --- FIX: VISUAL CLARITY COLOR MAPPING ---
                // 0 -> PULSE (Blue), 1 -> RED (Default/Enemy), 2 -> OVERCLOCK (Orange), 3 -> BOSS (Magenta)
                pLife[i] = when(colorMode) {
                    0 -> 0.9f  // Pulse Blue
                    1 -> 1.0f  // Red
                    2 -> 2.0f  // Overclock
                    3 -> 3.0f  // Boss
                    else -> 1.0f
                }
                if (Random.nextFloat() > 0.8f) break
            }
        }
    }

    fun spawnFloatingText(x: Float, y: Float, value: Int, color: Int) {
        for (i in 0 until ftn) {
            if (ftLife[i] <= 0f) {
                ftX[i] = x; ftY[i] = y; ftLife[i] = 1f; ftColor[i] = color
                ftStr[i] = "+$value"
                break
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
                c.drawCircle(tx, ty, trailSize * (i.toFloat() / trailLength), p)
            }
        }
    }

    // FIXED: Particles now render relative to camera
    fun drawParticles(c: Canvas, cameraX: Float, cameraY: Float, scale: Float) {
        p.style = Paint.Style.FILL
        for (i in 0 until pn) {
            if (pLife[i] > 0) {
                val pColor = when {
                    pLife[i] > 2.0f -> GameColors.BOSS
                    pLife[i] > 1.0f -> GameColors.OVERCLOCK
                    pLife[i] < 1.0f && pLife[i] > 0.85f -> GameColors.PULSE // Spawner Blue
                    else -> GameColors.RED
                }
                val alphaAlpha = if (pLife[i] > 1f) pLife[i] - pLife[i].toInt() else pLife[i]
                p.color = ((alphaAlpha * 255).toInt() shl 24) or (pColor and 0xFFFFFF)
                c.drawCircle(pxA[i] - cameraX, pyA[i] - cameraY, alphaAlpha * (scale * 0.008f), p)
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