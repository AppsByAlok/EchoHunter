package com.appsbyalok.echohunter.view

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
import com.appsbyalok.echohunter.ui.UIDecompiler
import com.appsbyalok.echohunter.ui.UIHelpMenu
import com.appsbyalok.echohunter.ui.UIMainMenu
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

    private var targetW = 1920f
    private var targetH = 1080f
    private var gameScale = 1f

    private var lastFrameTime = System.nanoTime()
    private var isInitialized = false
    private var currentStoryLines = StoryProtocol.storyIntroLines
    private var storyStep = 0

    private var isModMenuOpen = false
    private var holdingLevelDir = 0
    private var holdStartTime = 0L
    private var lastLevelChangeTime = 0L

    private var lockedIsLandscape: Boolean? = null

    private val gs = GameState()
    private val effectSys = EffectSystem()
    private val enemySys = EnemySystem()
    private val collisionSys = CollisionSystem(gs, effectSys, enemySys)
    private val gameEngine = GameEngine(gs, effectSys, enemySys, collisionSys, context)

    private val worldRenderer = WorldRenderer(context, effectSys, enemySys)
    private val hudRenderer = HUDRenderer(context)
    private val menuRenderer = MenuRenderer(context)
    private val uiMainMenu = UIMainMenu(context)
    private val uiHelpMenu = UIHelpMenu(context)
    private val uiDecompiler = UIDecompiler()
    private val uiArchives = UIArchives()
    private val touchController = TouchController(gs)

    private val onMenuRoute: (Int) -> Unit = { mode -> routeMenuConnection(mode) }
    private val onDisconnect: () -> Unit = { disconnectCable() }
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
    }

    fun restoreState(b: Bundle) {
        gs.restoreState(b)
        b.getIntArray("currentStoryLines")?.let { currentStoryLines = it }
        storyStep = b.getInt("storyStep", 0)
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
            10, 11, 12 -> { disconnectCable(); true }
            9 -> true
            else -> false
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val deviceW = w.toFloat()
        val deviceH = h.toFloat()

        val oldTargetW = targetW
        val oldTargetH = targetH

        if (deviceW >= deviceH) {
            targetH = 1080f
            targetW = 1080f * (deviceW / deviceH)
        } else {
            targetW = 1080f
            targetH = 1080f * (deviceH / deviceW)
        }

        gameScale = deviceW / targetW
        worldRenderer.updateDashEffect(min(targetW, targetH))

        if (!isInitialized) {
            gs.px = targetW / 2f; gs.py = targetH / 2f
            gs.baseWorldSpeed = targetW * 0.2f
            enemySys.respawnAll(gs, targetW, targetH)

            var startX = targetW
            for (i in 0 until gs.obsCount) {
                startX += (targetW * 0.6f); gs.obsX[i] = startX
                gs.randomizeObstacle(i, targetH)
            }
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

            if (gs.gridMap == null) {
                gs.px = (gs.px / oldTargetW) * targetW
                gs.py = (gs.py / oldTargetH) * targetH

                for (i in 0 until gs.obsCount) {
                    gs.obsX[i] = (gs.obsX[i] / oldTargetW) * targetW
                    gs.obsGapY[i] = (gs.obsGapY[i] / oldTargetH) * targetH
                    gs.obsGapSize[i] = (gs.obsGapSize[i] / oldTargetH) * targetH
                }
            }

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

        var startX = targetW
        for (i in 0 until gs.obsCount) {
            startX += (targetW * 0.6f); gs.obsX[i] = startX
            gs.randomizeObstacle(i, targetH)
        }
        enemySys.respawnAll(gs, targetW, targetH)

        val scale = min(targetW, targetH)
        gameEngine.generateLevelMaze(targetW, targetH, scale)

        changeState(1)
        lastFrameTime = System.nanoTime()
    }

    private fun disconnectCable() {
        uiMainMenu.disconnect()
        changeState(0)
    }

    private fun routeMenuConnection(mode: Int) {
        when (mode) {
            0 -> changeState(11)
            1 -> if (!SaveManager.isStoryModeUnlocked) {
                StoryProtocol.showIngameMessage("ADMIN: \"MAINFRAME ACCESS DENIED.\"", 2f)
                uiMainMenu.turnOffSwitch()
            } else startGame(mode)
            2 -> changeState(10)
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
                if (isModMenuOpen) drawModMenu(c, scale, targetW, targetH)
            }
            2 -> menuRenderer.drawPause(c, scale, gs, targetW, targetH)
            3 -> uiHelpMenu.draw(c, scale, targetW, targetH, gs)
            10 -> uiDecompiler.draw(c, targetW, targetH, scale, gs.timeSinceStart)
            11 -> uiArchives.draw(c, targetW, targetH, scale)
            12 -> menuRenderer.drawLevelVictory(c, scale, gs, targetW, targetH)
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

                if (gs.empFlashTimer <= 0.8f && gs.state != 9) hudRenderer.drawHUD(c, scale, gs, targetW, targetH, gs.isOverclocked)
                if (gs.showOverclockTextTimer > 0f) hudRenderer.renderOverclockText(c, scale, gs, targetW, targetH)
            }
        }

        if (gs.chromaticIntensity > 0f) c.restore()

        if (gs.damageFlash > 0.05f) { c.drawColor(((gs.damageFlash * 100).toInt() shl 24) or 0xFF0000); gs.damageFlash *= (1f - 5f * dt) }
        if (gs.sectorFlash > 0.05f) { c.drawColor(((gs.sectorFlash * 80).toInt() shl 24) or 0x00FFFF); gs.sectorFlash *= (1f - 5f * dt) }
        if (gs.empFlashTimer > 0f && Random.nextDouble() < 0.3) c.drawColor((220 shl 24) or 0x050505)
        if (gs.whiteFlash > 0f) c.drawColor((min(255, (gs.whiteFlash * 255).toInt()) shl 24) or 0xFFFFFF)

        if (gs.shakeAmount > 1f || StoryProtocol.isGlitchActive) c.translate(-sdx, -sdy)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val scale = min(targetW, targetH)
        val vx = e.x / gameScale
        val vy = e.y / gameScale
        val action = e.actionMasked

        when (gs.state) {
            10 -> return uiDecompiler.onTouch(vx, vy, action, scale, onDisconnect)
            11 -> return uiArchives.onTouch(vx, vy, action, scale, onArchiveSelect, onDisconnect)
            12 -> {
                if (action == MotionEvent.ACTION_UP && gs.stateTimer > 0.5f) {
                    EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 50)
                    changeState(11)
                }
                return true
            }
            5, 7, 4, 6 -> {
                if (action == MotionEvent.ACTION_UP) {
                    if (gs.stateTimer < 1.5f || vy < targetH / 2f) return true
                    if (storyStep < currentStoryLines.size) storyStep = currentStoryLines.size
                    else { if (gs.state == 7) changeState(gs.nextStateAfterStory) else disconnectCable() }
                }
                return true
            }
            0 -> {
                if (isModMenuOpen) {
                    handleModMenuTouch(vx, vy, action, scale, targetW, targetH)
                    return true
                }

                if (action == MotionEvent.ACTION_DOWN && vx > targetW - scale * 0.15f && vy < scale * 0.15f) {
                    isModMenuOpen = true
                    EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_ALERT, 100)
                    return true
                }

                return uiMainMenu.onTouch(vx, vy, action, scale, targetW, targetH, this, gs, onDifficultyToggle, onHelpOpen, onMenuRoute)
            }
            3 -> return uiHelpMenu.onTouch(vx, vy, action, scale, gs, this, effectSys, onHelpClose)
            2 -> {
                if (action == MotionEvent.ACTION_UP && gs.stateTimer > 0.2f) {
                    if (vy > targetH * 0.68f) {
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
        gs.hp--; gs.combo = 0; gs.comboBreakTimer = 1.0f; gs.damageFlash = 1f; gs.shakeAmount = scale * 0.08f
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
        gs.bossHp = gs.bossMaxHp; gs.bossX = gs.cameraX + targetW + scale * 0.1f; gs.bossY = gs.cameraY + targetH / 2f
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

    private fun updateModMenuHoldLogic() {
        if (holdingLevelDir != 0) {
            val currentTime = System.currentTimeMillis()
            val holdDuration = currentTime - holdStartTime
            val delayMs = max(20L, 300L - (holdDuration / 5L))

            if (currentTime - lastLevelChangeTime >= delayMs) {
                changeLevel(holdingLevelDir)
                lastLevelChangeTime = currentTime
            }
        }
    }

    private fun changeLevel(dir: Int) {
        val newLevel = max(1, SaveManager.maxCampaignLevel + dir)
        SaveManager.debugSetLevel(newLevel)
        EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 30)
    }

    private fun drawModMenu(c: Canvas, scale: Float, targetW: Float, targetH: Float) {
        updateModMenuHoldLogic()
        val p = android.graphics.Paint().apply { isAntiAlias = true; color = 0xEE050505.toInt() }
        c.drawRect(0f, 0f, targetW, targetH, p)

        val panelRect = android.graphics.RectF(targetW * 0.15f, targetH * 0.1f, targetW * 0.85f, targetH * 0.9f)
        p.color = 0xFF111111.toInt()
        c.drawRoundRect(panelRect, scale * 0.05f, scale * 0.05f, p)
        p.style = android.graphics.Paint.Style.STROKE
        p.color = GameColors.RED
        p.strokeWidth = scale * 0.01f
        c.drawRoundRect(panelRect, scale * 0.05f, scale * 0.05f, p)

        val pText = android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
        }

        pText.color = GameColors.RED
        pText.textSize = scale * 0.08f
        c.drawText("[ DEVELOPER MOD MENU ]", targetW / 2f, targetH * 0.22f, pText)

        val btnHeight = scale * 0.12f
        fun drawButton(text: String, cx: Float, cy: Float, width: Float, color: Int, isPressed: Boolean = false) {
            val rect = android.graphics.RectF(cx - width/2, cy - btnHeight/2, cx + width/2, cy + btnHeight/2)

            p.style = android.graphics.Paint.Style.FILL
            p.color = if (isPressed) 0xFF444444.toInt() else 0xFF222222.toInt()
            c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, p)

            p.style = android.graphics.Paint.Style.STROKE
            p.color = color
            c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, p)

            pText.textSize = scale * 0.05f
            pText.color = color
            c.drawText(text, cx, cy - (pText.descent() + pText.ascent()) / 2, pText)
        }

        val startY = targetH * 0.4f
        val gap = scale * 0.16f

        pText.textSize = scale * 0.06f
        pText.color = GameColors.CLARITY
        c.drawText("LEVEL: ${SaveManager.maxCampaignLevel}", targetW / 2f, startY - (pText.descent() + pText.ascent()) / 2, pText)

        val sideBtnWidth = scale * 0.25f
        val leftCx = targetW / 2f - scale * 0.45f
        val rightCx = targetW / 2f + scale * 0.45f

        drawButton("<< -1", leftCx, startY, sideBtnWidth, GameColors.CLARITY, holdingLevelDir == -1)
        drawButton("+1 >>", rightCx, startY, sideBtnWidth, GameColors.CLARITY, holdingLevelDir == 1)

        drawButton("+ 1 MB DATA", targetW / 2f, startY + gap, scale * 0.6f, GameColors.OVERCLOCK)
        drawButton("+ 1 GB DATA", targetW / 2f, startY + gap * 2, scale * 0.6f, GameColors.OVERCLOCK)
        drawButton("CLOSE MENU", targetW / 2f, startY + gap * 3.5f, scale * 0.6f, GameColors.YELLOW)
    }

    private fun handleModMenuTouch(vx: Float, vy: Float, action: Int, scale: Float, targetW: Float, targetH: Float) {
        val startY = targetH * 0.4f
        val gap = scale * 0.16f
        val btnHeight = scale * 0.12f

        val leftBtnRight = targetW / 2f - scale * 0.45f + (scale * 0.125f)
        val rightBtnLeft = targetW / 2f + scale * 0.45f - (scale * 0.125f)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (vy in (startY - btnHeight/2)..(startY + btnHeight/2)) {
                    if (vx < leftBtnRight) {
                        holdingLevelDir = -1
                        holdStartTime = System.currentTimeMillis()
                        lastLevelChangeTime = holdStartTime
                        changeLevel(-1)
                    } else if (vx > rightBtnLeft) {
                        holdingLevelDir = 1
                        holdStartTime = System.currentTimeMillis()
                        lastLevelChangeTime = holdStartTime
                        changeLevel(1)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                holdingLevelDir = 0
                if (vy in (startY + gap - btnHeight/2)..(startY + gap + btnHeight/2)) {
                    SaveManager.addData(1024L)
                    EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 100)
                }
                else if (vy in (startY + gap * 2 - btnHeight/2)..(startY + gap * 2 + btnHeight/2)) {
                    SaveManager.addData(1024L * 1024L)
                    EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 100)
                }
                else if (vy in (startY + gap * 3.5f - btnHeight/2)..(startY + gap * 3.5f + btnHeight/2)) {
                    isModMenuOpen = false
                    EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 100)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                holdingLevelDir = 0
            }
        }
    }
}