package com.appsbyalok.echohunter.ui.arsenal

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.GameColors

class ArsenalMainScreen(private val probeRenderer: ProbeDetailRenderer) {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    val weaponDirRect = RectF()
    val trapDirRect = RectF()
    val attackModeRect = RectF()

    fun draw(c: Canvas, targetW: Float, targetH: Float, scale: Float, gs: GameState, headerHeight: Float, hitOnDown: Int) {
        val isPortrait = targetH > targetW

        // Dynamic placements
        val droneX = if (isPortrait) targetW * 0.5f else targetW * 0.3f
        val droneY = if (isPortrait) headerHeight + scale * 0.15f else targetH * 0.5f

        // Folders placements
        val dirX = if (isPortrait) targetW * 0.5f else targetW * 0.75f
        val dirYStart = if (isPortrait) droneY + scale * 0.22f else headerHeight + scale * 0.1f
        val boxW = if (isPortrait) targetW * 0.85f else scale * 0.65f
        val boxH = if (isPortrait) scale * 0.16f else scale * 0.14f
        val spacing = scale * 0.04f

        // --- Hardware Wireframe (via ProbeDetailRenderer) ---
        probeRenderer.draw(c, droneX, droneY, scale, gs)

        pText.textAlign = Paint.Align.CENTER
        pText.color = GameColors.CLARITY
        pText.textSize = scale * 0.03f
        c.drawText("PROBE-7 DIAGNOSTICS: NOMINAL", droneX, droneY + scale * 0.28f, pText)

        // --- Folders ---
        weaponDirRect.set(dirX - boxW/2f, dirYStart, dirX + boxW/2f, dirYStart + boxH)
        drawFolderBox(c, weaponDirRect, "WEAPONS.dir", getWeaponName(gs.controls.currentWeapon), scale, isPortrait, hitOnDown == 1)

        trapDirRect.set(dirX - boxW/2f, dirYStart + boxH + spacing, dirX + boxW/2f, dirYStart + boxH * 2 + spacing)
        drawFolderBox(c, trapDirRect, "TRAPS.dir", getTrapName(gs.controls.currentTrap), scale, isPortrait, hitOnDown == 2)

        attackModeRect.set(dirX - boxW/2f, dirYStart + (boxH + spacing) * 2, dirX + boxW/2f, dirYStart + boxH * 3 + spacing * 2)
        drawFolderBox(c, attackModeRect, "LOGIC_AIM.sys", gs.controls.activeAttackMode.name.replace("_", " "), scale, isPortrait, hitOnDown == 3)
    }

    private fun drawFolderBox(c: Canvas, rect: RectF, title: String, activeItem: String, scale: Float, isPortrait: Boolean, isPressed: Boolean) {
        val baseColor = 0xFF051515.toInt()

        p.style = Paint.Style.FILL; p.color = if (isPressed) GameColors.mixColors(baseColor, GameColors.PULSE, 0.2f) else baseColor
        c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, p)
        p.style = Paint.Style.STROKE; p.color = if (isPressed) GameColors.CLARITY else GameColors.PULSE; p.strokeWidth = scale * 0.005f
        c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, p)

        pText.textAlign = Paint.Align.LEFT

        // Scale text up slightly for portrait mode readability
        val titleSize = if (isPortrait) scale * 0.05f else scale * 0.045f
        val subSize = if (isPortrait) scale * 0.035f else scale * 0.03f

        pText.color = if (isPressed) GameColors.CLARITY else GameColors.PULSE
        pText.textSize = titleSize
        if (isPressed) pText.setShadowLayer(10f, 0f, 0f, GameColors.PULSE)
        c.drawText("> $title", rect.left + scale * 0.04f, rect.top + titleSize * 1.6f, pText)

        pText.color = GameColors.YELLOW; pText.textSize = subSize
        c.drawText("STATUS: $activeItem", rect.left + scale * 0.04f, rect.bottom - scale * 0.03f, pText)
        pText.clearShadowLayer()
    }

    private fun getWeaponName(id: Int) = when(id) { 1 -> "Shotgun"; 2 -> "Sniper"; else -> "Blaster" }
    private fun getTrapName(id: Int) = when(id) { 0 -> "Camouflage"; 1 -> "Decoy"; 2 -> "EMP Mine"; else -> "None" }
}
