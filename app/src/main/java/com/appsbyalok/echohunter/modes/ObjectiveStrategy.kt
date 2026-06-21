package com.appsbyalok.echohunter.modes

import android.media.ToneGenerator
import com.appsbyalok.echohunter.data.LevelEngine
import com.appsbyalok.echohunter.data.LevelFeature
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.systems.EnemySystem
import com.appsbyalok.echohunter.systems.SpawnerSystem
import com.appsbyalok.echohunter.utils.EchoAudioManager
import kotlin.math.sqrt

// Blueprint for all level objectives
interface IGameObjective {
    fun setupObjective(gs: GameState, targetW: Float, targetH: Float, scale: Float)
    fun updateObjective(dt: Float, gs: GameState, enemySys: EnemySystem, spawnerSys: SpawnerSystem, targetW: Float, targetH: Float, scale: Float)
    fun checkWinCondition(gs: GameState): Boolean
    fun isBossTriggerReady(gs: GameState): Boolean

    // NAYA: Shared verification to ensure all level features are satisfied
    fun areSecondaryFeaturesMet(gs: GameState): Boolean {
        val config = LevelEngine.getLevelConfig(gs.currentLevel)
        // 1. Elimination Check
        if (config.features.contains(LevelFeature.ELIMINATION) && gs.elimTargetsKilled < gs.elimTargetsRequired) return false
        
        // 2. Boss Check (Boss must be dead if it exists)
        if (config.features.contains(LevelFeature.BOSS)) {
            // Boss is dead if bossActive is false AND it was either killed or sector incremented
            val bossDead = !gs.bossActive && (gs.bossDeathTimer > 0.1f || gs.currentSector > 1)
            if (!bossDead) return false
        }
        
        // 3. Score Check (Classic)
        if (config.features.contains(LevelFeature.CLASSIC) && gs.score < config.targetScore) return false
        
        return true
    }
}

// 1. STANDARD / ELIMINATION OBJECTIVE (Score based)
class StandardObjective : IGameObjective {
    private var trickleTimer = 2f

    override fun setupObjective(gs: GameState, targetW: Float, targetH: Float, scale: Float) {
        gs.coreX = -9999f
        gs.coreY = -9999f
        gs.coreRadius = 0f
    }
    override fun updateObjective(dt: Float, gs: GameState, enemySys: EnemySystem, spawnerSys: SpawnerSystem, targetW: Float, targetH: Float, scale: Float) {
        val config = LevelEngine.getLevelConfig(gs.currentLevel)
        gs.objectiveLabel = "SYSTEM PENETRATION"
        gs.objectiveProgress = (gs.score.toFloat() / config.targetScore).coerceIn(0f, 1f)

        trickleTimer -= dt
        if (trickleTimer <= 0f) {
            var activeCount = 0
            for (i in 0 until enemySys.n) if (enemySys.ex[i] > -1000f) activeCount++
            
            val baseLimit = if (gs.difficulty == 1) 18f else 14f
            val limit = LevelEngine.getSaturatedValue(gs.currentLevel, baseLimit, 40f - baseLimit, 100f).toInt()
            val queued = spawnerSys.getTotalQueue(gs)
            
            if (activeCount + queued < limit) {
                spawnerSys.queueSpawns(limit - (activeCount + queued), gs)
            }
            trickleTimer = 1f
        }
    }
    override fun checkWinCondition(gs: GameState): Boolean {
        return areSecondaryFeaturesMet(gs)
    }
    override fun isBossTriggerReady(gs: GameState): Boolean {
        val config = LevelEngine.getLevelConfig(gs.currentLevel)
        return gs.score >= config.targetScore
    }
}

// 2. DEFENSE OBJECTIVE (Wave based, Protect Core)
class DefenseObjective : IGameObjective {
    override fun setupObjective(gs: GameState, targetW: Float, targetH: Float, scale: Float) {
        gs.defWaveMax = when {
            gs.currentLevel <= 30 -> 1
            gs.currentLevel <= 70 -> 2
            gs.currentLevel <= 150 -> 3
            gs.currentLevel <= 300 -> 4
            else -> 5
        }
        gs.defWaveCurrent = 1
        gs.defWaveState = 0 // Buffer phase
        gs.defWaveTimer = 5f // 5 seconds initial pause

        gs.coreMaxHp = LevelEngine.getSaturatedValue(gs.currentLevel, 10f, 15f, 150f).toInt()
        gs.coreHp = gs.coreMaxHp

        // Locate DEST_NODE for Core position
        val grid = gs.gridMap
        if (grid != null) {
            for (x in 0 until grid.size) {
                for (y in 0 until grid[0].size) {
                    if (grid[x][y] == 2) { // 2 is DEST_NODE
                        gs.coreX = x * gs.tileSize + (gs.tileSize / 2f)
                        gs.coreY = y * gs.tileSize + (gs.tileSize / 2f)
                        gs.coreRadius = scale * 0.15f
                        return
                    }
                }
            }
        }
        // Fallback
        gs.coreX = gs.px
        gs.coreY = gs.py
        gs.coreRadius = scale * 0.15f
    }

    override fun updateObjective(dt: Float, gs: GameState, enemySys: EnemySystem, spawnerSys: SpawnerSystem, targetW: Float, targetH: Float, scale: Float) {
        when (gs.defWaveState) {
            0, 2 -> { // Buffer or Cooldown Phase
                gs.defWaveTimer -= dt
                if (gs.defWaveTimer <= 0f) {
                    gs.defWaveState = 1
                    val diffMult = if (gs.difficulty == 1) 1.5f else 1.0f
                    val hardCap = if (gs.difficulty == 1) 40f else 25f
                    val baseCount = 2f * diffMult
                    
                    gs.defEnemiesToSpawn = LevelEngine.getSaturatedValue(gs.currentLevel, baseCount, hardCap - baseCount, 80f).toInt()
                    gs.defEnemiesAlive = 0
                    
                    spawnerSys.queueSpawns(gs.defEnemiesToSpawn, gs)
                    
                    StoryProtocol.showIngameMessage("WAVE ${gs.defWaveCurrent} / ${gs.defWaveMax} INCOMING!", 2f)
                    EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
                }
            }
            1 -> { // Fighting Phase
                if (gs.defEnemiesAlive <= 0 && gs.defEnemiesToSpawn <= 0) {
                    if (gs.defWaveCurrent >= gs.defWaveMax) {
                        gs.defWaveCurrent++ // Win condition check increments this to > Max
                    } else {
                        gs.defWaveCurrent++
                        gs.defWaveState = 2
                        gs.defWaveTimer = 7f 
                        StoryProtocol.showIngameMessage("WAVE CLEAR! REGROUPING...", 2f)
                    }
                }
            }
        }
    }

    override fun checkWinCondition(gs: GameState): Boolean {
        // Must complete all waves AND secondary features (Boss/Elim)
        return gs.defWaveCurrent > gs.defWaveMax && gs.coreHp > 0 && areSecondaryFeaturesMet(gs)
    }

    override fun isBossTriggerReady(gs: GameState): Boolean {
        // Hybrid: Boss spawns at the start of the final wave for maximum chaos
        return gs.defWaveCurrent == gs.defWaveMax && gs.defWaveState == 1
    }
}

// 3. ESCAPE OBJECTIVE (Reach the portal)
class EscapeObjective : IGameObjective {
    private var trickleTimer = 2f

    override fun setupObjective(gs: GameState, targetW: Float, targetH: Float, scale: Float) {
        gs.escapeGateActive = false
        val grid = gs.gridMap
        if (grid != null) {
            for (x in 0 until grid.size) {
                for (y in 0 until grid[0].size) {
                    if (grid[x][y] == 2) {
                        gs.coreX = x * gs.tileSize + (gs.tileSize / 2f)
                        gs.coreY = y * gs.tileSize + (gs.tileSize / 2f)
                        gs.coreRadius = scale * 0.12f
                        return
                    }
                }
            }
        }
        gs.coreX = gs.px + (if (Math.random() > 0.5) 1f else -1f) * targetW * 0.8f
        gs.coreY = gs.py + (if (Math.random() > 0.5) 1f else -1f) * targetH * 0.8f
        gs.coreRadius = scale * 0.12f
    }
    override fun updateObjective(dt: Float, gs: GameState, enemySys: EnemySystem, spawnerSys: SpawnerSystem, targetW: Float, targetH: Float, scale: Float) {
        trickleTimer -= dt
        if (trickleTimer <= 0f) {
            var activeCount = 0
            for (i in 0 until enemySys.n) if (enemySys.ex[i] > -1000f) activeCount++
            val baseLimit = if (gs.difficulty == 1) 16f else 12f
            val limit = LevelEngine.getSaturatedValue(gs.currentLevel, baseLimit, 35f - baseLimit, 120f).toInt()
            val queued = spawnerSys.getTotalQueue(gs)
            if (activeCount + queued < limit) {
                spawnerSys.queueSpawns(limit - (activeCount + queued), gs)
            }
            trickleTimer = 1f
        }

        // Hybrid check: Gate activates ONLY when all other features are met
        if (areSecondaryFeaturesMet(gs) && !gs.escapeGateActive) {
            gs.escapeGateActive = true
            StoryProtocol.showIngameMessage("SYSTEM BREACHED! EXIT PORTAL OPEN!", 4f)
            EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_ALERT, 300)
        }
    }
    override fun checkWinCondition(gs: GameState): Boolean {
        if (!gs.escapeGateActive) return false
        val cdx = gs.px - gs.coreX
        val cdy = gs.py - gs.coreY
        return (cdx * cdx + cdy * cdy < gs.coreRadius * gs.coreRadius)
    }

    override fun isBossTriggerReady(gs: GameState): Boolean {
        val config = LevelEngine.getLevelConfig(gs.currentLevel)
        return gs.score >= config.targetScore || gs.elimTargetsKilled >= gs.elimTargetsRequired
    }
}

// 4. STORY OBJECTIVE (Progresses only via Boss Kills)
class StoryObjective : IGameObjective {
    private var trickleTimer = 2f
    override fun setupObjective(gs: GameState, targetW: Float, targetH: Float, scale: Float) {
        gs.coreRadius = 0f 
    }
    override fun updateObjective(dt: Float, gs: GameState, enemySys: EnemySystem, spawnerSys: SpawnerSystem, targetW: Float, targetH: Float, scale: Float) {
        trickleTimer -= dt
        if (trickleTimer <= 0f) {
            var activeCount = 0
            for (i in 0 until enemySys.n) if (enemySys.ex[i] > -1000f) activeCount++
            val baseLimit = if (gs.difficulty == 1) 20 else 15
            val limit = kotlin.math.min(45, baseLimit + (gs.currentSector * 5))
            val queued = spawnerSys.getTotalQueue(gs)
            if (activeCount + queued < limit) {
                spawnerSys.queueSpawns(limit - (activeCount + queued), gs)
            }
            trickleTimer = 1f
        }
        if (gs.state == 8 && gs.coreRadius == 0f) gs.coreRadius = 100f
    }
    override fun checkWinCondition(gs: GameState): Boolean = false
    override fun isBossTriggerReady(gs: GameState): Boolean = false
}

// 5. ELIMINATION OBJECTIVE (Kill High Value Targets)
class EliminationObjective : IGameObjective {
    private var trickleTimer = 1f

    override fun setupObjective(gs: GameState, targetW: Float, targetH: Float, scale: Float) {
        gs.coreX = -9999f
        gs.coreY = -9999f
        gs.coreRadius = 0f
        gs.elimTargetsKilled = 0
        gs.elimTargetsRequired = LevelEngine.getSaturatedValue(gs.currentLevel, 5f, 10f, 100f).toInt()
    }

    override fun updateObjective(dt: Float, gs: GameState, enemySys: EnemySystem, spawnerSys: SpawnerSystem, targetW: Float, targetH: Float, scale: Float) {
        trickleTimer -= dt
        if (trickleTimer <= 0f) {
            var activeCount = 0
            for (i in 0 until enemySys.n) if (enemySys.ex[i] > -1000f) activeCount++
            val baseLimit = if (gs.difficulty == 1) 18f else 14f
            val limit = LevelEngine.getSaturatedValue(gs.currentLevel, baseLimit, 40f - baseLimit, 100f).toInt()
            val queued = spawnerSys.getTotalQueue(gs)
            if (activeCount + queued < limit) {
                spawnerSys.queueSpawns(limit - (activeCount + queued), gs)
            }
            trickleTimer = 1f
        }
    }

    override fun checkWinCondition(gs: GameState): Boolean {
        return gs.elimTargetsKilled >= gs.elimTargetsRequired && areSecondaryFeaturesMet(gs)
    }

    override fun isBossTriggerReady(gs: GameState): Boolean {
        return gs.elimTargetsKilled >= gs.elimTargetsRequired
    }
}

// 6. BOMB OBJECTIVE (Plant and Defend)
class BombObjective : IGameObjective {
    private var trickleTimer = 1f
    private var bombPlanted = false
    private var plantTimer = 3f 
    private var defuseTimer = 45f 
    private var detonationTimer = 0f 

    override fun setupObjective(gs: GameState, targetW: Float, targetH: Float, scale: Float) {
        gs.coreX = -9999f
        gs.coreY = -9999f
        gs.coreRadius = 0f
        bombPlanted = false
        plantTimer = 3f
        detonationTimer = 0f
        defuseTimer = 30f + (gs.currentLevel / 5f).coerceAtMost(30f)

        val grid = gs.gridMap
        if (grid != null) {
            val paths = mutableListOf<Pair<Int, Int>>()
            for (x in 0 until grid.size) {
                for (y in 0 until grid[0].size) {
                    if (grid[x][y] == 0) paths.add(Pair(x, y))
                }
            }
            val targetNode = paths.filter { 
                val dx = it.first * gs.tileSize - gs.px
                val dy = it.second * gs.tileSize - gs.py
                (dx*dx + dy*dy) > (targetW * targetW * 0.5f)
            }.randomOrNull() ?: paths.random()

            gs.bombTargetX = targetNode.first * gs.tileSize + gs.tileSize/2f
            gs.bombTargetY = targetNode.second * gs.tileSize + gs.tileSize/2f
        } else {
            gs.bombTargetX = gs.px + (if (Math.random() > 0.5) 1f else -1f) * targetW * 0.8f
            gs.bombTargetY = gs.py + (if (Math.random() > 0.5) 1f else -1f) * targetH * 0.8f
        }
    }

    override fun updateObjective(dt: Float, gs: GameState, enemySys: EnemySystem, spawnerSys: SpawnerSystem, targetW: Float, targetH: Float, scale: Float) {
        trickleTimer -= dt
        if (trickleTimer <= 0f) {
            var activeCount = 0
            for (i in 0 until enemySys.n) if (enemySys.ex[i] > -1000f) activeCount++
            val baseLimit = if (gs.difficulty == 1) 20f else 15f
            val limit = LevelEngine.getSaturatedValue(gs.currentLevel, baseLimit, 45f - baseLimit, 100f).toInt()
            val queued = spawnerSys.getTotalQueue(gs)
            if (activeCount + queued < limit) {
                spawnerSys.queueSpawns(limit - (activeCount + queued), gs)
            }
            trickleTimer = 1f
        }

        if (!bombPlanted) {
            val dx = gs.px - gs.bombTargetX
            val dy = gs.py - gs.bombTargetY
            val dist = sqrt(dx * dx + dy * dy)
            gs.objectiveLabel = "LOCATE PLANT SITE"
            gs.objectiveProgress = (1.0f - (dist / 1000f)).coerceIn(0f, 0.9f)
            if (dist < 150f) {
                plantTimer -= dt
                gs.objectiveLabel = "PLANTING LOGIC BOMB..."
                gs.objectiveProgress = 1.0f - (plantTimer / 3f)
                if (plantTimer <= 0f) {
                    bombPlanted = true
                    StoryProtocol.showIngameMessage("LOGIC BOMB PLANTED! DEFEND THE UPLINK!", 3f)
                    EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_ALERT, 500)
                    for(node in gs.spawnerNodes) node.queue += 5
                }
            } else plantTimer = 3f
        } else {
            if (detonationTimer > 0f) {
                detonationTimer -= dt
                gs.shakeAmount = scale * 0.2f
                gs.whiteFlash = (detonationTimer / 1.5f).coerceIn(0f, 1f)
                if (detonationTimer <= 0f) gs.isLevelCleared = true
                return
            }

            defuseTimer -= dt
            gs.objectiveLabel = "BOMB DETONATION IN: ${defuseTimer.toInt()}s"
            val totalTime = 45f + (gs.currentLevel / 10f)
            gs.objectiveProgress = 1.0f - (defuseTimer / totalTime)

            // Hybrid: Detonation only starts if boss/elim features are ALSO cleared
            if (defuseTimer <= 0f && areSecondaryFeaturesMet(gs)) {
                detonationTimer = 1.5f
                gs.whiteFlash = 1.0f
                gs.shakeAmount = scale * 0.25f
                StoryProtocol.isGlitchActive = true
                gs.objectiveLabel = "CRITICAL FAILURE IMMINENT"
                EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_HIGH_L, 1000)
            }
        }
    }

    override fun checkWinCondition(gs: GameState): Boolean {
        return bombPlanted && defuseTimer <= 0f && detonationTimer <= 0f && areSecondaryFeaturesMet(gs)
    }

    override fun isBossTriggerReady(gs: GameState): Boolean {
        return bombPlanted && defuseTimer <= 15f && defuseTimer > 0f
    }
}
