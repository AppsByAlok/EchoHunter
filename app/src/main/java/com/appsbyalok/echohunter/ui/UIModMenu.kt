package com.appsbyalok.echohunter.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ToneGenerator
import android.view.MotionEvent
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.EchoAudioManager
import com.appsbyalok.echohunter.utils.GameColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class UIModMenu {
    var isOpen = false
    private var scrollY = 0f
    private var maxScrollY = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var isCloseTapValid = false

    // Hold Matrix Tracking variables
    private var holdingLevelDir = 0 // -1 for DOWN, 1 for UP, 0 for None, 2 for DATA hold
    private var holdStartTime = 0L
    private var lastLevelChangeTime = 0L

    interface ModMenuListener {
        fun onForceBossSpawn()
        fun onTriggerCoreMerge()
        fun onForceEMP()
    }
    var listener: ModMenuListener? = null

    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private fun updateHoldLogic(gs: GameState) {
        if (holdingLevelDir != 0 && !isDragging) {
            val currentTime = System.currentTimeMillis()
            val holdDuration = currentTime - holdStartTime
            val delayMs = max(10L, 250L - (holdDuration / 6L))

            if (currentTime - lastLevelChangeTime >= delayMs) {
                when (holdingLevelDir) {
                    1 -> { // Level Up Fast Engine
                        val step = when {
                            holdDuration > 3000L -> 25
                            holdDuration > 1500L -> 5
                            else -> 1
                        }
                        gs.currentLevel += step
                        SaveManager.debugSetLevel(gs.currentLevel)
                        EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 20)
                    }
                    -1 -> { // Level Down Fast Engine
                        val step = when {
                            holdDuration > 3000L -> 25
                            holdDuration > 1500L -> 5
                            else -> 1
                        }
                        gs.currentLevel = max(1, gs.currentLevel - step)
                        SaveManager.debugSetLevel(gs.currentLevel)
                        EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 20)
                    }
                    2 -> { // Data Add Fast Engine (Hold System)
                        // Hold duration ke base par dynamic data multiplier (100MB up to 1GB per tick)
                        val dataMultiplier = when {
                            holdDuration > 3000L -> 10 // 1 GB per tick!
                            holdDuration > 1500L -> 5  // 500 MB per tick
                            else -> 1                  // 100 MB per tick
                        }
                        val dataToAdd = 102400L * dataMultiplier
                        gs.collectedDataKB += dataToAdd
                        SaveManager.addData(dataToAdd)
                        EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 20)
                    }
                }
                lastLevelChangeTime = currentTime
            }
        }
    }

    fun draw(c: Canvas, scale: Float, targetW: Float, targetH: Float, gs: GameState) {
        if (!isOpen) return

        updateHoldLogic(gs) // Frame loop engine trigger

        p.color = 0xEE050505.toInt()
        p.style = Paint.Style.FILL
        c.drawRect(0f, 0f, targetW, targetH, p)

        pText.textSize = scale * 0.08f
        pText.color = GameColors.YELLOW
        c.drawText("> DEV MOD MENU <", targetW / 2f, targetH * 0.15f, pText)

        val startY = targetH * 0.25f + scrollY
        val itemHeight = scale * 0.12f
        val gap = scale * 0.04f
        val btnW = scale * 0.8f

        val items = listOf(
            "God Mode (HP <= 1): " + if(gs.modGodMode) "ON" else "OFF",
            "Infinite Overclock: " + if(gs.modInfiniteOvr) "ON" else "OFF",
            "Full Visibility: " + if(gs.modFullVisibility) "ON" else "OFF",
            "Add +100 MB Data",                        // Index 3 (Holdable)
            "Level UP (+1) [Cur: ${gs.currentLevel}]", // Index 4 (Holdable)
            "Level DOWN (-1)",                        // Index 5 (Holdable)
            "Instant Boss Spawn",
            "Trigger Core Merge",
            "Force EMP Blast",
            "[ CRITICAL: RESET ALL DATA ]",           // Index 9
            "Close Menu"                              // Index 10
        )

        val totalHeight = items.size * (itemHeight + gap)
        maxScrollY = max(0f, totalHeight - (targetH * 0.7f))

        for ((i, item) in items.withIndex()) {
            val y = startY + i * (itemHeight + gap)
            if (y + itemHeight < targetH * 0.2f || y > targetH * 0.95f) continue

            val rect = RectF(targetW / 2f - btnW / 2f, y, targetW / 2f + btnW / 2f, y + itemHeight)

            p.style = Paint.Style.FILL
            p.color = when {
                i == 3 && holdingLevelDir == 2 -> 0xFF333333.toInt()
                i == 4 && holdingLevelDir == 1 -> 0xFF333333.toInt()
                i == 5 && holdingLevelDir == -1 -> 0xFF333333.toInt()
                i < 3 && ((i==0 && gs.modGodMode) || (i==1 && gs.modInfiniteOvr) || (i==2 && gs.modFullVisibility)) -> 0xFF114411.toInt()
                i == 9 -> 0xFF2A0505.toInt()
                else -> 0xFF1A1A1A.toInt()
            }
            c.drawRoundRect(rect, scale * 0.03f, scale * 0.03f, p)

            p.style = Paint.Style.STROKE
            p.color = when(i) {
                9 -> GameColors.RED
                items.lastIndex -> GameColors.YELLOW
                else -> GameColors.PULSE
            }
            p.strokeWidth = scale * 0.005f
            c.drawRoundRect(rect, scale * 0.03f, scale * 0.03f, p)

            p.style = Paint.Style.FILL
            pText.textSize = scale * 0.045f
            pText.color = if (i == 9 || i == items.lastIndex) GameColors.RED else GameColors.CLARITY
            c.drawText(item, targetW / 2f, y + itemHeight * 0.65f, pText)
        }

        if (maxScrollY > 0) {
            val scrollPercent = abs(scrollY) / maxScrollY
            val indicatorY = targetH * 0.25f + ((targetH * 0.7f - scale*0.1f) * scrollPercent)
            p.color = GameColors.YELLOW
            c.drawRect(targetW - scale*0.05f, indicatorY, targetW - scale*0.02f, indicatorY + scale*0.1f, p)
        }
    }

    fun onTouch(vx: Float, vy: Float, action: Int, scale: Float, targetW: Float, targetH: Float, gs: GameState, listener: ModMenuListener? = null): Boolean {
        if (!isOpen) return false

        val startY = targetH * 0.25f + scrollY
        val itemHeight = scale * 0.12f
        val gap = scale * 0.04f
        val btnW = scale * 0.8f

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = vy
                isDragging = false

                isCloseTapValid = (vx > targetW - scale * 0.15f && vy < scale * 0.15f)

                val clickedIndex = ((vy - startY) / (itemHeight + gap)).toInt()
                val itemYStart = startY + clickedIndex * (itemHeight + gap)

                if (vy >= itemYStart && vy <= itemYStart + itemHeight && vx >= targetW / 2f - btnW / 2f && vx <= targetW / 2f + btnW / 2f) {
                    // Instant single-click execution on touch down + activate loop holding state
                    when (clickedIndex) {
                        3 -> {
                            holdingLevelDir = 2
                            holdStartTime = System.currentTimeMillis()
                            lastLevelChangeTime = holdStartTime
                            val dataToAdd = 102400L
                            gs.collectedDataKB += dataToAdd
                            SaveManager.addData(dataToAdd)
                            EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 100)
                        }
                        4 -> {
                            holdingLevelDir = 1
                            holdStartTime = System.currentTimeMillis()
                            lastLevelChangeTime = holdStartTime
                            gs.currentLevel++
                            SaveManager.debugSetLevel(gs.currentLevel)
                            EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 60)
                        }
                        5 -> {
                            if (gs.currentLevel > 1) {
                                holdingLevelDir = -1
                                holdStartTime = System.currentTimeMillis()
                                lastLevelChangeTime = holdStartTime
                                gs.currentLevel--
                                SaveManager.debugSetLevel(gs.currentLevel)
                                EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 60)
                            }
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = vy - lastTouchY
                if (abs(dy) > scale * 0.02f) {
                    isDragging = true
                    holdingLevelDir = 0 // Break holds safely if user is dragging up/down list
                }
                if (isDragging) {
                    scrollY += dy
                    scrollY = min(0f, max(-maxScrollY, scrollY))
                    lastTouchY = vy
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasHolding = holdingLevelDir != 0
                holdingLevelDir = 0 // Release hold latch

                // Trigger click blocks only if user wasn't holding or scrolling layout
                if (!isDragging && !wasHolding && action != MotionEvent.ACTION_CANCEL) {
                    if (isCloseTapValid && vx > targetW - scale * 0.15f && vy < scale * 0.15f) {
                        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_ALERT, 100)
                        isOpen = false
                        isDragging = false
                        isCloseTapValid = false
                        return true
                    }

                    val clickedIndex = ((vy - startY) / (itemHeight + gap)).toInt()
                    val itemYStart = startY + clickedIndex * (itemHeight + gap)

                    if (clickedIndex in 0..10 && vy >= itemYStart && vy <= itemYStart + itemHeight && vx >= targetW / 2f - btnW / 2f && vx <= targetW / 2f + btnW / 2f) {
                        when (clickedIndex) {
                            0 -> { EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 100); gs.modGodMode = !gs.modGodMode }
                            1 -> { EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 100); gs.modInfiniteOvr = !gs.modInfiniteOvr }
                            2 -> { EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 100); gs.modFullVisibility = !gs.modFullVisibility }
                            6 -> { EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 100); listener?.onForceBossSpawn(); isOpen = false }
                            7 -> { EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 100); listener?.onTriggerCoreMerge(); isOpen = false }
                            8 -> { EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 100); listener?.onForceEMP(); isOpen = false }
                            9 -> {
                                SaveManager.clearAllData()
                                gs.currentLevel = 1
                                EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_ALERT, 600)
                                isOpen = false
                            }
                            10 -> { EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 100); isOpen = false }
                        }
                    }
                }
                isDragging = false
            }
        }
        return true
    }
}