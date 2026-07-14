package com.appsbyalok.echohunter.ui.arsenal

import android.graphics.Canvas
import android.graphics.Paint
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.GameColors
import kotlin.math.cos
import kotlin.math.sin

class ProbeDetailRenderer {
    private val p = Paint().apply { isAntiAlias = true }
    private var lastWeaponId = -1
    private var lastTrapId = -1
    private var partSwapAnim = 0f // 0 to 1 anim for swapping parts

    fun draw(c: Canvas, x: Float, y: Float, scale: Float, gs: GameState) {
        val currentWeaponId = gs.controls.currentWeapon
        val currentTrapId = gs.controls.currentTrap

        if (currentWeaponId != lastWeaponId || currentTrapId != lastTrapId) {
            partSwapAnim = 1.0f
            lastWeaponId = currentWeaponId
            lastTrapId = currentTrapId
        }

        if (partSwapAnim > 0) {
            partSwapAnim -= 0.05f
            if (partSwapAnim < 0) partSwapAnim = 0f
        }

        // --- Hardware Wireframe ---
        p.style = Paint.Style.STROKE
        p.color = 0x3300FFFF
        p.strokeWidth = scale * 0.003f
        
        // Outer Rings
        c.drawCircle(x, y, scale * 0.15f, p)
        c.drawCircle(x, y, scale * 0.10f, p)
        
        // Rotating Hexagon/Core
        val rot = gs.timeSinceStart * 20f
        c.save()
        c.rotate(rot, x, y)
        drawHexagon(c, x, y, scale * 0.07f, p)
        c.restore()

        // Scanning line effect over the drone
        val scanY = y - scale * 0.15f + ((gs.timeSinceStart * 0.5f) % 1.0f) * scale * 0.3f
        p.color = 0x6600FFFF
        p.strokeWidth = scale * 0.005f
        c.drawLine(x - scale * 0.12f, scanY, x + scale * 0.12f, scanY, p)

        // Pulse Core
        p.color = GameColors.PULSE
        val pulseSize = scale * 0.02f + sin(gs.timeSinceStart * 8f) * scale * 0.005f
        p.style = Paint.Style.FILL
        c.drawCircle(x, y, pulseSize, p)

        // Connection Lines to hypothetical "parts"
        p.style = Paint.Style.STROKE
        p.color = 0x4400FFFF
        p.strokeWidth = scale * 0.002f
        
        // Top Part (Weapon)
        val weaponPulse = if (partSwapAnim > 0) 1.0f + partSwapAnim * 0.5f else 1.0f
        c.save()
        c.translate(x, y - scale * 0.22f)
        c.scale(weaponPulse, weaponPulse)
        c.drawLine(0f, scale * 0.12f, 0f, scale * 0.02f, p)
        
        p.style = Paint.Style.FILL
        p.color = if (partSwapAnim > 0) GameColors.PULSE else 0x4400FFFF
        c.drawCircle(0f, 0f, scale * 0.025f, p)
        
        p.style = Paint.Style.STROKE
        p.color = GameColors.PULSE
        c.drawCircle(0f, 0f, scale * (0.025f + partSwapAnim * 0.05f), p)
        c.restore()
        
        // Bottom Part (Engine/Logic)
        c.drawLine(x, y + scale * 0.1f, x, y + scale * 0.2f, p)
        c.drawCircle(x, y + scale * 0.22f, scale * 0.02f, p)

        // Left/Right Parts (Utility/Traps)
        val trapPulse = if (partSwapAnim > 0) 1.0f + partSwapAnim * 0.3f else 1.0f
        
        // Left
        c.save()
        c.translate(x - scale * 0.22f, y)
        c.scale(trapPulse, trapPulse)
        c.drawLine(scale * 0.12f, 0f, scale * 0.02f, 0f, p)
        p.style = Paint.Style.FILL
        p.color = if (partSwapAnim > 0) GameColors.PULSE else 0x4400FFFF
        c.drawCircle(0f, 0f, scale * 0.02f, p)
        c.restore()

        // Right
        c.save()
        c.translate(x + scale * 0.22f, y)
        c.scale(trapPulse, trapPulse)
        c.drawLine(-scale * 0.12f, 0f, -scale * 0.02f, 0f, p)
        p.style = Paint.Style.FILL
        p.color = if (partSwapAnim > 0) GameColors.PULSE else 0x4400FFFF
        c.drawCircle(0f, 0f, scale * 0.02f, p)
        c.restore()
    }

    private fun drawHexagon(c: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val path = android.graphics.Path()
        for (i in 0..5) {
            val angle = Math.toRadians((i * 60).toDouble())
            val px = cx + radius * cos(angle).toFloat()
            val py = cy + radius * sin(angle).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        c.drawPath(path, paint)
    }
}
