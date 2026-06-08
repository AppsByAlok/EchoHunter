package com.appsbyalok.echohunter.systems

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.GameColors
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class EnemySystem {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val entityPath = Path()

    val ai = EnemyAI()
    private var heatTimer = 0f

    val n = 25
    val enemyBrains = Array<IEnemyBehavior>(n) { PatrolBehavior } // MODULAR BRAIN ARRAY

    val ex = FloatArray(n)
    val ey = FloatArray(n)
    val evx = FloatArray(n)
    val evy = FloatArray(n)
    val vis = FloatArray(n)
    val type = IntArray(n)
    val eState = IntArray(n)
    val investigateTimer = FloatArray(n)
    val invX = FloatArray(n)
    val invY = FloatArray(n)

    val pwn = 4
    val pwX = FloatArray(pwn)
    val pwY = FloatArray(pwn)
    val pwType = IntArray(pwn)
    val pwVis = FloatArray(pwn)
    val pwActive = BooleanArray(pwn)
    private val puIcons = arrayOf("+", "V", "S")

    fun respawnAll(gs: GameState, width: Float, height: Float) {
        for (i in 0 until pwn) pwActive[i] = false
        val isDefense = gs.coreRadius > 0f && gs.activeObjective is com.appsbyalok.echohunter.modes.DefenseObjective

        for (i in 0 until n) {
            if (isDefense) {
                ex[i] = -9999f
                ey[i] = -9999f
            } else {
                spawn(i, gs, width, height)
            }
        }
    }

    fun killEnemy(i: Int, gs: GameState, width: Float, height: Float) {
        if (ex[i] < -1000f) return
        ex[i] = -9999f
        ey[i] = -9999f

        if (gs.activeObjective is com.appsbyalok.echohunter.modes.DefenseObjective) {
            gs.defEnemiesAlive--
            if (gs.defEnemiesAlive < 0) gs.defEnemiesAlive = 0
        } else {
            spawn(i, gs, width, height)
        }
    }

    fun spawn(i: Int, gs: GameState, width: Float, height: Float) {
        val scale = min(width, height)
        val isSwarm = gs.bossActive && (gs.bossType == 1 || gs.bossType == 4)

        if (gs.gridMap != null) {
            val w = gs.gridMap!!.size; val h = gs.gridMap!![0].size
            var placed = false
            val pCol = (gs.px / gs.tileSize).toInt().coerceIn(1, w - 2)
            val pRow = (gs.py / gs.tileSize).toInt().coerceIn(1, h - 2)

            for (attempt in 0..100) {
                val rx = pCol + Random.nextInt(-12, 13)
                val ry = pRow + Random.nextInt(-12, 13)

                if (rx in 1 until w - 1 && ry in 1 until h - 1 && gs.gridMap!![rx][ry] != 1) {
                    val tryX = rx * gs.tileSize + gs.tileSize / 2f
                    val tryY = ry * gs.tileSize + gs.tileSize / 2f
                    val dx = tryX - gs.px; val dy = tryY - gs.py

                    if (dx * dx + dy * dy > (scale * 0.5f) * (scale * 0.5f) || gs.timeSinceStart == 0f) {
                        ex[i] = tryX; ey[i] = tryY
                        placed = true
                        break
                    }
                }
            }
            if (!placed) { ex[i] = gs.px; ey[i] = gs.py }
        } else {
            val spawnPos = gs.modeStrategy.getEnemySpawnPosition(gs, width, height, scale)
            ex[i] = spawnPos.first; ey[i] = spawnPos.second
        }

        val diffSpeedMult = if (gs.difficulty == 0) 0.65f else 1.0f
        val speedMult = when (gs.gameMode) {
            0 -> 0.4f + (gs.wave * 0.1f)
            1 -> 0.5f + (gs.currentSector * 0.15f)
            else -> 1f + (gs.score * 0.005f)
        } * diffSpeedMult

        val sp = (scale * 0.3f * speedMult) + Random.nextFloat() * (scale * 0.2f)
        val angle = Random.nextFloat() * 6.28f
        evx[i] = cos(angle) * sp; evy[i] = sin(angle) * sp

        val config = com.appsbyalok.echohunter.data.LevelEngine.getLevelConfig(gs.currentLevel)
        val isElimination = config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.ELIMINATION)
        val isDefense = gs.activeObjective is com.appsbyalok.echohunter.modes.DefenseObjective
        val hunterProb = if (gs.score < 10) 0.0f else min(0.65f, (gs.score - 10) * 0.015f)

        // --- BRAIN ASSIGNMENT ---
        if (isSwarm) {
            type[i] = 1; enemyBrains[i] = HunterBehavior
        } else if (isElimination && i < 5) {
            type[i] = 3; enemyBrains[i] = PatrolBehavior
        } else if (isDefense) {
            type[i] = 2; enemyBrains[i] = KamikazeBehavior
        } else {
            type[i] = if (Random.nextFloat() < hunterProb) 1 else 0
            enemyBrains[i] = if (type[i] == 1) HunterBehavior else PatrolBehavior
        }

        vis[i] = 1f; eState[i] = 0; investigateTimer[i] = 0f
    }

    fun updateEnemies(dt: Float, gs: GameState, width: Float, height: Float, scale: Float) {
        gs.isEnemyNear = false
        gs.isEnemyVeryNear = false

        if (gs.gridMap != null) {
            heatTimer -= dt
            if (heatTimer <= 0f) {
                ai.updatePlayerHeatMap(gs)
                if (gs.coreRadius > 0f) ai.updateCoreHeatMap(gs)
                heatTimer = 0.5f
            }
        }

        val enemyRadius = scale * 0.02f

        for (i in 0 until n) {
            if (ex[i] < -1000f) continue
            val nextEx = ex[i] + evx[i] * dt
            if (!isCollidingWithWall(nextEx, ey[i], enemyRadius, gs)) ex[i] = nextEx
            else evx[i] = -evx[i]

            val nextEy = ey[i] + evy[i] * dt
            if (!isCollidingWithWall(ex[i], nextEy, enemyRadius, gs)) ey[i] = nextEy
            else evy[i] = -evy[i]

            // DELEGATE TO MODULAR AI BRAIN
            enemyBrains[i].updateBehavior(i, dt, gs, this, ai, width, height, scale)

            val d2 = (gs.px - ex[i]) * (gs.px - ex[i]) + (gs.py - ey[i]) * (gs.py - ey[i])
            val hitByPulse = (gs.pulse && d2 in gs.innerRSq..gs.outerRSq)

            if (type[i] == 1) {
                val hitDistSq = (scale * 0.045f) * (scale * 0.045f)
                if (d2 < hitDistSq * 15f) gs.isEnemyNear = true
                if (d2 < hitDistSq * 4f) gs.isEnemyVeryNear = true
            }

            if (hitByPulse || d2 < gs.passiveAuraRadiusSq) vis[i] = 1f
            vis[i] *= gs.fadeMultiplier

            val maxAllowedDistSq = if (gs.bossActive || gs.coreRadius > 0f) (width * 1.5f) * (width * 1.5f) else (width * 4.0f) * (width * 4.0f)
            if (d2 > maxAllowedDistSq) spawn(i, gs, width, height)
        }
        gs.globalSonarAlert = false
        gs.localAttackAlert = false
    }

    private fun isCollidingWithWall(cx: Float, cy: Float, radius: Float, gs: GameState): Boolean {
        val grid = gs.gridMap ?: return false
        val ts = gs.tileSize
        val hitbox = radius * 0.8f

        val left = ((cx - hitbox) / ts).toInt(); val right = ((cx + hitbox) / ts).toInt()
        val top = ((cy - hitbox) / ts).toInt(); val bottom = ((cy + hitbox) / ts).toInt()

        for (x in left..right) {
            for (y in top..bottom) {
                if (x in grid.indices && y in grid[0].indices) {
                    if (grid[x][y] == 1) return true
                } else return true
            }
        }
        return false
    }

    fun spawnSwarmIfNeeded(gs: GameState, width: Float, height: Float) {
        if (gs.bossType == 1 || gs.bossType == 4) {
            for (i in 0 until n) spawn(i, gs, width, height)
        }
    }

    fun updateBoss(dt: Float, gs: GameState, scale: Float) {
        if (!gs.bossActive) return

        val behavior = when (gs.bossType) {
            1 -> GuardianBossBehavior
            2 -> StalkerBossBehavior
            3 -> GlitchBossBehavior
            4 -> OmegaBossBehavior
            else -> GuardianBossBehavior // Type 0 is also Guardian
        }

        val bdx = gs.px - gs.bossX; val bdy = gs.py - gs.bossY
        val bDistSq = bdx * bdx + bdy * bdy
        val bDist = sqrt(bDistSq)

        val bSpeed = scale * (if (gs.bossType == 3 || gs.bossType == 4) 0.6f else 0.3f) * (if (gs.difficulty == 0) 0.7f else 1.0f) * (if (gs.isBossRage) 2.0f else 1.0f)

        if (bDist > 0f) {
            val (vx, vy) = if (gs.gridMap != null) {
                ai.steerByPlayerHeatMap(gs.bossX, gs.bossY, 0f, 0f, bSpeed * 5f, gs)
            } else {
                Pair((bdx / bDist) * bSpeed, (bdy / bDist) * bSpeed)
            }

            val (finalVx, finalVy) = behavior.applyMovementPattern(vx, vy, dt, gs, scale)

            val bossRadius = scale * 0.08f
            val nextBx = gs.bossX + finalVx * dt
            if (!isCollidingWithWall(nextBx, gs.bossY, bossRadius * 0.8f, gs)) gs.bossX = nextBx
            val nextBy = gs.bossY + finalVy * dt
            if (!isCollidingWithWall(gs.bossX, nextBy, bossRadius * 0.8f, gs)) gs.bossY = nextBy
        }

        behavior.updateSpecial(dt, gs, this, scale)

        // Visibility handling
        if (gs.difficulty == 1) {
            if ((gs.pulse && bDistSq in gs.innerRSq..gs.outerRSq) || bDistSq < gs.passiveAuraRadiusSq) gs.bossVis = 1.0f
            gs.bossVis *= gs.fadeMultiplier
            if (gs.bossVis < 0.05f) gs.bossVis = 0.05f
        } else {
            gs.bossVis = 1.0f
        }
    }

    fun updatePowerups(dt: Float, gs: GameState, width: Float, height: Float) {
        val puDropRate = if (gs.difficulty == 0) 0.004f else 0.002f
        if (Random.nextFloat() < puDropRate * dt * 60f && gs.score > 15 && !gs.bossActive) {
            for (i in 0 until pwn) {
                if (!pwActive[i]) {
                    pwX[i] = gs.cameraX + width * 0.2f + Random.nextFloat() * width * 0.8f
                    pwY[i] = gs.cameraY + Random.nextFloat() * height
                    pwType[i] = Random.nextInt(3)
                    pwActive[i] = true
                    pwVis[i] = 0f
                    break
                }
            }
        }
    }

    fun drawEntities(c: Canvas, gs: GameState, width: Float, scale: Float) {
        val entityRadius = scale * 0.03f
        val baseVisProgression = max(0f, 0.20f - (gs.timeSinceStart * 0.002f))

        for (i in 0 until pwn) {
            if (pwActive[i]) {
                val dx = pwX[i] - gs.px
                val dy = pwY[i] - gs.py
                val d2 = dx * dx + dy * dy

                if ((gs.pulse && d2 in gs.innerRSq..gs.outerRSq) || d2 < gs.passiveAuraRadiusSq) pwVis[i] = 1f
                pwVis[i] *= gs.fadeMultiplier

                val screenPwX = pwX[i] - gs.cameraX
                val screenPwY = pwY[i] - gs.cameraY

                if (screenPwX < -scale || screenPwX > width + scale) pwActive[i] = false

                val effectivePwVis = max(0.15f, pwVis[i])

                if (effectivePwVis > 0.02f) {
                    p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.005f
                    p.color = ((effectivePwVis * 255).toInt() shl 24) or (when (pwType[i]) {
                        0 -> GameColors.HP; 1 -> GameColors.CLARITY; else -> GameColors.SHIELD
                    } and 0xFFFFFF)

                    c.drawCircle(screenPwX, screenPwY, entityRadius * 0.8f, p)

                    pText.color = GameColors.BG
                    pText.textSize = scale * 0.04f
                    c.drawText(puIcons[pwType[i]], screenPwX, screenPwY + scale * 0.012f, pText)
                }
            }
        }

        for (i in 0 until n) {
            val screenEx = ex[i] - gs.cameraX
            val screenEy = ey[i] - gs.cameraY
            val effectiveVis = max(baseVisProgression, vis[i])

            if (effectiveVis > 0.02f) {
                val a = (effectiveVis * 255).toInt()
                val entitySize = entityRadius * (if (type[i] == 2) 1.2f else 0.8f)

                p.style = Paint.Style.FILL

                when (type[i]) {
                    1 -> {
                        p.color = (a shl 24) or (GameColors.RED and 0xFFFFFF)
                        c.drawRect(screenEx - entitySize, screenEy - entitySize, screenEx + entitySize, screenEy + entitySize, p)
                        p.color = (a shl 24) or (GameColors.BG and 0xFFFFFF)
                        c.drawRect(screenEx - entitySize / 2f, screenEy - entitySize / 2f, screenEx + entitySize / 2f, screenEy + entitySize / 2f, p)

                        if (eState[i] == 2 || eState[i] == 1) {
                            pText.color = (a shl 24) or 0xFFFFFF
                            pText.textSize = scale * 0.03f
                            c.drawText("!", screenEx, screenEy - entitySize - scale * 0.01f, pText)
                        }
                    }
                    2 -> {
                        p.color = (a shl 24) or (GameColors.COOLANT and 0xFFFFFF)
                        entityPath.reset()
                        entityPath.moveTo(screenEx, screenEy - entitySize)
                        entityPath.lineTo(screenEx + entitySize, screenEy)
                        entityPath.lineTo(screenEx, screenEy + entitySize)
                        entityPath.lineTo(screenEx - entitySize, screenEy)
                        entityPath.close()
                        c.drawPath(entityPath, p)
                        p.color = (a shl 24) or (GameColors.CLARITY and 0xFFFFFF)
                        c.drawCircle(screenEx, screenEy, entityRadius * 0.3f, p)
                    }
                    3 -> {
                        // --- NAYA: ELIMINATION TARGET (HVT) ---
                        // Bright Red Bada Circle
                        p.color = (a shl 24) or (0xFFFF2A4D.toInt() and 0xFFFFFF)
                        c.drawCircle(screenEx, screenEy, entitySize, p)

                        // Andar ek chota black circle (Holo-look)
                        p.color = (a shl 24) or (GameColors.BG and 0xFFFFFF)
                        c.drawCircle(screenEx, screenEy, entitySize * 0.5f, p)

                        // Upar "TARGET" likha hoga
                        pText.color = (a shl 24) or (0xFFFF2A4D.toInt() and 0xFFFFFF)
                        pText.textSize = scale * 0.025f
                        c.drawText("TARGET", screenEx, screenEy - entitySize * 1.5f, pText)
                    }
                    else -> {
                        p.color = (a shl 24) or (GameColors.YELLOW and 0xFFFFFF)
                        entityPath.reset()
                        entityPath.moveTo(screenEx, screenEy - entitySize)
                        entityPath.lineTo(screenEx + entitySize, screenEy)
                        entityPath.lineTo(screenEx, screenEy + entitySize)
                        entityPath.lineTo(screenEx - entitySize, screenEy)
                        entityPath.close()
                        c.drawPath(entityPath, p)

                        // --- SUSPICIOUS QUESTION MARK ---
                        if (eState[i] == 2) {
                            pText.color = (a shl 24) or 0xFFFFFF
                            pText.textSize = scale * 0.035f
                            c.drawText("?", screenEx, screenEy - entitySize - scale * 0.01f, pText)
                        }
                    }
                }
            }
        }

        if (gs.bossActive) {
            val bossRadius = scale * 0.08f
            val screenBx = gs.bossX - gs.cameraX
            val screenBy = gs.bossY - gs.cameraY
            val bossAlpha = (gs.bossVis * 255).toInt()

            p.style = Paint.Style.FILL
            val pulseOffset = sin(gs.timeSinceStart * 15f) * (scale * 0.015f)

            p.color = (bossAlpha shl 24) or (GameColors.BOSS and 0xFFFFFF)
            c.drawCircle(screenBx, screenBy, bossRadius + pulseOffset, p)

            p.color = if (gs.bossIframe > 0f) (bossAlpha shl 24) or 0xFFFFFF else (bossAlpha shl 24) or (GameColors.RED and 0xFFFFFF)
            c.drawCircle(screenBx, screenBy, bossRadius * 0.5f, p)

            p.color = (bossAlpha shl 24) or (GameColors.RED and 0xFFFFFF)
            c.drawRect(screenBx - bossRadius, screenBy - bossRadius - scale * 0.02f, screenBx + bossRadius, screenBy - bossRadius - scale * 0.01f, p)

            p.color = (bossAlpha shl 24) or (GameColors.HP and 0xFFFFFF)
            val hpWidth = (bossRadius * 2f) * (gs.bossHp.toFloat() / gs.bossMaxHp)
            c.drawRect(screenBx - bossRadius, screenBy - bossRadius - scale * 0.02f, screenBx - bossRadius + hpWidth, screenBy - bossRadius - scale * 0.01f, p)
        }
    }
}