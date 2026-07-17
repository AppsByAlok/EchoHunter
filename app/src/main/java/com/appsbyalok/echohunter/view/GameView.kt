package com.appsbyalok.echohunter.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ToneGenerator
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import com.appsbyalok.echohunter.MainActivity
import com.appsbyalok.echohunter.R
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.data.UpgradeSystem
import com.appsbyalok.echohunter.engine.GameEngine
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.input.AttackMode
import com.appsbyalok.echohunter.input.TouchController
import com.appsbyalok.echohunter.statemachine.AppStateManager
import com.appsbyalok.echohunter.systems.CollisionSystem
import com.appsbyalok.echohunter.systems.EffectSystem
import com.appsbyalok.echohunter.systems.EnemySystem
import com.appsbyalok.echohunter.systems.GlitchBossBehavior
import com.appsbyalok.echohunter.systems.GuardianBossBehavior
import com.appsbyalok.echohunter.systems.OmegaBossBehavior
import com.appsbyalok.echohunter.systems.SpawnerSystem
import com.appsbyalok.echohunter.systems.StalkerBossBehavior
import com.appsbyalok.echohunter.systems.UltimaBossBehavior
import com.appsbyalok.echohunter.systems.triggerCinematicFocus
import com.appsbyalok.echohunter.ui.UIArchives
import com.appsbyalok.echohunter.ui.UIArsenal
import com.appsbyalok.echohunter.ui.UIDecompiler
import com.appsbyalok.echohunter.ui.UIHelpMenu
import com.appsbyalok.echohunter.ui.UIMainMenu
import com.appsbyalok.echohunter.ui.UINanoOS
import com.appsbyalok.echohunter.ui.UITerminal
import com.appsbyalok.echohunter.utils.EchoAudioManager
import com.appsbyalok.echohunter.utils.GameColors
import com.appsbyalok.echohunter.view.renderers.HUDRenderer
import com.appsbyalok.echohunter.view.renderers.MenuRenderer
import com.appsbyalok.echohunter.view.renderers.WorldRenderer
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class GameView(context: Context) : View(context) {

    val gs = GameState()
    internal val effectSys = EffectSystem()
    internal val enemySys = EnemySystem()
    internal val spawnerSys = SpawnerSystem(enemySys, effectSys)
    internal val collisionSys = CollisionSystem(gs, effectSys, enemySys, spawnerSys)

    val engine = GameEngine(gs, effectSys, enemySys, spawnerSys, collisionSys, context)

    internal val worldRenderer = WorldRenderer(context, effectSys, enemySys)
    internal val hudRenderer = HUDRenderer(context)
    internal val menuRenderer = MenuRenderer(context)
    internal val uiMainMenu = UIMainMenu(context)
    internal val uiHelpMenu = UIHelpMenu(context)
    internal val uiDecompiler = UIDecompiler()
    internal val uiArsenal = UIArsenal()
    internal val uiNanoOS = UINanoOS()
    internal val uiArchives = UIArchives()
    internal val uiTerminal = UITerminal()
    internal val uiSettings = com.appsbyalok.echohunter.ui.UISettings()
    internal val touchController = TouchController(gs)

    internal var storyStep = 0
    internal var currentStoryLines = StoryProtocol.storyIntroLines

    // --- THE APP STATE MANAGER HOOK ---
    internal val stateManager = AppStateManager(this, gs)

    var gameScale = 1f
    var lastFrameTime = System.nanoTime()

    var menuReturnState = 0

    // --- Callbacks for State Machine & UI ---
    internal val onAppClose: () -> Unit = { changeState(menuReturnState) }
    internal val onArchiveSelect: (Int) -> Unit = { lvl -> startGame(0, lvl) }
    internal val onHelpOpen: () -> Unit = { changeState(3) }
    internal val onHelpClose: () -> Unit = { changeState(0) }
    internal val onWipeData: () -> Unit = {
        gs.resetGame()
        disconnectCable()
    }
    internal val onOrientationChange: () -> Unit = {
        (context as? MainActivity)?.applyOrientation()
    }
    internal val onDifficultyToggle: () -> Unit = {
        if (SaveManager.isHardModeUnlocked) {
            gs.difficulty = if (gs.difficulty == 0) 1 else 0
            EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 100)
        } else {
            EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 100)
        }
    }
    internal val onMenuRoute: (Int) -> Unit = { route ->
        when (route) {
            0 -> { // 0 = Sandbox -> Campaign Archives
                gs.gameMode = 0
                menuReturnState = 0
                changeState(11)
            }
            1 -> {
                if (SaveManager.isStoryModeUnlocked) {
                    startGame(1, 1)
                } else {
                    gs.showGlobalMessage("ADMIN: \"MAINFRAME ACCESS DENIED.\"", 2f)
                    uiMainMenu.turnOffSwitch()
                    EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 100)
                }
            }
            2 ->{// 2 = Nano-OS -> OS Menu
                menuReturnState = 0
                changeState(14)
            }
        }
    }
    internal val onDisconnect: () -> Unit = { disconnectCable() }


    private val pAlert = Paint().apply {
        color = GameColors.YELLOW
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val rectToast = RectF()

    init {
        EchoAudioManager.init()

        // Attach Engine Logic
        engine.onChangeState = { s -> changeState(s) }
        engine.onDamage = { s -> takeDamage(s) }
        engine.onScore = { s -> addScore(s) }
        engine.onCoreUnlock = { p -> handleCoreUnlock(p) }
        engine.onBossTrigger = { t, s -> triggerBoss(t, s) }
        engine.onStoryState = { lines, nextState ->
            currentStoryLines = lines
            storyStep = 0
            gs.nextStateAfterStory = nextState
            changeState(7)
        }

        touchController.onPauseClicked = { pauseGame() }
        touchController.onPulseTriggered = { triggerPulseAction() }

        // --- INIT STATE MANAGER ---
        syncStateToManager()
    }

    fun startGame(mode: Int, level: Int) {
        gs.gameMode = mode
        gs.currentLevel = level
        gs.resetGame()

        if (mode == 0) {
            SaveManager.incrementLevelAttempts(level, gs.difficulty == 1)
        }
        
        // Restore persistent combat/loadout preferences after resetGame clears transient state.
        gs.controls.activeAttackMode = AttackMode.entries.toTypedArray()[SaveManager.activeAttackMode]
        gs.controls.currentWeapon = SaveManager.activeWeapon
        gs.controls.currentTrap = SaveManager.activeTrap
        gs.isAutoPilotActive = SaveManager.isAutoPilotEnabled
        gs.autoPilotTimer = if (gs.isAutoPilotActive) 600f else 0f

        effectSys.reset()
        enemySys.respawnAll(gs)
        engine.generateLevelMaze(width.toFloat(), height.toFloat(), gameScale)
        uiMainMenu.disconnect()
        lastFrameTime = System.nanoTime()
        EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 50)


        if (mode == 1) {
            currentStoryLines = gs.modeStrategy.getIntroLines()
            storyStep = 0
            gs.nextStateAfterStory = 1
            changeState(5)
        } else {
            changeState(1)
        }
    }

    fun disconnectCable() {
        cleanupLevelEffects()
        uiMainMenu.disconnect()
        changeState(0)
        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 100)
    }

    fun returnToArchives() {
        cleanupLevelEffects()
        changeState(11) // State 11 is UIArchives
        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 100)
    }

    private fun cleanupLevelEffects() {
        if (!StoryProtocol.isGlitchActive) {
            gs.shakeAmount = 0f
            gs.chromaticIntensity = 0f
            gs.sectorFlash = 0f
        }
    }

    fun pauseGame() {
        if (gs.state == 1 || gs.state == 8) {
            changeState(2)
            EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 100)
        }
    }

    fun resetGame() {
        val cm = gs.gameMode
        val cl = gs.currentLevel
        startGame(cm, cl)
    }

    fun changeState(newState: Int) {
        gs.state = newState
        gs.stateTimer = 0f
        syncStateToManager()
    }

    // --- State Switcher ---
    private fun syncStateToManager() {
        val newStateObj = when (gs.state) {
            0 -> stateManager.mainMenuState
            1, 8, 9 -> stateManager.gameplayState
            2 -> stateManager.pauseState
            3 -> stateManager.helpState
            4, 5, 6, 7 -> stateManager.storyState
            12 -> stateManager.victoryState
            10, 11, 13, 14, 15, 16 -> stateManager.subMenuState
            else -> stateManager.mainMenuState
        }
        if (stateManager.currentState != newStateObj) {
            stateManager.changeState(newStateObj)
        }
    }

    private fun takeDamage(scale: Float) {
        if (gs.modGodMode && gs.hp <= 1) {
            StoryProtocol.showIngameMessage("MOD: GOD MODE PREVENTED DEATH", 1.5f)
            return
        }

        gs.hp--
        gs.tookDamageInLevel = true
        gs.combo = 0
        gs.comboBreakTimer = 1.0f
        gs.overclockMeter -= gs.overclockMeter * 0.25f
        gs.playerIframe = 1.5f
        gs.damageFlash = 1.0f
        gs.shakeAmount = scale * 0.08f
        gs.chromaticIntensity = 1.0f
        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 150)
        StoryProtocol.showIngameMessage(R.string.msg_damage_detected, 1.5f)

        if (gs.hp <= 0) {
            SaveManager.addData(gs.collectedDataKB)
            SaveManager.saveRunResult(gs.score)
            if (gs.gameMode == 1) SaveManager.updateStoryStreak(false, gs.difficulty == 1, gs.selectedStoryAct)

            val config = com.appsbyalok.echohunter.data.LevelEngine.getLevelConfig(gs.currentLevel)
            StoryProtocol.isGlitchActive =
                config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.BOSS) ||
                    config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.ELIMINATION) ||
                    gs.difficulty == 1

            currentStoryLines = StoryProtocol.badEndingLines
            storyStep = 0
            gs.nextStateAfterStory = 0
            changeState(4)
        }
    }

    private fun addScore(points: Long) {
        gs.score += points
//        if (gs.score > SaveManager.highScore) {
//            // Can trigger a small sound effect for high score
//        }
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

        SaveManager.addData(finalReward)
        SaveManager.saveRunResult(gs.score)

        gs.isPerfectEnd = perfectEnd
        gs.coreRadius = gameScale * 0.15f
        if (gs.coreX <= 0f || gs.coreY <= 0f) {
            gs.coreX = gs.px + gameScale * 0.5f
            gs.coreY = gs.py
        }
        for (i in 0 until enemySys.n) {
            enemySys.ex[i] = -5000f
            enemySys.ey[i] = -5000f
            enemySys.vis[i] = 0f
        }
        changeState(8)
    }

    private fun triggerBoss(type: Int, scale: Float) {
        gs.bossActive = true
        var bType = type
        if (gs.currentLevel % 100 == 0) bType = 5 // Force Ultra Boss on level 100/200/etc
        
        gs.bossType = bType
        gs.bossLockTimer = 1.0f 

        val behavior = when (bType) {
            1 -> GuardianBossBehavior
            2 -> StalkerBossBehavior
            3 -> GlitchBossBehavior
            4 -> OmegaBossBehavior
            5 -> UltimaBossBehavior
            else -> GuardianBossBehavior
        }
        
        val config = com.appsbyalok.echohunter.data.LevelEngine.getLevelConfig(gs.currentLevel, gs.difficulty)
        val bossScaling = com.appsbyalok.echohunter.data.LevelEngine.getSaturatedValue(gs.currentLevel, 0f, 475f, 300f)

        // Difficulty-based Boss HP scaling
        val difficultyHpMult = if (gs.difficulty == 1) 1.2f else 0.7f
        gs.bossHp = ((25 + bossScaling) * behavior.baseHpMult * config.hpMultiplier * difficultyHpMult).toInt()
        gs.bossMaxHp = gs.bossHp
        var safeX = gs.px + scale * 1.2f
        var safeY = gs.py
        gs.gridMap?.let { grid ->
            val minDistanceSq = (scale * 0.8f) * (scale * 0.8f)
            var found = false
            repeat(101) {
                if (!found) {
                    val rx = Random.nextInt(1, grid.size - 1)
                    val ry = Random.nextInt(1, grid[0].size - 1)
                    if (grid[rx][ry] != com.appsbyalok.echohunter.data.MazeGenerator.WALL) {
                        val tryX = rx * gs.tileSize + gs.tileSize / 2f
                        val tryY = ry * gs.tileSize + gs.tileSize / 2f
                        val dx = tryX - gs.px
                        val dy = tryY - gs.py
                        if (dx * dx + dy * dy > minDistanceSq) {
                            safeX = tryX
                            safeY = tryY
                            found = true
                        }
                    }
                }
            }
        }
        gs.bossX = safeX
        gs.bossY = safeY
        gs.isBossRage = false
        gs.shakeAmount = scale * 0.12f // Stronger shake on boss spawn
        gs.damageFlash = 0.3f
        gs.chromaticIntensity = 0.5f

        // --- NAYA: CENTRALIZED CINEMATIC FOCUS ---
        gs.triggerCinematicFocus(safeX, safeY, zoom = 1.4f, duration = 1.5f, hitStop = 0.2f)
        gs.shakeAmount = scale * 0.15f // Intense vibration on boss arrival

        StoryProtocol.startBossIntro(bType)
        gs.showGlobalMessage(behavior.spawnMessage, 4f)
        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_ALERT, 400)
        enemySys.spawnSwarmIfNeeded(gs, scale)
    }

    private fun triggerPulseAction() {
        if (gs.cooldownTimer <= 0f) {
            gs.pulse = true
            gs.pulseR = 0f
            gs.cooldownTimer = 0.25f * UpgradeSystem.getPulseCooldownMultiplier()
            if (gs.isDarknessLevel) {
                gs.visionClarity = max(0.0f, gs.visionClarity - 0.25f)
            }
            gs.globalSonarAlert = true
            EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 150)
        } else {
            EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 100)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gameScale = min(w, h).toFloat()
        uiMainMenu.initLayout(w.toFloat(), h.toFloat())
        worldRenderer.updateDashEffect(gameScale)

        resolveHudLayout(w.toFloat(), h.toFloat())
    }

    fun resolveHudLayout(targetW: Float = width.toFloat(), targetH: Float = height.toFloat()) {
        if (targetW <= 0f || targetH <= 0f) return

        // Apply Safe Area Insets (Notch Handling from SaveManager)
        val insetL = SaveManager.lastInsetLeft
        val insetR = SaveManager.lastInsetRight
        val insetT = SaveManager.lastInsetTop
        val insetB = SaveManager.lastInsetBottom

        // Sync with HUDLayout for other systems
        gs.hudLayout.safeInsetLeft = insetL
        gs.hudLayout.safeInsetRight = insetR
        gs.hudLayout.safeInsetTop = insetT
        gs.hudLayout.safeInsetBottom = insetB

        val isPortrait = targetH > targetW
        gs.hudLayout.resolve(SaveManager.loadHudLayoutProfile(isPortrait), targetW, targetH, gameScale)
        gs.touch.moveBaseX = gs.hudLayout.movementX
        gs.touch.moveBaseY = gs.hudLayout.movementY
        gs.touch.moveKnobX = gs.touch.moveBaseX
        gs.touch.moveKnobY = gs.touch.moveBaseY
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val dt = ((now - lastFrameTime) / 1000000000.0).toFloat().coerceAtMost(0.05f)
        lastFrameTime = now

        engine.update(dt, width.toFloat(), height.toFloat(), gameScale)

        // NAYA: StateManager updates UI logic
        stateManager.update(dt, width.toFloat(), height.toFloat(), gameScale)

        // --- NAYA: MODULAR DRAW (No more giant when block!) ---
        stateManager.draw(canvas, width.toFloat(), height.toFloat(), gameScale, dt)

        drawTransientOverlays(canvas, dt)
        worldRenderer.drawCRTOverlay(canvas, gs, width.toFloat(), height.toFloat())

        // Global Overlay Message (Universal Toast - Restored Top Bar Style)
        if (gs.globalMessageTimer > 0f) {
            val alpha = (min(1f, gs.globalMessageTimer * 2f) * 255).toInt().coerceIn(0, 255)
            val boxW = if (width > height) width * 0.45f else width * 0.85f
            val boxH = gameScale * 0.12f
            val boxX = width / 2f
            val boxY = gameScale * 0.05f 

            val rect = rectToast.apply {
                set(boxX - boxW/2f, boxY, boxX + boxW/2f, boxY + boxH)
            }

            pAlert.style = Paint.Style.FILL
            pAlert.color = (alpha shl 24) or 0x110505
            canvas.drawRoundRect(rect, gameScale * 0.02f, gameScale * 0.02f, pAlert)

            pAlert.style = Paint.Style.STROKE
            pAlert.color = (alpha shl 24) or (GameColors.RED and 0xFFFFFF)
            pAlert.strokeWidth = gameScale * 0.005f
            canvas.drawRoundRect(rect, gameScale * 0.02f, gameScale * 0.02f, pAlert)

            pAlert.color = (alpha shl 24) or (GameColors.CLARITY and 0xFFFFFF)
            pAlert.textSize = gameScale * 0.04f
            pAlert.style = Paint.Style.FILL

            val text = gs.globalMessage
            val lines = text.split("\n")
            if (lines.size == 1) {
                canvas.drawText(lines[0], boxX, rect.centerY() + pAlert.textSize * 0.3f, pAlert)
            } else {
                canvas.drawText(lines[0], boxX, rect.centerY() - gameScale * 0.01f, pAlert)
                canvas.drawText(lines[1], boxX, rect.centerY() + gameScale * 0.045f, pAlert)
            }

            gs.globalMessageTimer -= dt
        }

        invalidate()
    }

    fun drawTransientOverlays(canvas: Canvas, dt: Float) {
        if (gs.damageFlash > 0f) {
            canvas.drawColor((gs.damageFlash * 100).toInt() shl 24 or 0xFF0000)
            gs.damageFlash = max(0f, gs.damageFlash - dt * 2f)
        }
        if (gs.sectorFlash > 0f) {
            canvas.drawColor((gs.sectorFlash * 80).toInt() shl 24 or 0x00FFFF)
            gs.sectorFlash = max(0f, gs.sectorFlash - dt * 1.5f)
        }
        if (gs.empFlashTimer > 0f && Random.nextDouble() < 0.3) {
            canvas.drawColor((220 shl 24) or 0x050505)
        }
        if (gs.whiteFlash > 0f) {
            canvas.drawColor((min(255, (gs.whiteFlash * 255).toInt()) shl 24) or 0xFFFFFF)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        // --- MODULAR TOUCH (TouchController aur menus handle) ---
        return stateManager.onTouch(event, x, y, event.action, gameScale, width.toFloat(), height.toFloat())
    }


    fun handleBackPressed(): Boolean {
        if (gs.state == 16) {
            onAppClose()
            return true
        }
        return stateManager.onBackPressed()
    }

    fun saveState(outState: Bundle) {
        gs.saveState(outState)
        outState.putIntArray("currentStoryLines", currentStoryLines)
        outState.putInt("storyStep", storyStep)
        outState.putInt("menuReturnState", menuReturnState)
    }

    fun restoreState(savedInstanceState: Bundle) {
        gs.restoreState(savedInstanceState)
        savedInstanceState.getIntArray("currentStoryLines")?.let { currentStoryLines = it }
        storyStep = savedInstanceState.getInt("storyStep", 0)
        menuReturnState = savedInstanceState.getInt("menuReturnState", 0)
        syncStateToManager()
    }

    // --- LIFECYCLE ---
    fun onPause() {
        pauseGame()
        val b = Bundle()
        gs.saveState(b)
        // Ensure you call shared preferences saving if implemented
    }

    fun onResume() {
        lastFrameTime = System.nanoTime()
    }
}
