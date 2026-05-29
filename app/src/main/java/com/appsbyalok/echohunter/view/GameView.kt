package com.appsbyalok.echohunter.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.media.ToneGenerator
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import com.appsbyalok.echohunter.R
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.data.UpgradeSystem
import com.appsbyalok.echohunter.engine.GameEngine
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.input.TouchController
import com.appsbyalok.echohunter.systems.CollisionSystem
import com.appsbyalok.echohunter.systems.EffectSystem
import com.appsbyalok.echohunter.systems.EnemySystem
import com.appsbyalok.echohunter.ui.UIArchives
import com.appsbyalok.echohunter.ui.UIArsenal
import com.appsbyalok.echohunter.ui.UIDecompiler
import com.appsbyalok.echohunter.ui.UIHelpMenu
import com.appsbyalok.echohunter.ui.UIMainMenu
import com.appsbyalok.echohunter.ui.UIModMenu
import com.appsbyalok.echohunter.ui.UINanoOS
import com.appsbyalok.echohunter.utils.EchoAudioManager
import com.appsbyalok.echohunter.utils.GameColors
import com.appsbyalok.echohunter.view.renderers.HUDRenderer
import com.appsbyalok.echohunter.view.renderers.MenuRenderer
import com.appsbyalok.echohunter.view.renderers.WorldRenderer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class GameView(context: Context) : View(context) {
    private val modMenu = UIModMenu()

    private var targetW = 1920f
    private var targetH = 1080f
    private var gameScale = 1f

    private var lastFrameTime = System.nanoTime()
    private var isInitialized = false
    private var currentStoryLines = StoryProtocol.storyIntroLines
    private var storyStep = 0

    private var lockedIsLandscape: Boolean? = null

    private val gs = GameState()
    private val effectSys = EffectSystem()
    private val enemySys = EnemySystem()
    private val collisionSys = CollisionSystem(gs, effectSys, enemySys)
    private val gameEngine = GameEngine(gs, effectSys, enemySys, collisionSys, context)

    private val worldRenderer = WorldRenderer(context, effectSys, enemySys)
    private val hudRenderer = HUDRenderer()
    private val menuRenderer = MenuRenderer(context)
    private val uiMainMenu = UIMainMenu(context)
    private val uiHelpMenu = UIHelpMenu(context)
    private val uiDecompiler = UIDecompiler()
    private val uiArsenal = UIArsenal()
    private val uiNanoOS = UINanoOS()
    private val uiArchives = UIArchives()
    private val touchController = TouchController(gs)

    private var currentActivePort = -1

    // NAYA: Mod Menu Action Listener
    private val modMenuListener = object : UIModMenu.ModMenuListener {
        override fun onForceBossSpawn() {
            triggerBoss(0, min(targetW, targetH))
        }
        override fun onTriggerCoreMerge() {
            handleCoreUnlock(true)
        }
        override fun onForceEMP() {
            gs.empMineActive = true
            gs.empMineX = gs.px
            gs.empMineY = gs.py
        }
    }



    private val onMenuRoute: (Int) -> Unit = { mode ->
        currentActivePort = mode
        routeMenuConnection(mode)
    }

    private val onDisconnect: () -> Unit = { disconnectCable() }

    private val onAppClose: () -> Unit = {
        if (currentActivePort == 2) changeState(14) else disconnectCable()
    }

    private val onArchiveSelect: (Int) -> Unit = { mode -> startGame(0, mode) }
    private val onHelpOpen: () -> Unit = { changeState(3) }
    private val onHelpClose: () -> Unit = {
        changeState(0)
        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 100)
    }
    private val onDifficultyToggle: () -> Unit = {
        if (SaveManager.isHardModeUnlocked) {
            gs.difficulty = 1 - gs.difficulty
            EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 50)
        }
    }

    init {
        EchoAudioManager.init()
        setupEngineCallbacks()
    }

    private fun setupEngineCallbacks() {
        gameEngine.onChangeState = { changeState(it) }
        gameEngine.onDamage = { handleDamage(it) }
        gameEngine.onScore = { gs.score += it }
        gameEngine.onBossTrigger = { type, scale -> triggerBoss(type, scale) }
        gameEngine.onStoryState = { lines, nextSt -> setStoryState(lines, nextSt) }
        gameEngine.onCoreUnlock = { perfectEnd -> handleCoreUnlock(perfectEnd) }

        touchController.onPauseClicked = { pauseGame() }
        touchController.onPulseTriggered = { triggerPulseAction() }
    }

    fun saveState(b: Bundle) {
        gs.saveState(b)
        b.putIntArray("currentStoryLines", currentStoryLines)
        b.putInt("storyStep", storyStep)
        b.putInt("currentActivePort", currentActivePort)
    }

    fun restoreState(b: Bundle) {
        gs.restoreState(b)
        b.getIntArray("currentStoryLines")?.let { currentStoryLines = it }
        storyStep = b.getInt("storyStep", 0)
        currentActivePort = b.getInt("currentActivePort", -1)
    }

    fun pauseGame() { if (gs.state == 1 || gs.state == 8) changeState(2) }

    private fun changeState(newState: Int) {
        gs.state = newState
        gs.stateTimer = 0f
    }

    fun handleBackPressed(): Boolean {
        return when (gs.state) {
            5, 7 -> { changeState(0); true }
            1, 8 -> { changeState(2); true }
            2, 3, 4, 6 -> { changeState(0); true }
            10, 11, 13 -> { onAppClose(); true }
            14, 12 -> { disconnectCable(); true }
            9 -> true
            else -> false
        }
    }

    // --- UI COORDINATE CALCULATOR ---
    private fun updateUICoordinates(scale: Float) {
        gs.uiBtnRadius = scale * 0.12f

        // Bottom Right Combat Grid [PULSE][OVR] -> [TRAP][ATK]
        gs.uiAtkX = targetW - scale * 0.18f
        gs.uiAtkY = targetH - scale * 0.18f

        gs.uiOvrX = gs.uiAtkX
        gs.uiOvrY = gs.uiAtkY - scale * 0.28f

        gs.uiTrapX = gs.uiAtkX - scale * 0.28f
        gs.uiTrapY = gs.uiAtkY

        gs.uiPulseX = gs.uiAtkX - scale * 0.28f
        gs.uiPulseY = gs.uiAtkY - scale * 0.28f

        // Pause is purely Top Right
        gs.uiPauseX = targetW - scale * 0.12f
        gs.uiPauseY = scale * 0.12f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val deviceW = w.toFloat()
        val deviceH = h.toFloat()

        if (deviceW >= deviceH) {
            targetH = 1080f
            targetW = 1080f * (deviceW / deviceH)
        } else {
            targetW = 1080f
            targetH = 1080f * (deviceH / deviceW)
        }

        gameScale = deviceW / targetW
        worldRenderer.updateDashEffect(min(targetW, targetH))
        val scale = min(targetW, targetH)
        worldRenderer.updateDashEffect(scale)

        // Setup coordinates cleanly
        updateUICoordinates(scale)

        if (!isInitialized) {
            gs.px = targetW / 2f; gs.py = targetH / 2f
            gs.baseWorldSpeed = targetW * 0.2f

            gameEngine.generateLevelMaze(targetW, targetH, scale)
            enemySys.respawnAll(gs, targetW, targetH)

            uiMainMenu.initLayout(targetW, targetH)
            isInitialized = true
        } else {
            val currentIsLandscape = targetW >= targetH
            gs.isRotationWarning = (lockedIsLandscape != null && lockedIsLandscape != currentIsLandscape)

            if (gs.state == 1 || gs.state == 8) {
                if (gs.isRotationWarning) {
                    pauseGame()
                    EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_ALERT, 200)
                }
            }

            gs.baseWorldSpeed = targetW * 0.2f
            uiMainMenu.initLayout(targetW, targetH)

            gs.cameraX = gs.px - targetW / 2f
            gs.cameraY = gs.py - targetH / 2f
            if (gs.gridMap != null) {
                gs.cameraX = max(0f, min(gs.cameraX, gs.mapWidth - targetW))
                gs.cameraY = max(0f, min(gs.cameraY, gs.mapHeight - targetH))
            }
        }
    }

    private fun resetGame() {
        gs.resetGame(); effectSys.reset()

        lockedIsLandscape = (targetW >= targetH)

        val scale = min(targetW, targetH)
        gameEngine.generateLevelMaze(targetW, targetH, scale)

        enemySys.respawnAll(gs, targetW, targetH)

        changeState(1)
        lastFrameTime = System.nanoTime()
    }

    private fun disconnectCable() {
        if (!StoryProtocol.isGlitchActive) {
            gs.shakeAmount = 0f
            gs.chromaticIntensity = 0f
            gs.sectorFlash = 0f
        }
        uiMainMenu.disconnect()
        changeState(0)
    }

    private fun routeMenuConnection(mode: Int) {
        when (mode) {
            0 -> changeState(11) // Archives (Sandbox)
            1 -> if (!SaveManager.isStoryModeUnlocked) {
                gs.showGlobalMessage("ADMIN: \"MAINFRAME ACCESS DENIED.\"", 2f)
                uiMainMenu.turnOffSwitch()
            } else startGame(mode)
            2 -> changeState(14) // Nano OS Dashboard
            else -> startGame(mode)
        }
    }

    private fun startGame(mode: Int, overrideLevel: Int? = null) {
        gs.gameMode = mode
        if (mode == 0) gs.currentLevel = overrideLevel ?: SaveManager.maxCampaignLevel
        setStoryState(gs.modeStrategy.getIntroLines(), 1)
        resetGame()
        EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 50)
        uiMainMenu.disconnect()
    }

    private fun setStoryState(lines: IntArray, nextSt: Int) {
        currentStoryLines = lines; storyStep = 0
        gs.nextStateAfterStory = nextSt
        changeState(if (nextSt == 6 || nextSt == 0 && gs.hp <= 0) 4 else 7)
        if (nextSt == 6) changeState(6)
        gs.timeSinceStart = 0f
    }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        var dt = (now - lastFrameTime) / 1_000_000_000f
        lastFrameTime = now
        if (dt > 0.05f) dt = 0.05f

        gameEngine.update(dt, targetW, targetH, min(targetW, targetH))
        if (gs.state == 0) uiMainMenu.update(dt, targetW, targetH, this, effectSys, gs, onMenuRoute)
        if (gs.state == 3) uiHelpMenu.update(dt)

        c.save()
        c.scale(gameScale, gameScale)
        renderScene(c, dt)
        worldRenderer.drawCRTOverlay(c, gs, targetW, targetH)
        c.restore()

        invalidate()
    }

    private var sdx = 0f; private var sdy = 0f
    private fun renderScene(c: Canvas, dt: Float) {
        val scale = min(targetW, targetH)
        var currentBgColor = if (gs.difficulty == 1) 0xFF1A0505.toInt() else GameColors.BG
        if (StoryProtocol.isGlitchActive && Random.nextDouble() > 0.8) currentBgColor = 0xFF2A0000.toInt()
        c.drawColor(currentBgColor)

        if (!isInitialized) return

        if (gs.shakeAmount > 1f || StoryProtocol.isGlitchActive) {
            val totalShake = gs.shakeAmount + (if(StoryProtocol.isGlitchActive) 10f else 0f)
            sdx = (Random.nextFloat() - 0.5f) * totalShake
            sdy = (Random.nextFloat() - 0.5f) * totalShake
            c.translate(sdx, sdy)
            gs.shakeAmount *= 0.85f
        }

        if (gs.chromaticIntensity > 0f) {
            c.save()
            c.translate((Random.nextFloat() - 0.5f) * (gs.chromaticIntensity * scale * 0.02f), 0f)
            c.drawColor(((gs.chromaticIntensity * 50).toInt() shl 24) or 0xFF0000)
        }

        worldRenderer.drawGrid(c, scale, gs, targetW, targetH)

        when (gs.state) {
            5, 7, 6, 4 -> storyStep = menuRenderer.drawStory(c, if (gs.state == 4) StoryProtocol.badEndingLines else currentStoryLines, scale, gs, targetW, targetH, storyStep)
            0 -> {
                uiMainMenu.draw(c, scale, gs, targetW, targetH, effectSys)
            }
            2 -> menuRenderer.drawPause(c, scale, gs, targetW, targetH)
            3 -> uiHelpMenu.draw(c, scale, targetW, targetH, gs)
            10 -> uiDecompiler.draw(c, targetW, targetH, scale)
            11 -> uiArchives.draw(c, targetW, targetH, gs, scale)
            12 -> {
                menuRenderer.drawLevelVictory(c, scale, gs, targetW, targetH)
                // --- NAYA: AUTO-NEXT LEVEL LOGIC ---
                // Wait for 2 seconds (victory screen) then start the next level
                if (SaveManager.isAutoNextLevelEnabled && gs.gameMode == 0 && gs.stateTimer > 2.0f) {
                    val nextLevel = gs.currentLevel + 1
                    startGame(0, nextLevel)
                }
            }
            13 -> uiArsenal.draw(c, targetW, targetH, scale, gs)
            14 -> uiNanoOS.draw(c, targetW, targetH, scale, gs.timeSinceStart)
            1, 8, 9 -> {
                c.save()
                if (gs.slowMoTimer > 0f) {
                    val zoom = 1.05f + sin(gs.timeSinceStart * 8.0).toFloat() * 0.015f
                    c.scale(zoom, zoom, targetW / 2f, targetH / 2f)
                } else if (gs.state == 9) {
                    val zoom = 1f + (gs.mergeTimer * 0.5f)
                    c.scale(zoom, zoom, gs.coreX - gs.cameraX, gs.coreY - gs.cameraY)
                }
                worldRenderer.drawMaze(c, gs, scale, targetW, targetH)
                worldRenderer.drawGamePlay(c, scale, gs, targetW, targetH)
                c.restore()

                if (gs.empFlashTimer <= 0.8f && gs.state != 9) hudRenderer.drawHUD(c, scale, gs, targetW)

                if (gs.showDebugHitboxes) modMenu.drawDebugHitboxes(c, scale, gs)

                if (gs.showOverclockTextTimer > 0f) hudRenderer.renderOverclockText(c, scale,
                    targetW, targetH)

            }
        }

        if (gs.chromaticIntensity > 0f) c.restore()

        if (gs.damageFlash > 0.05f) { c.drawColor(((gs.damageFlash * 100).toInt() shl 24) or 0xFF0000); gs.damageFlash *= (1f - 5f * dt) }
        if (gs.sectorFlash > 0.05f) { c.drawColor(((gs.sectorFlash * 80).toInt() shl 24) or 0x00FFFF); gs.sectorFlash *= (1f - 5f * dt) }
        if (gs.empFlashTimer > 0f && Random.nextDouble() < 0.3) c.drawColor((220 shl 24) or 0x050505)
        if (gs.whiteFlash > 0f) c.drawColor((min(255, (gs.whiteFlash * 255).toInt()) shl 24) or 0xFFFFFF)

        if (gs.shakeAmount > 1f || StoryProtocol.isGlitchActive) c.translate(-sdx, -sdy)

        if (gs.globalMessageTimer > 0f) {
            gs.globalMessageTimer -= dt

            val pMsg = android.graphics.Paint().apply { isAntiAlias = true }
            val pMsgText = android.graphics.Paint().apply {
                isAntiAlias = true
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
            }

            // Fade out effect
            val alpha = (min(1f, gs.globalMessageTimer * 2f) * 255).toInt().coerceIn(0, 255)

            val boxW = if (targetW > targetH) targetW * 0.4f else targetW * 0.8f
            val boxH = scale * 0.12f
            val boxX = targetW / 2f
            val boxY = scale * 0.05f // Top Notification drop-down

            val rect = android.graphics.RectF(boxX - boxW/2f, boxY, boxX + boxW/2f, boxY + boxH)

            pMsg.style = android.graphics.Paint.Style.FILL
            pMsg.color = (alpha shl 24) or 0x110505
            c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, pMsg)

            pMsg.style = android.graphics.Paint.Style.STROKE
            pMsg.color = (alpha shl 24) or (GameColors.RED and 0xFFFFFF)
            pMsg.strokeWidth = scale * 0.005f
            c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, pMsg)

            pMsgText.color = (alpha shl 24) or (GameColors.CLARITY and 0xFFFFFF)
            pMsgText.textSize = scale * 0.04f

            val lines = gs.globalMessage.split("\n")
            if (lines.size == 1) {
                c.drawText(lines[0], boxX, rect.centerY() + pMsgText.textSize * 0.3f, pMsgText)
            } else {
                c.drawText(lines[0], boxX, rect.centerY() - scale * 0.01f, pMsgText)
                c.drawText(lines[1], boxX, rect.centerY() + scale * 0.045f, pMsgText)
            }
        }

        // --- NAYA GLOBAL RENDER ---
        if (modMenu.isOpen) {
            modMenu.drawModMenu(c, scale, targetW, targetH, gs)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val scale = min(targetW, targetH)
        val vx = e.x / gameScale
        val vy = e.y / gameScale
        val action = e.actionMasked

        // NAYA: Intercept touches if Mod Menu is open anywhere
        if (modMenu.isOpen) {
            modMenu.handleModMenuTouch(vx, vy, action, scale, targetW, targetH, gs, modMenuListener)
            return true
        }

        when (gs.state) {
            10 -> return uiDecompiler.onTouch(vx, vy, action, scale, gs, onAppClose)
            11 -> return uiArchives.onTouch(vx, vy, action, scale, gs, onArchiveSelect, onAppClose)
            12 -> {
                // If Auto-Next is ON, ignore screen taps so it seamlessly goes to the next level
                if (action == MotionEvent.ACTION_UP && gs.stateTimer > 0.5f && !SaveManager.isAutoNextLevelEnabled) {
                    EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 50)
                    changeState(11)
                }
                return true
            }
            13 -> return uiArsenal.onTouch(vx, vy, action, gs, onAppClose)
            14 -> return uiNanoOS.onTouch(vx, vy, action, { appIndex ->
                when (appIndex) {
                    0 -> changeState(10) // Launch Decompiler
                    1 -> changeState(13) // Launch Arsenal
                    2 -> changeState(11) // Launch Archives
                }
            }, onDisconnect)
            5, 7, 4, 6 -> {
                // ACTION_UP ke sath sath multi-touch aur swipe cancels ko bhi register karenge
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {

                    // --- FIX: Strict 1.5s freeze aur top-half restriction ko jadd se hataya ---
                    // Ab player jab chahe tab dynamic tap kar sakta hai pure screen par!

                    // Pata karo screen par kaun si dialogue lines dikh rahi hain right now
                    val activeLines = if (gs.state == 4) StoryProtocol.badEndingLines else currentStoryLines

                    // TWO-TAP SYSTEM LOGIC:
                    if (storyStep < activeLines.size) {
                        // TAP 1: Agar typewriter chal raha hai, toh click karte hi instantly poora text reveal ho jaye
                        storyStep = activeLines.size
                    } else {
                        if (gs.stateTimer < 0.5f || vy < targetH / 2f) return true
                        // TAP 2: Agar text pehle se poora typed hai, toh click par agali screen par badho
                        if (gs.state == 7) changeState(gs.nextStateAfterStory) else disconnectCable()
                    }
                }
                return true
            }
            0 -> {

                if (action == MotionEvent.ACTION_DOWN && vx > targetW - scale * 0.15f && vy < scale * 0.15f) {
                    modMenu.isOpen = true
                    EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_ALERT, 100)
                    return true
                }

                return uiMainMenu.onTouch(vx, vy, action, scale, targetW, targetH, this, gs, onDifficultyToggle, onHelpOpen, onMenuRoute)
            }
            3 -> return uiHelpMenu.onTouch(vx, vy, action, scale, gs, this, effectSys, onHelpClose)
            2 -> {
                if (action == MotionEvent.ACTION_UP && gs.stateTimer > 0.2f) {
                    if (vy > targetH * 0.8f) { // NAYA: MOD MENU touch logic
                        modMenu.isOpen = true
                    }
                    else if (vy > targetH * 0.68f) {
                        disconnectCable()
                    }
                    else if (vx > targetW - scale*0.15f && vy < scale*0.15f || (vy in targetH*0.5f..targetH*0.68f)) {
                        if (gs.isRotationWarning) {
                            EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 100)
                        } else {
                            changeState(if (gs.coreX > 0f && gs.bossHp <= 0 && gs.currentSector > 5) 8 else 1)
                            lastFrameTime = System.nanoTime()
                        }
                    }
                }
                return true
            }
            1, 8 -> {
                return touchController.handleTouch(e, 0f, 0f, gameScale, targetW, targetH, scale)
            }
        }
        return true
    }

    private fun triggerPulseAction() {
        if (gs.cooldownTimer <= 0f) {
            gs.pulse = true
            gs.pulseR = 0f
            gs.cooldownTimer = 0.25f * UpgradeSystem.getPulseCooldownMultiplier()
            gs.visionClarity = max(0.0f, gs.visionClarity - 0.25f)
            EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 150)
        } else {
            EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 100)
        }
    }

    private fun handleDamage(scale: Float) {
            // NAYA: God Mode Check
            if (gs.modGodMode && gs.hp <= 1) {
                StoryProtocol.showIngameMessage("MOD: GOD MODE PREVENTED DEATH", 1.5f)
                return
            }





        gs.hp--; gs.combo = 0; gs.comboBreakTimer = 1.0f; gs.damageFlash = 1f; gs.shakeAmount = scale * 0.08f
        gs.overclockMeter -= gs.overclockMeter*0.25f
        gs.playerIframe = 1.5f; gs.chromaticIntensity = 1.0f
        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 150)
        StoryProtocol.showIngameMessage(R.string.msg_damage_detected, 1.5f)

        if (gs.hp <= 0) {
            SaveManager.addData(gs.collectedDataKB); SaveManager.saveRunResult(gs.score)
            if (gs.gameMode == 1) SaveManager.updateStoryStreak(false, gs.difficulty == 1, gs.selectedStoryAct)
            StoryProtocol.isGlitchActive = true; changeState(4)
        }
    }

    private fun triggerBoss(type: Int, scale: Float) {
        gs.bossActive = true; gs.bossType = type; gs.bossMaxHp = 3 + gs.wave + gs.currentSector
        gs.bossHp = gs.bossMaxHp

        // --- NAYA: SAFE BOSS SPAWN LOGIC ---
        var safeX = gs.px
        var safeY = gs.py
        gs.gridMap?.let { grid ->
            val w = grid.size
            val h = grid[0].size
            val minDistanceSq = (scale * 0.8f) * (scale * 0.8f)
            repeat(101) {
                val rx = Random.nextInt(1, w - 1)
                val ry = Random.nextInt(1, h - 1)

                if (grid[rx][ry] != 1) {
                    val tryX = rx * gs.tileSize + gs.tileSize / 2f
                    val tryY = ry * gs.tileSize + gs.tileSize / 2f
                    val dx = tryX - gs.px
                    val dy = tryY - gs.py

                    if ((dx * dx) + (dy * dy) > minDistanceSq) {
                        safeX = tryX
                        safeY = tryY
                        return@let // Exits the 'let' block early once found
                    }
                }
            }
        }
        gs.bossX = safeX
        gs.bossY = safeY
        // ------------------------------------
        gs.shakeAmount = scale * 0.08f; gs.damageFlash = 0.3f; gs.chromaticIntensity = 0.5f
        StoryProtocol.startBossIntro(type); StoryProtocol.showIngameMessage(StoryProtocol.currentBossNameRes, 4f)
        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_ALERT, 400)
        enemySys.spawnSwarmIfNeeded(gs, targetW, targetH)
    }

    private fun handleCoreUnlock(perfectEnd: Boolean) {
        var finalReward = gs.collectedDataKB
        if (gs.gameMode == 1) {
            val currentStreak = if (gs.difficulty == 1) SaveManager.currentHardStreak else SaveManager.currentStoryStreak
            val mul = when (currentStreak) { 0, 1 -> 1.0; 2 -> 1.25; 3 -> 1.50; else -> 2.0 }
            finalReward = (finalReward * mul).toLong()

            SaveManager.updateStoryStreak(true, gs.difficulty == 1, gs.selectedStoryAct)

            if (SaveManager.unlockedStoryStreak >= 3) {
                StoryProtocol.showIngameMessage("ADMIN: \"TRACE COMPLETED. ENGAGING BLACKOUT.\"", 5f)
                EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_ALERT, 1000)
            } else {
                StoryProtocol.showIngameMessage("PAYLOAD ACCESSED. EXTRACTING...", 4f)
            }
        } else {
            StoryProtocol.showIngameMessage("SYSTEM CORE UNLOCKED. FOLLOW THE SIGNAL.", 4f)
        }

        SaveManager.addData(finalReward); SaveManager.saveRunResult(gs.score)
        gs.state = 8; gs.isPerfectEnd = perfectEnd
        gs.coreRadius = min(targetW, targetH) * 0.15f

        for (i in 0 until enemySys.n) { enemySys.ex[i] = -5000f; enemySys.vis[i] = 0f }
    }
}