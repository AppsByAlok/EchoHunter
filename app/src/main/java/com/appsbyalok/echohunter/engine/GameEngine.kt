package com.appsbyalok.echohunter.engine

import android.content.Context
import android.media.ToneGenerator
import com.appsbyalok.echohunter.data.LevelEngine
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.data.UpgradeSystem
import com.appsbyalok.echohunter.systems.ArsenalSystem
import com.appsbyalok.echohunter.systems.CollisionSystem
import com.appsbyalok.echohunter.systems.EffectSystem
import com.appsbyalok.echohunter.systems.EnemySystem
import com.appsbyalok.echohunter.systems.PlayerAI
import com.appsbyalok.echohunter.systems.SpawnerSystem
import com.appsbyalok.echohunter.utils.EchoAudioManager

class GameEngine(
    private val gs: GameState,
    private val effectSys: EffectSystem,
    private val enemySys: EnemySystem,
    private val collisionSys: CollisionSystem,
    private val context: Context
) {

    var onChangeState: ((Int) -> Unit)? = null
    var onDamage: ((Float) -> Unit)? = null
    var onScore: ((Long) -> Unit)? = null
    var onCoreUnlock: ((Boolean) -> Unit)? = null
    var onBossTrigger: ((Int, Float) -> Unit)? = null
    var onStoryState: ((IntArray, Int) -> Unit)? = null

    private val arsenalSys = ArsenalSystem(gs, effectSys)
    private val playerAI = PlayerAI(gs, enemySys)
    private val spawnerSys = SpawnerSystem(enemySys, effectSys)
    private val inputSys = com.appsbyalok.echohunter.systems.InputSystem(gs)

    fun update(dt: Float, targetW: Float, targetH: Float, scale: Float) {
        gs.lastDt = dt
        gs.timeSinceStart += dt
        gs.stateTimer += dt
        StoryProtocol.update(dt)

        if (gs.state == 0) {
            effectSys.update(dt, targetH)
            return
        }

        if (gs.state == 3) return
        if (gs.state != 1 && gs.state != 8 && gs.state != 9) return

        if (gs.hitStopTimer > 0f) {
            gs.hitStopTimer -= dt
            return
        }

        gs.updateTimers(dt, scale)
        val simDt = if (gs.slowMoTimer > 0f) dt * 0.15f else dt

        if (gs.modInfiniteOvr) {
            gs.overclockMeter = 100f
            if (gs.isOverclocked) gs.overclockTimer = 5f
        }

        if (gs.state == 1 || gs.state == 8) {
            inputSys.update(simDt, scale, enemySys)
            if (gs.isAutoPilotActive) playerAI.update(simDt, scale)
            arsenalSys.update(simDt, scale)

            if (gs.controls.isTrapPressed && gs.trapCooldownTimer <= 0f) arsenalSys.deployTrap()
            if (gs.controls.isOverclockPressed && gs.overclockMeter >= 100f && !gs.isOverclocked) activateOverclock(scale)

            for (i in 0 until gs.maxSpikes) {
                if (gs.spikeActive[i]) {
                    gs.spikeX[i] += gs.spikeVx[i] * simDt
                    gs.spikeY[i] += gs.spikeVy[i] * simDt
                    gs.spikeLife[i] -= simDt
                    if (gs.spikeLife[i] <= 0f) gs.spikeActive[i] = false
                }
            }

            gs.updatePlayerMovement(simDt, targetW, targetH, scale)

            // --- FIX: Camera update should always follow ModeStrategy ---
            gs.updateCameraAndMovement(simDt, targetW, targetH ,scale)

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
            gs.cameraX += (gs.coreX - targetW / 2f - gs.cameraX) * 2f * dt
            gs.cameraY += (gs.coreY - targetH / 2f - gs.cameraY) * 2f * dt
            gs.px += (gs.coreX - gs.px) * 2.5f * dt
            gs.py += (gs.coreY - gs.py) * 2.5f * dt
            gs.mergeTimer += dt
            gs.shakeAmount = scale * 0.04f

            if (gs.mergeTimer > 2.5f) gs.whiteFlash += dt * 0.5f
            if (gs.whiteFlash >= 1f) {
                onStoryState?.invoke(if (gs.isPerfectEnd) StoryProtocol.storyPerfectEnding else StoryProtocol.storyNeutralEnding, 6)
                gs.whiteFlash = 0f
            }
        }

        val maxSonarRad = targetW * 0.75f * UpgradeSystem.getSonarRangeMultiplier()
        gs.updateVisibilityMath(scale, maxSonarRad, simDt)
        gs.updatePulseRadius(simDt, maxSonarRad)


        if (gs.isLevelCleared && gs.state == 1 && gs.gameMode == 0) {
            gs.isLevelCleared = false
            val config = LevelEngine.getLevelConfig(gs.currentLevel)
            var finalReward = config.clearRewardKB
            if (gs.currentLevel < SaveManager.maxCampaignLevel) finalReward /= 2
            finalReward += (finalReward * UpgradeSystem.getRewardBonusPercent()).toLong()

            gs.collectedDataKB += finalReward
            SaveManager.addData(finalReward)
            SaveManager.updateCampaignProgress(gs.currentLevel)

            EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 500)
            onChangeState?.invoke(12)
            return
        }

        effectSys.recordTrail(gs.px, gs.py)
        effectSys.update(simDt, scale)

        if (gs.state == 1 || gs.state == 8) {
            // --- NAYA: MODULAR OBJECTIVE CALL ---
            // Updates timers and dynamic logic (like Core activation in Story Mode)
            gs.activeObjective.updateObjective(simDt, gs, enemySys, spawnerSys, targetW, targetH, scale)

            // --- CRITICAL: WIN CONDITION CHECK ---
            // Modular objectives determine if the level is cleared (Campaign Mode)
            if (gs.gameMode == 0 && !gs.isLevelCleared && gs.activeObjective.checkWinCondition(gs)) {
                gs.isLevelCleared = true
            }

            spawnerSys.update(simDt, gs, targetW, targetH, scale)
            enemySys.updateEnemies(simDt, gs, targetW, targetH, scale)
            enemySys.updateBoss(simDt, gs, scale)
            enemySys.updatePowerups(simDt, gs, targetW, targetH)

            collisionSys.checkCollisions(targetW, targetH, scale, onDamage!!, onScore!!, onCoreUnlock!!)
            
            // Boss Spawns & Sector Story triggers only in main gameplay
            if (gs.state == 1) gs.modeStrategy.checkProgression(context, gs, scale, onBossTrigger!!, onStoryState!!)

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

    fun generateLevelMaze(targetW: Float, targetH: Float, scale: Float) {
        val seed = 1000 + gs.currentLevel
        gs.gridMap = com.appsbyalok.echohunter.data.MazeGenerator.generateLevelMap(gs.currentLevel, gs.gameMode, gs.difficulty, seed, gs.selectedStoryAct)

        val columns = gs.gridMap!!.size
        val rows = gs.gridMap!![0].size
        
        // NAYA: Initialize wall visibility map
        gs.wallVisMap = Array(columns) { FloatArray(rows) { 0f } }

        gs.tileSize = scale * 0.15f
        gs.mapWidth = columns * gs.tileSize
        gs.mapHeight = rows * gs.tileSize

        var pCol = 2
        var pRow = 2

        for (x in 0 until columns) {
            for (y in 0 until rows) {
                if (gs.gridMap!![x][y] == com.appsbyalok.echohunter.data.MazeGenerator.PLAYER_SPAWN) {
                    pCol = x; pRow = y
                    gs.gridMap!![x][y] = com.appsbyalok.echohunter.data.MazeGenerator.PATH
                }
            }
        }

        gs.px = pCol * gs.tileSize + (gs.tileSize / 2f)
        gs.py = pRow * gs.tileSize + (gs.tileSize / 2f)

        // --- OBJECTIVE SETUP ---
        // Note: activeObjective is assigned in GameState.resetGame() based on LevelFeature.
        // We just need to call its setup here after map generation.
        gs.activeObjective.setupObjective(gs, targetW, targetH, scale)

        // NAYA: Generate Spawner Nodes for the level
        spawnerSys.generateNodes(gs, gs.mapWidth, gs.mapHeight, scale)

        // NAYA: Physically spawn enemies immediately so the map isn't empty
        val preSpawnCount = if (gs.difficulty == 1) 10 else 7
        for (i in 0 until preSpawnCount) {
            val node = gs.spawnerNodes.random()
            for (j in 0 until enemySys.n) {
                if (enemySys.ex[j] < -1000f) {
                    enemySys.spawnAt(j, node.x, node.y, gs, targetW, targetH, node.type)
                    break
                }
            }
        }

        // Initialize progression flags
        gs.controls.isManualAimUnlocked = SaveManager.isManualAimUnlocked

        // NAYA: Start level with a few more enemies queued to emerge shortly
        val initialPop = if (gs.difficulty == 1) 8 else 5
        spawnerSys.queueSpawns(initialPop, gs)

        gs.cameraX = gs.px - targetW / 2f
        gs.cameraY = gs.py - targetH / 2f
    }

    private fun activateOverclock(scale: Float) {
        gs.overclockTimer = 5f + UpgradeSystem.getBonusOverclockTime()
        gs.overclockMeter = 0f
        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_ALERT, 200)
        gs.shakeAmount = scale * 0.08f
        gs.sectorFlash = 0.5f
        gs.showOverclockTextTimer = 2.0f
        gs.controls.isOverclockPressed = false
    }
}