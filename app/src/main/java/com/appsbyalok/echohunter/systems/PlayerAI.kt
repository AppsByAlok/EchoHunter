package com.appsbyalok.echohunter.systems

import com.appsbyalok.echohunter.data.LevelEngine
import com.appsbyalok.echohunter.data.LevelFeature
import com.appsbyalok.echohunter.engine.GameState
import kotlin.math.sqrt
import kotlin.random.Random

class PlayerAI(private val gs: GameState, private val enemySys: EnemySystem) {

    private var stuckTimer = 0f
    private var lastPx = 0f
    private var lastPy = 0f
    private var decisionTimer = 0f

    private var playerHeatMap: Array<IntArray>? = null
    companion object { private const val MAX_PLAYER_QUEUE = 30000 }
    private val qX = IntArray(MAX_PLAYER_QUEUE)
    private val qY = IntArray(MAX_PLAYER_QUEUE)

    private var targetGridX = -1
    private var targetGridY = -1

    fun update(dt: Float, scale: Float) {
        if (!gs.isAutoPilotActive) return

        if (gs.isJoyActive) return

        if (gs.autoPilotTimer > 0f) {
            gs.autoPilotTimer -= dt
            if (gs.autoPilotTimer <= 0f) {
                deactivateAutopilot()
                return
            }
        }

        gs.isAutoFireLocked = true
        gs.isAutoSonarLocked = true

        decisionTimer -= dt
        if (decisionTimer <= 0f) {
            val strategicTarget = analyzeStrategicTarget()
            targetGridX = strategicTarget.first
            targetGridY = strategicTarget.second

            computePlayerHeatMap(targetGridX, targetGridY)
            decisionTimer = 0.2f
        }

        steerWithTacticalKiting(dt, scale)
    }

    /**
     * Context-Aware Threat Analyzer
     */
    private fun analyzeStrategicTarget(): Pair<Int, Int> {
        val grid = gs.gridMap ?: return Pair(0, 0)
        val w = grid.size; val h = grid[0].size
        val ts = gs.tileSize
        val currentConfig = LevelEngine.getLevelConfig(gs.currentLevel)

        // Find Core/Exit node positions
        var objectiveX = w / 2
        var objectiveY = h / 2
        for (x in 0 until w) {
            for (y in 0 until h) {
                if (grid[x][y] == 2) {
                    objectiveX = x; objectiveY = y
                    break
                }
            }
        }
        val coreWorldX = objectiveX * ts + (ts / 2f)
        val coreWorldY = objectiveY * ts + (ts / 2f)

        // --- LAYER 1: CORE PROTECTION PROTOCOL ---
        if (currentConfig.features.contains(LevelFeature.DEFENSE)) {
            var worstThreatIdx = -1
            var closestToCoreDistSq = Float.MAX_VALUE

            // Find the enemy closest to destroying your core
            for (i in 0 until enemySys.n) {
                if (enemySys.vis[i] > 0.05f) {
                    val edx = enemySys.ex[i] - coreWorldX
                    val edy = enemySys.ey[i] - coreWorldY
                    val distToCoreSq = edx * edx + edy * edy

                    if (distToCoreSq < closestToCoreDistSq) {
                        closestToCoreDistSq = distToCoreSq
                        worstThreatIdx = i
                    }
                }
            }

            // If an enemy breached the 7-tile outer perimeter of the core, intercept them immediately!
            val coreBreachRadiusSq = (ts * 7f) * (ts * 7f)
            if (worstThreatIdx != -1 && closestToCoreDistSq < coreBreachRadiusSq) {
                val tx = (enemySys.ex[worstThreatIdx] / ts).toInt().coerceIn(0, w - 1)
                val ty = (enemySys.ey[worstThreatIdx] / ts).toInt().coerceIn(0, h - 1)
                return Pair(tx, ty)
            }
        }

        // --- LAYER 2: GENERAL COMBAT SEEKING ---
        var closestEnemyIdx = -1
        var minEnemyDistSq = Float.MAX_VALUE
        for (i in 0 until enemySys.n) {
            if (enemySys.vis[i] > 0.05f) {
                val dx = enemySys.ex[i] - gs.px
                val dy = enemySys.ey[i] - gs.py
                val distSq = dx * dx + dy * dy
                if (distSq < minEnemyDistSq) {
                    minEnemyDistSq = distSq
                    closestEnemyIdx = i
                }
            }
        }

        // Engage visible targets if they step within operational reach (5 tiles)
        val engageRadiusSq = (ts * 5f) * (ts * 5f)
        if (closestEnemyIdx != -1 && minEnemyDistSq < engageRadiusSq) {
            val ex = (enemySys.ex[closestEnemyIdx] / ts).toInt().coerceIn(0, w - 1)
            val ey = (enemySys.ey[closestEnemyIdx] / ts).toInt().coerceIn(0, h - 1)
            return Pair(ex, ey)
        }

        // --- LAYER 3: ECONOMY / PROGRESSION FALLBACKS ---
        // Hunt powerups if safe
        var closestPwIdx = -1
        var minPwDistSq = Float.MAX_VALUE
        for (i in 0 until enemySys.pwn) {
            if (enemySys.pwActive[i]) {
                val dx = enemySys.pwX[i] - gs.px
                val dy = enemySys.pwY[i] - gs.py
                val distSq = dx * dx + dy * dy
                if (distSq < minPwDistSq) {
                    minPwDistSq = distSq
                    closestPwIdx = i
                }
            }
        }

        if (closestPwIdx != -1) {
            val pwx = (enemySys.pwX[closestPwIdx] / ts).toInt().coerceIn(0, w - 1)
            val pwy = (enemySys.pwY[closestPwIdx] / ts).toInt().coerceIn(0, h - 1)
            return Pair(pwx, pwy)
        }

        // Default path destination
        return Pair(objectiveX, objectiveY)
    }

    /**
     * Steers along pathing vectors while actively blending an Evasion Vector
     * if threats compromise personal boundaries.
     */
    private fun steerWithTacticalKiting(dt: Float, scale: Float) {
        val hm = playerHeatMap ?: return
        val ts = gs.tileSize
        val cx = (gs.px / ts).toInt()
        val cy = (gs.py / ts).toInt()

        if (cx !in hm.indices || cy !in hm[0].indices) return

        // 1. Resolve standard Pathfinding direction
        var bestVal = hm[cx][cy]
        var stepX = cx
        var stepY = cy

        val dirsX = intArrayOf(0, 0, -1, 1)
        val dirsY = intArrayOf(-1, 1, 0, 0)

        for (d in 0..3) {
            val nx = cx + dirsX[d]; val ny = cy + dirsY[d]
            if (nx in hm.indices && ny in hm[0].indices && hm[nx][ny] < bestVal) {
                bestVal = hm[nx][ny]
                stepX = nx; stepY = ny
            }
        }

        val pathWorldX = stepX * ts + (ts / 2f)
        val pathWorldY = stepY * ts + (ts / 2f)

        var pathDirX = pathWorldX - gs.px
        var pathDirY = pathWorldY - gs.py
        val pathDist = sqrt(pathDirX * pathDirX + pathDirY * pathDirY)

        if (pathDist > 0f) {
            pathDirX /= pathDist
            pathDirY /= pathDist
        }

        // 2. Identify threat parameters for personal space violations
        var closestEnemyIdx = -1
        var minEnemyDistSq = Float.MAX_VALUE
        for (i in 0 until enemySys.n) {
            if (enemySys.vis[i] > 0.05f) {
                val dx = enemySys.ex[i] - gs.px
                val dy = enemySys.ey[i] - gs.py
                val distSq = dx * dx + dy * dy
                if (distSq < minEnemyDistSq) {
                    minEnemyDistSq = distSq
                    closestEnemyIdx = i
                }
            }
        }

        // 3. Vector Blending Core Calculation
        var finalDirX = pathDirX
        var finalDirY = pathDirY
        val kiteTriggerRadius = ts * 2.5f // Critically close at less than 2.5 tiles

        if (closestEnemyIdx != -1 && minEnemyDistSq < (kiteTriggerRadius * kiteTriggerRadius)) {
            val currentEnemyDist = sqrt(minEnemyDistSq)

            if (currentEnemyDist > 0f) {
                // Evasion vector points directly *away* from the immediate threat
                val fleeDirX = (gs.px - enemySys.ex[closestEnemyIdx]) / currentEnemyDist
                val fleeDirY = (gs.py - enemySys.ey[closestEnemyIdx]) / currentEnemyDist

                // Calculate panic weighting (closer enemy = stronger urge to back away)
                val proximityFactor = 1.0f - (currentEnemyDist / kiteTriggerRadius).coerceIn(0f, 1f)
                val fleeWeight = proximityFactor * 0.55f // Up to 55% influence assigned to kiting out
                val pathWeight = 1.0f - fleeWeight

                // Blend vectors seamlessly
                finalDirX = (pathDirX * pathWeight) + (fleeDirX * fleeWeight)
                finalDirY = (pathDirY * pathWeight) + (fleeDirY * fleeWeight)
            }
        }

        // 4. Finalizing Movement Directives
        val finalMag = sqrt(finalDirX * finalDirX + finalDirY * finalDirY)
        if (finalMag > 0f && pathDist > scale * 0.02f) {
            var dirX = finalDirX / finalMag
            var dirY = finalDirY / finalMag

            // Frame check stall verifications
            val framesMoved = sqrt((gs.px - lastPx) * (gs.px - lastPx) + (gs.py - lastPy) * (gs.py - lastPy))
            if (framesMoved < scale * 0.04f * dt) stuckTimer += dt else stuckTimer = 0f

            if (stuckTimer > 0.12f) {
                dirX += (Random.nextFloat() - 0.5f) * 2f
                dirY += (Random.nextFloat() - 0.5f) * 2f
                val length = sqrt(dirX * dirX + dirY * dirY)
                if (length > 0) { dirX /= length; dirY /= length }
            }

            gs.joyDirX = dirX
            gs.joyDirY = dirY

            // Aiming behavior: Face the enemy while retreating, otherwise look where moving
            if (closestEnemyIdx != -1 && minEnemyDistSq < (ts * 6f) * (ts * 6f)) {
                val aimDx = enemySys.ex[closestEnemyIdx] - gs.px
                val aimDy = enemySys.ey[closestEnemyIdx] - gs.py
                val aimDist = sqrt(aimDx * aimDx + aimDy * aimDy)
                if (aimDist > 0f) {
                    gs.lastFacingX = aimDx / aimDist
                    gs.lastFacingY = aimDy / aimDist
                }
            } else {
                gs.lastFacingX = dirX
                gs.lastFacingY = dirY
            }
        } else {
            gs.joyDirX = 0f
            gs.joyDirY = 0f
        }

        lastPx = gs.px
        lastPy = gs.py
    }

    private fun computePlayerHeatMap(tx: Int, ty: Int) {
        val grid = gs.gridMap ?: return
        val w = grid.size; val h = grid[0].size

        if (playerHeatMap == null || playerHeatMap!!.size != w || playerHeatMap!![0].size != h) {
            playerHeatMap = Array(w) { IntArray(h) }
        }

        for (x in 0 until w) {
            for (y in 0 until h) {
                playerHeatMap!![x][y] = 9999
            }
        }

        var head = 0; var tail = 0
        val targetX = tx.coerceIn(0, w - 1)
        val targetY = ty.coerceIn(0, h - 1)

        qX[tail] = targetX; qY[tail] = targetY; tail++
        playerHeatMap!![targetX][targetY] = 0

        val dirsX = intArrayOf(0, 0, -1, 1)
        val dirsY = intArrayOf(-1, 1, 0, 0)

        while (head < tail) {
            val cx = qX[head]; val cy = qY[head]; head++
            val dist = playerHeatMap!![cx][cy]

            for (d in 0..3) {
                val nx = cx + dirsX[d]; val ny = cy + dirsY[d]
                if (nx in 0 until w && ny in 0 until h && grid[nx][ny] != 1) {
                    if (playerHeatMap!![nx][ny] > dist + 1) {
                        playerHeatMap!![nx][ny] = dist + 1
                        if (tail < MAX_PLAYER_QUEUE) {
                            qX[tail] = nx; qY[tail] = ny; tail++
                        }
                    }
                }
            }
        }
    }

    fun deactivateAutopilot() {
        gs.isAutoPilotActive = false
        gs.joyDirX = 0f; gs.joyDirY = 0f
        gs.isAutoFireLocked = false
        gs.isAutoSonarLocked = false
    }
}