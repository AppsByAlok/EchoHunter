package com.appsbyalok.echohunter.systems

import com.appsbyalok.echohunter.engine.GameState
import kotlin.math.sqrt
import kotlin.random.Random

class PlayerAI(private val gs: GameState, private val enemySys: EnemySystem) {

    private var stuckTimer = 0f
    private var lastPx = 0f
    private var lastPy = 0f
    private var decisionTimer = 0f
    private var cachedTargetX = 0f
    private var cachedTargetY = 0f

    fun update(dt: Float, scale: Float) {
        if (!gs.isAutoPilotActive) return

        if (gs.autoPilotTimer > 0f) {
            gs.autoPilotTimer -= dt
            if (gs.autoPilotTimer <= 0f) {
                gs.isAutoPilotActive = false
                gs.joyDirX = 0f; gs.joyDirY = 0f
                gs.isAutoFireLocked = false
                gs.isAutoSonarLocked = false
                return
            }
        }

        // Auto combat locks
        gs.isAutoFireLocked = true
        gs.isAutoSonarLocked = true

        decisionTimer -= dt
        if (decisionTimer <= 0f) {
            findTarget()
            decisionTimer = 0.2f // Update target 5 times a second
        }

        val dx = cachedTargetX - gs.px
        val dy = cachedTargetY - gs.py
        val dist = sqrt(dx * dx + dy * dy)

        if (dist > scale * 0.05f) {
            var dirX = dx / dist
            var dirY = dy / dist

            val moveDist = sqrt((gs.px - lastPx) * (gs.px - lastPx) + (gs.py - lastPy) * (gs.py - lastPy))
            if (moveDist < scale * 0.05f * dt) stuckTimer += dt else stuckTimer = 0f

            // Anti-Stuck Random Jiggle
            if (stuckTimer > 0.15f) {
                dirX += (Random.nextFloat() - 0.5f) * 3f
                dirY += (Random.nextFloat() - 0.5f) * 3f
                val nDist = sqrt(dirX*dirX + dirY*dirY)
                dirX /= nDist; dirY /= nDist
            }

            gs.joyDirX = dirX
            gs.joyDirY = dirY
            gs.lastFacingX = dirX
            gs.lastFacingY = dirY
        } else {
            gs.joyDirX = 0f
            gs.joyDirY = 0f
        }

        lastPx = gs.px
        lastPy = gs.py
    }

    private fun findTarget() {
        var foundDest = false
        cachedTargetX = gs.px; cachedTargetY = gs.py

        // 1. Prioritize Target Core (If alive)
        if (gs.gridMap != null) {
            val w = gs.gridMap!!.size
            val h = gs.gridMap!![0].size
            for (x in 0 until w) {
                for (y in 0 until h) {
                    if (gs.gridMap!![x][y] == 2) {
                        cachedTargetX = x * gs.tileSize + gs.tileSize / 2f
                        cachedTargetY = y * gs.tileSize + gs.tileSize / 2f
                        foundDest = true
                        break
                    }
                }
                if (foundDest) break
            }
        }

        // 2. Chase enemies if they are near
        if (!foundDest || Random.nextFloat() > 0.5f) {
            var closestDist = Float.MAX_VALUE
            for (i in 0 until enemySys.n) {
                if (enemySys.vis[i] > 0.05f) {
                    val dx = enemySys.ex[i] - gs.px
                    val dy = enemySys.ey[i] - gs.py
                    val distSq = dx*dx + dy*dy
                    if (distSq < closestDist) {
                        closestDist = distSq
                        cachedTargetX = enemySys.ex[i]
                        cachedTargetY = enemySys.ey[i]
                    }
                }
            }

            // 3. Chase Powerups
            for (i in 0 until enemySys.pwn) {
                if (enemySys.pwActive[i]) {
                    val dx = enemySys.pwX[i] - gs.px
                    val dy = enemySys.pwY[i] - gs.py
                    val distSq = dx*dx + dy*dy
                    if (distSq < closestDist) {
                        closestDist = distSq
                        cachedTargetX = enemySys.pwX[i]
                        cachedTargetY = enemySys.pwY[i]
                    }
                }
            }
        }
    }
}