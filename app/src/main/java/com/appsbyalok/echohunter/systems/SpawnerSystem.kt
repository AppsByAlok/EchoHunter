package com.appsbyalok.echohunter.systems

import com.appsbyalok.echohunter.data.LevelEngine
import com.appsbyalok.echohunter.engine.GameState
import kotlin.random.Random

// Data Class for Spawn Nodes (Kahan se enemy aayega)
class SpawnNode(
    var x: Float,
    var y: Float,
    var type: Int, // 0 = Compiler (Normal), 1 = Glitch Tear (Swarm), 2 = Admin Gateway (HVT/Boss)
    var cooldownTimer: Float = 0f,
    var maxCooldown: Float = 5f,
    var queue: Int = 0 // Kitne dushman line me hain nikalne ke liye
)

class SpawnerSystem(private val enemySys: EnemySystem, private val effectSys: EffectSystem) {
    
    fun generateNodes(gs: GameState, mapW: Float, mapH: Float, scale: Float) {
        gs.spawnerNodes.clear()
        
        val baseNodes = if (gs.difficulty == 1) 5f else 4f
        val bonus = if (gs.gameMode == 1) 4f else 0f
        val nodeCount = LevelEngine.getSaturatedValue(gs.currentLevel, baseNodes + bonus, 11f, 150f).toInt()
        
        for (i in 0 until nodeCount) {
            var nx = Random.nextFloat() * mapW
            var ny = Random.nextFloat() * mapH
            
            if (gs.gridMap != null) {
                val ts = gs.tileSize
                val gridW = gs.gridMap!!.size
                val gridH = gs.gridMap!![0].size
                var attempts = 0
                var col = Random.nextInt(1, gridW - 1)
                var row = Random.nextInt(1, gridH - 1)
                
                while (gs.gridMap!![col][row] == 1 && attempts < 50) {
                    col = Random.nextInt(1, gridW - 1)
                    row = Random.nextInt(1, gridH - 1)
                    attempts++
                }
                nx = col * ts + (ts / 2f)
                ny = row * ts + (ts / 2f)
            }

            val config = LevelEngine.getLevelConfig(gs.currentLevel)
            val type = when {
                config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.ELIMINATION) -> {
                    if (i < 3) 2 else Random.nextInt(0, 2)
                }
                i == 0 -> 0 
                i == 1 -> 1 
                Random.nextFloat() < 0.2f -> 2 
                else -> Random.nextInt(0, 2)
            }

            val maxC = when(type) {
                0 -> 1.5f 
                1 -> 4.0f 
                else -> 6.0f 
            }

            gs.spawnerNodes.add(SpawnNode(nx, ny, type, maxCooldown = maxC))
        }
    }

    fun queueSpawns(count: Int, gs: GameState) {
        if (gs.spawnerNodes.isEmpty()) return
        for (i in 0 until count) {
            val node = gs.spawnerNodes.random() 
            node.queue++
        }
    }

    fun update(dt: Float, gs: GameState, targetW: Float, targetH: Float, scale: Float) {
        val maxAllowedDistSq = (targetW * 1.5f) * (targetW * 1.5f)

        for (node in gs.spawnerNodes) {
            val dx = node.x - gs.px
            val dy = node.y - gs.py
            
            val isTooFar = dx * dx + dy * dy > maxAllowedDistSq

            if (isTooFar) {
                // Relocate ONLY if too far. Initial closeness was handled at generation.
                for (attempt in 0 until 15) {
                    val angle = Random.nextFloat() * 6.2831855f
                    // Stay between 0.8 and 1.3 of targetW to be off-screen but not too far
                    val dist = (targetW * 0.8f) + Random.nextFloat() * (targetW * 0.5f)
                    val tx = gs.px + kotlin.math.cos(angle) * dist
                    val ty = gs.py + kotlin.math.sin(angle) * dist

                    if (gs.gridMap != null) {
                        val ts = gs.tileSize
                        val col = (tx / ts).toInt().coerceIn(1, gs.gridMap!!.size - 2)
                        val row = (ty / ts).toInt().coerceIn(1, gs.gridMap!![0].size - 2)

                        if (gs.gridMap!![col][row] == 0) { // Found a PATH (0 = PATH, 1 = WALL)
                            node.x = col * ts + (ts / 2f)
                            node.y = row * ts + (ts / 2f)
                            break
                        }
                    } else {
                        node.x = tx
                        node.y = ty
                        break
                    }
                }
            }

            if (node.cooldownTimer > 0f) {
                node.cooldownTimer -= dt
            }

            if (Random.nextFloat() < 0.05f) {
                effectSys.spawnParticles(node.x, node.y, 0, scale * 0.5f)
            }

            if (node.cooldownTimer <= 0f && node.queue > 0) {
                for (i in 0 until enemySys.n) {
                    if (enemySys.ex[i] < -1000f) {
                        enemySys.spawnAt(i, node.x, node.y, gs, targetW, targetH, node.type)
                        node.queue--

                        if (gs.activeObjective is com.appsbyalok.echohunter.modes.DefenseObjective) {
                            gs.defEnemiesAlive++
                            gs.defEnemiesToSpawn--
                            if (gs.defEnemiesToSpawn < 0) gs.defEnemiesToSpawn = 0
                        }

                        val rushFactor = if (node.queue > 2) 0.5f else 1.0f
                        node.cooldownTimer = node.maxCooldown * rushFactor * (0.8f + Random.nextFloat() * 0.4f)
                        
                        val particleType = if (node.type == 1) 1 else 0
                        effectSys.spawnParticles(node.x, node.y, particleType, scale * 2f)
                        break
                    }
                }
            }
        }
    }

    fun getTotalQueue(gs: GameState): Int {
        var total = 0
        for (node in gs.spawnerNodes) total += node.queue
        return total
    }
}
