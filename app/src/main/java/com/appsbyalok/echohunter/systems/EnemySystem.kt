package com.appsbyalok.echohunter.systems

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.media.ToneGenerator
import com.appsbyalok.echohunter.R
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.EchoAudioManager
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

    // AI Module Link
    private val ai = EnemyAI()
    private var heatTimer = 0f

    val n = 25
    val ex = FloatArray(n)
    val ey = FloatArray(n)
    val evx = FloatArray(n)
    val evy = FloatArray(n)
    val vis = FloatArray(n)
    val type = IntArray(n)
    val eState = IntArray(n) // 0=Patrol, 1=Alert, 2=Hunt/Investigate
    val investigateTimer = FloatArray(n)

    val pwn = 4
    val pwX = FloatArray(pwn)
    val pwY = FloatArray(pwn)
    val pwType = IntArray(pwn)
    val pwVis = FloatArray(pwn)
    val pwActive = BooleanArray(pwn)
    private val puIcons = arrayOf("+", "V", "S")

    fun respawnAll(gs: GameState, width: Float, height: Float) {
        for (i in 0 until pwn) pwActive[i] = false
        for (i in 0 until n) spawn(i, gs, width, height)
    }

    fun spawn(i: Int, gs: GameState, width: Float, height: Float) {
        val scale = min(width, height)
        val isSwarm = gs.bossActive && (gs.bossType == 1 || gs.bossType == 4)

        // --- FIX 1: STRICT PATH SPAWNING FOR ENEMIES ---
        if (gs.gridMap != null) {
            val w = gs.gridMap!!.size
            val h = gs.gridMap!![0].size
            var placed = false

            // Find a safe path near the player
            val pCol = (gs.px / gs.tileSize).toInt().coerceIn(1, w - 2)
            val pRow = (gs.py / gs.tileSize).toInt().coerceIn(1, h - 2)

            for (attempt in 0..100) {
                val rx = pCol + Random.nextInt(-12, 13)
                val ry = pRow + Random.nextInt(-12, 13)

                // != 1 means it's not a wall (can be path, dest, or guard spawn)
                if (rx in 1 until w - 1 && ry in 1 until h - 1 && gs.gridMap!![rx][ry] != 1) {
                    val tryX = rx * gs.tileSize + gs.tileSize / 2f
                    val tryY = ry * gs.tileSize + gs.tileSize / 2f

                    val dx = tryX - gs.px
                    val dy = tryY - gs.py

                    // Keep some safe distance from player
                    if (dx * dx + dy * dy > (scale * 0.5f) * (scale * 0.5f) || gs.timeSinceStart == 0f) {
                        ex[i] = tryX
                        ey[i] = tryY
                        placed = true
                        break
                    }
                }
            }

            // Fallback just in case
            if (!placed) {
                ex[i] = gs.px
                ey[i] = gs.py
            }
        } else {
            // Endless Mode Fallback
            val spawnPos = gs.modeStrategy.getEnemySpawnPosition(gs, width, height, scale)
            ex[i] = spawnPos.first
            ey[i] = spawnPos.second
        }

        val diffSpeedMult = if (gs.difficulty == 0) 0.65f else 1.0f
        val speedMult = when (gs.gameMode) {
            0 -> 0.4f + (gs.wave * 0.1f)
            1 -> 0.5f + (gs.currentSector * 0.15f)
            else -> 1f + (gs.score * 0.005f)
        } * diffSpeedMult

        val sp = (scale * 0.3f * speedMult) + Random.nextFloat() * (scale * 0.2f)

        val angle = Random.nextFloat() * 6.28f
        evx[i] = cos(angle) * sp
        evy[i] = sin(angle) * sp

        val hunterProbability = if (gs.score < 10) 0.0f else min(0.65f, (gs.score - 10) * 0.015f)

        // --- NAYA: ELIMINATION & DEFENSE MODE SPAWN LOGIC ---
        val config = com.appsbyalok.echohunter.data.LevelEngine.getLevelConfig(gs.currentLevel)
        val isElimination = config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.ELIMINATION) && gs.gameMode == 0
        val isDefense = config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.DEFENSE) && gs.gameMode == 0

        if (isSwarm) {
            type[i] = 1
        } else if (isElimination && i < 5) {
            type[i] = 3
        } else if (isDefense) {
            type[i] = 1
        } else if (gs.gameMode == 2) {
            type[i] = if (Random.nextFloat() < hunterProbability) 1 else if (Random.nextFloat() > 0.7f) 2 else 0
        } else {
            type[i] = if (Random.nextFloat() < hunterProbability) 1 else 0
        }

        vis[i] = 1f
        eState[i] = 0
        investigateTimer[i] = 0f
    }

    fun updateEnemies(dt: Float, gs: GameState, width: Float, height: Float, scale: Float) {
        gs.isEnemyNear = false
        gs.isEnemyVeryNear = false

        // --- NAYA FIX: config ko function ke start me declare karo ---
        val config = com.appsbyalok.echohunter.data.LevelEngine.getLevelConfig(gs.currentLevel)
        val isDefense = config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.DEFENSE) && gs.gameMode == 0

        val hitDistSq = (scale * 0.045f) * (scale * 0.045f)
        val speed = scale * (if (gs.difficulty == 0) 0.25f else 0.4f)

        if (gs.gridMap != null) {
            heatTimer -= dt
            if (heatTimer <= 0f) {
                ai.updateHeatMap(gs)
                heatTimer = 0.5f
            }
        }

        val enemyRadius = scale * 0.02f

        for (i in 0 until n) {
            val nextEx = ex[i] + evx[i] * dt
            if (!isCollidingWithWall(nextEx, ey[i], enemyRadius, gs)) {
                ex[i] = nextEx
            } else {
                evx[i] = -evx[i]
            }

            val nextEy = ey[i] + evy[i] * dt
            if (!isCollidingWithWall(ex[i], nextEy, enemyRadius, gs)) {
                ey[i] = nextEy
            } else {
                evy[i] = -evy[i]
            }

            // 1. TARGET FIX: Defense mode mein sab Core par attack karenge!
            val targetX = if (isDefense) gs.coreX else if (gs.isDecoyActive) gs.decoyX else gs.px
            val targetY = if (isDefense) gs.coreY else if (gs.isDecoyActive) gs.decoyY else gs.py
            val tdx = targetX - ex[i]
            val tdy = targetY - ey[i]
            val td2 = tdx * tdx + tdy * tdy

            val d2 = (gs.px - ex[i]) * (gs.px - ex[i]) + (gs.py - ey[i]) * (gs.py - ey[i])

            val hitByPulse = (gs.pulse && d2 in gs.innerRSq..gs.outerRSq)

            // --- YELLOW PATROLS INVESTIGATE LOGIC ---
            if (type[i] == 0) {
                if (hitByPulse || (gs.localAttackAlert && td2 < (scale * 1.5f) * (scale * 1.5f))) {
                    eState[i] = 2 // 2 = Suspicious/Investigate
                    investigateTimer[i] = 3.0f
                }

                if (eState[i] == 2) {
                    investigateTimer[i] -= dt

                    if (investigateTimer[i] <= 0f) {
                        eState[i] = 0
                    } else {
                        if (gs.gridMap != null) {
                            val (nvx, nvy) = ai.steerByHeatMap(ex[i], ey[i], evx[i], evy[i], speed * 0.8f, gs)
                            evx[i] = nvx; evy[i] = nvy
                        }

                        // THE TWIST: Agar investigate time enemy in rang to (Line of sight!)
                        val inLoS = ai.hasLineOfSight(ex[i], ey[i], targetX, targetY, gs)
                        if (inLoS && td2 < hitDistSq * 50f) {
                            type[i] = 1 // Ab ban gaya ye permanent Red Hunter!
                            eState[i] = 1
                            EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_ALERT, 50)
                        }
                    }
                }
            }

            // Red Hunters Tracking
            if (type[i] == 1) {
                if (hitByPulse) {
                    eState[i] = 2
                } else if (gs.localAttackAlert && td2 < (scale * 1.5f) * (scale * 1.5f)) {
                    eState[i] = 2
                }

                val inLoS = ai.hasLineOfSight(ex[i], ey[i], targetX, targetY, gs)

                if (inLoS && td2 < hitDistSq * 50f) {
                    eState[i] = 1
                    val eDist = kotlin.math.sqrt(td2)
                    val chaseSpeed = speed * (if (gs.isOverclocked && !gs.isDecoyActive) -0.5f else 1.2f)
                    evx[i] = (evx[i] * 0.9f) + ((tdx / eDist) * chaseSpeed * 0.1f)
                    evy[i] = (evy[i] * 0.9f) + ((tdy / eDist) * chaseSpeed * 0.1f)
                } else if (eState[i] == 1) {
                    eState[i] = 2
                }

                if (eState[i] == 2 && gs.gridMap != null) {
                    val (nvx, nvy) = ai.steerByHeatMap(ex[i], ey[i], evx[i], evy[i], speed, gs)
                    evx[i] = nvx; evy[i] = nvy
                }

                if (d2 < hitDistSq * 15f) gs.isEnemyNear = true
                if (d2 < hitDistSq * 4f) gs.isEnemyVeryNear = true
            }

            if (hitByPulse || d2 < gs.passiveAuraRadiusSq) vis[i] = 1f
            vis[i] *= gs.fadeMultiplier

            // 2. SMART ANTI-DESPAWN:
            val maxAllowedDistSq = if (isDefense || gs.bossActive) {
                (width * 1.5f) * (width * 1.5f) // Tight Leash for Arena
            } else {
                (width * 4.0f) * (width * 4.0f) // Loose Leash for Maze Explorer
            }

            if (d2 > maxAllowedDistSq) {
                spawn(i, gs, width, height)
            }
        }
        gs.globalSonarAlert = false
        gs.localAttackAlert = false
    }

    private fun isCollidingWithWall(cx: Float, cy: Float, radius: Float, gs: GameState): Boolean {
        val grid = gs.gridMap ?: return false
        val ts = gs.tileSize
        val hitbox = radius * 0.8f

        val left = ((cx - hitbox) / ts).toInt()
        val right = ((cx + hitbox) / ts).toInt()
        val top = ((cy - hitbox) / ts).toInt()
        val bottom = ((cy + hitbox) / ts).toInt()

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

        val bdx = gs.px - gs.bossX
        val bdy = gs.py - gs.bossY
        val bDistSq = bdx * bdx + bdy * bdy
        val bDist = sqrt(bDistSq)

        var bSpeed = scale * (if (gs.bossType == 3 || gs.bossType == 4) 0.6f else 0.3f) * (if (gs.difficulty == 0) 0.7f else 1.0f)
        if (gs.isBossRage) bSpeed *= 2.0f


        if (bDist > 0f) {
            var vx: Float
            var vy: Float

            // --- NAYA: BOSS USES A* HEATMAP AI ---
            if (gs.gridMap != null) {
                val (nvx, nvy) = ai.steerByHeatMap(gs.bossX, gs.bossY, 0f, 0f, bSpeed * 5f, gs)
                vx = nvx
                vy = nvy
            } else {
                vx = (bdx / bDist) * bSpeed
                vy = (bdy / bDist) * bSpeed
            }

            val rageMult = if (gs.isBossRage) 2.0f else 1.0f
            // Special Boss Movement Patterns (Dash, Jiggle, etc.)
            when (gs.bossType) {
                1 -> vy += sin(gs.timeSinceStart * (4f * rageMult)) * scale * 0.4f // Faster oscillation
                2 -> if (gs.timeSinceStart % (2.5f / rageMult) < 0.2f) { vx *= 4.5f; vy *= 4.5f } // More frequent dashes
                3 -> { vx += (Random.nextFloat() - 0.5f) * scale * 0.8f * rageMult; vy += (Random.nextFloat() - 0.5f) * scale * 0.8f * rageMult }
                4 -> { vy += cos(gs.timeSinceStart * (5f * rageMult)) * scale * 0.6f; vx += sin(gs.timeSinceStart * (3f * rageMult)) * scale * 0.3f }
            }

            // --- BOSS STRICT WALL SLIDING ---
            val bossRadius = scale * 0.08f
            val nextBx = gs.bossX + vx * dt
            if (!isCollidingWithWall(nextBx, gs.bossY, bossRadius * 0.8f, gs)) {
                gs.bossX = nextBx
            }
            val nextBy = gs.bossY + vy * dt
            if (!isCollidingWithWall(gs.bossX, nextBy, bossRadius * 0.8f, gs)) {
                gs.bossY = nextBy
            }
        }

        // Visibility Logic
        if (gs.difficulty == 1) {
            if ((gs.pulse && bDistSq in gs.innerRSq..gs.outerRSq) || bDistSq < gs.passiveAuraRadiusSq) {
                gs.bossVis = 1.0f
            }
            gs.bossVis *= gs.fadeMultiplier
            if (gs.bossVis < 0.05f) gs.bossVis = 0.05f
        } else {
            gs.bossVis = 1.0f
        }

        // EMP Boss Mechanic
        if ((gs.bossType == 2 || gs.bossType == 4) && Random.nextFloat() < 0.6f * dt) {
            gs.empFlashTimer = 1.0f
            EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 100)
            StoryProtocol.showIngameMessage(R.string.msg_emp_detected, 1f)
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