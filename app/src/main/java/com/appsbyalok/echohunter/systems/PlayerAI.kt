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

    companion object {
        private const val MAX_PLAYER_QUEUE = 30000
    }

    private val qX = IntArray(MAX_PLAYER_QUEUE)
    private val qY = IntArray(MAX_PLAYER_QUEUE)

    private var targetGridX = -1
    private var targetGridY = -1

    fun update(dt: Float, scale: Float) {
        if (!gs.isAutoPilotActive) return

        if (gs.controls.isMoveJoyActive) return

        if (gs.autoPilotTimer > 0f) {
            gs.autoPilotTimer -= dt
            if (gs.autoPilotTimer <= 0f) {
                deactivateAutopilot()
                return
            }
        }

        gs.controls.isAutoSonarLocked = true

        // 1. EVALUATE TACTICAL CONTEXT (Every Frame)
        val ctx = evaluateTacticalContext()

        // 2. COMBAT & ABILITIES (Every Frame - Independent of movement decisions)
        handleCombatAI(ctx)
        handleTacticalAbilities(ctx)

        // 3. STRATEGIC DECISION (Throttled for performance)
        decisionTimer -= dt
        if (decisionTimer <= 0f) {
            val strategicTarget = analyzeStrategicTarget()
            targetGridX = strategicTarget.first
            targetGridY = strategicTarget.second

            computePlayerHeatMap(targetGridX, targetGridY)
            decisionTimer = 0.15f // Slightly faster response
        }

        // 4. MOVEMENT EXECUTION (Every Frame)
        steerWithTacticalKiting(dt, scale, ctx)
    }

    private data class TacticalContext(
        val closestEnemyIdx: Int,
        val closestEnemyDistSq: Float,
        val enemiesNearCount: Int,
        val extremeThreat: Boolean,
        val bestTargetX: Float,
        val bestTargetY: Float,
        val hasTarget: Boolean
    )

    private fun evaluateTacticalContext(): TacticalContext {
        val ts = gs.tileSize
        var closestIdx = -1
        var minDistSq = Float.MAX_VALUE
        var nearCount = 0
        var extremeThreat = false
        
        var bestX = 0f
        var bestY = 0f
        var hasTarget = false
        var bestScore = Float.MAX_VALUE

        for (i in 0 until enemySys.n) {
            if (enemySys.ex[i] < -1000f) continue
            
            val dx = enemySys.ex[i] - gs.px
            val dy = enemySys.ey[i] - gs.py
            val d2 = dx * dx + dy * dy
            val isVis = enemySys.vis[i] > 0.05f

            // 1. General proximity (for kiting/visibility)
            if (isVis && d2 < minDistSq) {
                minDistSq = d2
                closestIdx = i
            }

            // 2. Threat levels (for traps)
            if (isVis) {
                if (d2 < (ts * 1.8f) * (ts * 1.8f)) extremeThreat = true
                if (d2 < (ts * 4f) * (ts * 4f)) nearCount++
            }

            // 3. Combat targeting (for weapons)
            // AI is more aggressive: targets enemies even if vis is low but they are very close
            val combatVisBonus = if (isVis) 0.5f else (if (d2 < (ts * 3f) * (ts * 3f)) 0.8f else 2.0f)
            var score = d2 * combatVisBonus
            if (enemySys.type[i] == 3) score *= 0.6f // Prioritize HVT

            if (score < bestScore) {
                bestScore = score
                bestX = enemySys.ex[i]; bestY = enemySys.ey[i]
                hasTarget = true
            }
        }
        
        return TacticalContext(closestIdx, minDistSq, nearCount, extremeThreat, bestX, bestY, hasTarget)
    }

    private fun handleCombatAI(ctx: TacticalContext) {
        if (!ctx.hasTarget) return

        val ts = gs.tileSize
        val attackRange = if (gs.controls.currentWeapon == 2) ts * 15f else ts * 6.5f
        
        val dx = ctx.bestTargetX - gs.px
        val dy = ctx.bestTargetY - gs.py
        val distSq = dx * dx + dy * dy

        if (distSq < attackRange * attackRange) {
            val dist = sqrt(distSq)
            if (dist > 0f) {
                val adx = dx / dist
                val ady = dy / dist
                
                // Set Aim and Fire request every frame
                gs.controls.aimDirX = adx
                gs.controls.aimDirY = ady
                
                // Ensure sprite faces the target during combat
                gs.lastFacingX = adx
                gs.lastFacingY = ady

                // Force attack request: ArsenalSystem will fire whenever cooldown permits
                gs.controls.attackRequested = true
            }
        }
    }

    private fun handleTacticalAbilities(ctx: TacticalContext) {
        // 1. AUTO-SONAR: Trigger if vision is compromised
        val isDark = gs.isDarknessLevel || com.appsbyalok.echohunter.data.StoryProtocol.isBlackoutActive
        if (isDark && gs.visionClarity < 0.35f && gs.sonarTimer <= 0f) {
            gs.controls.isSonarPressed = true
        }

        // 2. AUTO-TRAPS: Deploy defensively when swarmed or critically approached
        if (gs.trapCooldownTimer <= 0f) {
            // Strategy: Use trap if someone is about to touch us, OR if 3+ enemies are in mid-range
            if (ctx.extremeThreat || ctx.enemiesNearCount >= 3) {
                gs.controls.trapRequested = true
            }
        }
    }

    private data class ScoredTarget(val tx: Int, val ty: Int, val score: Float)

    /**
     * Strategic Target Evaluator: Assigns scores to all potential targets (POI)
     * and returns the coordinates of the highest scoring one.
     */
    private fun analyzeStrategicTarget(): Pair<Int, Int> {
        val grid = gs.gridMap ?: return Pair(0, 0)
        val w = grid.size
        val h = grid[0].size
        val ts = gs.tileSize
        val currentConfig = LevelEngine.getLevelConfig(gs.currentLevel)
        val targets = mutableListOf<ScoredTarget>()

        // 1. BASE OBJECTIVE (Exit/Core Node)
        var objX = w / 2
        var objY = h / 2
        for (x in 0 until w) for (y in 0 until h) if (grid[x][y] == 2) {
            objX = x; objY = y; break
        }

        // Base score for the exit/main goal.
        targets.add(ScoredTarget(objX, objY, 10f))

        // 2. CORE DEFENSE (Highest Priority if Defense Feature is active)
        val isDefense = currentConfig.features.contains(LevelFeature.DEFENSE)
        val coreWorldX = objX * ts + (ts / 2f)
        val coreWorldY = objY * ts + (ts / 2f)

        // 3. ENEMY THREATS
        for (i in 0 until enemySys.n) {
            if (enemySys.vis[i] > 0.05f) {
                val edx = enemySys.ex[i] - gs.px
                val edy = enemySys.ey[i] - gs.py
                val distToPlayer = sqrt(edx * edx + edy * edy)

                var enemyScore = 0f

                // Threat to Core (Defense Mode)
                if (isDefense) {
                    val cdx = enemySys.ex[i] - coreWorldX
                    val cdy = enemySys.ey[i] - coreWorldY
                    val distToCore = sqrt(cdx * cdx + cdy * cdy)
                    if (distToCore < ts * 8f) {
                        enemyScore += (10f - (distToCore / ts)) * 15f // Higher weight for core protection
                    }
                }

                // Threat to Player (Engagement)
                if (distToPlayer < ts * 10f) {
                    enemyScore += (10f - (distToPlayer / ts)) * 5f
                }

                if (enemyScore > 0) {
                    // Calculate standoff position if we are engaging an enemy
                    val preferredRange = when (gs.controls.currentWeapon) {
                        2 -> ts * 5.5f // Sniper
                        1 -> ts * 1.6f // Shotgun
                        else -> ts * 3.0f
                    }
                    val distance = distToPlayer.coerceAtLeast(1f)
                    val awayX = (gs.px - enemySys.ex[i]) / distance
                    val awayY = (gs.py - enemySys.ey[i]) / distance
                    val sx = (enemySys.ex[i] + awayX * preferredRange).coerceIn(
                        ts / 2f, w * ts - ts / 2f
                    )
                    val sy = (enemySys.ey[i] + awayY * preferredRange).coerceIn(
                        ts / 2f, h * ts - ts / 2f
                    )

                    targets.add(ScoredTarget((sx / ts).toInt(), (sy / ts).toInt(), enemyScore))
                }
            }
        }

        // 4. ECONOMY (Powerups)
        for (i in 0 until enemySys.pwn) {
            if (enemySys.pwActive[i]) {
                val pdx = enemySys.pwX[i] - gs.px
                val pdy = enemySys.pwY[i] - gs.py
                val dist = sqrt(pdx * pdx + pdy * pdy)
                if (dist < ts * 12f) {
                    targets.add(
                        ScoredTarget(
                            (enemySys.pwX[i] / ts).toInt(),
                            (enemySys.pwY[i] / ts).toInt(),
                            (12f - (dist / ts)) * 2f
                        )
                    )
                }
            }
        }

        // 5. PURGE TARGETS (Spawners in Clean Sweep)
        if (currentConfig.features.contains(LevelFeature.CLEAN_SWEEP)) {
            for (node in gs.spawnerNodes) {
                if (node.state != SpawnState.DESTROYED && node.visibility > 0.1f) {
                    val sdx = node.x - gs.px
                    val sdy = node.y - gs.py
                    val dist = sqrt(sdx * sdx + sdy * sdy)
                    targets.add(
                        ScoredTarget(
                            (node.x / ts).toInt(),
                            (node.y / ts).toInt(),
                            (15f - (dist / ts).coerceAtMost(10f)) * 4f
                        )
                    )
                }
            }
        }

        // 6. BOMB SITE (Priority if BOMB Feature is active)
        if (currentConfig.features.contains(LevelFeature.BOMB) && gs.bombTargetX > -5000f) {
            val bdx = gs.bombTargetX - gs.px
            val bdy = gs.bombTargetY - gs.py
            val dist = sqrt(bdx * bdx + bdy * bdy)

            // Logic: High priority to reach the plant site, then it becomes a defense point
            val isPlanted =
                gs.objectiveLabel.contains("DETONATION") || gs.objectiveLabel.contains("FAILURE")
            if (!isPlanted) {
                targets.add(
                    ScoredTarget(
                        (gs.bombTargetX / ts).toInt(),
                        (gs.bombTargetY / ts).toInt(),
                        (20f - (dist / ts).coerceAtMost(15f)) * 5f
                    )
                )
            } else {
                // Once planted, defend the site similarly to the Core in Defense mode
                targets.add(
                    ScoredTarget(
                        (gs.bombTargetX / ts).toInt(), (gs.bombTargetY / ts).toInt(), 12f
                    )
                )
            }
        }

        // 7. ELIMINATION TARGETS (HVTs)
        if (currentConfig.features.contains(LevelFeature.ELIMINATION)) {
            for (i in 0 until enemySys.n) {
                if (enemySys.type[i] == 3 && enemySys.vis[i] > 0.05f) { // Type 3 is HVT
                    val hdx = enemySys.ex[i] - gs.px
                    val hdy = enemySys.ey[i] - gs.py
                    val dist = sqrt(hdx * hdx + hdy * hdy)
                    targets.add(
                        ScoredTarget(
                            (enemySys.ex[i] / ts).toInt(),
                            (enemySys.ey[i] / ts).toInt(),
                            (15f - (dist / ts).coerceAtMost(10f)) * 8f
                        )
                    )
                }
            }
        }

        // Pick the target with the highest score
        val best = targets.maxByOrNull { it.score } ?: ScoredTarget(objX, objY, 0f)
        return Pair(best.tx.coerceIn(0, w - 1), best.ty.coerceIn(0, h - 1))
    }

    /**
     * Steers along pathing vectors while actively blending an Evasion Vector
     * if threats compromise personal boundaries.
     */
    private fun steerWithTacticalKiting(dt: Float, scale: Float, ctx: TacticalContext) {
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
            val nx = cx + dirsX[d]
            val ny = cy + dirsY[d]
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

        // 2. Vector Blending Core Calculation (Using ctx for performance)
        var finalDirX = pathDirX
        var finalDirY = pathDirY

        // WEAPON-AWARE KITING: Snipers stay further back, Shotguns get closer
        val kiteTriggerRadius = when (gs.controls.currentWeapon) {
            2 -> ts * 6.5f // Sniper: High distance
            1 -> ts * 1.8f // Shotgun: Close quarters
            else -> ts * 3.2f // Standard
        }

        var isEmergencyFleeing = false
        val closestEnemyIdx = ctx.closestEnemyIdx
        val minEnemyDistSq = ctx.closestEnemyDistSq

        if (closestEnemyIdx != -1 && minEnemyDistSq < (kiteTriggerRadius * kiteTriggerRadius)) {
            val currentEnemyDist = sqrt(minEnemyDistSq)

            if (currentEnemyDist > 0f) {
                // Evasion vector points directly *away* from the immediate threat
                val fleeDirX = (gs.px - enemySys.ex[closestEnemyIdx]) / currentEnemyDist
                val fleeDirY = (gs.py - enemySys.ey[closestEnemyIdx]) / currentEnemyDist

                // At close range, fleeing must override the path target rather than blending into it.
                val proximityFactor = 1.0f - (currentEnemyDist / kiteTriggerRadius).coerceIn(0f, 1f)
                val fleeWeight = 0.40f + proximityFactor * 0.55f
                val pathWeight = 1.0f - fleeWeight

                finalDirX = (pathDirX * pathWeight) + (fleeDirX * fleeWeight)
                finalDirY = (pathDirY * pathWeight) + (fleeDirY * fleeWeight)
                if (currentEnemyDist < ts * 1.35f) {
                    // Inject slight jitter during emergency flee to prevent getting pinned in corners
                    finalDirX = fleeDirX + (Random.nextFloat() - 0.5f) * 0.3f
                    finalDirY = fleeDirY + (Random.nextFloat() - 0.5f) * 0.3f
                    isEmergencyFleeing = true
                }
            }
        }

        // 3. Finalizing Movement Directives
        val finalMag = sqrt(finalDirX * finalDirX + finalDirY * finalDirY)
        if (finalMag > 0f && (pathDist > scale * 0.02f || isEmergencyFleeing)) {
            var dirX = finalDirX / finalMag
            var dirY = finalDirY / finalMag

            // Frame check stall verifications
            val framesMoved =
                sqrt((gs.px - lastPx) * (gs.px - lastPx) + (gs.py - lastPy) * (gs.py - lastPy))
            if (framesMoved < scale * 0.04f * dt) stuckTimer += dt else stuckTimer = 0f

            if (stuckTimer > 0.5f) { // FIX: Increased from 0.12f to 0.5f to prevent corner jitter
                dirX += (Random.nextFloat() - 0.5f) * 2f
                dirY += (Random.nextFloat() - 0.5f) * 2f
                val length = sqrt(dirX * dirX + dirY * dirY)
                if (length > 0) {
                    dirX /= length; dirY /= length
                }
            }

            gs.controls.moveDirX = dirX
            gs.controls.moveDirY = dirY

            // If we are NOT in combat (handled by handleCombatAI), face the movement direction
            val ts8Sq = (ts * 8f) * (ts * 8f)
            if (closestEnemyIdx == -1 || minEnemyDistSq >= ts8Sq) {
                gs.lastFacingX = dirX
                gs.lastFacingY = dirY
            }
        } else {
            gs.controls.moveDirX = 0f
            gs.controls.moveDirY = 0f
        }

        lastPx = gs.px
        lastPy = gs.py
    }

    private fun computePlayerHeatMap(tx: Int, ty: Int) {
        val grid = gs.gridMap ?: return
        val w = grid.size
        val h = grid[0].size

        if (playerHeatMap == null || playerHeatMap!!.size != w || playerHeatMap!![0].size != h) {
            playerHeatMap = Array(w) { IntArray(h) }
        }

        for (x in 0 until w) {
            for (y in 0 until h) {
                playerHeatMap!![x][y] = 9999
            }
        }

        var head = 0
        var tail = 0
        val targetX = tx.coerceIn(0, w - 1)
        val targetY = ty.coerceIn(0, h - 1)

        qX[tail] = targetX; qY[tail] = targetY; tail++
        playerHeatMap!![targetX][targetY] = 0

        val dirsX = intArrayOf(0, 0, -1, 1)
        val dirsY = intArrayOf(-1, 1, 0, 0)

        while (head < tail) {
            val cx = qX[head]
            val cy = qY[head]; head++
            val dist = playerHeatMap!![cx][cy]

            for (d in 0..3) {
                val nx = cx + dirsX[d]
                val ny = cy + dirsY[d]
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
        gs.controls.moveDirX = 0f; gs.controls.moveDirY = 0f
        gs.controls.isAutoSonarLocked = false
    }
}
