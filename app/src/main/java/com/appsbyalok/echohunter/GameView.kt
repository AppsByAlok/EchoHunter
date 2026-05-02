package com.appsbyalok.echohunter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.media.ToneGenerator
import android.os.Bundle
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class GameView(context: Context) : View(context) {

    private val gs = GameState()
    private val effectSys = EffectSystem()
    private val enemySys = EnemySystem()
    private val collisionSys = CollisionSystem(gs, effectSys, enemySys)

    private val p = Paint().apply { isAntiAlias = true }
    private val pGlow = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
    private val pDash = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = 0x55FFFF00
    }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val pauseRect = RectF()
    private val repairBtnRect = RectF()

    // --- Cached Strings for Zero-GC Render Loop ---
    private var lastScore = -1; private var scoreStr = ""
    private var lastCombo = -1; private var comboStr = ""
    private var lastBankedData = -1; private var bankedStr = ""
    private var lastCoreDist = -1; private var coreDistStr = ""

    // Smart String Cache: Prevents calling context.getString() inside onDraw
    private val stringCache = SparseArray<String>()

    private fun getCachedString(resId: Int): String {
        var str = stringCache.get(resId)
        if (str == null) {
            str = context.getString(resId)
            stringCache.put(resId, str)
        }
        return str
    }

    // --- Pre-allocated Paths to avoid allocation in onDraw ---
    private val cablePath = Path()
    private val arrowPath = Path()

    private val menuTitles = intArrayOf(R.string.menu_endless, R.string.menu_story, R.string.menu_firewall)
    private val menuSubs = intArrayOf(
        R.string.menu_sub_endless,
        R.string.menu_sub_story,
        R.string.menu_sub_firewall
    )

    // --- Interactive Menu Mechanics Variables ---
    private var plugX = 0f
    private var plugY = 0f
    private var plugRestX = 0f
    private var plugRestY = 0f
    private var isDraggingPlug = false
    private var connectedMode = -1
    private var isSwitchOn = false
    private val portX = FloatArray(3)
    private val portY = FloatArray(3)

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var wasSwitchHitOnDown = false

    private var repairFadeTimer = 0f

    private var lastFrameTime = System.nanoTime()
    private var isInitialized = false

    private var currentStoryLines = StoryProtocol.storyIntroLines
    private var storyStep = 0

    // --- Cached Lambdas for Zero-GC updateLogic ---
    private val onDamage: (Float) -> Unit = { scale -> handleDamage(scale) }
    private val onScore: (Int) -> Unit = { points -> addScore(points) }
    private val onBossTrigger: (Int, Float) -> Unit = { type, scale -> triggerBoss(type, scale) }
    private val onStoryState: (IntArray, Int) -> Unit = { lines, nextSt -> setStoryState(lines, nextSt) }
    private val onCoreUnlock: (Boolean) -> Unit = { perfectEnd ->
        SaveManager.addData(gs.score)
        gs.state = 8
        gs.isPerfectEnd = perfectEnd
        val wF = width.toFloat()
        gs.coreX = gs.cameraX + wF * 5.0f
        gs.coreY = height.toFloat() / 2f
        gs.coreRadius = min(width, height).toFloat() * 0.15f
        gs.targetPx = gs.px - gs.cameraX
        gs.targetPy = gs.py
        StoryProtocol.showIngameMessage(R.string.msg_core_unlocked, 4f)

        for (i in 0 until enemySys.n) {
            enemySys.ex[i] = -5000f
            enemySys.vis[i] = 0f
        }
    }

    init {
        EchoAudioManager.init()
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

    fun pauseGame() {
        if (gs.state == 1 || gs.state == 8) changeState(2)
    }

    private fun changeState(newState: Int) {
        gs.state = newState
        gs.stateTimer = 0f
    }

    fun handleBackPressed(): Boolean {
        return when (gs.state) {
            5, 7 -> { changeState(0); true }
            1, 8 -> { changeState(2); true }
            2, 3, 4, 6 -> { changeState(0); true }
            9 -> true
            else -> false
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val scale = min(w, h).toFloat()

        // Setup DashPathEffect once on resize, rather than calculating dozens of lines in onDraw
        pDash.pathEffect = DashPathEffect(floatArrayOf(scale * 0.05f, scale * 0.05f), 0f)

        if (!isInitialized) {
            gs.px = w / 2f; gs.py = h / 2f
            gs.targetPx = gs.px; gs.targetPy = gs.py
            gs.baseWorldSpeed = w * 0.2f

            enemySys.respawnAll(gs, w.toFloat(), h.toFloat())

            var startX = w.toFloat()
            for (i in 0 until gs.obsCount) {
                startX += (w * 0.6f)
                gs.obsX[i] = startX
                gs.randomizeObstacle(i, h.toFloat())
            }

            // --- Initialize Interactive Menu UI ---
            plugRestX = w - scale * 0.08f
            plugRestY = h * 0.85f
            plugX = plugRestX
            plugY = plugRestY

            for (i in 0..2) {
                // Ports are placed towards the right-middle, leaving left side for text
                portX[i] = w * 0.55f
                portY[i] = h * 0.55f + i * (h * 0.13f)
            }

            isInitialized = true
        } else {
            gs.baseWorldSpeed = w * 0.2f
        }
    }

    private fun resetGame() {
        gs.resetGame()
        effectSys.reset()

        var startX = width.toFloat()
        for (i in 0 until gs.obsCount) {
            startX += (width * 0.6f)
            gs.obsX[i] = startX
            gs.randomizeObstacle(i, height.toFloat())
        }

        enemySys.respawnAll(gs, width.toFloat(), height.toFloat())

        gs.targetPx = width / 2f; gs.targetPy = height / 2f
        gs.px = gs.targetPx; gs.py = gs.targetPy
        changeState(1)
        lastFrameTime = System.nanoTime()
    }

    private fun startGame(mode: Int) {
        gs.gameMode = mode
        setStoryState(gs.modeStrategy.getIntroLines(), 1)
        resetGame()
        EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 50)

//        // --- DEBUG HACK: ADD THESE TWO LINES ---
//        onCoreUnlock(true)
//        gs.coreX = gs.cameraX + width.toFloat() * 1.2f // Core ko paas
//        // ---------------------------------------

        // Reset plug safely for when user returns to menu
        connectedMode = -1
        plugX = plugRestX
        plugY = plugRestY
        isSwitchOn = false
        isDraggingPlug = false
    }

    private fun setStoryState(lines: IntArray, nextSt: Int) {
        currentStoryLines = lines
        storyStep = 0
        gs.nextStateAfterStory = nextSt
        changeState(if (nextSt == 6 || nextSt == 0 && gs.hp <= 0) 4 else 7)
        if (nextSt == 6) changeState(6)
        gs.timeSinceStart = 0f
    }

    private fun addScore(points: Int) {
        gs.score += points
    }

    private fun handleDamage(scale: Float) {
        gs.hp--
        gs.combo = 0
        gs.comboBreakTimer = 1.0f
        gs.damageFlash = 1f
        gs.shakeAmount = scale * 0.08f
        gs.playerIframe = 1.5f
        gs.chromaticIntensity = 1.0f
        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 150)
        StoryProtocol.showIngameMessage(R.string.msg_damage_detected, 1.5f)
        if (gs.hp <= 0) {
            SaveManager.addData(gs.score)
            SaveManager.saveRunResult(gs.score)
            StoryProtocol.isGlitchActive = true
            changeState(4)
        }
    }

    private fun triggerBoss(type: Int, scale: Float) {
        gs.bossActive = true; gs.bossType = type; gs.bossMaxHp = 3 + gs.wave + gs.currentSector
        gs.bossHp = gs.bossMaxHp; gs.bossX = gs.cameraX + width + scale * 0.1f; gs.bossY = height / 2f
        gs.shakeAmount = scale * 0.08f; gs.damageFlash = 0.3f
        gs.chromaticIntensity = 0.5f

        StoryProtocol.startBossIntro(type)
        StoryProtocol.showIngameMessage(StoryProtocol.currentBossNameRes, 4f)

        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_ALERT, 400)
        enemySys.spawnSwarmIfNeeded(gs, width.toFloat(), height.toFloat())
    }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        var dt = (now - lastFrameTime) / 1_000_000_000f
        lastFrameTime = now
        if (dt > 0.05f) dt = 0.05f

        updateLogic(dt)
        renderScene(c, dt)
        drawCRTOverlay(c)
        invalidate()
    }

    private fun updateLogic(dt: Float) {
        gs.timeSinceStart += dt
        gs.stateTimer += dt
        StoryProtocol.update(dt)

        if (!isInitialized) return

        // Update particles in the menu state for sparks
        if (gs.state == 0) {
            effectSys.update(dt, min(width, height).toFloat())
            return
        }

        if (gs.state == 3) {
            if (repairFadeTimer > 0f) repairFadeTimer -= dt
            return
        }

        if (gs.state != 1 && gs.state != 8 && gs.state != 9) return

        val wF = width.toFloat(); val hF = height.toFloat(); val scale = min(width, height).toFloat()

        if (gs.hitStopTimer > 0f) {
            gs.hitStopTimer -= dt
            return
        }

        gs.updateTimers(dt, scale)
        val simDt = if (gs.slowMoTimer > 0f) dt * 0.15f else dt

        if (gs.state == 1 || gs.state == 8) {
            gs.updatePlayerMovement(simDt, hF, scale)
            gs.updateCameraAndMovement(simDt, wF, scale)

            if (gs.state == 8) {
                val cdx = gs.px - gs.coreX
                val cdy = gs.py - gs.coreY
                if (cdx * cdx + cdy * cdy < gs.coreRadius * gs.coreRadius) {
                    gs.state = 9
                    gs.mergeTimer = 0f
                    gs.whiteFlash = 0f
                    gs.isTouching = false
                    EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_ALERT, 800)
                }
            }
        }

        if (gs.state == 9) {
            gs.cameraX += (gs.coreX - wF / 2f - gs.cameraX) * 2f * dt
            gs.px += (gs.coreX - gs.px) * 2.5f * dt
            gs.py += (gs.coreY - gs.py) * 2.5f * dt
            gs.mergeTimer += dt

            // FIXED: Shake logic moved here to be frame-rate independent
            gs.shakeAmount = scale * 0.04f

            if (gs.mergeTimer > 2.5f) {
                gs.whiteFlash += dt * 0.5f
            }
            if (gs.whiteFlash >= 1f) {
                setStoryState(if (gs.isPerfectEnd) StoryProtocol.storyPerfectEnding else StoryProtocol.storyNeutralEnding, 6)
                gs.whiteFlash = 0f
            }
        }

        gs.updateVisibilityMath(scale, max(width, height) * 0.75f)
        gs.updatePulseRadius(simDt, max(width, height) * 0.75f)

        effectSys.recordTrail(gs.px, gs.py)
        effectSys.update(simDt, scale)

        if (gs.state == 1) {
            enemySys.updateEnemies(simDt, gs, wF, hF, scale)
            enemySys.updateBoss(simDt, gs, wF, scale)
            enemySys.updatePowerups(simDt, gs, wF, hF)

            // Using cached lambdas to prevent KFunction allocations on every frame!
            collisionSys.checkCollisions(wF, hF, scale, onDamage, onScore, onCoreUnlock)
            gs.modeStrategy.checkProgression(context, gs, scale, onBossTrigger, onStoryState)

            handleAudioBeats(simDt)
        }
    }

    private fun handleAudioBeats(dt: Float) {
        if (gs.isEnemyVeryNear) {
            gs.heartbeatTimer -= dt
            if (gs.heartbeatTimer <= 0f) {
                EchoAudioManager.playSound(ToneGenerator.TONE_SUP_RADIO_NOTAVAIL, 50)
                gs.heartbeatTimer = 0.5f
            }
        } else if (gs.isEnemyNear) {
            gs.radarPingTimer -= dt
            if (gs.radarPingTimer <= 0f) {
                EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 40)
                gs.radarPingTimer = 0.8f
            }
        } else {
            gs.radarPingTimer = 0f; gs.heartbeatTimer = 0f
        }
    }

    private var sdx = 0f; private var sdy = 0f
    private fun renderScene(c: Canvas, dt: Float) {
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
            val shift = gs.chromaticIntensity * min(width,height) * 0.02f
            c.translate((Random.nextFloat() - 0.5f) * shift, 0f)
            // FIXED: Bitwise color blending
            c.drawColor(((gs.chromaticIntensity * 50).toInt() shl 24) or 0xFF0000)
        }

        drawGrid(c)

        when (gs.state) {
            5, 7, 6 -> drawStory(c, currentStoryLines)
            4 -> drawStory(c, StoryProtocol.badEndingLines)
            0 -> drawMenu(c)
            2 -> drawPause(c)
            3 -> drawHelp(c)
            1, 8, 9 -> {
                c.save()
                if (gs.slowMoTimer > 0f) {
                    val zoom = 1.05f + sin(gs.timeSinceStart * 8f) * 0.015f
                    c.scale(zoom, zoom, width / 2f, height / 2f)
                } else if (gs.state == 9) {
                    val zoom = 1f + (gs.mergeTimer * 0.5f)
                    c.scale(zoom, zoom, gs.coreX - gs.cameraX, gs.coreY)
                }

                drawGamePlay(c, dt)
                c.restore()

                if (gs.empFlashTimer <= 0.8f && gs.state != 9) drawHUD(c, min(width, height).toFloat(), gs.isOverclocked)
            }
        }

        if (gs.chromaticIntensity > 0f) {
            c.restore()
        }

        if (gs.damageFlash > 0.05f) {
            c.drawColor(((gs.damageFlash * 100).toInt() shl 24) or (GameColors.RED and 0xFFFFFF))
            gs.damageFlash *= (1f - 5f * dt)
        }
        if (gs.sectorFlash > 0.05f) {
            c.drawColor(((gs.sectorFlash * 80).toInt() shl 24) or (GameColors.PULSE and 0xFFFFFF))
            gs.sectorFlash *= (1f - 5f * dt)
        }
        if (gs.empFlashTimer > 0f && Random.nextDouble() < 0.3) {
            c.drawColor((220 shl 24) or 0x050505)
        }

        if (gs.whiteFlash > 0f) {
            val alpha = min(255, (gs.whiteFlash * 255).toInt())
            c.drawColor((alpha shl 24) or 0xFFFFFF)
        }

        if (gs.shakeAmount > 1f || StoryProtocol.isGlitchActive) c.translate(-sdx, -sdy)
    }

    private fun drawGrid(c: Canvas) {
        p.color = if (gs.difficulty == 1) 0xFF330A0A.toInt() else GameColors.GRID
        p.strokeWidth = 2f
        val gap = min(width, height) / 8f
        val parallaxX = gs.cameraX * 0.5f
        val offsetX = -(parallaxX % gap)

        var i = -gap + offsetX
        while (i < width + gap) { c.drawLine(i, 0f, i, height.toFloat(), p); i += gap }
        var j = -gap
        while (j < height + gap) { c.drawLine(0f, j, width.toFloat(), j, p); j += gap }
    }

    private fun drawCRTOverlay(c: Canvas) {
        p.color = 0x22000000
        p.strokeWidth = 2f
        var yLine = (gs.timeSinceStart * 20f) % 8f
        while (yLine < height) {
            c.drawLine(0f, yLine, width.toFloat(), yLine, p)
            yLine += 8f
        }
    }

    private fun drawGamePlay(c: Canvas, dt: Float) {
        val scale = min(width, height).toFloat()
        val currentPlayerColor = if (gs.isOverclocked) GameColors.OVERCLOCK else GameColors.PULSE
        val screenPlayerX = gs.px - gs.cameraX
        val wF = width.toFloat()
        val hF = height.toFloat()

        p.style = Paint.Style.FILL; p.color = 0x1A00FFFF
        c.drawCircle(screenPlayerX, gs.py, scale * 0.12f + sin(gs.timeSinceStart * 3f) * scale * 0.01f, p)

        gs.modeStrategy.drawModeSpecificWorld(c, gs, wF, hF, scale, p)

        if (gs.pulse) {
            val alpha = (255 * (1f - (gs.pulseR / (max(width, height) * 0.75f)))).toInt()
            val colorGlow = if (StoryProtocol.isGlitchActive) GameColors.RED else if (gs.isOverclocked) GameColors.OVERCLOCK else if (gs.visionClarity > 0.3f) GameColors.PULSE else 0xFF006666.toInt()
            // FIXED: Bitwise color conversion
            pGlow.color = (max(0, alpha) shl 24) or (colorGlow and 0xFFFFFF)
            pGlow.strokeWidth = scale * 0.008f
            c.drawCircle(screenPlayerX, gs.py, gs.pulseR, pGlow)
        }

        if (gs.shockwaveActive) {
            val screenShockX = gs.shockwaveX - gs.cameraX
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(2f, scale * 0.05f * (1f - (gs.shockwaveR / (scale * 1.5f))))
            p.color = GameColors.CLARITY
            c.drawCircle(screenShockX, gs.shockwaveY, gs.shockwaveR, p)
            p.style = Paint.Style.FILL
            p.color = 0x22FFFFFF
            c.drawCircle(screenShockX, gs.shockwaveY, gs.shockwaveR, p)
        }

        if (gs.bossDeathTimer > 0f) {
            val screenBx = gs.bossDeathX - gs.cameraX
            val progress = 1f - (gs.bossDeathTimer / 2.0f)
            val alpha = (255 * (1f - progress)).toInt()

            p.style = Paint.Style.FILL
            // FIXED: Bitwise color conversion
            p.color = (alpha shl 24) or (GameColors.BOSS and 0xFFFFFF)

            for(i in 0..15) {
                val ang = (i * 24.0) + (gs.timeSinceStart * 2.0)
                val spd = scale * 0.1f + ((i % 5) * scale * 0.05f)
                val dist = progress * spd
                val px = screenBx + cos(ang).toFloat() * dist
                val py = gs.bossDeathY + sin(ang).toFloat() * dist

                val size = scale * 0.02f * (1f - progress)
                c.drawRect(px - size, py - size, px + size, py + size, p)
            }

            p.style = Paint.Style.STROKE
            p.strokeWidth = scale * 0.01f * (1f - progress)
            c.drawCircle(screenBx, gs.bossDeathY, scale * 0.08f + progress * scale * 0.15f, p)
        }

        if (gs.state == 8 || gs.state == 9) {
            val screenCoreX = gs.coreX - gs.cameraX

            if (screenCoreX > wF - scale * 0.1f && gs.state == 8) {
                val arrowX = wF - scale * 0.06f
                val arrowY = gs.coreY

                val alpha = ((sin(gs.timeSinceStart * 10f) + 1f) / 2f * 155 + 100).toInt()
                // FIXED: Bitwise color
                p.color = (alpha shl 24) or 0xFFFF00
                p.style = Paint.Style.FILL

                // Reset and reuse path instead of new allocation
                arrowPath.reset()
                arrowPath.moveTo(arrowX - scale * 0.04f, arrowY - scale * 0.03f)
                arrowPath.lineTo(arrowX + scale * 0.02f, arrowY)
                arrowPath.lineTo(arrowX - scale * 0.04f, arrowY + scale * 0.03f)
                arrowPath.lineTo(arrowX - scale * 0.02f, arrowY)
                arrowPath.close()
                c.drawPath(arrowPath, p)

                pText.color = (alpha shl 24) or 0xFFFF00
                pText.textSize = scale * 0.035f
                pText.textAlign = Paint.Align.RIGHT

                // Cached String usage for core signal distance
                val dist = ((screenCoreX - wF) / scale * 10).toInt()
                if (dist != lastCoreDist) {
                    lastCoreDist = dist
                    coreDistStr = context.getString(R.string.ui_core_signal, dist)
                }
                c.drawText(coreDistStr, arrowX - scale * 0.05f, arrowY + scale * 0.01f, pText)
            } else {
                p.style = Paint.Style.STROKE
                p.strokeWidth = scale * 0.015f
                p.color = GameColors.YELLOW
                c.drawCircle(screenCoreX, gs.coreY, gs.coreRadius + sin(gs.timeSinceStart * 5f) * scale * 0.03f, p)

                p.style = Paint.Style.FILL
                p.color = GameColors.CLARITY
                c.drawCircle(screenCoreX, gs.coreY, gs.coreRadius * 0.4f, p)

                for(i in 0..7) {
                    val angle = gs.timeSinceStart + (i * Math.PI / 4f)
                    val bx = screenCoreX + cos(angle).toFloat() * gs.coreRadius * 0.8f
                    val by = gs.coreY + sin(angle).toFloat() * gs.coreRadius * 0.8f
                    c.drawCircle(bx, by, scale * 0.01f, p)
                }

                if (gs.state == 8) {
                    val dx = screenCoreX - screenPlayerX
                    val dy = gs.coreY - gs.py
                    val dist = sqrt(dx*dx + dy*dy)

                    // FIXED: Eliminated the heavy for-loop of tiny lines. Using GPU DashPathEffect!
                    if (dist > scale * 0.2f) {
                        pDash.strokeWidth = scale * 0.005f
                        c.drawLine(screenPlayerX, gs.py, screenCoreX, gs.coreY, pDash)
                    }
                }
            }

            if (gs.state == 9) {
                p.color = GameColors.PULSE
                p.strokeWidth = scale * 0.01f
                for(i in 0..4) {
                    val offset = (gs.timeSinceStart * 5f + (i * 0.2f)) % 1f
                    val lx = screenPlayerX + (screenCoreX - screenPlayerX) * offset
                    val ly = gs.py + (gs.coreY - gs.py) * offset
                    c.drawCircle(lx, ly, scale * 0.015f, p)
                }
                // FIXED: Removed state mutations here
            }
        }

        if (gs.state == 1) enemySys.drawEntities(c, gs, wF, scale)

        val pdx = (gs.cameraX + gs.targetPx) - gs.px
        val pdy = gs.targetPy - gs.py
        if (pdx * pdx + pdy * pdy > (scale*dt*scale*dt)) {
            // FIXED: Bitwise color conversion
            pGlow.color = (100 shl 24) or (currentPlayerColor and 0xFFFFFF)
            pGlow.strokeWidth = scale * 0.002f
            c.drawLine(screenPlayerX, gs.py, gs.targetPx, gs.targetPy, pGlow)
        }

        effectSys.drawTrails(c, gs.cameraX, scale, currentPlayerColor)

        if (gs.isOverclocked) {
            effectSys.drawLightning(c, screenPlayerX, gs.py, scale)
        }

        val shouldDrawPlayer = gs.playerIframe <= 0f || ((gs.timeSinceStart * 15).toInt() % 2 == 0)
        if (shouldDrawPlayer) {
            val playerRadius = scale * 0.015f
            p.style = Paint.Style.FILL; p.color = currentPlayerColor
            c.drawCircle(screenPlayerX, gs.py, playerRadius, p)

            p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.003f
            if (gs.shieldTimer > 0f) {
                p.color = GameColors.SHIELD; p.strokeWidth = scale * 0.006f
                c.drawCircle(screenPlayerX, gs.py, playerRadius * 3f + sin(gs.timeSinceStart * 10f) * scale * 0.005f, p)
                p.strokeWidth = scale * 0.003f
            } else p.color = currentPlayerColor
            c.drawCircle(screenPlayerX, gs.py, playerRadius * 2f, p)
        }

        effectSys.drawParticles(c, scale)
        effectSys.drawFloatingTexts(c, scale)

        if (gs.showOverclockTextTimer > 0f) {
            renderOverclockText(c, scale)
        }
    }


    private fun drawHUD(c: Canvas, scale: Float, isOverclocked: Boolean) {
        val topMargin = scale * 0.06f
        val edgeMargin = scale * 0.05f

        val pauseSize = scale * 0.1f
        pauseRect.set(width - edgeMargin - pauseSize, edgeMargin, width - edgeMargin, edgeMargin + pauseSize)
        p.style = Paint.Style.FILL
        p.color = 0x33FFFFFF
        c.drawRoundRect(pauseRect, scale*0.02f, scale*0.02f, p)
        pText.color = GameColors.CLARITY; pText.textSize = scale*0.04f; pText.textAlign = Paint.Align.CENTER
        c.drawText("||", pauseRect.centerX(), pauseRect.centerY() + scale*0.015f, pText)

        pText.textAlign = Paint.Align.LEFT
        if (gs.score != lastScore) {
            scoreStr = context.getString(R.string.ui_data, gs.score)
            lastScore = gs.score
        }
        pText.color = GameColors.PULSE; pText.textSize = scale * 0.055f
        pText.setShadowLayer(10f, 0f, 0f, GameColors.PULSE)
        c.drawText(scoreStr, edgeMargin, topMargin + scale * 0.02f, pText)
        pText.clearShadowLayer()

        // Cached string for banked data
        if (SaveManager.totalData != lastBankedData) {
            lastBankedData = SaveManager.totalData
            bankedStr = context.getString(R.string.ui_banked, lastBankedData)
        }
        pText.color = 0xFFAAAAAA.toInt(); pText.textSize = scale * 0.035f
        c.drawText(bankedStr, edgeMargin, topMargin + scale * 0.07f, pText)

        pText.textAlign = Paint.Align.CENTER
        gs.modeStrategy.drawModeSpecificHUD(context, c, gs, width.toFloat(), height.toFloat(), scale, pText)

        val barW = scale * 0.06f; val barH = scale * 0.015f; val gap = scale * 0.008f
        val metersTopY = edgeMargin + pauseSize + gap * 3
        val metersRightX = width - edgeMargin

        // FIXED: Hoisted sine math out of the loop!
        val isHpFlashing = gs.hp == 1 && sin(gs.timeSinceStart * 15f) > 0

        p.style = Paint.Style.FILL
        for (i in 0 until gs.maxHp) {
            p.color = if (i < gs.hp) (if (isHpFlashing) GameColors.RED else GameColors.HP) else 0xFF333333.toInt()
            val rx = metersRightX - (i + 1) * (barW + gap) + gap
            c.drawRect(rx, metersTopY, rx + barW, metersTopY + barH, p)
        }

        val totalMeterW = (gs.maxHp * (barW + gap)) - gap
        val meterLeftX = metersRightX - totalMeterW

        val cRy = metersTopY + barH + gap
        p.color = 0xFF333333.toInt(); c.drawRect(meterLeftX, cRy, metersRightX, cRy + barH, p)
        p.color = GameColors.CLARITY; c.drawRect(meterLeftX, cRy, meterLeftX + totalMeterW * min(1f, gs.visionClarity), cRy + barH, p)

        val ocRy = cRy + barH + gap
        p.color = 0xFF333333.toInt(); c.drawRect(meterLeftX, ocRy, metersRightX, ocRy + barH * 1.5f, p)

        // FIXED: Hoisted sine math here as well
        val isOcFlashing = isOverclocked && sin(gs.timeSinceStart * 20f) > 0
        p.color = if (isOcFlashing) GameColors.CLARITY else GameColors.OVERCLOCK

        c.drawRect(meterLeftX, ocRy, meterLeftX + totalMeterW * (gs.overclockMeter / 100f), ocRy + barH * 1.5f, p)

        pText.textAlign = Paint.Align.RIGHT; pText.textSize = scale * 0.025f; pText.color = GameColors.TEXT
        c.drawText(getCachedString(R.string.ui_vis), meterLeftX - gap, cRy + barH * 0.9f, pText)
        c.drawText(getCachedString(R.string.ui_ovr), meterLeftX - gap, ocRy + barH * 1.2f, pText)

        if (gs.combo > 1) {
            pText.textAlign = Paint.Align.CENTER
            val bounce = sin(gs.timeSinceStart * 15f) * scale * 0.005f
            pText.textSize = scale * 0.065f + bounce
            val comboColor = when { gs.combo >= 15 -> GameColors.OVERCLOCK; gs.combo >= 8 -> GameColors.YELLOW; else -> GameColors.PULSE }
            pText.color = comboColor; pText.setShadowLayer(25f, 0f, 0f, comboColor)
            if (gs.combo != lastCombo) {
                comboStr = context.getString(R.string.ui_combo, gs.combo)
                lastCombo = gs.combo
            }
            c.drawText(comboStr, width / 2f, height * 0.28f, pText)
            pText.clearShadowLayer()
        }

        if (gs.comboBreakTimer > 0f) {
            pText.textAlign = Paint.Align.CENTER; pText.textSize = scale * 0.05f; pText.color = GameColors.RED
            pText.alpha = (gs.comboBreakTimer * 255).toInt()
            c.drawText(getCachedString(R.string.ui_combo_broken), width / 2f, height * 0.35f, pText)
            pText.alpha = 255
        }

        if (StoryProtocol.popupTimer > 0f) {
            pText.textAlign = Paint.Align.CENTER; pText.textSize = scale * 0.05f; pText.color = GameColors.RED
            pText.alpha = min(255, (StoryProtocol.popupTimer * 255).toInt())
            pText.setShadowLayer(20f, 0f, 0f, GameColors.RED)
            val textX = width / 2f + if(StoryProtocol.isGlitchActive) (Random.nextFloat()-0.5f)*10f else 0f
            c.drawText(getCachedString(StoryProtocol.currentPopupRes), textX, height * 0.42f, pText)
            pText.alpha = 255; pText.clearShadowLayer()
        }
    }

    private fun drawStory(c: Canvas, lines: IntArray) {
        val scale = min(width, height).toFloat()
        if (gs.timeSinceStart > (storyStep + 1) * 1.5f && storyStep < lines.size) {
            storyStep++
            EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 50)
        }
        pText.textAlign = Paint.Align.LEFT; pText.textSize = scale * 0.04f
        pText.color = when (gs.state) {
            4 -> GameColors.RED
            6 -> if (currentStoryLines.contentEquals(StoryProtocol.storyPerfectEnding)) GameColors.YELLOW else GameColors.HP
            else -> GameColors.PULSE
        }
        var y = height * 0.3f
        val maxLinesToDraw = min(storyStep, lines.size)
        for (i in 0 until maxLinesToDraw) {
            // FIXED: Used cached string to prevent GC stutters
            c.drawText(getCachedString(lines[i]), width * 0.1f, y, pText)
            y += scale * 0.08f
        }
        if (storyStep >= lines.size && gs.stateTimer > 1.5f) {
            val alpha = ((sin(gs.timeSinceStart * 5.0) + 1) / 2 * 155 + 100).toInt()
            // FIXED: Bitwise color conversion
            pText.color = (alpha shl 24) or 0xFFFFFF
            pText.textAlign = Paint.Align.CENTER
            c.drawText(getCachedString(R.string.ui_tap_continue), width / 2f, height * 0.85f, pText)
        }
    }

    private fun drawMenu(c: Canvas) {
        val scale = min(width, height).toFloat()
        val pw = scale * 0.08f
        val ph = scale * 0.045f

        // 1. Draw Top Status
        pText.color = if (gs.difficulty == 0) GameColors.TEXT else GameColors.RED
        pText.textAlign = Paint.Align.LEFT; pText.textSize = scale * 0.045f
        pText.setShadowLayer(15f, 0f, 0f, (if (gs.difficulty == 0) GameColors.PULSE else GameColors.RED))
        c.drawText(getCachedString(if(gs.difficulty == 0) R.string.ui_mode_easy else R.string.ui_mode_hard), scale * 0.05f, scale * 0.1f, pText)
        pText.clearShadowLayer()

        // Inside drawMenu, maybe under the "EASY / HARD" mode text:
        pText.color = GameColors.YELLOW
        pText.textAlign = Paint.Align.LEFT
        pText.textSize = scale * 0.035f
        c.drawText("HIGH SCORE: ${SaveManager.highScore}", scale * 0.05f, scale * 0.16f, pText)
        pText.color = 0xFFAAAAAA.toInt() // Gray color
        c.drawText("LAST RUN: ${SaveManager.previousScore}", scale * 0.05f, scale * 0.22f, pText)

        pText.color = GameColors.TEXT; pText.textAlign = Paint.Align.RIGHT
        pText.setShadowLayer(15f, 0f, 0f, GameColors.PULSE)
        c.drawText(getCachedString(R.string.ui_help_btn), width - scale * 0.05f, scale * 0.1f, pText)
        pText.clearShadowLayer()

        // 2. Glitch Title
        pText.textAlign = Paint.Align.CENTER; pText.letterSpacing = 0.05f
        if (StoryProtocol.isGlitchActive) {
            val glitchRoll = Random.nextDouble()
            if (glitchRoll < 0.08) {
                pText.textSize = scale * 0.15f
                val flashOffsetX = (Random.nextFloat() - 0.5f) * scale * 0.03f
                pText.color = GameColors.PULSE
                pText.setShadowLayer(25f, 0f, 0f, GameColors.PULSE)
                c.drawText(getCachedString(R.string.ui_title_echo), width / 2f + flashOffsetX, height * 0.22f, pText)

                pText.color = GameColors.CLARITY
                pText.setShadowLayer(25f, 0f, 0f, GameColors.CLARITY)
                c.drawText(getCachedString(R.string.ui_title_hunter), width / 2f - flashOffsetX, height * 0.32f, pText)
            }
            else {
                pText.textSize = scale * 0.12f
                val jitterX = (Random.nextFloat() - 0.5f) * scale * 0.02f
                val jitterY = (Random.nextFloat() - 0.5f) * scale * 0.02f

                pText.color = GameColors.RED
                pText.setShadowLayer(25f, 0f, 0f, GameColors.RED)
                c.drawText(getCachedString(R.string.ui_title_system), width / 2f + jitterX, height * 0.22f + jitterY, pText)
                c.drawText(getCachedString(R.string.ui_title_corrupted), width / 2f - jitterX, height * 0.32f - jitterY, pText)
            }
        } else {
            pText.textSize = scale * 0.15f
            pText.color = GameColors.PULSE
            pText.setShadowLayer(25f, 0f, 0f, GameColors.PULSE)
            c.drawText(getCachedString(R.string.ui_title_echo), width / 2f, height * 0.22f, pText)

            pText.color = GameColors.RED
            pText.setShadowLayer(25f, 0f, 0f, GameColors.RED)
            c.drawText(getCachedString(R.string.ui_title_hunter), width / 2f, height * 0.32f, pText)
        }
        pText.clearShadowLayer()

        // 3. Drawing Hint Text
        pText.letterSpacing = 0.05f
        pText.textSize = scale * 0.035f
        if (connectedMode == -1) {
            pText.color = GameColors.YELLOW
            c.drawText(getCachedString(R.string.ui_hint_connect), width / 2f, height * 0.44f, pText)
        } else {
            if (!isSwitchOn) {
                pText.color = GameColors.RED
                c.drawText(getCachedString(R.string.ui_hint_toggle), width / 2f, height * 0.44f, pText)
            } else {
                pText.color = GameColors.HP
                c.drawText(getCachedString(R.string.ui_hint_initializing), width / 2f, height * 0.44f, pText)
            }
        }
        pText.letterSpacing = 0f

        // 4. Draw Protocol Ports
        for (i in 0..2) {
            val pinTipX = plugX - pw - scale * 0.04f
            val dxHover = pinTipX - portX[i]
            val dyHover = plugY - portY[i]
            val isHovered = isDraggingPlug && (dxHover * dxHover + dyHover * dyHover) < (scale * 0.15f) * (scale * 0.15f)

            p.style = Paint.Style.STROKE
            p.strokeWidth = if (isHovered) scale * 0.02f else scale * 0.015f
            p.color = if (connectedMode == i) GameColors.PULSE else if (isHovered) GameColors.CLARITY else 0xFF444444.toInt()
            c.drawCircle(portX[i], portY[i], scale * 0.045f, p)

            p.style = Paint.Style.FILL
            p.color = 0xFF0A0A0A.toInt()
            c.drawCircle(portX[i], portY[i], scale * 0.035f, p)

            p.color = if (connectedMode == i && isSwitchOn) GameColors.PULSE else 0xFF000000.toInt()
            c.drawRect(portX[i] - scale*0.02f, portY[i] - scale*0.008f, portX[i] + scale*0.02f, portY[i] + scale*0.008f, p)

            p.color = 0xFF333333.toInt()
            c.drawRect(portX[i] - scale*0.015f, portY[i] - scale*0.004f, portX[i] + scale*0.015f, portY[i] + scale*0.004f, p)

            pText.textAlign = Paint.Align.RIGHT
            pText.textSize = if (connectedMode == i) scale * 0.05f else scale * 0.04f
            pText.color = if (connectedMode == i) GameColors.CLARITY else GameColors.TEXT
            pText.setShadowLayer(if(connectedMode == i) 15f else 0f, 0f, 0f, GameColors.PULSE)
            // FIXED: Cached Strings
            c.drawText(getCachedString(menuTitles[i]), portX[i] - scale * 0.08f, portY[i] + scale * 0.01f, pText)
            pText.clearShadowLayer()

            pText.textSize = scale * 0.025f
            pText.color = if (connectedMode == i) GameColors.PULSE else 0xFF888888.toInt()
            c.drawText(getCachedString(menuSubs[i]), portX[i] - scale * 0.08f, portY[i] + scale * 0.05f, pText)
        }

        // 5. Draw the Physical Cable
        cablePath.reset()
        cablePath.moveTo(width.toFloat(), height * 0.85f)
        if (connectedMode == -1) {
            val cx = (width + plugX) / 2f
            val cy = max(height * 0.85f, plugY) + scale * 0.2f
            cablePath.quadTo(cx, cy, plugX, plugY)
        } else {
            val cx = plugX + scale * 0.1f
            val cy = plugY + ph + scale * 0.2f
            cablePath.quadTo(cx, cy, plugX, plugY + ph - scale * 0.01f)
        }

        p.style = Paint.Style.STROKE
        p.strokeWidth = scale * 0.025f
        p.color = 0xFF181818.toInt()
        c.drawPath(cablePath, p)

        p.strokeWidth = scale * 0.008f
        p.color = if (isSwitchOn) GameColors.PULSE else 0xFF444444.toInt()
        c.drawPath(cablePath, p)

        // 6. Draw The Hardware Plug
        if (connectedMode == -1) {
            p.style = Paint.Style.FILL
            p.color = 0xFF999999.toInt()
            c.drawRect(plugX - pw - scale * 0.04f, plugY - scale * 0.015f, plugX - pw, plugY + scale * 0.015f, p)

            p.color = 0xFF222222.toInt()
            c.drawRoundRect(plugX - pw, plugY - ph, plugX + pw, plugY + ph, scale * 0.015f, scale * 0.015f, p)

            p.color = 0xFF111111.toInt()
            p.strokeWidth = scale * 0.005f
            p.style = Paint.Style.STROKE
            for(i in -1..1) {
                val gx = plugX + (i * scale * 0.02f)
                c.drawLine(gx, plugY - ph + scale*0.01f, gx, plugY + ph - scale*0.01f, p)
            }

            val swSize = scale * 0.025f
            val swX = plugX + pw * 0.4f
            val swColor = if (isSwitchOn) GameColors.HP else GameColors.RED
            val swOffset = if (isSwitchOn) -scale * 0.01f else scale * 0.01f

            p.style = Paint.Style.FILL
            p.color = 0xFF050505.toInt()
            c.drawRoundRect(swX - swSize * 0.8f, plugY - swSize * 1.5f, swX + swSize * 0.8f, plugY + swSize * 1.5f, scale*0.01f, scale*0.01f, p)

            p.color = swColor
            c.drawRoundRect(swX - swSize, plugY - swSize + swOffset, swX + swSize, plugY + swSize + swOffset, scale*0.01f, scale*0.01f, p)

            p.style = Paint.Style.STROKE
            p.color = 0x55FFFFFF
            p.strokeWidth = scale * 0.003f
            c.drawRoundRect(swX - swSize, plugY - swSize + swOffset, swX + swSize, plugY + swSize + swOffset, scale*0.01f, scale*0.01f, p)

        } else {

            p.style = Paint.Style.FILL
            p.color = 0xFF1B1B1B.toInt()
            c.drawRoundRect(
                plugX - pw,
                plugY - ph, plugX + pw, plugY + ph, scale * 0.015f, scale * 0.015f, p)

            p.color = 0xFF222222.toInt()
            c.drawRoundRect(plugX - pw * 0.8f, plugY - ph * 0.8f, plugX + pw * 0.8f, plugY + ph * 0.8f, scale * 0.01f, scale * 0.01f, p)

            val swSize = scale * 0.025f
            val swColor = if (isSwitchOn) GameColors.HP else GameColors.RED
            val swOffset = if (isSwitchOn) -scale * 0.01f else scale * 0.01f

            p.color = 0xFF050505.toInt()
            c.drawRoundRect(plugX - swSize * 1.5f, plugY - swSize * 0.8f, plugX + swSize * 1.5f, plugY + swSize * 0.8f, scale*0.01f, scale*0.01f, p)

            p.color = swColor
            c.drawRoundRect(plugX - swSize + swOffset, plugY - swSize, plugX + swSize + swOffset, plugY + swSize, scale*0.01f, scale*0.01f, p)

            p.style = Paint.Style.STROKE
            p.color = 0x55FFFFFF
            p.strokeWidth = scale * 0.003f
            c.drawRoundRect(plugX - swSize + swOffset, plugY - swSize, plugX + swSize + swOffset, plugY + swSize, scale*0.01f, scale*0.01f, p)
        }

        effectSys.drawParticles(c, scale)
    }

    private fun drawHelp(c: Canvas) {
        val scale = min(width, height).toFloat()
        pText.textAlign = Paint.Align.CENTER; pText.textSize = scale * 0.08f; pText.color = GameColors.PULSE
        pText.setShadowLayer(20f, 0f, 0f, GameColors.PULSE)
        c.drawText(getCachedString(R.string.help_title), width / 2f, height * 0.12f, pText)
        pText.clearShadowLayer()

        pText.color = GameColors.TEXT; pText.textSize = scale * 0.035f
        val lh = scale * 0.055f; var sy = height * 0.20f

        c.drawText(getCachedString(R.string.help_how_to_survive), width / 2f, sy, pText); sy += lh
        c.drawText(getCachedString(R.string.help_controls), width / 2f, sy, pText); sy += lh
        c.drawText(getCachedString(R.string.help_vision_blur), width / 2f, sy, pText); sy += lh
        sy += lh

        pText.color = GameColors.YELLOW; c.drawText(getCachedString(R.string.help_item_yellow), width / 2f, sy, pText); sy += lh
        pText.color = GameColors.COOLANT; c.drawText(getCachedString(R.string.help_item_blue), width / 2f, sy, pText); sy += lh
        pText.color = GameColors.SHIELD; c.drawText(getCachedString(R.string.help_item_purple), width / 2f, sy, pText); sy += lh
        pText.color = GameColors.RED; c.drawText(getCachedString(R.string.help_item_red), width / 2f, sy, pText); sy += lh
        sy += lh

        pText.color = GameColors.OVERCLOCK; c.drawText(getCachedString(R.string.help_overclock_title), width / 2f, sy, pText); sy += lh
        pText.color = GameColors.TEXT
        c.drawText(getCachedString(R.string.help_overclock_desc), width / 2f, sy, pText); sy += lh
        c.drawText(getCachedString(R.string.help_overclock_combat), width / 2f, sy, pText); sy += lh

        pText.color = GameColors.RED
        c.drawText(getCachedString(R.string.help_hard_mode_desc), width / 2f, sy, pText)

        // REPAIR BUTTON when glitch is active
        val ry = height * 0.80f
        pText.textSize = scale * 0.045f

        if (repairFadeTimer > 0f) {
            // 1. Draw fading "SYSTEM REPAIRED"
            val fadeAlpha = max(0, min(255, (repairFadeTimer * 127).toInt())) // Fades over 2 seconds
            pText.color = (fadeAlpha shl 24) or (GameColors.CLARITY and 0xFFFFFF)
            pText.setShadowLayer(15f, 0f, 0f, GameColors.CLARITY)
            c.drawText(getCachedString(R.string.ui_system_repaired), width / 2f, ry, pText)
            pText.clearShadowLayer()
        } else if (StoryProtocol.isGlitchActive) {
            val btnW = scale * 0.5f
            val btnH = scale * 0.12f
            val rx = width / 2f
            repairBtnRect.set(rx - btnW/2f, ry - btnH/2f, rx + btnW/2f, ry + btnH/2f)

            // 1. Draw Subtle Pulsing Background
            val pulse = (sin(gs.timeSinceStart * 3f) * 20 + 40).toInt()
            p.style = Paint.Style.FILL
            p.color = (pulse shl 24) or (GameColors.HP and 0xFFFFFF)
            c.drawRoundRect(repairBtnRect, scale * 0.01f, scale * 0.01f, p)

            // 2. Draw Outer Frame & Corner Brackets
            p.style = Paint.Style.STROKE
            p.strokeWidth = scale * 0.005f
            p.color = GameColors.HP
            c.drawRoundRect(repairBtnRect, scale * 0.01f, scale * 0.01f, p)

            // Draw "Tech" Corners (thickened bracket ends)
            val cs = scale * 0.03f // corner size
            p.strokeWidth = scale * 0.01f
            // Top Left
            c.drawLine(repairBtnRect.left, repairBtnRect.top, repairBtnRect.left + cs, repairBtnRect.top, p)
            c.drawLine(repairBtnRect.left, repairBtnRect.top, repairBtnRect.left, repairBtnRect.top + cs, p)
            // Bottom Right
            c.drawLine(repairBtnRect.right, repairBtnRect.bottom, repairBtnRect.right - cs, repairBtnRect.bottom, p)
            c.drawLine(repairBtnRect.right, repairBtnRect.bottom, repairBtnRect.right, repairBtnRect.bottom - cs, p)

            // 3. Animated Scanning Line
            val scanPos = (gs.timeSinceStart * 0.5f % 1.0f)
            val scanY = repairBtnRect.top + (repairBtnRect.height() * scanPos)
            p.strokeWidth = scale * 0.002f
            p.alpha = 150
            c.drawLine(repairBtnRect.left, scanY, repairBtnRect.right, scanY, p)
            p.alpha = 255

            // 4. Button Text
            pText.color = GameColors.HP
            pText.textSize = scale * 0.045f
            pText.setShadowLayer(15f, 0f, 0f, GameColors.HP)
            c.drawText(getCachedString(R.string.ui_repair_system), rx, ry + scale * 0.015f, pText)
            pText.clearShadowLayer()

            // Sub-text for "Authentication Required" feel
            pText.textSize = scale * 0.018f
            pText.alpha = 180
            c.drawText(getCachedString(R.string.ui_repair_protocol), rx, repairBtnRect.bottom + scale * 0.03f, pText)
            pText.alpha = 255
        }


        val alpha = ((sin(gs.timeSinceStart * 5.0) + 1) / 2 * 155 + 100).toInt()
        pText.color = (alpha shl 24) or 0xFFFFFF; pText.setShadowLayer(10f, 0f, 0f, GameColors.CLARITY)
        c.drawText(getCachedString(R.string.help_tap_return), width / 2f, height * 0.90f, pText)
        pText.clearShadowLayer()
    }

    private fun drawPause(c: Canvas) {
        val scale = min(width, height).toFloat()
        pText.textAlign = Paint.Align.CENTER; pText.textSize = scale * 0.1f; pText.color = GameColors.TEXT
        pText.setShadowLayer(20f, 0f, 0f, GameColors.PULSE)
        c.drawText(getCachedString(R.string.pause_title), width / 2f, height * 0.4f, pText)
        pText.clearShadowLayer()

        pText.textSize = scale * 0.05f
        val alpha = ((sin(gs.timeSinceStart * 5.0) + 1) / 2 * 155 + 100).toInt()
        pText.color = (alpha shl 24) or 0x00FFFF; pText.setShadowLayer(15f, 0f, 0f, GameColors.PULSE)
        c.drawText(getCachedString(R.string.pause_resume), width / 2f, height * 0.6f, pText)
        pText.clearShadowLayer()

        pText.color = GameColors.RED; pText.alpha = 255; pText.setShadowLayer(15f, 0f, 0f, GameColors.RED)
        c.drawText(getCachedString(R.string.pause_menu), width / 2f, height * 0.75f, pText)
        pText.clearShadowLayer()
    }

    private fun triggerPulseAction() {
        val scale = min(width, height).toFloat()
        if (gs.cooldownTimer <= 0f) {
            gs.pulse = true; gs.pulseR = 0f; gs.cooldownTimer = 0.25f
            gs.visionClarity = max(0.0f, gs.visionClarity - 0.25f)

            val pts = 1 + gs.combo
            addScore(pts)
            gs.combo++
            if (gs.combo > gs.maxCombo) gs.maxCombo = gs.combo

            val floatingColor = when {
                gs.combo >= 15 -> GameColors.OVERCLOCK
                gs.combo >= 8 -> GameColors.YELLOW
                else -> GameColors.PULSE
            }
            effectSys.spawnFloatingText(gs.px - gs.cameraX, gs.py - (scale * 0.05f), pts, floatingColor)

            if (gs.combo % 10 == 0) EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 150)
            else EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 50)

            if (gs.overclockTimer <= 0f) {
                val multiplier = 1f + (gs.combo * 0.1f)
                gs.overclockMeter = min(100f, gs.overclockMeter + (pts * 1.5f * multiplier))
                if (gs.overclockMeter >= 100f) {
                    gs.overclockTimer = 5f
                    EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_ALERT, 200)
                    gs.shakeAmount = scale * 0.08f; gs.sectorFlash = 0.5f; gs.showOverclockTextTimer = 2.0f
                }
            }
        } else {
            if(gs.combo > 5) {
                gs.comboBreakTimer = 1.0f
                EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 100)
            }
            gs.combo = 0
        }
    }

    private fun renderOverclockText(c: Canvas, scale: Float) {
        pText.color = GameColors.OVERCLOCK; pText.textSize = scale * 0.06f; pText.textAlign = Paint.Align.CENTER
        pText.alpha = (min(1f, gs.showOverclockTextTimer) * 255).toInt()
        pText.setShadowLayer(10f, 0f, 0f, GameColors.RED)
        c.drawText(getCachedString(R.string.ui_overclock_ready), width / 2f, height * 0.45f, pText)
        pText.alpha = 255; pText.clearShadowLayer()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val scale = min(width, height).toFloat()
        val action = e.actionMasked

        when (action) {
            MotionEvent.ACTION_DOWN -> gs.isTouching = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> gs.isTouching = false
        }

        when (gs.state) {
            // --- Story / Endings ---
            5, 7, 4, 6 -> if (action == MotionEvent.ACTION_UP) {
                if (gs.stateTimer < 1.5f) return true
                if (e.y < height / 2f) return true
                if (storyStep < currentStoryLines.size) storyStep = currentStoryLines.size
                else {
                    if (gs.state == 7) changeState(gs.nextStateAfterStory)
                    else {
                        // Resting the menu plug before showing it
                        connectedMode = -1
                        plugX = plugRestX
                        plugY = plugRestY
                        isSwitchOn = false
                        changeState(0)
                    }
                }
            }

            // --- Main Menu ---
            0 -> {
                val pw = scale * 0.08f
                val ph = scale * 0.045f

                val hitSwitch: Boolean
                val hitPlug: Boolean

                if (connectedMode == -1) {
                    val swSize = scale * 0.025f
                    val swX = plugX + pw * 0.4f
                    hitSwitch = e.x in (swX - swSize * 2.5f)..(swX + swSize * 2.5f) && e.y in (plugY - ph * 1.5f)..(plugY + ph * 1.5f)
                    hitPlug = e.x in (plugX - pw - scale*0.06f)..(plugX + pw + scale*0.06f) && e.y in (plugY - ph - scale*0.06f)..(plugY + ph + scale*0.06f)
                } else {
                    val swSize = scale * 0.025f
                    hitSwitch = e.x in (plugX - swSize * 2.5f)..(plugX + swSize * 2.5f) && e.y in (plugY - swSize * 2.5f)..(plugY + swSize * 2.5f)
                    hitPlug = e.x in (plugX - pw - scale*0.06f)..(plugX + pw + scale*0.06f) && e.y in (plugY - ph - scale*0.06f)..(plugY + ph + scale*0.06f)
                }

                when (action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Register the touch, but don't take action yet
                        if (hitSwitch || hitPlug) {
                            isDraggingPlug = true
                            touchDownX = e.x
                            touchDownY = e.y
                            wasSwitchHitOnDown = hitSwitch
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isDraggingPlug) {
                            val dx = e.x - touchDownX
                            val dy = e.y - touchDownY

                            // If it's plugged in, wait until the user drags past a "slop" threshold to unplug it
                            if (connectedMode != -1 && (dx * dx + dy * dy > (scale * 0.05f) * (scale * 0.05f))) {
                                connectedMode = -1
                                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 50)
                                wasSwitchHitOnDown = false // Cancel the tap since we are now dragging
                            }

                            // Only move the plug if it's currently unplugged
                            if (connectedMode == -1) {
                                plugX = e.x
                                plugY = e.y
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        // Check Top UI Buttons on UP (Prevents touch-through to the Help screen)
                        if (e.x < scale * 0.35f && e.y < scale * 0.15f) {
                            gs.difficulty = 1 - gs.difficulty
                            EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 50)
                            return true
                        }
                        if (e.x > width * 0.7f && e.y < height * 0.2f) {
                            changeState(3)
                            return true
                        }

                        if (isDraggingPlug) {
                            isDraggingPlug = false

                            val dx = e.x - touchDownX
                            val dy = e.y - touchDownY
                            val isTap = (dx * dx + dy * dy) < (scale * 0.05f) * (scale * 0.05f)

                            // 1. Handle Switch Tap
                            if (isTap && wasSwitchHitOnDown) {
                                isSwitchOn = !isSwitchOn
                                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 50)

                                if (isSwitchOn && connectedMode != -1) {
                                    postDelayed({
                                        // Double-check they didn't toggle it off or unplug it during the 0.25s wait!
                                        if (isSwitchOn && connectedMode != -1 && gs.state == 0) {
                                            startGame(connectedMode)
                                        }
                                    }, 250) // 250 milliseconds = 0.25 seconds
                                }
                                return true
                            }

                            // 2. Handle Snapping (if we were dragging it)
                            if (connectedMode == -1) {
                                var snapped = false

                                for (i in 0..2) {
                                    val pinTipX = plugX - pw - scale * 0.04f
                                    val pdx = pinTipX - portX[i]
                                    val pdy = plugY - portY[i]

                                    if (pdx * pdx + pdy * pdy < (scale * 0.15f) * (scale * 0.15f)) {
                                        connectedMode = i
                                        plugX = portX[i]
                                        plugY = portY[i]
                                        snapped = true

                                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 100)
                                        effectSys.spawnParticles(portX[i], portY[i], 8, scale)

                                        if (isSwitchOn) {
                                            postDelayed({
                                                // Double-check they didn't toggle it off or unplug it during the 0.25s wait!
                                                if (isSwitchOn && connectedMode != -1 && gs.state == 0) {
                                                    startGame(i) // i is the port they just snapped into
                                                }
                                            }, 250)
                                        }
                                        break
                                    }
                                }

                                // Snap back to rest position if missed
                                if (!snapped) {
                                    plugX = plugRestX
                                    plugY = plugRestY
                                    EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 50)
                                }
                            }
                        }
                    }
                }
                return true
            }

            // --- Help Menu ---
            3 -> {
                if (action == MotionEvent.ACTION_UP && gs.stateTimer > 0.2f) {
                    // Check for Repair Button Tap
                    if (StoryProtocol.isGlitchActive && repairFadeTimer <= 0f) {
                        if (repairBtnRect.contains(e.x, e.y)) {
                            // --- THE REPAIR ACTION ---
                            StoryProtocol.isGlitchActive = false
                            StoryProtocol.popupTimer = 0f
                            gs.chromaticIntensity = 0f
                            gs.shakeAmount = 0f
                            gs.damageFlash = 0f
                            effectSys.reset()
                            repairFadeTimer = 2.0f

                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 150)

                            // Spawn some "healing" particles around the button
                            effectSys.spawnParticles(repairBtnRect.centerX(), repairBtnRect.centerY(), 15, scale)

                            return true
                        }
                    }

                    // Return to menu if tapped anywhere else
                    changeState(0)
                }
            }

            // --- Pause Menu ---
            2 -> {
                val edgeMargin = scale * 0.05f
                val pauseSize = scale * 0.1f
                val isClickingPause = e.x > width - edgeMargin - pauseSize && e.y < edgeMargin + pauseSize

                if (action == MotionEvent.ACTION_UP && gs.stateTimer > 0.2f) {
                    if (e.y > height * 0.68f) {
                        connectedMode = -1
                        plugX = plugRestX
                        plugY = plugRestY
                        isSwitchOn = false
                        changeState(0)
                    } else if (isClickingPause || (e.y > height * 0.5f && e.y < height * 0.68f)) {
                        changeState(if (gs.coreX > 0f && gs.bossHp <= 0 && gs.currentSector > 5) 8 else 1)
                        lastFrameTime = System.nanoTime()
                    }
                }
            }

            // --- Core Merge (No interactions allowed) ---
            9 -> return true

            // --- Gameplay ---
            1, 8 -> {
                val edgeMargin = scale * 0.05f
                val pauseSize = scale * 0.1f
                val isClickingPause = e.x > width - edgeMargin - pauseSize && e.y < edgeMargin + pauseSize

                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                    if (!isClickingPause) {
                        if (StoryProtocol.areControlsInverted) {
                            gs.targetPx = width - e.x
                            gs.targetPy = height - e.y
                        } else {
                            gs.targetPx = e.x
                            gs.targetPy = e.y
                        }

                        val playerRadius = scale * 0.015f

                        if (gs.targetPy < playerRadius) gs.targetPy = playerRadius
                        if (gs.targetPy > height - playerRadius) gs.targetPy = height - playerRadius

                        if (gs.targetPx < playerRadius) gs.targetPx = playerRadius
                        if (gs.targetPx > width - playerRadius) gs.targetPx = width - playerRadius
                    }
                }
                if (action == MotionEvent.ACTION_DOWN) {
                    if (!isClickingPause) triggerPulseAction()
                }
                if (action == MotionEvent.ACTION_UP && isClickingPause) {
                    pauseGame()
                    return true
                }
            }
        }
        return true
    }
}