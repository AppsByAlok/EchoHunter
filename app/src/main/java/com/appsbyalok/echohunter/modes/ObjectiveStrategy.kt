package com.appsbyalok.echohunter.modes

import android.media.ToneGenerator
import com.appsbyalok.echohunter.data.LevelEngine
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.systems.EnemySystem
import com.appsbyalok.echohunter.utils.EchoAudioManager
import kotlin.math.min
import kotlin.random.Random

// Blueprint for all level objectives
interface IGameObjective {
    fun setupObjective(gs: GameState, targetW: Float, targetH: Float, scale: Float)
    fun updateObjective(dt: Float, gs: GameState, enemySys: EnemySystem, targetW: Float, targetH: Float)
    fun checkWinCondition(gs: GameState): Boolean
}

// 1. STANDARD / ELIMINATION OBJECTIVE (Score based)
class StandardObjective : IGameObjective {
    override fun setupObjective(gs: GameState, targetW: Float, targetH: Float, scale: Float) {
        gs.coreX = -9999f
        gs.coreY = -9999f
        gs.coreRadius = 0f
    }
    override fun updateObjective(dt: Float, gs: GameState, enemySys: EnemySystem, targetW: Float, targetH: Float) {}
    override fun checkWinCondition(gs: GameState): Boolean {
        val config = LevelEngine.getLevelConfig(gs.currentLevel)
        return gs.score >= config.targetScore && !gs.bossActive
    }
}

// 2. DEFENSE OBJECTIVE (Wave based, Protect Core)
class DefenseObjective : IGameObjective {
    override fun setupObjective(gs: GameState, targetW: Float, targetH: Float, scale: Float) {
        gs.defWaveMax = when {
            gs.currentLevel < 5 -> 1
            gs.currentLevel < 25 -> 2
            gs.currentLevel < 125 -> 3
            gs.currentLevel < 250 -> 4
            else -> 5
        }
        gs.defWaveCurrent = 1
        gs.defWaveState = 0 // Buffer phase
        gs.defWaveTimer = 5f // 5 seconds pause

        val totalTime = LevelEngine.getDefenseTimer(gs.currentLevel)
        gs.maxDefenseTimer = totalTime / gs.defWaveMax
        gs.defenseTimer = totalTime

        gs.coreMaxHp = min(25, 10 + (gs.currentLevel / 15))
        gs.coreHp = gs.coreMaxHp
    }

    override fun updateObjective(dt: Float, gs: GameState, enemySys: EnemySystem, targetW: Float, targetH: Float) {
        if (gs.defenseTimer < 2f) gs.defenseTimer = 2f

        when (gs.defWaveState) {
            0, 2 -> { // Buffer ya Cooldown Phase
                gs.defWaveTimer -= dt
                if (gs.defWaveTimer <= 0f) {
                    gs.defWaveState = 1
                    gs.defEnemiesToSpawn = 8 + (gs.currentLevel * 3)
                    gs.defEnemiesAlive = 0
                    StoryProtocol.showIngameMessage("WAVE ${gs.defWaveCurrent} / ${gs.defWaveMax} INCOMING!", 2f)
                    EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
                }
            }
            1 -> { // Fighting Phase (Wave Injection)
                for (i in 0 until enemySys.n) {
                    if (enemySys.ex[i] < -1000f && gs.defEnemiesToSpawn > 0) {
                        if (Random.nextFloat() < 0.1f) {
                            gs.defEnemiesToSpawn--
                            gs.defEnemiesAlive++
                            enemySys.spawn(i, gs, targetW, targetH)
                        }
                    }
                }

                // Win Condition for Current Wave
                if (gs.defEnemiesAlive <= 0 && gs.defEnemiesToSpawn <= 0) {
                    if (gs.defWaveCurrent >= gs.defWaveMax) {
                        gs.defenseTimer = 0f // Final Signal to end level
                    } else {
                        gs.defWaveCurrent++
                        gs.defWaveState = 2
                        gs.defWaveTimer = 5f
                        StoryProtocol.showIngameMessage("WAVE CLEARED. RELOADING...", 2f)
                    }
                }
            }
        }
    }
    override fun checkWinCondition(gs: GameState): Boolean {
        return gs.defenseTimer <= 0f && gs.coreHp > 0
    }
}

// 3. ESCAPE OBJECTIVE (Reach the portal)
class EscapeObjective : IGameObjective {
    override fun setupObjective(gs: GameState, targetW: Float, targetH: Float, scale: Float) {
        gs.escapeGateActive = false
    }
    override fun updateObjective(dt: Float, gs: GameState, enemySys: EnemySystem, targetW: Float, targetH: Float) {
        val config = LevelEngine.getLevelConfig(gs.currentLevel)
        if (gs.score >= config.targetScore && !gs.escapeGateActive) {
            gs.escapeGateActive = true
            StoryProtocol.showIngameMessage("TARGET REACHED! LOCATE THE EXIT PORTAL!", 4f)
            EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_ALERT, 300)
        }
    }
    override fun checkWinCondition(gs: GameState): Boolean {
        if (!gs.escapeGateActive) return false
        val cdx = gs.px - gs.coreX
        val cdy = gs.py - gs.coreY
        return (cdx * cdx + cdy * cdy < gs.coreRadius * gs.coreRadius)
    }
}


// 4. STORY OBJECTIVE (Progresses only via Boss Kills in CollisionSystem)
class StoryObjective : IGameObjective {
    override fun setupObjective(gs: GameState, targetW: Float, targetH: Float, scale: Float) {
        // Core is hidden until the final boss of Sector 5 is defeated
        gs.coreRadius = 0f 
    }
    override fun updateObjective(dt: Float, gs: GameState, enemySys: EnemySystem, targetW: Float, targetH: Float) {
        // Reactivate core radius for the final merge sequence if we are in State 8
        if (gs.state == 8 && gs.coreRadius == 0f) {
            gs.coreRadius = 100f // Arbitrary base, will be scaled in GameState/Renderer
        }
    }
    override fun checkWinCondition(gs: GameState): Boolean {
        return false // Story mode win is handled directly via State 8 -> 9 transition in GameEngine
    }
}
