package com.appsbyalok.echohunter.modes

import android.media.ToneGenerator
import com.appsbyalok.echohunter.data.LevelEngine
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.systems.EnemySystem
import com.appsbyalok.echohunter.systems.SpawnerSystem
import com.appsbyalok.echohunter.utils.EchoAudioManager

// Blueprint for all level objectives
interface IGameObjective {
    fun setupObjective(gs: GameState, targetW: Float, targetH: Float, scale: Float)
    fun updateObjective(dt: Float, gs: GameState, enemySys: EnemySystem, spawnerSys: SpawnerSystem, targetW: Float, targetH: Float)
    fun checkWinCondition(gs: GameState): Boolean
    fun isBossTriggerReady(gs: GameState): Boolean
}

// 1. STANDARD / ELIMINATION OBJECTIVE (Score based)
class StandardObjective : IGameObjective {
    private var trickleTimer = 2f

    override fun setupObjective(gs: GameState, targetW: Float, targetH: Float, scale: Float) {
        gs.coreX = -9999f
        gs.coreY = -9999f
        gs.coreRadius = 0f
    }
    override fun updateObjective(dt: Float, gs: GameState, enemySys: EnemySystem, spawnerSys: SpawnerSystem, targetW: Float, targetH: Float) {
        trickleTimer -= dt
        if (trickleTimer <= 0f) {
            var activeCount = 0
            for (i in 0 until enemySys.n) if (enemySys.ex[i] > -1000f) activeCount++
            
            // Limit badhti jayegi jaise-jaise level badhega (Max 40 tak)
            val baseLimit = if (gs.difficulty == 1) 18f else 14f
            val limit = LevelEngine.getSaturatedValue(gs.currentLevel, baseLimit, 40f - baseLimit, 100f).toInt()
            val queued = spawnerSys.getTotalQueue(gs)
            
            // --- NAYA: BULK QUEUE LOGIC ---
            if (activeCount + queued < limit) {
                val needed = limit - (activeCount + queued)
                spawnerSys.queueSpawns(needed, gs) // Jitne kam hain, sabka order de do
            }
            trickleTimer = 1f // Har 1 second me map ki bheed check karo
        }
    }
    override fun checkWinCondition(gs: GameState): Boolean {
        val config = LevelEngine.getLevelConfig(gs.currentLevel)
        return gs.score >= config.targetScore && !gs.bossActive
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
    }

    override fun updateObjective(dt: Float, gs: GameState, enemySys: EnemySystem, spawnerSys: SpawnerSystem, targetW: Float, targetH: Float) {
        when (gs.defWaveState) {
            0, 2 -> { // Buffer or Cooldown Phase
                gs.defWaveTimer -= dt
                if (gs.defWaveTimer <= 0f) {
                    gs.defWaveState = 1
                    // BALANCING: Hard mode increases enemy volume and caps for higher intensity
                    val diffMult = if (gs.difficulty == 1) 1.5f else 1.0f
                    val hardCap = if (gs.difficulty == 1) 40f else 25f
                    val baseCount = 2f * diffMult
                    
                    gs.defEnemiesToSpawn = LevelEngine.getSaturatedValue(gs.currentLevel, baseCount, hardCap - baseCount, 80f).toInt()
                    gs.defEnemiesAlive = 0
                    
                    // Queue the entire wave into the Spawner System!
                    // Note: SpawnerSystem.update will decrement defEnemiesToSpawn as they physically appear
                    spawnerSys.queueSpawns(gs.defEnemiesToSpawn, gs)
                    
                    StoryProtocol.showIngameMessage("WAVE ${gs.defWaveCurrent} / ${gs.defWaveMax} INCOMING!", 2f)
                    EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
                }
            }
            1 -> { // Fighting Phase
                // DefenseObjective now just waits for enemies to be cleared
                // SpawnerSystem handles the actual instantiation
                if (gs.defEnemiesAlive <= 0 && gs.defEnemiesToSpawn <= 0) {
                    if (gs.defWaveCurrent >= gs.defWaveMax) {
                        gs.defWaveCurrent++
                    } else {
                        gs.defWaveCurrent++
                        gs.defWaveState = 2
                        gs.defWaveTimer = 7f // More breathing time between waves
                        StoryProtocol.showIngameMessage("WAVE CLEAR! REGROUPING...", 2f)
                    }
                }
            }
        }
    }

    override fun checkWinCondition(gs: GameState): Boolean {
        return gs.defWaveCurrent > gs.defWaveMax && gs.coreHp > 0 && !gs.bossActive
    }

    override fun isBossTriggerReady(gs: GameState): Boolean {
        return gs.defWaveCurrent > gs.defWaveMax
    }
}

// 3. ESCAPE OBJECTIVE (Reach the portal)
class EscapeObjective : IGameObjective {
    private var trickleTimer = 2f

    override fun setupObjective(gs: GameState, targetW: Float, targetH: Float, scale: Float) {
        gs.escapeGateActive = false
    }
    override fun updateObjective(dt: Float, gs: GameState, enemySys: EnemySystem, spawnerSys: SpawnerSystem, targetW: Float, targetH: Float) {
        val config = LevelEngine.getLevelConfig(gs.currentLevel)
        
        trickleTimer -= dt
        if (trickleTimer <= 0f) {
            var activeCount = 0
            for (i in 0 until enemySys.n) if (enemySys.ex[i] > -1000f) activeCount++
            
            // Escape me bheed thodi kam chahiye hoti hai bhagne ke liye
            val baseLimit = if (gs.difficulty == 1) 16f else 12f
            val limit = LevelEngine.getSaturatedValue(gs.currentLevel, baseLimit, 35f - baseLimit, 120f).toInt()
            val queued = spawnerSys.getTotalQueue(gs)
            
            if (activeCount + queued < limit) {
                val needed = limit - (activeCount + queued)
                spawnerSys.queueSpawns(needed, gs)
            }
            trickleTimer = 1f
        }

        // NAYA: AND logic - Boss maarna jaruri hai agar level me boss hai
        val hasBoss = config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.BOSS)
        val bossDone = !hasBoss || gs.currentSector > 1 // Sector increments after boss death
        
        if (gs.score >= config.targetScore && bossDone && !gs.escapeGateActive) {
            gs.escapeGateActive = true
            StoryProtocol.showIngameMessage("OBJECTIVES COMPLETE! LOCATE THE EXIT PORTAL!", 4f)
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
        return gs.score >= config.targetScore
    }
}


// 4. STORY OBJECTIVE (Progresses only via Boss Kills in CollisionSystem)
class StoryObjective : IGameObjective {
    private var trickleTimer = 2f

    override fun setupObjective(gs: GameState, targetW: Float, targetH: Float, scale: Float) {
        // Core is hidden until the final boss of Sector 5 is defeated
        gs.coreRadius = 0f 
    }
    override fun updateObjective(dt: Float, gs: GameState, enemySys: EnemySystem, spawnerSys: SpawnerSystem, targetW: Float, targetH: Float) {
        trickleTimer -= dt
        if (trickleTimer <= 0f) {
            var activeCount = 0
            for (i in 0 until enemySys.n) if (enemySys.ex[i] > -1000f) activeCount++
            
            // Story mode maps kaafi bade hote hain, base limit high rakhi hai
            val baseLimit = if (gs.difficulty == 1) 20 else 15
            val limit = kotlin.math.min(45, baseLimit + (gs.currentSector * 5))
            val queued = spawnerSys.getTotalQueue(gs)
            
            if (activeCount + queued < limit) {
                val needed = limit - (activeCount + queued)
                spawnerSys.queueSpawns(needed, gs)
            }
            trickleTimer = 1f
        }

        // Reactivate core radius for the final merge sequence if we are in State 8
        if (gs.state == 8 && gs.coreRadius == 0f) {
            gs.coreRadius = 100f // Arbitrary base, will be scaled in GameState/Renderer
        }
    }
    override fun checkWinCondition(gs: GameState): Boolean {
        return false // Story mode win is handled directly via State 8 -> 9 transition in GameEngine
    }

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

    override fun updateObjective(dt: Float, gs: GameState, enemySys: EnemySystem, spawnerSys: SpawnerSystem, targetW: Float, targetH: Float) {
        trickleTimer -= dt
        if (trickleTimer <= 0f) {
            var activeCount = 0
            for (i in 0 until enemySys.n) if (enemySys.ex[i] > -1000f) activeCount++
            
            // Limit badhti jayegi jaise-jaise level badhega (Max 40 tak)
            val baseLimit = if (gs.difficulty == 1) 18f else 14f
            val limit = LevelEngine.getSaturatedValue(gs.currentLevel, baseLimit, 40f - baseLimit, 100f).toInt()
            val queued = spawnerSys.getTotalQueue(gs)
            
            if (activeCount + queued < limit) {
                val needed = limit - (activeCount + queued)
                spawnerSys.queueSpawns(needed, gs)
            }
            trickleTimer = 1f
        }
    }

    override fun checkWinCondition(gs: GameState): Boolean {
        return gs.elimTargetsKilled >= gs.elimTargetsRequired && !gs.bossActive
    }

    override fun isBossTriggerReady(gs: GameState): Boolean {
        return gs.elimTargetsKilled >= gs.elimTargetsRequired
    }
}
