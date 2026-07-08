package com.appsbyalok.echohunter.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ToneGenerator
import android.view.MotionEvent
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.data.UpgradeSystem
import com.appsbyalok.echohunter.data.UpgradeType
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.EchoAudioManager
import com.appsbyalok.echohunter.utils.GameColors
import kotlin.math.abs
import kotlin.math.max

class UIDecompiler {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private var scrollY = 0f
    private var maxScroll = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private var scrollVelocity = 0f
    private var lastTouchTime = 0L

    private var expandedType: UpgradeType? = null
    private val buyButtons = mutableMapOf<UpgradeType, RectF>()
    private val cardRects = mutableMapOf<UpgradeType, RectF>()
    private val closeBtnRect = RectF()
    private var hitOnDown = -1
    private var hitTypeOnDown: UpgradeType? = null
    private var listTop = 0f
    private var listBottom = 0f

    private val branches = listOf(
        Triple("--- [ ARCHITECT ] ---", "CORE SYSTEMS & SURVIVAL", listOf(UpgradeType.MAX_HP, UpgradeType.THRUSTER_OPTIMIZE, UpgradeType.DATA_MAGNET, UpgradeType.COMPRESSION_ALGO, UpgradeType.NANITE_REPAIR, UpgradeType.QUANTUM_CORE, UpgradeType.DATA_SYNDICATE, UpgradeType.OVERCLOCK_DUR, UpgradeType.OPTIC_SENSORS)),
        Triple("--- [ ENFORCER ] ---", "TACTICAL COMBAT & OFFENSE", listOf(UpgradeType.SPIKE_PAYLOAD, UpgradeType.CRIT_CHANCE, UpgradeType.KINETIC_OVERLOAD, UpgradeType.MULTITHREAD_SPIKES, UpgradeType.COMBO_EXTENDER)),
        Triple("--- [ GHOST ] ---", "STEALTH & SONAR RECON", listOf(UpgradeType.PULSE_FREQUENCY, UpgradeType.STEALTH_CAMO, UpgradeType.TRAP_COOLDOWN, UpgradeType.SHIELD_RECOVERY, UpgradeType.GHOST_PROTOCOL, UpgradeType.SONAR_RANGE, UpgradeType.SILENT_SONAR, UpgradeType.SONAR_DUR))
    )

    fun draw(c: Canvas, targetW: Float, targetH: Float, scale: Float) {
        c.drawColor(0xEE020502.toInt()) // Even darker terminal BG

        // --- SCANLINE EFFECT ---
        p.style = Paint.Style.STROKE
        p.strokeWidth = scale * 0.002f
        p.color = 0x0AFFFFFF
        var slY = 0f
        while (slY < targetH) {
            c.drawLine(0f, slY, targetW, slY, p)
            slY += scale * 0.012f
        }

        if (!isDragging && abs(scrollVelocity) > 0.5f) {
            scrollY += scrollVelocity
            scrollVelocity *= 0.95f // Slightly faster friction

            if (scrollY > 0f) {
                scrollY = 0f
                scrollVelocity = 0f
            } else if (scrollY < -maxScroll) {
                scrollY = -maxScroll
                scrollVelocity = 0f
            }
        }

        // --- Header ---
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = scale * 0.08f
        pText.color = GameColors.HP
        pText.setShadowLayer(15f, 0f, 0f, GameColors.HP)
        c.drawText("DECOMPILER v4.0", targetW / 2f, scale * 0.12f, pText)
        pText.clearShadowLayer()

        pText.textSize = scale * 0.035f
        pText.color = GameColors.CLARITY
        c.drawText("AVAILABLE DATA: ${SaveManager.formatDataString(SaveManager.dataCoinsKB)}", targetW / 2f, scale * 0.18f, pText)

        // --- Tier Labels & List ---
        listTop = scale * 0.22f
        listBottom = targetH - scale * 0.16f
        
        c.save()
        c.clipRect(0f, listTop, targetW, listBottom)

        val startY = scale * 0.25f + scrollY
        var currentY = startY
        val marginX = scale * 0.05f

        buyButtons.clear()
        cardRects.clear()

        for ((branchName, branchDesc, types) in branches) {
            // Branch Header
            pText.textAlign = Paint.Align.CENTER
            pText.textSize = scale * 0.045f
            pText.color = GameColors.PULSE
            c.drawText(branchName, targetW / 2f, currentY + scale * 0.04f, pText)
            
            pText.textSize = scale * 0.025f
            pText.color = GameColors.YELLOW
            c.drawText(branchDesc, targetW / 2f, currentY + scale * 0.075f, pText)
            
            currentY += scale * 0.11f

            for (type in types) {
                val config = UpgradeSystem.catalog[type] ?: continue
                val isExpanded = expandedType == type
                
                // Dimensions & Widths
                val cardWidth = targetW - 2 * marginX
                val internalPadding = scale * 0.04f
                val textWidth = cardWidth - internalPadding * 2f
                
                val btnW = scale * 0.26f
                val btnH = scale * 0.14f
                val maxHeaderW = cardWidth - btnW - scale * 0.12f // Gap for button

                pText.textSize = scale * 0.028f
                val funcH = measureWrappedTextHeight("FUNCTION: ${config.descStr}", textWidth, pText)
                val tactH = measureWrappedTextHeight("TACTICAL: ${config.usageStr}", textWidth, pText)
                val loreH = measureWrappedTextHeight("LOG: \"${config.loreStr}\"", textWidth, pText)
                
                val currentItemH = if (isExpanded) {
                    scale * 0.28f + funcH + tactH + loreH + scale * 0.12f 
                } else scale * 0.22f
                
                val itemRect = RectF(marginX, currentY, targetW - marginX, currentY + currentItemH - scale * 0.02f)
                cardRects[type] = itemRect

                val currentLvl = UpgradeSystem.getLevel(type)
                val isMaxed = currentLvl >= config.maxLevel
                val cost = UpgradeSystem.getNextLevelCost(type)
                val canAfford = !isMaxed && SaveManager.dataCoinsKB >= cost

                // Draw Background Box
                p.style = Paint.Style.FILL
                p.color = if (isExpanded) 0xFF0D180D.toInt() else if (isMaxed) 0xFF051105.toInt() else 0xFF0A0A0A.toInt()
                c.drawRoundRect(itemRect, scale * 0.01f, scale * 0.01f, p)

                // Draw Border
                p.style = Paint.Style.STROKE
                p.color = if (isExpanded) GameColors.HP else if (isMaxed) 0xFF004400.toInt() else if (canAfford) GameColors.PULSE else 0xFF444444.toInt()
                p.strokeWidth = if (isExpanded) scale * 0.006f else scale * 0.004f
                c.drawRoundRect(itemRect, scale * 0.01f, scale * 0.01f, p)

                // Inject Button (Top Right)
                val btnRect = RectF(itemRect.right - btnW - scale*0.02f, itemRect.top + scale*0.02f, itemRect.right - scale*0.02f, itemRect.top + btnH + scale*0.02f)
                
                // Only register click for button if not maxed
                if (!isMaxed) buyButtons[type] = btnRect

                // Draw Title (Dynamic resizing & Truncation)
                pText.textAlign = Paint.Align.LEFT
                pText.color = if (isMaxed) GameColors.HP else GameColors.CLARITY
                var titleSize = scale * 0.042f
                pText.textSize = titleSize
                
                var titleText = "> ${config.nameStr}"
                while (pText.measureText(titleText) > maxHeaderW && titleSize > scale * 0.03f) {
                    titleSize -= 1f
                    pText.textSize = titleSize
                }
                // Final truncation check if still too long
                if (pText.measureText(titleText) > maxHeaderW) {
                    while (titleText.length > 5 && pText.measureText("$titleText...") > maxHeaderW) {
                        titleText = titleText.dropLast(1)
                    }
                    titleText += "..."
                }
                c.drawText(titleText, marginX + internalPadding, currentY + scale * 0.055f, pText)
                
                pText.textSize = scale * 0.025f
                pText.color = 0xFF888888.toInt()
                c.drawText("Ver: $currentLvl/${config.maxLevel}", marginX + internalPadding, currentY + scale * 0.09f, pText)

                if (isExpanded) {
                    // Expanded Details (Starts below button)
                    var nextY = currentY + scale * 0.20f

                    pText.textSize = scale * 0.028f
                    pText.color = GameColors.CLARITY
                    nextY = drawWrappedText(c, "FUNCTION: ${config.descStr}", marginX + internalPadding, nextY, textWidth, pText)

                    pText.color = GameColors.YELLOW
                    nextY += scale * 0.015f
                    nextY = drawWrappedText(c, "TACTICAL: ${config.usageStr}", marginX + scale * 0.04f, nextY, textWidth, pText)

                    pText.color = 0xFF666666.toInt()
                    nextY += scale * 0.015f
                    drawWrappedText(c, "LOG: \"${config.loreStr}\"", marginX + scale * 0.04f, nextY, textWidth, pText)

                    // Bottom Status
                    pText.color = GameColors.HP
                    pText.textSize = scale * 0.024f
                    pText.textAlign = Paint.Align.LEFT
                    val statsText = if (isMaxed) "SYSTEM FULLY OPTIMIZED." else "UPGRADE TO INJECT NEXT FIRMWARE LAYER."
                    c.drawText(statsText, marginX + scale * 0.04f, itemRect.bottom - scale * 0.035f, pText)
                } else {
                    // Collapsed Description
                    pText.textSize = scale * 0.026f
                    pText.color = 0xFFAAAAAA.toInt()
                    var shortDesc = config.descStr
                    if (pText.measureText(shortDesc) > maxHeaderW) {
                        while (shortDesc.length > 5 && pText.measureText("$shortDesc...") > maxHeaderW) {
                            shortDesc = shortDesc.dropLast(1)
                        }
                        shortDesc += "..."
                    }
                    c.drawText(shortDesc, marginX + scale * 0.04f, currentY + scale * 0.13f, pText)
                    
                    pText.textSize = scale * 0.022f
                    pText.color = 0xFF555555.toInt()
                    c.drawText(if (isMaxed) "MAX LEVEL REACHED" else "TAP FOR DETAILS", marginX + scale * 0.04f, currentY + scale * 0.17f, pText)
                }

                // Draw Button (ONLY IF NOT MAXED OR NOT EXPANDED to avoid overlap and redundancy)
                if (!isMaxed || !isExpanded) {
                    val isPressed = hitOnDown == 2 && hitTypeOnDown == type
                    
                    p.style = Paint.Style.FILL
                    val btnBaseColor = if (isMaxed) 0xFF002200.toInt() 
                                      else if (canAfford) 0xFF002222.toInt() 
                                      else 0xFF220000.toInt()
                    
                    p.color = if (isPressed) GameColors.mixColors(btnBaseColor, 0xFFFFFFFF.toInt(), 0.3f) else btnBaseColor
                    c.drawRoundRect(btnRect, scale*0.005f, scale*0.005f, p)

                    p.style = Paint.Style.STROKE
                    p.color = if (isMaxed) GameColors.HP else if (canAfford) GameColors.PULSE else GameColors.RED
                    c.drawRoundRect(btnRect, scale*0.005f, scale*0.005f, p)

                    pText.textAlign = Paint.Align.CENTER
                    pText.color = p.color
                    if (isPressed) pText.setShadowLayer(8f, 0f, 0f, p.color)
                    pText.textSize = scale * 0.028f

                    val btnLabel = if (isMaxed) "OPTIMIZED" else "COMPILE\n${SaveManager.formatDataString(cost)}"
                    val lines = btnLabel.split("\n")
                    if (lines.size == 1) {
                        c.drawText(lines[0], btnRect.centerX(), btnRect.centerY() + scale * 0.01f, pText)
                    } else {
                        c.drawText(lines[0], btnRect.centerX(), btnRect.centerY() - scale * 0.01f, pText)
                        pText.textSize = scale * 0.024f
                        c.drawText(lines[1], btnRect.centerX(), btnRect.centerY() + scale * 0.025f, pText)
                    }
                    pText.clearShadowLayer()
                }

                currentY += currentItemH
            }
            currentY += scale * 0.05f // Branch spacing
        }
        
        c.restore()

        val totalListHeight = currentY - scrollY
        maxScroll = max(0f, totalListHeight - targetH + scale * 0.2f)

        // --- DISCONNECT BUTTON ---
        closeBtnRect.set(targetW / 2f - scale * 0.2f, targetH - scale * 0.12f, targetW / 2f + scale * 0.2f, targetH - scale * 0.03f)
        p.style = Paint.Style.FILL; p.color = if (hitOnDown == 1) 0xFF660A0A.toInt() else 0xFF330000.toInt()
        c.drawRoundRect(closeBtnRect, scale * 0.02f, scale * 0.02f, p)
        p.style = Paint.Style.STROKE; p.color = GameColors.RED
        c.drawRoundRect(closeBtnRect, scale * 0.02f, scale * 0.02f, p)

        pText.textAlign = Paint.Align.CENTER; pText.color = GameColors.RED; pText.textSize = scale * 0.04f
        if (hitOnDown == 1) pText.setShadowLayer(10f, 0f, 0f, GameColors.RED)
        c.drawText("DISCONNECT", closeBtnRect.centerX(), closeBtnRect.centerY() + scale * 0.015f, pText)
        pText.clearShadowLayer()
    }

    private var touchDownX = 0f
    private var touchDownY = 0f

    fun onTouch(x: Float, y: Float, action: Int, scale: Float, gs: GameState, onBack: () -> Unit): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = x
                touchDownY = y
                lastTouchY = y
                lastTouchTime = System.currentTimeMillis()
                scrollVelocity = 0f
                isDragging = false

                hitOnDown = when {
                    closeBtnRect.contains(x, y) -> 1
                    else -> 0
                }

                hitTypeOnDown = null
                if (hitOnDown == 0 && y >= listTop && y <= listBottom) {
                    for ((type, rect) in buyButtons) {
                        if (rect.contains(x, y)) {
                            hitTypeOnDown = type
                            hitOnDown = 2
                            break
                        }
                    }
                    if (hitTypeOnDown == null) {
                        for ((type, rect) in cardRects) {
                            if (rect.contains(x, y)) {
                                hitTypeOnDown = type
                                hitOnDown = 3
                                break
                            }
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val currentTime = System.currentTimeMillis()
                val dt = currentTime - lastTouchTime
                val dy = y - lastTouchY
                val dx = x - touchDownX

                val distSq = dx * dx + (y - touchDownY) * (y - touchDownY)
                val threshold = scale * scale * 0.05f

                if (hitOnDown == 1) {
                    // Started on Disconnect button: don't scroll and don't cancel easily
                    if (distSq > threshold) {
                        hitOnDown = -1
                    }
                } else {
                    if (abs(dy) > scale * 0.02f) {
                        isDragging = true
                        hitOnDown = -1
                        hitTypeOnDown = null
                    } else if (distSq > threshold) {
                        hitOnDown = -1
                        hitTypeOnDown = null
                    }

                    if (isDragging || hitOnDown == 0) {
                        if (dt > 0) {
                            val rawVelocity = (dy / dt.toFloat()) * 20f
                            scrollVelocity = (scrollVelocity * 0.4f) + (rawVelocity * 0.6f)
                        }

                        scrollY += dy
                        if (scrollY > 0f) {
                            scrollY = 0f
                            scrollVelocity = 0f
                        }
                        if (scrollY < -maxScroll) {
                            scrollY = -maxScroll
                            scrollVelocity = 0f
                        }
                    }
                }

                lastTouchY = y
                lastTouchTime = currentTime
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging && hitOnDown != -1) {
                    val hitOnUp = when {
                        closeBtnRect.contains(x, y) -> 1
                        else -> 0
                    }

                    var hitTypeOnUp: UpgradeType? = null
                    var upType = 0
                    if (hitOnUp == 0 && y >= listTop && y <= listBottom) {
                        for ((type, rect) in buyButtons) {
                            if (rect.contains(x, y)) {
                                hitTypeOnUp = type
                                upType = 2
                                break
                            }
                        }
                        if (hitTypeOnUp == null) {
                            for ((type, rect) in cardRects) {
                                if (rect.contains(x, y)) {
                                    hitTypeOnUp = type
                                    upType = 3
                                    break
                                }
                            }
                        }
                    }

                    if (hitOnUp == 1 && hitOnDown == 1) {
                        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 100)
                        onBack()
                    } else if (upType != 0 && upType == hitOnDown && hitTypeOnUp == hitTypeOnDown) {
                        if (upType == 2) {
                            if (UpgradeSystem.purchaseUpgrade(hitTypeOnUp!!)) {
                                EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 150)
                                gs.showGlobalMessage("SCRIPT INJECTED.\nFIRMWARE OVERRIDDEN.", 2f)
                            } else {
                                EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 100)
                                gs.showGlobalMessage("ERROR: INSUFFICIENT DATA.\nREQUIRE MORE KILOBYTES.", 2f)
                            }
                        } else { // upType == 3
                            expandedType = if (expandedType == hitTypeOnUp) null else hitTypeOnUp
                            EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_PIP, 50)
                        }
                    }
                }
                isDragging = false
                hitOnDown = -1
                hitTypeOnDown = null
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                hitOnDown = -1
                hitTypeOnDown = null
            }
        }
        return true
    }

    private fun measureWrappedTextHeight(text: String, maxWidth: Float, paint: Paint): Float {
        val words = text.split(" ")
        var line = ""
        var lines = 0
        val lineHeight = paint.textSize * 1.3f
        for (word in words) {
            val testLine = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(testLine) > maxWidth) {
                if (line.isNotEmpty()) lines++
                line = word
            } else {
                line = testLine
            }
        }
        if (line.isNotEmpty()) lines++
        return lines * lineHeight
    }

    private fun drawWrappedText(c: Canvas, text: String, x: Float, y: Float, maxWidth: Float, paint: Paint): Float {
        val words = text.split(" ")
        var line = ""
        var curY = y
        val lineHeight = paint.textSize * 1.3f
        for (word in words) {
            val testLine = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(testLine) > maxWidth) {
                if (line.isNotEmpty()) {
                    c.drawText(line, x, curY, paint)
                    curY += lineHeight
                }
                line = word
            } else {
                line = testLine
            }
        }
        if (line.isNotEmpty()) {
            c.drawText(line, x, curY, paint)
            curY += lineHeight
        }
        return curY
    }
}