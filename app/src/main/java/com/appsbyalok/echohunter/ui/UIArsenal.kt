package com.appsbyalok.echohunter.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ToneGenerator
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.ui.arsenal.HardwareTreeRenderer
import com.appsbyalok.echohunter.ui.components.UIMenuButton
import com.appsbyalok.echohunter.ui.components.UIMenuMetrics
import com.appsbyalok.echohunter.ui.components.UIMenuScreenChrome
import com.appsbyalok.echohunter.utils.EchoAudioManager
import com.appsbyalok.echohunter.utils.GameColors

class UIArsenal {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = TextPaint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val treeRenderer = HardwareTreeRenderer()
    private val chrome = UIMenuScreenChrome()
    private val closeButton = UIMenuButton()
    private val actionButton = UIMenuButton()
    private val secondaryButton = UIMenuButton()
    private val resetButton = UIMenuButton()
    
    // Track hit zones for stat upgrades
    private val statButtons = mutableMapOf<String, RectF>()

    private var hitOnDown: String? = null
    private var isClosePressed = false

    fun hasAffordableAction(): Boolean = treeRenderer.hasAffordableAction(SaveManager.dataCoinsKB)

    fun draw(c: Canvas, targetW: Float, targetH: Float, scale: Float, gs: GameState) {
        val metrics = UIMenuMetrics(targetW, targetH, scale)
        chrome.drawBackground(c, metrics, p, bgColor = 0xEE051015.toInt())

        val insetT = metrics.insetTop; val insetL = metrics.insetLeft
        val insetR = metrics.insetRight; val insetB = metrics.insetBottom

        // --- HEADER ---
        p.style = Paint.Style.FILL; p.color = GameColors.PULSE
        val headerHeight = metrics.headerHeight
        c.drawRect(0f, 0f, targetW, headerHeight, p)
        pText.color = GameColors.BG; pText.textSize = scale * 0.045f; pText.textAlign = Paint.Align.LEFT
        val headerText = "root@probe-7:/sys/loadout ~ [HARDWARE_EVOLUTION]"
        val headerTextX = scale * 0.05f + insetL
        val availableWidth = targetW - headerTextX - insetR - (scale * 0.08f)
        val truncatedText = TextUtils.ellipsize(headerText, pText, availableWidth.coerceAtLeast(0f), TextUtils.TruncateAt.END)
        c.drawText(truncatedText.toString(), headerTextX, insetT + (headerHeight - insetT) * 0.65f, pText)

        // --- LAYOUT ---
        val isPortrait = targetH > targetW
        val contentY = headerHeight + scale * 0.02f
        val contentH = targetH - contentY - scale * 0.15f - insetB
        
        if (isPortrait) {
            val schematicH = contentH * 0.5f
            treeRenderer.draw(c, insetL + scale * 0.02f, contentY, targetW - insetL - insetR - scale * 0.04f, schematicH, scale, gs, hitOnDown)
            drawDetails(c, insetL + scale * 0.02f, contentY + schematicH + scale * 0.02f, targetW - insetL - insetR - scale * 0.04f, contentH - schematicH - scale * 0.02f, scale)
        } else {
            val schematicW = targetW * 0.6f
            treeRenderer.draw(c, insetL + scale * 0.02f, contentY, schematicW - insetL - scale * 0.04f, contentH, scale, gs, hitOnDown)
            drawDetails(c, schematicW + scale * 0.02f, contentY, targetW - schematicW - insetR - scale * 0.04f, contentH, scale)
        }

        // --- DISCONNECT BUTTON ---
        val btnWidth = if (isPortrait) targetW * 0.7f else scale * 0.4f
        val btnHeight = scale * 0.09f
        closeButton.set(targetW / 2f - btnWidth / 2f, targetH - btnHeight - scale * 0.05f - insetB, targetW / 2f + btnWidth / 2f, targetH - scale * 0.05f - insetB)
        closeButton.draw(c, scale, p, pText, "DISCONNECT", isClosePressed, 0xFF330000.toInt(), GameColors.RED, GameColors.RED, scale * 0.02f, scale * 0.045f)
    }

    private fun drawDetails(c: Canvas, x: Float, y: Float, w: Float, h: Float, scale: Float) {
        val selected = treeRenderer.getSelectedNode() ?: return
        statButtons.clear()
        
        p.color = 0x2200FFFF; p.style = Paint.Style.FILL
        c.drawRect(x, y, x + w, y + h, p)
        p.style = Paint.Style.STROKE; p.color = GameColors.PULSE
        c.drawRect(x, y, x + w, y + h, p)

        val tx = x + scale * 0.03f
        var ty = y + scale * 0.05f

        pText.textAlign = Paint.Align.LEFT
        pText.color = GameColors.PULSE; pText.textSize = scale * 0.04f
        c.drawText(selected.name, tx, ty, pText)
        
        ty += scale * 0.04f
        pText.textAlign = Paint.Align.LEFT
        pText.color = GameColors.CLARITY; pText.textSize = scale * 0.022f
        val lines = wrapText(selected.description, w - scale * 0.06f, pText)
        for (line in lines) {
            c.drawText(line, tx, ty, pText)
            ty += scale * 0.03f
        }

        if (selected.isUnlocked) {
            ty += scale * 0.02f
            pText.textAlign = Paint.Align.LEFT
            val integrityColor = if (selected.integrity > 50) GameColors.HP else GameColors.RED
            pText.color = integrityColor; pText.textSize = scale * 0.025f
            c.drawText("INTEGRITY: ${selected.integrity.toInt()}%", tx, ty, pText)

            if (selected.integrity < 100f) {
                val rW = scale * 0.25f
                secondaryButton.set(x + w - rW - scale * 0.03f, ty - scale * 0.035f, x + w - scale * 0.03f, ty + scale * 0.015f)
                secondaryButton.draw(c, scale, p, pText, "REPAIR (${selected.getRepairCost()})", hitOnDown == "REPAIR", 0xFF332200.toInt(), GameColors.YELLOW, GameColors.YELLOW, scale * 0.005f, scale * 0.018f)
                pText.textAlign = Paint.Align.LEFT
            }

            // Since REPAIR and INTEGRITY are drawn together, let's advance ty to next line
            ty += scale * 0.05f

            if (selected.exclusiveGroup != null) {
                val resetW = scale * 0.25f
                val resetH = scale * 0.05f
                resetButton.set(x + w - resetW - scale * 0.03f, ty - scale * 0.025f, x + w - scale * 0.03f, ty + resetH - scale * 0.025f)
                resetButton.draw(c, scale, p, pText, "RESET ROUTE", hitOnDown == "RESET", 0xFF330000.toInt(), GameColors.RED, GameColors.RED, scale * 0.005f, scale * 0.016f)
                pText.textAlign = Paint.Align.LEFT
                // Advance ty dynamically to clear the button and provide clearance
                ty += resetH + scale * 0.01f
            }

            ty += scale * 0.02f
            pText.textAlign = Paint.Align.LEFT
            pText.color = GameColors.PULSE; pText.textSize = scale * 0.028f
            c.drawText("SUBSYSTEM UPGRADES:", tx, ty, pText)
            ty += scale * 0.05f

            for (stat in selected.stats) {
                pText.textAlign = Paint.Align.LEFT
                pText.color = GameColors.CLARITY; pText.textSize = scale * 0.022f

                val statText = "${stat.name} [LVL ${stat.level}/${stat.maxLevel}]"
                val availableWidth: Float

                if (stat.level < stat.maxLevel) {
                    val upW = scale * 0.22f
                    val btnX = x + w - upW - scale * 0.03f
                    availableWidth = btnX - tx - (scale * 0.02f) // Space between text and button
                } else {
                    // Position where "MAXED" text is drawn
                    val maxedTextX = x + w - scale * 0.12f
                    availableWidth = maxedTextX - tx - (scale * 0.02f)
                }

                val truncatedText = TextUtils.ellipsize(
                    statText,
                    pText,
                    availableWidth,
                    TextUtils.TruncateAt.END
                )

                c.drawText(truncatedText.toString(), tx, ty, pText)
                
                if (stat.level < stat.maxLevel) {
                    val upW = scale * 0.22f; val upH = scale * 0.045f
                    val btnX = x + w - upW - scale * 0.03f
                    val r = RectF(btnX, ty - upH * 0.7f, btnX + upW, ty + upH * 0.3f)
                    statButtons[stat.id] = r
                    
                    val canAfford = SaveManager.dataCoinsKB >= stat.getCost()
                    
                    p.color = if (hitOnDown == "UP_${stat.id}") Color.WHITE else 0xFF003300.toInt()
                    p.style = Paint.Style.FILL
                    c.drawRect(r, p)
                    p.style = Paint.Style.STROKE; p.color = GameColors.PULSE; p.strokeWidth = 2f
                    c.drawRect(r, p)
                    
                    // Highlight first hardware upgrade
                    if (!SaveManager.isFirstHardwareUpgradeDone && canAfford) {
                        p.color = GameColors.HP
                        p.strokeWidth = scale * 0.006f
                        val pulse = (System.currentTimeMillis() % 1000) / 1000f
                        p.alpha = (255 * (1f - pulse)).toInt()
                        val pad = scale * 0.005f + pulse * scale * 0.01f
                        c.drawRect(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad, p)
                        p.alpha = 255
                    }
                    
                    pText.textAlign = Paint.Align.CENTER
                    pText.color = GameColors.PULSE; pText.textSize = scale * 0.018f
                    c.drawText("UP (${stat.getCost()})", btnX + upW/2f, ty - scale * 0.005f, pText)
                    pText.textAlign = Paint.Align.LEFT
                } else {
                    pText.color = GameColors.HP
                    c.drawText("MAXED", x + w - scale * 0.12f, ty, pText)
                }
                ty += scale * 0.055f
            }
        } else {
            val canUnlock = treeRenderer.canUnlock(selected)
            ty += scale * 0.05f
            if (canUnlock) {
                pText.textAlign = Paint.Align.LEFT
                pText.color = GameColors.YELLOW; pText.textSize = scale * 0.03f
                c.drawText("INITIALIZATION COST: ${selected.cost} KB", tx, ty, pText)
                
                val abW = w * 0.8f; val abH = scale * 0.07f
                actionButton.set(x + w/2f - abW/2f, y + h - abH - scale * 0.03f, x + w/2f + abW/2f, y + h - scale * 0.03f)
                actionButton.draw(c, scale, p, pText, "INITIALIZE HARDWARE", hitOnDown == "INITIALIZE", 0xFF003300.toInt(), GameColors.PULSE, GameColors.PULSE, scale * 0.01f, scale * 0.035f)
            } else {
                pText.color = GameColors.RED; pText.textSize = scale * 0.025f
                c.drawText("ERROR: PRE-REQUISITE SYSTEMS OFFLINE", tx, ty, pText)
            }
        }
    }

    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            } else {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        return lines
    }

    fun onTouch(x: Float, y: Float, action: Int, gs: GameState, onBack: () -> Unit): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                isClosePressed = closeButton.contains(x, y)
                val node = treeRenderer.hitTest(x, y)
                if (node != null) {
                    hitOnDown = node.id
                } else if (actionButton.contains(x, y)) {
                    hitOnDown = "INITIALIZE"
                } else if (secondaryButton.contains(x, y)) {
                    hitOnDown = "REPAIR"
                } else if (treeRenderer.getSelectedNode()?.exclusiveGroup != null && resetButton.contains(x, y)) {
                    hitOnDown = "RESET"
                } else {
                    for ((id, rect) in statButtons) {
                        if (rect.contains(x, y)) {
                            hitOnDown = "UP_$id"
                            break
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isClosePressed && closeButton.contains(x, y)) {
                    EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 100)
                    onBack()
                } else if (hitOnDown != null) {
                    val selected = treeRenderer.getSelectedNode()
                    if (hitOnDown == "INITIALIZE" && actionButton.contains(x, y)) {
                        if (selected != null && !selected.isUnlocked) {
                            if (SaveManager.spendData(selected.cost.toLong())) {
                                selected.isUnlocked = true
                                SaveManager.unlockNode(selected.id)
                                EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 150)
                                gs.showGlobalMessage("${selected.name} ONLINE.", 1.5f)
                            } else {
                                EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 100)
                                gs.showGlobalMessage("INSUFFICIENT DATA.", 1.2f)
                            }
                        }
                    } else if (hitOnDown == "REPAIR" && secondaryButton.contains(x, y)) {
                        if (selected != null && selected.integrity < 100f) {
                            val cost = selected.getRepairCost()
                            if (SaveManager.spendData(cost.toLong())) {
                                selected.integrity = 100f
                                selected.isDamaged = false
                                SaveManager.setIntegrity(selected.id, 100f)
                                EchoAudioManager.playSound(ToneGenerator.TONE_SUP_PIP, 100)
                                gs.showGlobalMessage("${selected.name} REPAIRED.", 1.2f)
                            } else {
                                EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 100)
                                gs.showGlobalMessage("INSUFFICIENT DATA.", 1.2f)
                            }
                        }
                    } else if (hitOnDown == "RESET" && resetButton.contains(x, y)) {
                        if (selected != null && selected.exclusiveGroup != null) {
                            val (affected, refund) = treeRenderer.resetSubtree(selected)
                            SaveManager.resetHardwareNodes(affected.map { it.id })
                            SaveManager.addData(refund)
                            treeRenderer.selectNode(null)
                            EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 100)
                            gs.showGlobalMessage("ROUTE RESET. $refund KB RECOVERED.", 1.5f)
                        }
                    } else if (hitOnDown!!.startsWith("UP_")) {
                        val statId = hitOnDown!!.substring(3)
                        val stat = selected?.stats?.find { it.id == statId }
                        if (stat != null && statButtons[statId]?.contains(x, y) == true) {
                            val cost = stat.getCost()
                            if (SaveManager.spendData(cost.toLong())) {
                                stat.level++
                                SaveManager.setFirstHardwareUpgradeDone(true)
                                SaveManager.setStatLevel(selected.id, stat.id, stat.level)
                                EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 100)
                                gs.showGlobalMessage("${stat.name} UPGRADED TO LVL ${stat.level}.", 1.2f)
                            } else {
                                EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 100)
                                gs.showGlobalMessage("INSUFFICIENT DATA.", 1.2f)
                            }
                        }
                    } else {
                        val node = treeRenderer.hitTest(x, y)
                        if (node != null && node.id == hitOnDown) {
                            treeRenderer.selectNode(node.id)
                            EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 100)
                        }
                    }
                }
                isClosePressed = false
                hitOnDown = null
            }
        }
        return true
    }

    fun handleBack(): Boolean = false
}
