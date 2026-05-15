package com.appsbyalok.echohunter.systems

import android.media.ToneGenerator
import com.appsbyalok.echohunter.data.LevelEngine
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.data.UpgradeSystem
import com.appsbyalok.echohunter.utils.GameColors
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.EchoAudioManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

class CollisionSystem(
    val gs: GameState,
    val effectSystem: EffectSystem,
    val enemySystem: EnemySystem,
) {

    fun checkCollisions(
        width: Float,
        height: Float,
        scale: Float,
        onDamage: (Float) -> Unit,
        onScoreAdd: (Int) -> Unit,
        onCoreUnlock: (Boolean) -> Unit,
    ) {
        val screenPlayerX = gs.px - gs.cameraX
        val hitRadius = scale * 0.045f
        val hitDistSq = hitRadius * hitRadius

        var playedEnemyKillSound = false
        var playedEnemyHackSound = false

        val safeLeftBoundary = gs.cameraX + if (gs.gameMode == 2) gs.firewallOffset else 0f

        // 1. Firewall Logic (For Game Mode 2)
        if (gs.gameMode == 2) {
            if (screenPlayerX < gs.firewallOffset) {
                gs.px += width * 0.2f
                onDamage(scale)
                gs.chromaticIntensity = 1.0f
            }

            for (i in 0 until gs.obsCount) {
                val screenObsX = gs.obsX[i] - gs.cameraX
                val obsW = scale * 0.05f
                val isDanger = gs.obsType[i] == 1

                if (screenPlayerX + scale * 0.02f > screenObsX && screenPlayerX - scale * 0.02f < screenObsX + obsW) {
                    if (gs.py < gs.obsGapY[i] || gs.py > gs.obsGapY[i] + gs.obsGapSize[i]) {
                        if (isDanger) {
                            if (gs.playerIframe <= 0f) {
                                if (gs.shieldTimer <= 0) {
                                    val dmgAmount = if (gs.difficulty == 0 && Random.nextFloat() > 0.5f) 0 else 1
                                    if (dmgAmount > 0) {
                                        onDamage(scale)
                                        gs.chromaticIntensity = max(gs.chromaticIntensity, 0.8f)
                                        gs.px = max(safeLeftBoundary + scale * 0.05f, gs.obsX[i] - scale * 0.1f)
                                    }
                                } else {
                                    gs.shieldTimer = 0f
                                    gs.px = gs.obsX[i] + obsW + scale * 0.05f
                                    gs.playerIframe = 1.0f
                                }
                            } else {
                                gs.px = max(safeLeftBoundary + scale * 0.05f, gs.obsX[i] - scale * 0.05f)
                            }
                        } else {
                            gs.px = max(safeLeftBoundary + scale * 0.05f, gs.obsX[i] - scale * 0.02f)
                        }
                        break
                    }
                }

                if (screenObsX + obsW < 0) {
                    gs.obsX[i] = gs.getNextObstacleX(width)
                    gs.randomizeObstacle(i, height)
                    onScoreAdd(5)
                }
            }
        }

        // 2. Powerups & Data Drops Collision
        for (i in 0 until enemySystem.pwn) {
            if (enemySystem.pwActive[i]) {
                val dx = enemySystem.pwX[i] - gs.px
                val dy = enemySystem.pwY[i] - gs.py
                val d2 = dx * dx + dy * dy

                val magnetMult = UpgradeSystem.getDataMagnetRadiusMultiplier()
                val pickupDistSq = hitDistSq * 1.5f * magnetMult * magnetMult

                if (d2 < pickupDistSq) {
                    enemySystem.pwActive[i] = false
                    EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 100)

                    if (!gs.isOverclocked) {
                        gs.overclockMeter = min(100f, gs.overclockMeter + 25f)
                        if (gs.overclockMeter >= 100f && gs.showOverclockTextTimer <= 0f) {
                            gs.showOverclockTextTimer = 2.0f
                            EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 100)
                        }
                    }

                    when (enemySystem.pwType[i]) {
                        0 -> gs.hp = min(gs.maxHp, gs.hp + 1)
                        1 -> gs.visionClarity = 1.5f
                        2 -> gs.shieldTimer = 5f
                    }
                }
            }
        }

        // --- 1. EMP MINE TRAP COLLISION (FIXED & COMPLETED) ---
        if (gs.empMineActive) {
            var mineTriggered = false
            for (i in 0 until enemySystem.n) {
                val dx = gs.empMineX - enemySystem.ex[i]
                val dy = gs.empMineY - enemySystem.ey[i]
                if (dx * dx + dy * dy < (scale * 0.15f) * (scale * 0.15f)) { mineTriggered = true; break }
            }
            if (!mineTriggered && gs.bossActive) {
                val bdx = gs.empMineX - gs.bossX; val bdy = gs.empMineY - gs.bossY
                if (bdx * bdx + bdy * bdy < (scale * 0.2f) * (scale * 0.2f)) { mineTriggered = true }
            }

            if (mineTriggered) {
                gs.empMineActive = false; gs.shockwaveActive = true
                gs.shockwaveX = gs.empMineX; gs.shockwaveY = gs.empMineY; gs.shockwaveR = 0f
                gs.shakeAmount = max(gs.shakeAmount, scale * 0.15f)
                EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_ALERT, 300)
                effectSystem.spawnParticles(gs.empMineX, gs.empMineY, 1, scale * 2f)

                // Destroy nearby enemies & damage boss
                for (j in 0 until enemySystem.n) {
                    val edx = gs.empMineX - enemySystem.ex[j]; val edy = gs.empMineY - enemySystem.ey[j]
                    if (edx * edx + edy * edy < (scale * 0.4f) * (scale * 0.4f)) {
                        onScoreAdd(5); gs.collectedDataKB += LevelEngine.getKillRewardKB(gs.currentLevel, isBoss = false)
                        enemySystem.spawn(j, gs, width, height)
                    }
                }
                if (gs.bossActive) {
                    val bdx = gs.empMineX - gs.bossX; val bdy = gs.empMineY - gs.bossY
                    if (bdx * bdx + bdy * bdy < (scale * 0.5f) * (scale * 0.5f)) {
                        gs.bossHp -= 5 // Massive EMP damage
                        if (gs.bossHp <= 0) triggerBossDeath(scale, onScoreAdd, onCoreUnlock)
                    }
                }
            }
        }

        // --- 4. SPIKES VS ENEMIES & BOSS COLLISION ---
        for (s in 0 until gs.maxSpikes) {
            if (gs.spikeActive[s]) {
                var spikeHit = false

                // A. Boss Hit Detection
                if (gs.bossActive && gs.bossIframe <= 0f) {
                    val bdx = gs.spikeX[s] - gs.bossX
                    val bdy = gs.spikeY[s] - gs.bossY
                    val bossHitRadiusSq = (scale * 0.1f) * (scale * 0.1f)

                    if (bdx * bdx + bdy * bdy < bossHitRadiusSq) {
                        spikeHit = true
                        gs.bossHp--

                        if (gs.bossHp <= gs.bossMaxHp / 2 && !gs.isBossRage) {
                            gs.isBossRage = true
                            gs.chromaticIntensity = 1.0f
                            StoryProtocol.showIngameMessage("WARNING: BOSS OVERRIDE DETECTED - RAGE MODE ACTIVE!", 3f)
                        }

                        gs.bossIframe = 0.15f // Quick iframe against multi-hit spikes

                        effectSystem.spawnParticles(gs.bossX, gs.bossY, 2, scale)
                        EchoAudioManager.playSound(ToneGenerator.TONE_SUP_INTERCEPT, 100)

                        gs.combo++
                        onScoreAdd(10)

                        if (!gs.isOverclocked) {
                            gs.overclockMeter = min(100f, gs.overclockMeter + 5f)
                            if (gs.overclockMeter >= 100f && gs.showOverclockTextTimer <= 0f) {
                                gs.showOverclockTextTimer = 2.0f
                                EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 100)
                            }
                        }

                        // Check Boss Death from Spike
                        if (gs.bossHp <= 0) {
                            triggerBossDeath(scale, onScoreAdd, onCoreUnlock)
                        }
                    }
                }

                // B. Normal Enemy Hit Detection
                if (!spikeHit) {
                    for (i in 0 until enemySystem.n) {
                        val dx = gs.spikeX[s] - enemySystem.ex[i]
                        val dy = gs.spikeY[s] - enemySystem.ey[i]

                        if (dx * dx + dy * dy < hitDistSq * 1.5f) {
                            spikeHit = true

                            gs.combo++
                            onScoreAdd(2 + gs.combo)
                            gs.collectedDataKB += LevelEngine.getKillRewardKB(gs.currentLevel, isBoss = false)

                            if (!gs.isOverclocked) {
                                gs.overclockMeter = min(100f, gs.overclockMeter + 15f)
                                if (gs.overclockMeter >= 100f && gs.showOverclockTextTimer <= 0f) {
                                    gs.showOverclockTextTimer = 2.0f
                                    EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 100)
                                }
                            }

                            effectSystem.spawnParticles(enemySystem.ex[i], enemySystem.ey[i], 1, scale)
                            EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 50)

                            if (!gs.bossActive || enemySystem.type[i] == 1) {
                                enemySystem.spawn(i, gs, width, height)
                            }
                            break
                        }
                    }
                }

                // Sniper Spike (Type 2) pierces through enemies!
                if (spikeHit && gs.spikeType[s] != 2) {
                    gs.spikeActive[s] = false
                }
            }
        }

        // 5. Enemies Collision (Player Body / Ramming)
        for (i in 0 until enemySystem.n) {
            val dx = gs.px - enemySystem.ex[i]
            val dy = gs.py - enemySystem.ey[i]
            val d2 = dx * dx + dy * dy

            if (d2 < hitDistSq) {
                if (gs.isOverclocked) {
                    onScoreAdd(5)
                    gs.collectedDataKB += LevelEngine.getKillRewardKB(gs.currentLevel, isBoss = false)
                    effectSystem.spawnParticles(enemySystem.ex[i], enemySystem.ey[i], 1, scale)

                    if (!playedEnemyKillSound) {
                        EchoAudioManager.playSound(ToneGenerator.TONE_SUP_INTERCEPT, 50)
                        playedEnemyKillSound = true
                    }
                    gs.hitStopTimer = max(gs.hitStopTimer, 0.05f)

                    if (enemySystem.type[i] == 2) gs.firewallWorldX -= width * 0.25f
                } else if (enemySystem.type[i] == 1) {
                    if (gs.playerIframe <= 0f) {
                        if (gs.shieldTimer > 0f) {
                            gs.shieldTimer = 0f
                            gs.playerIframe = 1.0f
                            effectSystem.spawnParticles(enemySystem.ex[i], enemySystem.ey[i], 1, scale)
                        } else {
                            effectSystem.spawnParticles(gs.px, gs.py, 0, scale)
                            onDamage(scale)
                            gs.chromaticIntensity = max(gs.chromaticIntensity, 0.8f)
                        }
                    }
                } else if (enemySystem.type[i] == 2) {
                    onScoreAdd(5)
                    gs.collectedDataKB += LevelEngine.getKillRewardKB(gs.currentLevel, isBoss = false)
                    gs.firewallWorldX -= width * 0.25f
                    effectSystem.spawnParticles(enemySystem.ex[i], enemySystem.ey[i], 0, scale)

                    if (!playedEnemyHackSound) {
                        EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 80)
                        playedEnemyHackSound = true
                    }
                } else {
                    onScoreAdd(2)
                    gs.collectedDataKB += LevelEngine.getKillRewardKB(gs.currentLevel, isBoss = false)
                }

                enemySystem.spawn(i, gs, width, height)
            }
        }

        // 6. Boss Collision (Player touching boss)
        if (gs.bossActive) {
            val bdx = gs.px - gs.bossX
            val bdy = gs.py - gs.bossY
            val bossRadius = scale * 0.08f
            val entityRadius = scale * 0.03f
            val combinedRadiusSq = (bossRadius + entityRadius) * (bossRadius + entityRadius)

            if (bdx * bdx + bdy * bdy < combinedRadiusSq) {
                if (gs.isOverclocked && gs.bossIframe <= 0f) {
                    gs.bossHp--
                    gs.bossIframe = 1.0f
                    effectSystem.spawnParticles(gs.bossX, gs.bossY, 2, scale)
                    EchoAudioManager.playSound(ToneGenerator.TONE_SUP_INTERCEPT, 150)
                    gs.shakeAmount = max(gs.shakeAmount, scale * 0.08f)
                    gs.chromaticIntensity = max(gs.chromaticIntensity, 0.6f)
                    gs.hitStopTimer = max(gs.hitStopTimer, 0.15f)

                    if (gs.bossHp <= 0) triggerBossDeath(scale, onScoreAdd, onCoreUnlock)

                } else if (gs.bossIframe <= 0f && gs.playerIframe <= 0f) {
                    gs.bossIframe = 1.0f

                    // --- NAYA: PUSH / DHAKKA LOGIC COMPLETELY REMOVED ---
                    if (gs.shieldTimer > 0f) {
                        gs.shieldTimer = 0f
                        gs.playerIframe = 1.0f
                    } else {
                        onDamage(scale)
                        gs.chromaticIntensity = max(gs.chromaticIntensity, 1.0f)
                    }
                }
            }
        }
    }

    private fun triggerBossDeath(scale: Float, onScoreAdd: (Int) -> Unit, onCoreUnlock: (Boolean) -> Unit) {
        gs.bossActive = false
        gs.bossDeathX = gs.bossX
        gs.bossDeathY = gs.bossY
        gs.bossDeathTimer = 2.5f
        gs.shockwaveActive = true
        gs.shockwaveX = gs.bossX
        gs.shockwaveY = gs.bossY
        gs.shockwaveR = 0f
        gs.chromaticIntensity = 1.0f

        // Push away surviving enemies
        for (j in 0 until enemySystem.n) {
            val edx = enemySystem.ex[j] - gs.bossX
            val edy = enemySystem.ey[j] - gs.bossY
            val distSq = edx * edx + edy * edy
            if (distSq > 0.001f) {
                val dist = sqrt(distSq)
                enemySystem.evx[j] = (edx / dist) * scale * 2f
                enemySystem.evy[j] = (edy / dist) * scale * 2f
            }
        }

        gs.currentSector++
        gs.sectorTarget += gs.currentSector * 40
        gs.hp = min(gs.maxHp, gs.hp + 1)
        gs.sectorFlash = 1f; gs.shakeAmount = scale * 0.15f

        onScoreAdd(50)
        gs.collectedDataKB += LevelEngine.getKillRewardKB(gs.currentLevel, isBoss = true)

        effectSystem.spawnFloatingText(gs.bossX, gs.bossY, 50, GameColors.YELLOW)
        EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 500)

        if (gs.gameMode == 0) {
            gs.isLevelCleared = true
        } else {
            if (gs.currentSector > 5 && gs.gameMode == 1) {
                onCoreUnlock(gs.hp == gs.maxHp)
            }
        }
    }
}