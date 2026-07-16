package com.appsbyalok.echohunter.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.media.ToneGenerator
import android.view.MotionEvent
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.input.AttackMode
import com.appsbyalok.echohunter.ui.arsenal.ArsenalListView
import com.appsbyalok.echohunter.ui.arsenal.ArsenalMainScreen
import com.appsbyalok.echohunter.ui.arsenal.ProbeDetailRenderer
import com.appsbyalok.echohunter.ui.components.UIMenuButton
import com.appsbyalok.echohunter.ui.components.UIMenuMetrics
import com.appsbyalok.echohunter.ui.components.UIMenuScreenChrome
import com.appsbyalok.echohunter.utils.EchoAudioManager
import com.appsbyalok.echohunter.utils.GameColors

class UIArsenal {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val probeRenderer = ProbeDetailRenderer()
    private val mainScreen = ArsenalMainScreen(probeRenderer)
    private val listView = ArsenalListView()
    private val chrome = UIMenuScreenChrome()
    private val closeButton = UIMenuButton()

    // 0 = Main OS, 1 = Weapons Folder, 2 = Traps Folder, 3 = Attack Mode Folder
    private var currentTab = 0
    private var hitOnDown = -1

    fun draw(c: Canvas, targetW: Float, targetH: Float, scale: Float, gs: GameState, dt: Float) {
        val metrics = UIMenuMetrics(targetW, targetH, scale)
        chrome.drawBackground(c, metrics, p, bgColor = 0xEE051015.toInt())

        // --- NANO OS HEADER (Responsive) ---
        val insetT = metrics.insetTop
        val insetL = metrics.insetLeft
        val insetR = metrics.insetRight

        p.style = Paint.Style.FILL; p.color = GameColors.PULSE
        val headerHeight = metrics.headerHeight
        c.drawRect(0f, 0f, targetW, headerHeight, p)

        // Header Accents
        p.color = 0x44000000
        c.drawRect(0f, headerHeight - scale * 0.015f, targetW, headerHeight, p)

        pText.color = GameColors.BG; pText.textSize = scale * 0.045f
        pText.isFakeBoldText = true
        pText.textAlign = Paint.Align.LEFT
        val titleY = insetT + (headerHeight - insetT) * 0.65f
        val path = when(currentTab) {
            1 -> "arsenal/weapons"
            2 -> "arsenal/traps"
            3 -> "arsenal/logic_aim"
            else -> "arsenal"
        }
        c.drawText("root@probe-7:/mnt/$path ~", scale * 0.05f + insetL, titleY, pText)
        pText.isFakeBoldText = false

        // Route to active tab
        when (currentTab) {
            0 -> {
                mainScreen.draw(c, targetW, targetH, scale, gs, headerHeight, hitOnDown)
            }
            1 -> listView.draw(c, targetW, targetH, scale, "WEAPON PROTOCOLS", gs.controls.currentWeapon, 
                arrayOf("BLASTER", "SHOTGUN", "SNIPER"),
                arrayOf("Standard rapid-fire spike delivery.", "Wide-angle burst for crowd control.", "High-velocity armor-piercing shell."),
                headerHeight, hitOnDown, dt, insetR)
            2 -> listView.draw(c, targetW, targetH, scale, "TRAP MODULES", gs.controls.currentTrap, 
                arrayOf("CAMOUFLAGE", "DECOY", "EMP MINE"),
                arrayOf("Visual distortion; reduces detection.", "Holographic phantom; attracts sentinels.", "Localized shockwave; disables systems."),
                headerHeight, hitOnDown, dt, insetR)
            3 -> listView.draw(c, targetW, targetH, scale, "SELECT AIMING LOGIC", gs.controls.activeAttackMode.ordinal, 
                arrayOf("DIRECTIONAL", "AUTO-AIM", "MANUAL-AIM"),
                arrayOf("Classic fire in movement direction.", "Logic-assisted targeting of nearest threat.", "Touch-surface vector based targeting."),
                headerHeight, hitOnDown, dt, insetR)
        }

        // --- DISCONNECT / BACK BUTTON (Responsive Footer) ---
        val insetB = metrics.insetBottom
        pText.textAlign = Paint.Align.CENTER
        val isPortrait = metrics.isPortrait
        val btnWidth = if (isPortrait) targetW * 0.7f else scale * 0.4f
        val btnHeight = scale * 0.09f
        val btnBottomMargin = scale * 0.05f + insetB

        closeButton.set(
            targetW / 2f - btnWidth / 2f,
            targetH - btnHeight - btnBottomMargin,
            targetW / 2f + btnWidth / 2f,
            targetH - btnBottomMargin
        )

        closeButton.draw(
            c = c,
            scale = scale,
            paint = p,
            textPaint = pText,
            label = if (currentTab == 0) "DISCONNECT" else "< BACK",
            pressed = hitOnDown == 100,
            fillColor = 0xFF330000.toInt(),
            strokeColor = GameColors.RED,
            textColor = GameColors.RED,
            radius = scale * 0.02f,
            textSize = scale * 0.045f
        )
    }

    private var touchDownX = 0f
    private var touchDownY = 0f

    fun handleBack(): Boolean {
        if (currentTab == 0) return false
        currentTab = 0
        hitOnDown = -1
        listView.scroller.scrollY = 0f
        return true
    }

    fun onTouch(x: Float, y: Float, action: Int, scale: Float, gs: GameState, onBack: () -> Unit): Boolean {
        if (currentTab != 0) {
            if (listView.scroller.isDragging || listView.scroller.isDraggingScrollbar) {
                hitOnDown = -1
            }
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = x
                touchDownY = y

                hitOnDown = when {
                    closeButton.contains(x, y) -> 100
                    currentTab == 0 -> {
                        when {
                            mainScreen.weaponDirRect.contains(x, y) -> 1
                            mainScreen.trapDirRect.contains(x, y) -> 2
                            mainScreen.attackModeRect.contains(x, y) -> 3
                            else -> -1
                        }
                    }
                    else -> {
                        var hit = -1
                        if (listView.scroller.viewport.contains(x, y)) {
                            hit = listView.hitItem(x, y) ?: -1
                        }
                        hit
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - touchDownX
                val dy = y - touchDownY
                val distSq = dx * dx + dy * dy
                val threshold = scale * scale * 0.05f

                if (hitOnDown != -1 && ( (currentTab != 0 && (listView.scroller.isDragging || listView.scroller.isDraggingScrollbar)) || distSq > threshold)) {
                    hitOnDown = -1
                }
            }
            MotionEvent.ACTION_UP -> {
                if (hitOnDown != -1 && (currentTab == 0 || (!listView.scroller.isDragging && !listView.scroller.isDraggingScrollbar))) {
                    val hitOnUp = when {
                        closeButton.contains(x, y) -> 100
                        currentTab == 0 -> {
                            when {
                                mainScreen.weaponDirRect.contains(x, y) -> 1
                                mainScreen.trapDirRect.contains(x, y) -> 2
                                mainScreen.attackModeRect.contains(x, y) -> 3
                                else -> -1
                            }
                        }
                        else -> {
                            var hit = -1
                            if (listView.scroller.viewport.contains(x, y)) {
                                hit = listView.hitItem(x, y) ?: -1
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
                            listView.scroller.scrollY = 0f // Reset scroll on entry
                        } else {
                            EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 150)
                            when (currentTab) {
                                1 -> {
                                    gs.controls.currentWeapon = hitOnUp
                                    SaveManager.setActiveWeapon(hitOnUp)
                                    gs.showGlobalMessage("WEAPON PROTOCOL UPDATED.", 1.5f)
                                }
                                2 -> {
                                    gs.controls.currentTrap = hitOnUp
                                    SaveManager.setActiveTrap(hitOnUp)
                                    gs.showGlobalMessage("TRAP MODULE LOADED.", 1.5f)
                                }
                                3 -> {
                                    gs.controls.activeAttackMode = AttackMode.entries.toTypedArray()[hitOnUp]
                                    SaveManager.setAttackMode(hitOnUp)
                                    gs.showGlobalMessage("AIMING LOGIC RECONFIGURED.", 1.5f)
                                }
                            }
                            currentTab = 0
                        }
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
}
