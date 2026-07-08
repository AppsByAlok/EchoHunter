package com.appsbyalok.echohunter.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ToneGenerator
import android.view.MotionEvent
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.input.AttackMode
import com.appsbyalok.echohunter.utils.EchoAudioManager
import com.appsbyalok.echohunter.utils.GameColors
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
    private val attackModeRect = RectF()

    // 0 = Main OS, 1 = Weapons Folder, 2 = Traps Folder, 3 = Attack Mode Folder
    private var currentTab = 0
    private val itemReacts = mutableMapOf<Int, RectF>()
    private var hitOnDown = -1

    fun draw(c: Canvas, targetW: Float, targetH: Float, scale: Float, gs: GameState) {
        c.drawColor(0xEE051015.toInt()) // Dark Cyan-ish Terminal BG

        // --- SCANLINE EFFECT ---
        p.style = Paint.Style.STROKE
        p.strokeWidth = scale * 0.002f
        p.color = 0x0AFFFFFF
        var slY = 0f
        while (slY < targetH) {
            c.drawLine(0f, slY, targetW, slY, p)
            slY += scale * 0.012f
        }

        // --- NANO OS HEADER (Responsive) ---
        p.style = Paint.Style.FILL; p.color = GameColors.PULSE
        val headerHeight = if (targetH > targetW) scale * 0.12f else scale * 0.08f // Adjust header height for portrait
        c.drawRect(0f, 0f, targetW, headerHeight, p)

        pText.color = GameColors.BG; pText.textSize = scale * 0.04f
        pText.textAlign = Paint.Align.LEFT
        c.drawText("root@probe-7:/mnt/arsenal_loadout ~", scale * 0.05f, headerHeight * 0.65f, pText)

        // Route to active tab
        when (currentTab) {
            0 -> {
                drawMainScreen(c, targetW, targetH, scale, gs)
            }

            1 -> {
                drawWeaponFolder(c, targetW, targetH, scale, gs)
            }

            2 -> {
                drawTrapFolder(c, targetW, targetH, scale, gs)
            }

            3 -> {
                drawAttackModeFolder(c, targetW, targetH, scale, gs)
            }
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

        val isPressed = (hitOnDown == 100)
        val baseRed = 0xFF330000.toInt()
        p.style = Paint.Style.FILL; p.color = if (isPressed) GameColors.mixColors(baseRed, GameColors.RED, 0.4f) else baseRed
        c.drawRoundRect(closeBtnRect, scale * 0.02f, scale * 0.02f, p)
        p.style = Paint.Style.STROKE; p.color = GameColors.RED; p.strokeWidth = scale * 0.005f
        c.drawRoundRect(closeBtnRect, scale * 0.02f, scale * 0.02f, p)

        pText.color = GameColors.RED; pText.textSize = scale * 0.045f
        if (isPressed) pText.setShadowLayer(10f, 0f, 0f, GameColors.RED)
        c.drawText(
            if (currentTab == 0) "DISCONNECT" else "< BACK",
            closeBtnRect.centerX(),
            closeBtnRect.centerY() + scale * 0.015f,
            pText
        )
        pText.clearShadowLayer()
    }

    private fun drawMainScreen(c: Canvas, targetW: Float, targetH: Float, scale: Float, gs: GameState) {
        val isPortrait = targetH > targetW

        // Dynamic placements based on orientation
        val droneX = if (isPortrait) targetW * 0.5f else targetW * 0.3f
        val droneY = if (isPortrait) targetH * 0.22f else targetH * 0.5f

        // Folders placements
        val dirX = if (isPortrait) targetW * 0.5f else targetW * 0.75f
        val dirYStart = if (isPortrait) targetH * 0.38f else targetH * 0.22f
        val boxW = if (isPortrait) targetW * 0.85f else scale * 0.65f
        val boxH = if (isPortrait) scale * 0.16f else scale * 0.14f
        val spacing = scale * 0.04f

        // --- Drone Wireframe Graphic ---
        p.style = Paint.Style.STROKE; p.color = 0x5500FFFF; p.strokeWidth = scale * 0.005f
        c.drawCircle(droneX, droneY, scale * 0.12f, p)
        c.drawRect(droneX - scale*0.04f, droneY - scale*0.04f, droneX + scale*0.04f, droneY + scale*0.04f, p)

        p.style = Paint.Style.FILL; p.color = GameColors.PULSE
        c.drawCircle(droneX, droneY, scale * 0.02f + sin(gs.timeSinceStart * 5f) * scale * 0.01f, p)

        pText.textAlign = Paint.Align.CENTER; pText.color = GameColors.CLARITY
        pText.textSize = scale * 0.04f
        c.drawText("PROBE-7 HARDWARE STATUS", droneX, droneY - scale * 0.18f, pText)

        // --- OS Folders (Directories) ---
        // Weapons Folder
        weaponDirRect.set(dirX - boxW/2f, dirYStart, dirX + boxW/2f, dirYStart + boxH)
        drawFolderBox(c, weaponDirRect, "WEAPONS.dir", getWeaponName(gs.controls.currentWeapon), scale, isPortrait)

        // Traps Folder
        trapDirRect.set(dirX - boxW/2f, dirYStart + boxH + spacing, dirX + boxW/2f, dirYStart + boxH * 2 + spacing)
        drawFolderBox(c, trapDirRect, "TRAPS.dir", getTrapName(gs.controls.currentTrap), scale, isPortrait)

        // Attack Mode Folder
        attackModeRect.set(dirX - boxW/2f, dirYStart + (boxH + spacing) * 2, dirX + boxW/2f, dirYStart + boxH * 3 + spacing * 2)
        drawFolderBox(c, attackModeRect, "LOGIC_AIM.sys", gs.controls.activeAttackMode.name, scale, isPortrait)
    }

    private fun drawAttackModeFolder(c: Canvas, targetW: Float, targetH: Float, scale: Float, gs: GameState) {
        val modes = AttackMode.entries.toTypedArray()
        val names = modes.map { it.name }.toTypedArray()
        drawList(c, targetW, targetH, scale, "SELECT AIMING LOGIC", gs.controls.activeAttackMode.ordinal, names)
    }

    private fun drawFolderBox(c: Canvas, rect: RectF, title: String, activeItem: String, scale: Float, isPortrait: Boolean) {
        val hitId = when (rect) {
            weaponDirRect -> 1
            trapDirRect -> 2
            attackModeRect -> 3
            else -> -1
        }
        val isPressed = hitOnDown == hitId
        val baseColor = 0xFF051515.toInt()

        p.style = Paint.Style.FILL; p.color = if (isPressed) GameColors.mixColors(baseColor, GameColors.PULSE, 0.2f) else baseColor
        c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, p)
        p.style = Paint.Style.STROKE; p.color = if (isPressed) GameColors.CLARITY else GameColors.PULSE; p.strokeWidth = scale * 0.005f
        c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, p)

        pText.textAlign = Paint.Align.LEFT

        // Scale text up slightly for portrait mode readability
        val titleSize = if (isPortrait) scale * 0.05f else scale * 0.045f
        val subSize = if (isPortrait) scale * 0.035f else scale * 0.03f

        pText.color = p.color; pText.textSize = titleSize
        if (isPressed) pText.setShadowLayer(10f, 0f, 0f, p.color)
        c.drawText("> $title", rect.left + scale * 0.04f, rect.top + titleSize * 1.6f, pText)

        pText.color = GameColors.YELLOW; pText.textSize = subSize
        c.drawText("STATUS: $activeItem", rect.left + scale * 0.04f, rect.bottom - scale * 0.03f, pText)
        pText.clearShadowLayer()
    }

    private fun drawWeaponFolder(c: Canvas, targetW: Float, targetH: Float, scale: Float, gs: GameState) {
        drawList(c, targetW, targetH, scale, "SELECT WEAPON PROTOCOL", gs.controls.currentWeapon, arrayOf("BLASTER (Default)", "SHOTGUN (Spread)", "SNIPER (Pierce)"))
    }

    private fun drawTrapFolder(c: Canvas, targetW: Float, targetH: Float, scale: Float, gs: GameState) {
        drawList(c, targetW, targetH, scale, "SELECT TRAP MODULE", gs.controls.currentTrap, arrayOf("CAMOUFLAGE (Stealth)", "DECOY (Hologram)", "EMP MINE (Explosive)"))
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

        itemReacts.clear()

        for (i in items.indices) {
            val rect = RectF(startX, startY, startX + boxWidth, startY + itemHeight)
            itemReacts[i] = rect

            val isActive = (i == currentActive)
            val isPressed = (hitOnDown == i)
            
            val baseItemColor = if (isActive) 0xFF002222.toInt() else 0xFF111111.toInt()
            p.style = Paint.Style.FILL; p.color = if (isPressed) GameColors.mixColors(baseItemColor, GameColors.CLARITY, 0.3f) else baseItemColor
            c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, p)

            p.style = Paint.Style.STROKE; p.color = if (isPressed) GameColors.CLARITY else if (isActive) GameColors.PULSE else 0xFF555555.toInt()
            c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, p)

            pText.textAlign = Paint.Align.LEFT
            pText.color = if (isPressed || isActive) GameColors.CLARITY else 0xFFAAAAAA.toInt()
            pText.textSize = if(isPortrait) scale * 0.05f else scale * 0.045f
            
            if (isPressed) pText.setShadowLayer(8f, 0f, 0f, pText.color)

            c.drawText(
                if(isActive) "[ACTIVE] ${items[i]}" else "[ ] ${items[i]}",
                rect.left + scale * 0.05f,
                rect.centerY() + pText.textSize / 3f,
                pText
            )
            pText.clearShadowLayer()

            startY += itemHeight + spacing
        }
    }

    private var touchDownX = 0f
    private var touchDownY = 0f

    fun onTouch(x: Float, y: Float, action: Int, scale: Float, gs: GameState, onBack: () -> Unit): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = x
                touchDownY = y
                hitOnDown = when {
                    closeBtnRect.contains(x, y) -> 100 // Arbitrary ID for close button
                    currentTab == 0 -> {
                        when {
                            weaponDirRect.contains(x, y) -> 1
                            trapDirRect.contains(x, y) -> 2
                            attackModeRect.contains(x, y) -> 3
                            else -> -1
                        }
                    }
                    else -> {
                        var hit = -1
                        for ((index, rect) in itemReacts) {
                            if (rect.contains(x, y)) {
                                hit = index
                                break
                            }
                        }
                        hit
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - touchDownX
                val dy = y - touchDownY
                if (dx * dx + dy * dy > scale * scale * 0.05f) {
                    hitOnDown = -1
                }
            }
            MotionEvent.ACTION_UP -> {
                val hitOnUp = when {
                    closeBtnRect.contains(x, y) -> 100
                    currentTab == 0 -> {
                        when {
                            weaponDirRect.contains(x, y) -> 1
                            trapDirRect.contains(x, y) -> 2
                            attackModeRect.contains(x, y) -> 3
                            else -> -1
                        }
                    }
                    else -> {
                        var hit = -1
                        for ((index, rect) in itemReacts) {
                            if (rect.contains(x, y)) {
                                hit = index
                                break
                            }
                        }
                        hit
                    }
                }

                if (hitOnUp != -1 && hitOnUp == hitOnDown) {
                    if (hitOnUp == 100) {
                        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 100)
                        if (currentTab == 0) onBack() else currentTab = 0
                    } else if (currentTab == 0) {
                        EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 100)
                        currentTab = hitOnUp
                    } else {
                        EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 150)
                        when (currentTab) {
                            1 -> {
                                gs.controls.currentWeapon = hitOnUp
                                gs.showGlobalMessage("WEAPON PROTOCOL UPDATED.", 1.5f)
                            }
                            2 -> {
                                gs.controls.currentTrap = hitOnUp
                                gs.showGlobalMessage("TRAP MODULE LOADED.", 1.5f)
                            }
                            3 -> {
                                gs.controls.activeAttackMode = AttackMode.entries.toTypedArray()[hitOnUp]
                                gs.showGlobalMessage("AIMING LOGIC RECONFIGURED.", 1.5f)
                            }
                        }
                        currentTab = 0
                    }
                }
                hitOnDown = -1
            }
            MotionEvent.ACTION_CANCEL -> {
                hitOnDown = -1
            }
        }
        return true
    }

    private fun getWeaponName(id: Int) = when(id) { 1 -> "Shotgun"; 2 -> "Sniper"; else -> "Blaster" }
    private fun getTrapName(id: Int) = when(id) { 0 -> "Camouflage"; 1 -> "Decoy"; 2 -> "EMP Mine"; else -> "None" }
}