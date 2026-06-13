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
    
    // NAYA: Ab ye GameState ki global list use karega
    fun generateNodes(gs: GameState, mapW: Float, mapH: Float, scale: Float) {
        gs.spawnerNodes.clear()
        
        val baseNodes = if (gs.difficulty == 1) 5f else 4f
        val bonus = if (gs.gameMode == 1) 4f else 0f
        // Smoothly scale from 4-9 nodes up to 15-20 nodes max
        val nodeCount = LevelEngine.getSaturatedValue(gs.currentLevel, baseNodes + bonus, 11f, 150f).toInt()
        
        for (i in 0 until nodeCount) {
            var nx = Random.nextFloat() * mapW
            var ny = Random.nextFloat() * mapH
            
            // Agar Maze (Grid) hai, toh deewaron ke andar spawn nahi hone chahiye
            if (gs.gridMap != null) {
                val ts = gs.tileSize
                val gridW = gs.gridMap!!.size
                val gridH = gs.gridMap!![0].size
                var col = Random.nextInt(1, gridW - 1)
                var row = Random.nextInt(1, gridH - 1)
                
                // Rasta (PATH = 0) dhoondhne ka try karo
                var attempts = 0
                while (gs.gridMap!![col][row] == 1 && attempts < 50) {
                    col = Random.nextInt(1, gridW - 1)
                    row = Random.nextInt(1, gridH - 1)
                    attempts++
                }
                nx = col * ts + (ts / 2f)
                ny = row * ts + (ts / 2f)
            }

            // Node types set karna (Theme ke hisaab se)
            val type = when {
                i == 0 -> 0 // Kam se kam ek Compiler Node
                i == 1 -> 1 // Kam se kam ek Glitch Tear
                Random.nextFloat() < 0.2f -> 2 // 20% chance Admin Gateway ka
                else -> Random.nextInt(0, 2)
            }

            // Node ke hisaab se cooldown (Fast paced action ke liye)
            val maxC = when(type) {
                0 -> 1.5f // Compiler (Dhada-dhad nikale)
                1 -> 4.0f // Glitch Tear
                else -> 6.0f // Admin Gateway (HVT)
            }

            gs.spawnerNodes.add(SpawnNode(nx, ny, type, maxCooldown = maxC))
        }
    }

    // Wave system is function ko call karke dushmano ki line (queue) lagayega
    fun queueSpawns(count: Int, gs: GameState) {
        if (gs.spawnerNodes.isEmpty()) return
        for (i in 0 until count) {
            val node = gs.spawnerNodes.random() 
            node.queue++
        }
    }

    // Engine loop me chalega, aur queue se enemies ko baahar nikalega
    fun update(dt: Float, gs: GameState, targetW: Float, targetH: Float, scale: Float) {
        val maxAllowedDistSq = (targetW * 1.5f) * (targetW * 1.5f)

        for (node in gs.spawnerNodes) {
            // --- NAYA: EMPTY MAP BUG FIX (RELOCATION) ---
            val dx = node.x - gs.px
            val dy = node.y - gs.py
            if (dx * dx + dy * dy > maxAllowedDistSq) {
                // Node bahut door hai! Isko player ke aas-paas relocate karo
                val angle = kotlin.random.Random.nextFloat() * 6.28f
                val dist = (targetW * 0.6f) + kotlin.random.Random.nextFloat() * (targetW * 0.4f)
                var nx = gs.px + kotlin.math.cos(angle) * dist
                var ny = gs.py + kotlin.math.sin(angle) * dist

                if (gs.gridMap != null) {
                    val ts = gs.tileSize
                    val w = gs.gridMap!!.size
                    val h = gs.gridMap!![0].size
                    var col = (nx / ts).toInt().coerceIn(1, w - 2)
                    var row = (ny / ts).toInt().coerceIn(1, h - 2)
                    if (gs.gridMap!![col][row] == 1) { // Deewar me mat fanso
                        col = kotlin.random.Random.nextInt(1, w - 1)
                        row = kotlin.random.Random.nextInt(1, h - 1)
                    }
                    nx = col * ts + (ts / 2f)
                    ny = row * ts + (ts / 2f)
                }
                node.x = nx
                node.y = ny
            }

            if (node.cooldownTimer > 0f) {
                node.cooldownTimer -= dt
            }

            // VFX: Active Node halka-halka glow karega
            if (Random.nextFloat() < 0.05f) {
                effectSys.spawnParticles(node.x, node.y, 0, scale * 0.5f)
            }

            // Agar timer khatam aur line me dushman hai, toh usko paida karo!
            if (node.cooldownTimer <= 0f && node.queue > 0) {
                // Khali dushman slot dhoondho
                for (i in 0 until enemySys.n) {
                    if (enemySys.ex[i] < -1000f) {
                        
                        enemySys.spawnAt(i, node.x, node.y, gs, targetW, targetH, node.type)
                        
                        node.queue--

                        // Defense Mode logic: Track both alive count and remaining to spawn
                        if (gs.activeObjective is com.appsbyalok.echohunter.modes.DefenseObjective) {
                            gs.defEnemiesAlive++
                            gs.defEnemiesToSpawn--
                            if (gs.defEnemiesToSpawn < 0) gs.defEnemiesToSpawn = 0
                        }

                        // --- NAYA: RUSH FACTOR ---
                        // Agar line me bheed zyada hai (>2), toh spawner 50% fast kaam karega!
                        val rushFactor = if (node.queue > 2) 0.5f else 1.0f
                        node.cooldownTimer = node.maxCooldown * rushFactor * (0.8f + Random.nextFloat() * 0.4f)
                        
                        // --- FIX: VISUAL CLARITY ---
                        // Sirf Glitch Tear (1) laal (Red) fatega, baaki Normal (0) aur Admin (2) Blue/Cyan (0) fatenge
                        val particleType = if (node.type == 1) 1 else 0
                        effectSys.spawnParticles(node.x, node.y, particleType, scale * 2f)
                        break
                    }
                }
                
                // Agar poori screen me 25 dushman bhar chuke hain, toh agle frame me try karega
            }
        }
    }

    fun getTotalQueue(gs: GameState): Int {
        var total = 0
        for (node in gs.spawnerNodes) total += node.queue
        return total
    }
}
