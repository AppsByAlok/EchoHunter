package com.appsbyalok.echohunter.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ToneGenerator
import android.view.MotionEvent
import com.appsbyalok.echohunter.utils.GameColors
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.EchoAudioManager
import kotlin.math.sin

class UIArsenal {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val closeBtnRect = RectF()
    private val weaponDirRect = RectF()
    private val trapDirRect = RectF()

    // 0 = Main OS, 1 = Weapons Folder, 2 = Traps Folder
    private var currentTab = 0
    private val itemRects = mutableMapOf<Int, RectF>()

    fun draw(c: Canvas, targetW: Float, targetH: Float, scale: Float, gs: GameState) {
        c.drawColor(0xEE051015.toInt()) // Dark Cyan-ish Terminal BG

        // --- NANO OS HEADER (Responsive) ---
        p.style = Paint.Style.FILL; p.color = GameColors.PULSE
        val headerHeight = if (targetH > targetW) scale * 0.12f else scale * 0.08f // Adjust header height for portrait
        c.drawRect(0f, 0f, targetW, headerHeight, p)

        pText.color = GameColors.BG; pText.textSize = scale * 0.04f
        pText.textAlign = Paint.Align.LEFT
        c.drawText("root@probe-7:/mnt/arsenal_loadout ~", scale * 0.05f, headerHeight * 0.65f, pText)

        // Route to active tab
        if (currentTab == 0) {
            drawMainScreen(c, targetW, targetH, scale, gs)
        } else if (currentTab == 1) {
            drawWeaponFolder(c, targetW, targetH, scale, gs)
        } else if (currentTab == 2) {
            drawTrapFolder(c, targetW, targetH, scale, gs)
        }

        // --- DISCONNECT / BACK BUTTON (Responsive Footer) ---
        pText.textAlign = Paint.Align.CENTER
        val isPortrait = targetH > targetW
        val btnWidth = if (isPortrait) targetW * 0.7f else scale * 0.4f
        val btnHeight = scale * 0.09f
        val btnBottomMargin = scale * 0.05f

        closeBtnRect.set(
            targetW / 2f - btnWidth / 2f,
            targetH - btnHeight - btnBottomMargin,
            targetW / 2f + btnWidth / 2f,
            targetH - btnBottomMargin
        )

        p.style = Paint.Style.FILL; p.color = 0xFF330000.toInt()
        c.drawRoundRect(closeBtnRect, scale * 0.02f, scale * 0.02f, p)
        p.style = Paint.Style.STROKE; p.color = GameColors.RED; p.strokeWidth = scale * 0.005f
        c.drawRoundRect(closeBtnRect, scale * 0.02f, scale * 0.02f, p)

        pText.color = GameColors.RED; pText.textSize = scale * 0.045f
        c.drawText(
            if(currentTab == 0) "DISCONNECT" else "< BACK",
            closeBtnRect.centerX(),
            closeBtnRect.centerY() + scale * 0.015f,
            pText
        )
    }

    private fun drawMainScreen(c: Canvas, targetW: Float, targetH: Float, scale: Float, gs: GameState) {
        val isPortrait = targetH > targetW

        // Dynamic placements based on orientation
        val droneX = if (isPortrait) targetW * 0.5f else targetW * 0.3f
        val droneY = if (isPortrait) targetH * 0.25f else targetH * 0.5f

        // Folders placements
        val dirX = if (isPortrait) targetW * 0.5f else targetW * 0.75f
        val dirYStart = if (isPortrait) targetH * 0.45f else targetH * 0.35f
        val boxW = if (isPortrait) targetW * 0.85f else scale * 0.65f
        val boxH = if (isPortrait) scale * 0.22f else scale * 0.18f
        val spacing = scale * 0.06f

        // --- Drone Wireframe Graphic ---
        p.style = Paint.Style.STROKE; p.color = 0x5500FFFF; p.strokeWidth = scale * 0.005f
        c.drawCircle(droneX, droneY, scale * 0.15f, p)
        c.drawRect(droneX - scale*0.05f, droneY - scale*0.05f, droneX + scale*0.05f, droneY + scale*0.05f, p)

        p.style = Paint.Style.FILL; p.color = GameColors.PULSE
        c.drawCircle(droneX, droneY, scale * 0.02f + sin(gs.timeSinceStart * 5f) * scale * 0.01f, p)

        pText.textAlign = Paint.Align.CENTER; pText.color = GameColors.CLARITY
        pText.textSize = scale * 0.04f
        c.drawText("PROBE-7 HARDWARE STATUS", droneX, droneY - scale * 0.2f, pText)

        // --- OS Folders (Directories) ---
        // Weapons Folder
        weaponDirRect.set(dirX - boxW/2f, dirYStart, dirX + boxW/2f, dirYStart + boxH)
        drawFolderBox(c, weaponDirRect, "WEAPONS.dir", getWeaponName(gs.currentWeapon), scale, isPortrait)

        // Traps Folder
        trapDirRect.set(dirX - boxW/2f, dirYStart + boxH + spacing, dirX + boxW/2f, dirYStart + boxH * 2 + spacing)
        drawFolderBox(c, trapDirRect, "TRAPS.dir", getTrapName(gs.currentTrap), scale, isPortrait)
    }

    private fun drawFolderBox(c: Canvas, rect: RectF, title: String, activeItem: String, scale: Float, isPortrait: Boolean) {
        p.style = Paint.Style.FILL; p.color = 0xFF051515.toInt()
        c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, p)
        p.style = Paint.Style.STROKE; p.color = GameColors.PULSE; p.strokeWidth = scale * 0.005f
        c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, p)

        pText.textAlign = Paint.Align.LEFT

        // Scale text up slightly for portrait mode readability
        val titleSize = if (isPortrait) scale * 0.06f else scale * 0.05f
        val subSize = if (isPortrait) scale * 0.04f else scale * 0.035f

        pText.color = GameColors.CLARITY; pText.textSize = titleSize
        c.drawText("> $title", rect.left + scale * 0.05f, rect.top + titleSize * 1.5f, pText)

        pText.color = GameColors.YELLOW; pText.textSize = subSize
        c.drawText("LOADED: $activeItem", rect.left + scale * 0.05f, rect.bottom - scale * 0.04f, pText)
    }

    private fun drawWeaponFolder(c: Canvas, targetW: Float, targetH: Float, scale: Float, gs: GameState) {
        drawList(c, targetW, targetH, scale, "SELECT WEAPON PROTOCOL", gs.currentWeapon, arrayOf("BLASTER (Default)", "SHOTGUN (Spread)", "SNIPER (Pierce)"))
    }

    private fun drawTrapFolder(c: Canvas, targetW: Float, targetH: Float, scale: Float, gs: GameState) {
        drawList(c, targetW, targetH, scale, "SELECT TRAP MODULE", gs.currentTrap, arrayOf("CAMOUFLAGE (Stealth)", "DECOY (Hologram)", "EMP MINE (Explosive)"))
    }

    private fun drawList(c: Canvas, targetW: Float, targetH: Float, scale: Float, title: String, currentActive: Int, items: Array<String>) {
        val isPortrait = targetH > targetW

        pText.textAlign = Paint.Align.CENTER; pText.color = GameColors.YELLOW
        pText.textSize = if (isPortrait) scale * 0.07f else scale * 0.06f
        c.drawText(title, targetW / 2f, if(isPortrait) targetH * 0.15f else scale * 0.2f, pText)

        var startY = if (isPortrait) targetH * 0.25f else scale * 0.3f
        val itemHeight = if (isPortrait) scale * 0.15f else scale * 0.12f
        val spacing = scale * 0.05f

        val boxWidth = if (isPortrait) targetW * 0.85f else targetW * 0.6f
        val startX = (targetW - boxWidth) / 2f

        itemRects.clear()

        for (i in items.indices) {
            val rect = RectF(startX, startY, startX + boxWidth, startY + itemHeight)
            itemRects[i] = rect

            val isActive = (i == currentActive)
            p.style = Paint.Style.FILL; p.color = if (isActive) 0xFF003333.toInt() else 0xFF111111.toInt()
            c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, p)

            p.style = Paint.Style.STROKE; p.color = if (isActive) GameColors.PULSE else 0xFF555555.toInt()
            c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, p)

            pText.textAlign = Paint.Align.LEFT
            pText.color = if (isActive) GameColors.CLARITY else 0xFFAAAAAA.toInt()
            pText.textSize = if(isPortrait) scale * 0.05f else scale * 0.045f

            c.drawText(
                if(isActive) "[ACTIVE] ${items[i]}" else "[ ] ${items[i]}",
                rect.left + scale * 0.05f,
                rect.centerY() + pText.textSize / 3f,
                pText
            )

            startY += itemHeight + spacing
        }
    }

    fun onTouch(x: Float, y: Float, action: Int, gs: GameState, onBack: () -> Unit): Boolean {
        if (action == MotionEvent.ACTION_UP) {
            // Disconnect or Back Button
            if (closeBtnRect.contains(x, y)) {
                EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 100)
                if (currentTab == 0) onBack() else currentTab = 0
                return true
            }

            // Folder interactions
            if (currentTab == 0) {
                if (weaponDirRect.contains(x, y)) {
                    EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 100)
                    currentTab = 1
                    return true
                }
                if (trapDirRect.contains(x, y)) {
                    EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 100)
                    currentTab = 2
                    return true
                }
            } else {
                for ((index, rect) in itemRects) {
                    if (rect.contains(x, y)) {
                        EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 150)
                        if (currentTab == 1) {
                            gs.currentWeapon = index
                            gs.showGlobalMessage("WEAPON PROTOCOL UPDATED.", 1.5f)
                        }
                        if (currentTab == 2) {
                            gs.currentTrap = index
                            gs.showGlobalMessage("TRAP MODULE LOADED.", 1.5f)
                        }
                        currentTab = 0 // Go back to main OS screen
                        return true
                    }
                }
            }
        }
        return true
    }

    private fun getWeaponName(id: Int) = when(id) { 1 -> "Shotgun"; 2 -> "Sniper"; else -> "Blaster" }
    private fun getTrapName(id: Int) = when(id) { 0 -> "Camouflage"; 1 -> "Decoy"; 2 -> "EMP Mine"; else -> "None" }
}