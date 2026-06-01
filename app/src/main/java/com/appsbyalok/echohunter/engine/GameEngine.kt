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
    var onScore: ((Int) -> Unit)? = null
    var onCoreUnlock: ((Boolean) -> Unit)? = null
    var onBossTrigger: ((Int, Float) -> Unit)? = null
    var onStoryState: ((IntArray, Int) -> Unit)? = null

    // NAYA: Arsenal System instance
    private val arsenalSys = ArsenalSystem(gs, effectSys)
    private val playerAI = PlayerAI(gs, enemySys)

    fun update(dt: Float, targetW: Float, targetH: Float, scale: Float) {
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

        // MOD: Infinite Overclock
        if (gs.modInfiniteOvr) {
            gs.overclockMeter = 100f
            if (gs.isOverclocked) gs.overclockTimer = 5f // Hamesha active rakhega
        }

        if (gs.state == 1 || gs.state == 8) {
            if (gs.isAutoPilotActive) playerAI.update(simDt, scale)
            arsenalSys.update(simDt, scale)

            if (gs.isTrapPressed && gs.trapCooldownTimer <= 0f) arsenalSys.deployTrap()
            if (gs.isOverclockPressed && gs.overclockMeter >= 100f && !gs.isOverclocked) activateOverclock(scale)

            for (i in 0 until gs.maxSpikes) {
                if (gs.spikeActive[i]) {
                    gs.spikeX[i] += gs.spikeVx[i] * simDt
                    gs.spikeY[i] += gs.spikeVy[i] * simDt
                    gs.spikeLife[i] -= simDt
                    if (gs.spikeLife[i] <= 0f) gs.spikeActive[i] = false
                }
            }

            gs.updatePlayerMovement(simDt, targetW, targetH, scale)

            if (gs.gridMap == null) {
                gs.updateCameraAndMovement(simDt, targetW, scale)
            }

            // --- NAYA: ESCAPE GATE COLLISION ---
            if (gs.state == 1 && gs.escapeGateActive) {
                val cdx = gs.px - gs.coreX
                val cdy = gs.py - gs.coreY
                // Agar player portal ke collision radius me aa gaya
                if (cdx * cdx + cdy * cdy < gs.coreRadius * gs.coreRadius) {
                    gs.isLevelCleared = true
                    gs.escapeGateActive = false // Reset for next loop
                }
            }

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

            if (gs.mergeTimer > 2.5f) {
                gs.whiteFlash += dt * 0.5f
            }
            if (gs.whiteFlash >= 1f) {
                onStoryState?.invoke(if (gs.isPerfectEnd) StoryProtocol.storyPerfectEnding else StoryProtocol.storyNeutralEnding, 6)
                gs.whiteFlash = 0f
            }
        }

        gs.updateVisibilityMath(scale, targetW * 0.75f)
        gs.updatePulseRadius(simDt, targetW * 0.75f)

        if (gs.isLevelCleared && gs.state == 1) {
            gs.isLevelCleared = false

            val config = LevelEngine.getLevelConfig(gs.currentLevel)
            var finalReward = config.clearRewardKB

            if (gs.currentLevel < SaveManager.maxCampaignLevel) {
                finalReward /= 2
            }

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

        if (gs.state == 1) {
            enemySys.updateEnemies(simDt, gs, targetW, targetH, scale)
            enemySys.updateBoss(simDt, gs, scale)
            enemySys.updatePowerups(simDt, gs, targetW, targetH)

            collisionSys.checkCollisions(targetW, targetH, scale, onDamage!!, onScore!!, onCoreUnlock!!)
            gs.modeStrategy.checkProgression(context, gs, scale, onBossTrigger!!, onStoryState!!)

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

        gs.gridMap = com.appsbyalok.echohunter.data.MazeGenerator.generateLevelMap(
            gs.currentLevel, gs.gameMode, gs.difficulty, seed, gs.selectedStoryAct
        )

        val columns = gs.gridMap!!.size
        val rows = gs.gridMap!![0].size

        gs.tileSize = scale * 0.15f
        gs.mapWidth = columns * gs.tileSize
        gs.mapHeight = rows * gs.tileSize

        var pCol = 2; var pRow = 2
        var dCol = columns - 3; var dRow = rows - 3

        for (x in 0 until columns) {
            for (y in 0 until rows) {
                if (gs.gridMap!![x][y] == com.appsbyalok.echohunter.data.MazeGenerator.PLAYER_SPAWN) {
                    pCol = x
                    pRow = y
                    gs.gridMap!![x][y] = com.appsbyalok.echohunter.data.MazeGenerator.PATH
                } else if (gs.gridMap!![x][y] == com.appsbyalok.echohunter.data.MazeGenerator.DEST_NODE) {
                    dCol = x
                    dRow = y
                }
            }
        }

        gs.px = pCol * gs.tileSize + (gs.tileSize / 2f)
        gs.py = pRow * gs.tileSize + (gs.tileSize / 2f)

        // --- NAYA: CORE SIRF TABHI SPAWN HOGA JAB ZAROORAT HO --- TODO ise theek karna hai. avi v story mode me core suru me nahi aana chaahiye magar aa raha hai. story mode core defecce ban jaa raha hai.
        val config = LevelEngine.getLevelConfig(gs.currentLevel)
        if (config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.DEFENSE) ||
            config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.ESCAPE)) {
            gs.coreX = dCol * gs.tileSize + (gs.tileSize / 2f)
            gs.coreY = dRow * gs.tileSize + (gs.tileSize / 2f)
            gs.coreRadius = scale * 0.08f

            // --- NAYA: DEFENSE MODE SETUP ---
            if (config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.DEFENSE)) {
                gs.defenseTimer = LevelEngine.getDefenseTimer(gs.currentLevel)
                gs.maxDefenseTimer = gs.defenseTimer
                // HP gently scale hogi, par 25 se upar nahi jayegi
                gs.coreMaxHp = kotlin.math.min(25, 10 + (gs.currentLevel / 15))
                gs.coreHp = gs.coreMaxHp
            }
        } else {
            gs.coreX = -9999f
            gs.coreY = -9999f
            gs.coreRadius = 0f
        }

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
        gs.isOverclockPressed = false
    }
}