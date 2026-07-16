package com.appsbyalok.echohunter.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.input.HudAction
import com.appsbyalok.echohunter.input.HudControl
import com.appsbyalok.echohunter.input.HudInputBehavior
import com.appsbyalok.echohunter.input.HudLayoutProfile
import com.appsbyalok.echohunter.input.HudVisualType
import com.appsbyalok.echohunter.input.MovementMode
import com.appsbyalok.echohunter.ui.components.UIConfirmDialog
import com.appsbyalok.echohunter.utils.GameColors
import kotlin.math.sqrt

internal class UILayoutEditor {
    private val previewRect = RectF()
    private val menuButtonRect = RectF()
    private val menuCommandRects = Array(5) { RectF() }
    private val contextCommandRects = Array(4) { RectF() }
    private val deselectRect = RectF()
    private val confirmDialog = UIConfirmDialog()
    private var profile: HudLayoutProfile? = null
    private var savedProfile: HudLayoutProfile? = null
    private var portrait = true
    private var selectedId: String? = null
    private var dragging = false
    private var pinching = false
    private var pinchDistance = 0f
    private var pinchScale = 1f
    private var menuOpen = false
    private var menuDragging = false
    private var menuDownX = 0f
    private var menuDownY = 0f
    private var menuX = Float.NaN
    private var menuY = Float.NaN

    private var feedback = ""
    private var feedbackUntil = 0L

    private val globalCommands = arrayOf("ADD", "DEFAULT", "SAVED", "APPLY", "CANCEL")

    fun draw(c: Canvas, targetW: Float, targetH: Float, scale: Float, paint: Paint, text: Paint) {
        ensureProfile(targetW, targetH)
        val draft = profile ?: return
        val insetL = SaveManager.lastInsetLeft
        val insetR = SaveManager.lastInsetRight
        val insetB = SaveManager.lastInsetBottom
        val top = scale * 0.055f + SaveManager.lastInsetTop
        val footer = scale * 0.03f + insetB
        val toolbarH = scale * 0.095f
        previewRect.set(insetL + scale * 0.035f, top, targetW - insetR - scale * 0.035f, targetH - footer - toolbarH)

        paint.style = Paint.Style.FILL
        paint.color = 0xFF07111A.toInt()
        c.drawRect(previewRect, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = scale * 0.003f
        paint.color = GameColors.CLARITY
        c.drawRect(previewRect, paint)

        text.textAlign = Paint.Align.LEFT
        text.textSize = scale * 0.025f
        text.color = GameColors.CLARITY
        c.drawText("HUD LAYOUT / ${if (portrait) "PORTRAIT" else "LANDSCAPE"}", previewRect.left + scale * 0.025f, previewRect.top + scale * 0.04f, text)

        drawMovement(c, draft, scale, paint, text)
        drawManualAim(c, draft, scale, paint, text)
        draft.controls.forEach { control -> drawControl(c, control, scale, paint, text) }
        drawOverlapWarnings(c, draft, scale, paint)

        drawFloatingMenu(c, scale, paint, text)
        drawSelectionControls(c, draft, scale, paint, text)

        text.textAlign = Paint.Align.CENTER
        text.textSize = scale * 0.022f
        text.color = GameColors.TEXT
        val selected = selectedControl(draft)
        if (selected != null || selectedId == MOVEMENT_ID || selectedId == MANUAL_AIM_ID) {
            val description = when {
                selected != null -> "${selected.action.name} | ${selected.visualType.name} | ${selected.inputBehavior.name}"
                selectedId == MOVEMENT_ID -> "MOVEMENT | ${draft.movement.mode.name}"
                else -> "MANUAL AIM | ${draft.manualAim.mode.name}"
            }
            c.drawText(description, previewRect.centerX(), previewRect.bottom - scale * 0.018f, text)
        }
        if (feedbackUntil > System.currentTimeMillis()) {
            text.textAlign = Paint.Align.CENTER
            text.textSize = scale * 0.025f
            text.color = GameColors.PULSE
            c.drawText(feedback, previewRect.centerX(), previewRect.top + scale * 0.085f, text)
        }
        confirmDialog.draw(c, targetW, targetH, scale, paint, text, GameColors.PULSE)
    }

    private fun drawSelectionControls(c: Canvas, draft: HudLayoutProfile, scale: Float, paint: Paint, text: Paint) {
        if (selectedId == null) return

        // Draw Resize Handles for Zones
        if (selectedId == MOVEMENT_ID) {
            val control = draft.movement
            if (control.mode == MovementMode.FLOATING) {
                drawZoneHandles(c, control.zoneLeft, control.zoneTop, control.zoneRight, control.zoneBottom, scale, paint)
            }
        } else if (selectedId == MANUAL_AIM_ID) {
            val control = draft.manualAim
            if (control.mode == MovementMode.FLOATING) {
                drawZoneHandles(c, control.zoneLeft, control.zoneTop, control.zoneRight, control.zoneBottom, scale, paint)
            }
        }

        val labels = when {
            selectedControl(draft) != null -> {
                val selected = selectedControl(draft)!!
                if (selected.action == HudAction.ATTACK) arrayOf("ACTION", "STYLE", "INPUT", "DELETE") else arrayOf("ACTION", "STYLE", "DELETE")
            }
            selectedId == MOVEMENT_ID -> arrayOf("MOVE MODE")
            else -> arrayOf("AIM MODE")
        }
        val gap = scale * 0.012f
        val itemW = (previewRect.width() - gap * (labels.size - 1)) / labels.size
        val itemH = scale * 0.065f
        labels.indices.forEach { index ->
            val left = previewRect.left + index * (itemW + gap)
            contextCommandRects[index].set(left, previewRect.bottom + scale * 0.015f, left + itemW, previewRect.bottom + scale * 0.015f + itemH)
            drawCommand(c, contextCommandRects[index], labels[index], if (labels[index] == "DELETE") GameColors.RED else GameColors.PULSE, scale, paint, text)
        }
        for (index in labels.size until contextCommandRects.size) contextCommandRects[index].setEmpty()
    }

    fun onTouch(e: MotionEvent, targetW: Float, targetH: Float, scale: Float, onApplied: () -> Unit, onExit: () -> Unit): Boolean {
        ensureProfile(targetW, targetH)
        val draft = profile ?: return false
        val action = e.actionMasked
        val x = e.x
        val y = e.y
        if (confirmDialog.visible) return confirmDialog.onTouch(x, y, action)
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                pinching = false
                if (menuButtonRect.contains(x, y)) {
                    menuDragging = true
                    menuDownX = x
                    menuDownY = y
                    return true
                }
                if (menuOpen) {
                    menuCommandRects.indexOfFirst { it.contains(x, y) }.takeIf { it >= 0 }?.let { command ->
                        handleGlobalCommand(command, onApplied, onExit)
                        return true
                    }
                }
                if (selectedId != null && deselectRect.contains(x, y)) {
                    selectedId = null
                    return true
                }
                contextCommandRects.indexOfFirst { selectedId != null && it.contains(x, y) }.takeIf { it >= 0 }?.let { command ->
                    handleContextCommand(command)
                    return true
                }
                selectedId = hitControl(draft, x, y, scale)
                dragging = selectedId != null
                if (selectedId == null) menuOpen = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (selectedId != null && e.pointerCount >= 2) {
                    pinching = true
                    dragging = false
                    pinchDistance = distance(e)
                    pinchScale = selectedScale(draft)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (menuDragging) {
                    val radius = scale * 0.043f
                    menuX = x.coerceIn(previewRect.left + radius, previewRect.right - radius)
                    menuY = y.coerceIn(previewRect.top + radius, previewRect.bottom - radius)
                } else if (pinching && e.pointerCount >= 2) {
                    val ratio = if (pinchDistance > 0f) distance(e) / pinchDistance else 1f
                    setSelectedScale(draft, (pinchScale * ratio).coerceIn(HudLayoutProfile.MIN_CONTROL_SCALE, HudLayoutProfile.MAX_CONTROL_SCALE))
                } else if (dragging) {
                    moveSelected(draft, x, y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (menuDragging) {
                    val dx = x - menuDownX
                    val dy = y - menuDownY
                    if (action == MotionEvent.ACTION_UP && dx * dx + dy * dy < scale * scale * 0.0025f) menuOpen = !menuOpen
                    menuDragging = false
                    return true
                }
                dragging = false
                pinching = false
            }
        }
        return true
    }

    private fun ensureProfile(targetW: Float, targetH: Float) {
        val currentPortrait = targetH > targetW
        if (profile == null || portrait != currentPortrait) {
            portrait = currentPortrait
            savedProfile = SaveManager.loadHudLayoutProfile(portrait)
            profile = savedProfile?.copyMutable()
            selectedId = null
        }
    }

    private fun drawMovement(c: Canvas, draft: HudLayoutProfile, scale: Float, paint: Paint, text: Paint) {
        val selected = selectedId == MOVEMENT_ID
        val color = if (selected) GameColors.YELLOW else GameColors.PULSE
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = scale * 0.004f
        if (draft.movement.mode == MovementMode.FLOATING) {
            val zone = normalizedRect(draft.movement.zoneLeft, draft.movement.zoneTop, draft.movement.zoneRight, draft.movement.zoneBottom)
            c.drawRect(zone, paint)
            text.textAlign = Paint.Align.CENTER
            text.textSize = scale * 0.022f
            text.color = color
            c.drawText("MOVE ZONE", zone.centerX(), zone.centerY(), text)
        } else {
            val x = previewRect.left + draft.movement.x * previewRect.width()
            val y = previewRect.top + draft.movement.y * previewRect.height()
            val radius = scale * 0.10f * draft.movement.scale
            c.drawCircle(x, y, radius, paint)
            text.textAlign = Paint.Align.CENTER
            text.textSize = scale * 0.022f
            text.color = color
            c.drawText("MOVE", x, y + scale * 0.008f, text)
        }
    }

    private fun drawManualAim(c: Canvas, draft: HudLayoutProfile, scale: Float, paint: Paint, text: Paint) {
        val selected = selectedId == MANUAL_AIM_ID
        val color = if (selected) GameColors.YELLOW else GameColors.RED
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = scale * 0.004f
        if (draft.manualAim.mode == MovementMode.FLOATING) {
            val zone = normalizedRect(draft.manualAim.zoneLeft, draft.manualAim.zoneTop, draft.manualAim.zoneRight, draft.manualAim.zoneBottom)
            c.drawRect(zone, paint)
            text.textAlign = Paint.Align.CENTER
            text.textSize = scale * 0.022f
            text.color = color
            c.drawText("AIM ZONE", zone.centerX(), zone.centerY(), text)
        } else {
            val x = previewRect.left + draft.manualAim.x * previewRect.width()
            val y = previewRect.top + draft.manualAim.y * previewRect.height()
            val radius = scale * 0.10f * draft.manualAim.scale
            c.drawCircle(x, y, radius, paint)
            text.textAlign = Paint.Align.CENTER
            text.textSize = scale * 0.022f
            text.color = color
            c.drawText("AIM", x, y + scale * 0.008f, text)
        }
    }

    private fun drawControl(c: Canvas, control: HudControl, scale: Float, paint: Paint, text: Paint) {
        val x = previewRect.left + control.x * previewRect.width()
        val y = previewRect.top + control.y * previewRect.height()
        val radius = scale * 0.10f * control.scale
        val selected = selectedId == control.id
        paint.style = Paint.Style.FILL
        paint.color = if (selected) 0x6633FFFF else 0x33112233
        c.drawCircle(x, y, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = scale * 0.004f
        paint.color = if (selected) GameColors.YELLOW else GameColors.PULSE
        c.drawCircle(x, y, if (control.visualType == HudVisualType.COMPACT) radius * 0.68f else radius, paint)
        if (control.visualType != HudVisualType.COMPACT) {
            text.textAlign = Paint.Align.CENTER
            text.textSize = scale * if (control.visualType == HudVisualType.LABELED) 0.024f else 0.021f
            text.color = GameColors.CLARITY
            c.drawText(control.action.name.take(4), x, y + text.textSize * 0.35f, text)
        }
    }

    private fun drawOverlapWarnings(c: Canvas, draft: HudLayoutProfile, scale: Float, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = scale * 0.004f
        paint.color = GameColors.RED

        // Check buttons against each other
        for (i in draft.controls.indices) for (j in i + 1 until draft.controls.size) {
            val a = draft.controls[i]
            val b = draft.controls[j]
            val ax = previewRect.left + a.x * previewRect.width()
            val ay = previewRect.top + a.y * previewRect.height()
            val bx = previewRect.left + b.x * previewRect.width()
            val by = previewRect.top + b.y * previewRect.height()
            val dx = ax - bx
            val dy = ay - by
            val minDistance = scale * 0.10f * (a.scale + b.scale)
            if (dx * dx + dy * dy < minDistance * minDistance) {
                c.drawCircle(ax, ay, scale * 0.10f * a.scale * 1.08f, paint)
                c.drawCircle(bx, by, scale * 0.10f * b.scale * 1.08f, paint)
            }
        }

        // Check buttons against FLOATING zones
        val zones = mutableListOf<RectF>()
        if (draft.movement.mode == MovementMode.FLOATING) {
            zones.add(normalizedRect(draft.movement.zoneLeft, draft.movement.zoneTop, draft.movement.zoneRight, draft.movement.zoneBottom))
        }
        if (draft.manualAim.mode == MovementMode.FLOATING) {
            zones.add(normalizedRect(draft.manualAim.zoneLeft, draft.manualAim.zoneTop, draft.manualAim.zoneRight, draft.manualAim.zoneBottom))
        }

        for (button in draft.controls) {
            val bx = previewRect.left + button.x * previewRect.width()
            val by = previewRect.top + button.y * previewRect.height()
            val br = scale * 0.10f * button.scale
            for (zone in zones) {
                // Approximate circle-rect overlap
                val closestX = bx.coerceIn(zone.left, zone.right)
                val closestY = by.coerceIn(zone.top, zone.bottom)
                val dx = bx - closestX
                val dy = by - closestY
                if (dx * dx + dy * dy < br * br) {
                    c.drawCircle(bx, by, br * 1.08f, paint)
                    c.drawRect(zone, paint)
                }
            }
        }
    }

    private fun drawFloatingMenu(c: Canvas, scale: Float, paint: Paint, text: Paint) {
        val radius = scale * 0.043f
        if (menuX.isNaN()) {
            menuX = previewRect.right - radius - scale * 0.02f
            menuY = previewRect.top + radius + scale * 0.025f
        }
        menuX = menuX.coerceIn(previewRect.left + radius, previewRect.right - radius)
        menuY = menuY.coerceIn(previewRect.top + radius, previewRect.bottom - radius)
        menuButtonRect.set(menuX - radius, menuY - radius, menuX + radius, menuY + radius)
        paint.style = Paint.Style.FILL
        paint.color = 0xEE0A1520.toInt()
        c.drawCircle(menuX, menuY, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = scale * 0.003f
        paint.color = GameColors.PULSE
        c.drawCircle(menuX, menuY, radius, paint)
        text.textAlign = Paint.Align.CENTER
        text.textSize = scale * 0.036f
        text.color = GameColors.PULSE
        c.drawText(if (menuOpen) "x" else "+", menuX, menuY + text.textSize * 0.32f, text)

        if (!menuOpen) return
        val buttonW = scale * 0.19f
        val buttonH = scale * 0.052f
        val gap = scale * 0.012f
        val left = (menuX - buttonW - radius - gap).coerceIn(previewRect.left, previewRect.right - buttonW)
        var top = (menuY - (buttonH + gap) * globalCommands.size + buttonH / 2f).coerceIn(previewRect.top, previewRect.bottom - (buttonH + gap) * globalCommands.size)
        globalCommands.indices.forEach { index ->
            menuCommandRects[index].set(left, top, left + buttonW, top + buttonH)
            drawCommand(c, menuCommandRects[index], globalCommands[index], if (index == 4) GameColors.RED else GameColors.PULSE, scale, paint, text)
            top += buttonH + gap
        }
    }

    private fun drawZoneHandles(c: Canvas, left: Float, top: Float, right: Float, bottom: Float, scale: Float, paint: Paint) {
        val rect = normalizedRect(left, top, right, bottom)
        paint.style = Paint.Style.FILL
        paint.color = GameColors.YELLOW
        val hs = scale * 0.02f
        c.drawCircle(rect.left, rect.top, hs, paint)
        c.drawCircle(rect.right, rect.top, hs, paint)
        c.drawCircle(rect.left, rect.bottom, hs, paint)
        c.drawCircle(rect.right, rect.bottom, hs, paint)
    }

    private fun selectionCenter(draft: HudLayoutProfile): Pair<Float, Float> = when (selectedId) {
        MOVEMENT_ID -> if (draft.movement.mode == MovementMode.FLOATING) {
            Pair(previewRect.left + (draft.movement.zoneLeft + draft.movement.zoneRight) * previewRect.width() / 2f, previewRect.top + (draft.movement.zoneTop + draft.movement.zoneBottom) * previewRect.height() / 2f)
        } else Pair(previewRect.left + draft.movement.x * previewRect.width(), previewRect.top + draft.movement.y * previewRect.height())
        MANUAL_AIM_ID -> if (draft.manualAim.mode == MovementMode.FLOATING) {
            Pair(previewRect.left + (draft.manualAim.zoneLeft + draft.manualAim.zoneRight) * previewRect.width() / 2f, previewRect.top + (draft.manualAim.zoneTop + draft.manualAim.zoneBottom) * previewRect.height() / 2f)
        } else Pair(previewRect.left + draft.manualAim.x * previewRect.width(), previewRect.top + draft.manualAim.y * previewRect.height())
        else -> selectedControl(draft)?.let { Pair(previewRect.left + it.x * previewRect.width(), previewRect.top + it.y * previewRect.height()) }
            ?: Pair(previewRect.centerX(), previewRect.centerY())
    }

    private fun drawCommand(c: Canvas, rect: RectF, label: String, color: Int, scale: Float, paint: Paint, text: Paint) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFF0A1520.toInt()
        c.drawRect(rect, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = scale * 0.003f
        paint.color = color
        c.drawRect(rect, paint)
        text.textAlign = Paint.Align.CENTER
        text.textSize = scale * 0.021f
        text.color = color
        c.drawText(label, rect.centerX(), rect.centerY() + text.textSize * 0.35f, text)
    }

    private fun hitControl(draft: HudLayoutProfile, x: Float, y: Float, scale: Float): String? {
        draft.controls.asReversed().firstOrNull { control ->
            val dx = x - (previewRect.left + control.x * previewRect.width())
            val dy = y - (previewRect.top + control.y * previewRect.height())
            dx * dx + dy * dy <= (scale * 0.12f * control.scale) * (scale * 0.12f * control.scale)
        }?.let { return it.id }
        if (draft.movement.mode == MovementMode.FLOATING && normalizedRect(draft.movement.zoneLeft, draft.movement.zoneTop, draft.movement.zoneRight, draft.movement.zoneBottom).contains(x, y)) return MOVEMENT_ID
        if (draft.movement.mode == MovementMode.STATIC) {
            val dx = x - (previewRect.left + draft.movement.x * previewRect.width())
            val dy = y - (previewRect.top + draft.movement.y * previewRect.height())
            if (dx * dx + dy * dy <= (scale * 0.12f * draft.movement.scale) * (scale * 0.12f * draft.movement.scale)) return MOVEMENT_ID
        }
        if (draft.manualAim.mode == MovementMode.FLOATING && normalizedRect(draft.manualAim.zoneLeft, draft.manualAim.zoneTop, draft.manualAim.zoneRight, draft.manualAim.zoneBottom).contains(x, y)) return MANUAL_AIM_ID
        if (draft.manualAim.mode == MovementMode.STATIC) {
            val dx = x - (previewRect.left + draft.manualAim.x * previewRect.width())
            val dy = y - (previewRect.top + draft.manualAim.y * previewRect.height())
            if (dx * dx + dy * dy <= (scale * 0.12f * draft.manualAim.scale) * (scale * 0.12f * draft.manualAim.scale)) return MANUAL_AIM_ID
        }
        return null
    }

    private fun moveSelected(draft: HudLayoutProfile, x: Float, y: Float) {
        val nx = ((x - previewRect.left) / previewRect.width()).coerceIn(0f, 1f)
        val ny = ((y - previewRect.top) / previewRect.height()).coerceIn(0f, 1f)
        val selected = selectedControl(draft)
        if (selected != null) {
            selected.x = nx
            selected.y = ny
        } else if (selectedId == MOVEMENT_ID) {
            if (draft.movement.mode == MovementMode.STATIC) {
                draft.movement.x = nx
                draft.movement.y = ny
            } else {
                // Check if we are near a corner to resize instead of move
                val left = draft.movement.zoneLeft
                val right = draft.movement.zoneRight
                val top = draft.movement.zoneTop
                val bottom = draft.movement.zoneBottom
                
                val threshold = 0.05f
                if (Math.abs(nx - left) < threshold && Math.abs(ny - top) < threshold) {
                    draft.movement.zoneLeft = nx.coerceAtMost(right - 0.1f)
                    draft.movement.zoneTop = ny.coerceAtMost(bottom - 0.1f)
                } else if (Math.abs(nx - right) < threshold && Math.abs(ny - top) < threshold) {
                    draft.movement.zoneRight = nx.coerceAtLeast(left + 0.1f)
                    draft.movement.zoneTop = ny.coerceAtMost(bottom - 0.1f)
                } else if (Math.abs(nx - left) < threshold && Math.abs(ny - bottom) < threshold) {
                    draft.movement.zoneLeft = nx.coerceAtMost(right - 0.1f)
                    draft.movement.zoneBottom = ny.coerceAtLeast(top + 0.1f)
                } else if (Math.abs(nx - right) < threshold && Math.abs(ny - bottom) < threshold) {
                    draft.movement.zoneRight = nx.coerceAtLeast(left + 0.1f)
                    draft.movement.zoneBottom = ny.coerceAtLeast(top + 0.1f)
                } else {
                    val width = right - left
                    val height = bottom - top
                    draft.movement.zoneLeft = (nx - width / 2f).coerceIn(0f, 1f - width)
                    draft.movement.zoneRight = draft.movement.zoneLeft + width
                    draft.movement.zoneTop = (ny - height / 2f).coerceIn(0f, 1f - height)
                    draft.movement.zoneBottom = draft.movement.zoneTop + height
                }
            }
        } else if (selectedId == MANUAL_AIM_ID) {
            if (draft.manualAim.mode == MovementMode.STATIC) {
                draft.manualAim.x = nx
                draft.manualAim.y = ny
            } else {
                val left = draft.manualAim.zoneLeft
                val right = draft.manualAim.zoneRight
                val top = draft.manualAim.zoneTop
                val bottom = draft.manualAim.zoneBottom
                
                val threshold = 0.05f
                if (Math.abs(nx - left) < threshold && Math.abs(ny - top) < threshold) {
                    draft.manualAim.zoneLeft = nx.coerceAtMost(right - 0.1f)
                    draft.manualAim.zoneTop = ny.coerceAtMost(bottom - 0.1f)
                } else if (Math.abs(nx - right) < threshold && Math.abs(ny - top) < threshold) {
                    draft.manualAim.zoneRight = nx.coerceAtLeast(left + 0.1f)
                    draft.manualAim.zoneTop = ny.coerceAtMost(bottom - 0.1f)
                } else if (Math.abs(nx - left) < threshold && Math.abs(ny - bottom) < threshold) {
                    draft.manualAim.zoneLeft = nx.coerceAtMost(right - 0.1f)
                    draft.manualAim.zoneBottom = ny.coerceAtLeast(top + 0.1f)
                } else if (Math.abs(nx - right) < threshold && Math.abs(ny - bottom) < threshold) {
                    draft.manualAim.zoneRight = nx.coerceAtLeast(left + 0.1f)
                    draft.manualAim.zoneBottom = ny.coerceAtLeast(top + 0.1f)
                } else {
                    val width = right - left
                    val height = bottom - top
                    draft.manualAim.zoneLeft = (nx - width / 2f).coerceIn(0f, 1f - width)
                    draft.manualAim.zoneRight = draft.manualAim.zoneLeft + width
                    draft.manualAim.zoneTop = (ny - height / 2f).coerceIn(0f, 1f - height)
                    draft.manualAim.zoneBottom = draft.manualAim.zoneTop + height
                }
            }
        }
    }

    private fun handleGlobalCommand(command: Int, onApplied: () -> Unit, onExit: () -> Unit) {
        val draft = profile ?: return
        when (command) {
            0 -> HudAction.entries.firstOrNull { draft.canAdd(it) }?.let { action ->
                val id = "${action.name.lowercase()}_${draft.controls.count { it.action == action } + 1}"
                draft.controls += HudControl(id, action, 0.5f, 0.5f, inputBehavior = if (action == HudAction.ATTACK) HudInputBehavior.HOLD else HudInputBehavior.TAP)
                selectedId = id
                menuOpen = false
            }
            1 -> requestConfirmation("RESET DEFAULTS?", "REPLACE THIS PROFILE WITH DEFAULTS", "RESET") {
                profile = HudLayoutProfile.defaults(portrait)
                selectedId = null
                menuOpen = false
            }
            2 -> requestConfirmation("RESTORE SAVED?", "DISCARD DRAFT AND LOAD SAVED", "RESTORE") {
                profile = SaveManager.loadHudLayoutProfile(portrait)
                selectedId = null
                onApplied()
                menuOpen = false
            }
            3 -> requestConfirmation("APPLY LAYOUT?", "SAVE THIS HUD PROFILE", "APPLY") {
                SaveManager.saveHudLayoutProfile(portrait, profile ?: return@requestConfirmation)
                savedProfile = profile?.copyMutable()
                feedback = "LAYOUT APPLIED"
                feedbackUntil = System.currentTimeMillis() + 1500L
                onApplied()
                menuOpen = false
            }
            4 -> requestClose(onApplied, onExit)
        }
    }

    private fun handleContextCommand(command: Int) {
        val draft = profile ?: return
        val selected = selectedControl(draft)
        when {
            selected != null && command == 0 -> {
                val canReplace = selected.action !in setOf(HudAction.ATTACK, HudAction.PAUSE) ||
                    draft.controls.count { it.action == selected.action } > 1
                val candidates = HudAction.entries.filter { candidate ->
                    candidate == selected.action || (canReplace && draft.controls.count { it.action == candidate } < HudLayoutProfile.MAX_DUPLICATES_PER_ACTION)
                }
                selected.action = candidates[(candidates.indexOf(selected.action) + 1) % candidates.size]
                if (selected.action != HudAction.ATTACK) selected.inputBehavior = HudInputBehavior.TAP
            }
            selected != null && command == 1 -> selected.visualType = HudVisualType.entries[(selected.visualType.ordinal + 1) % HudVisualType.entries.size]
            selected != null && selected.action == HudAction.ATTACK && command == 2 -> {
                selected.inputBehavior = if (selected.inputBehavior == HudInputBehavior.TAP) HudInputBehavior.HOLD else HudInputBehavior.TAP
            }
            selected != null && ((selected.action == HudAction.ATTACK && command == 3) || (selected.action != HudAction.ATTACK && command == 2)) -> {
                if (draft.canRemove(selected)) requestConfirmation("DELETE CONTROL?", "REMOVE ${selected.action.name} BUTTON", "DELETE") {
                    profile?.controls?.removeAll { it.id == selected.id }
                    selectedId = null
                }
            }
            selectedId == MOVEMENT_ID && command == 0 -> draft.movement.mode = if (draft.movement.mode == MovementMode.FLOATING) MovementMode.STATIC else MovementMode.FLOATING
            selectedId == MANUAL_AIM_ID && command == 0 -> draft.manualAim.mode = if (draft.manualAim.mode == MovementMode.FLOATING) MovementMode.STATIC else MovementMode.FLOATING
        }
    }

    fun requestClose(onApplied: () -> Unit, onExit: () -> Unit) {
        requestConfirmation("DISCARD EDITS?", "RESTORE LAST SAVED LAYOUT", "DISCARD") {
            profile = savedProfile?.copyMutable() ?: SaveManager.loadHudLayoutProfile(portrait)
            selectedId = null
            onApplied()
            onExit()
        }
    }

    private fun requestConfirmation(title: String, detail: String, confirmLabel: String, action: () -> Unit) {
        confirmDialog.show(title, detail, confirmLabel) {
            action()
            feedback = when (confirmLabel) {
                "APPLY" -> "LAYOUT APPLIED"
                "DELETE" -> "CONTROL REMOVED"
                "RESET" -> "DEFAULTS LOADED"
                "RESTORE" -> "SAVED LAYOUT LOADED"
                else -> "DRAFT DISCARDED"
            }
            feedbackUntil = System.currentTimeMillis() + 1500L
        }
    }

    private fun selectedControl(draft: HudLayoutProfile): HudControl? = draft.controls.firstOrNull { it.id == selectedId }
    private fun selectedScale(draft: HudLayoutProfile): Float = when (selectedId) {
        MOVEMENT_ID -> draft.movement.scale
        MANUAL_AIM_ID -> draft.manualAim.scale
        else -> selectedControl(draft)?.scale ?: 1f
    }
    private fun setSelectedScale(draft: HudLayoutProfile, value: Float) {
        selectedControl(draft)?.scale = value
        if (selectedId == MOVEMENT_ID) {
            resizeZone(draft.movement.zoneLeft, draft.movement.zoneTop, draft.movement.zoneRight, draft.movement.zoneBottom, draft.movement.scale, value) { left, top, right, bottom ->
                draft.movement.zoneLeft = left; draft.movement.zoneTop = top; draft.movement.zoneRight = right; draft.movement.zoneBottom = bottom
            }
            draft.movement.scale = value
        }
        if (selectedId == MANUAL_AIM_ID) {
            resizeZone(draft.manualAim.zoneLeft, draft.manualAim.zoneTop, draft.manualAim.zoneRight, draft.manualAim.zoneBottom, draft.manualAim.scale, value) { left, top, right, bottom ->
                draft.manualAim.zoneLeft = left; draft.manualAim.zoneTop = top; draft.manualAim.zoneRight = right; draft.manualAim.zoneBottom = bottom
            }
            draft.manualAim.scale = value
        }
    }
    private fun resizeZone(left: Float, top: Float, right: Float, bottom: Float, oldScale: Float, newScale: Float, apply: (Float, Float, Float, Float) -> Unit) {
        val factor = newScale / oldScale
        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f
        val width = ((right - left) * factor).coerceIn(0.16f, 1f)
        val height = ((bottom - top) * factor).coerceIn(0.16f, 1f)
        val newLeft = (centerX - width / 2f).coerceIn(0f, 1f - width)
        val newTop = (centerY - height / 2f).coerceIn(0f, 1f - height)
        apply(newLeft, newTop, newLeft + width, newTop + height)
    }
    private fun normalizedRect(left: Float, top: Float, right: Float, bottom: Float) = RectF(
        previewRect.left + left * previewRect.width(), previewRect.top + top * previewRect.height(),
        previewRect.left + right * previewRect.width(), previewRect.top + bottom * previewRect.height()
    )
    private fun distance(e: MotionEvent): Float {
        val dx = e.getX(0) - e.getX(1)
        val dy = e.getY(0) - e.getY(1)
        return sqrt(dx * dx + dy * dy)
    }

    private companion object {
        const val MOVEMENT_ID = "movement"
        const val MANUAL_AIM_ID = "manual_aim"
    }
}
