package com.appsbyalok.echohunter.systems

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.media.ToneGenerator
import com.appsbyalok.echohunter.utils.EchoAudioManager
import com.appsbyalok.echohunter.R
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.engine.GameColors
import com.appsbyalok.echohunter.engine.GameState
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Handles Logic & Rendering for AI
class EnemySystem {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val entityPath = Path()

    val n = 25
    val ex = FloatArray(n)
    val ey = FloatArray(n)
    val evx = FloatArray(n)
    val evy = FloatArray(n)
    val vis = FloatArray(n)
    val type = IntArray(n)

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
        var safeSpawn = false
        var attempts = 0

        while (!safeSpawn && attempts < 10) {
            attempts++

            val spawnPos = gs.modeStrategy.getEnemySpawnPosition(gs, width, height, scale)
            ex[i] = spawnPos.first
            ey[i] = spawnPos.second

            val dx = ex[i] - gs.px
            val dy = ey[i] - gs.py
            val dToPlayerSq = dx * dx + dy * dy
            val safeDistanceSq = (scale * 0.35f) * (scale * 0.35f)

            if (dToPlayerSq > safeDistanceSq || gs.timeSinceStart == 0f) safeSpawn = true
        }

        if (!safeSpawn) {
            ex[i] = gs.cameraX + width + scale * 0.2f
            ey[i] = gs.cameraY - scale * 0.2f
        }

        val diffSpeedMult = if (gs.difficulty == 0) 0.65f else 1.0f
        val speedMult = when (gs.gameMode) {
            0 -> 0.4f + (gs.wave * 0.1f)
            1 -> 0.5f + (gs.currentSector * 0.15f)
            else -> 1f + (gs.score * 0.005f)
        } * diffSpeedMult

        val baseSp = scale * 0.3f * speedMult
        val sp = baseSp + Random.nextFloat() * (scale * 0.2f)

        evx[i] = (Random.nextFloat() - 0.5f) * sp * 2f
        evy[i] = (Random.nextFloat() - 0.5f) * sp * 2f

        val diffHunterMult = if (gs.difficulty == 0) 0.005f else 0.015f
        val hunterProbability = if (gs.score < 10) 0.0f else min(0.65f, (gs.score - 10) * diffHunterMult)

        if (isSwarm) {
            type[i] = 1
        } else if (gs.gameMode == 2) {
            if (Random.nextFloat() < hunterProbability) type[i] = 1
            else type[i] = if (Random.nextFloat() > 0.7f) 2 else 0
        } else {
            type[i] = if (Random.nextFloat() < hunterProbability) 1 else 0
        }
        vis[i] = 0f
    }

    fun spawnSwarmIfNeeded(gs: GameState, width: Float, height: Float) {
        if (gs.bossType == 1 || gs.bossType == 4) {
            for (i in 0 until n) spawn(i, gs, width, height)
        }
    }

    fun updateEnemies(dt: Float, gs: GameState, width: Float, height: Float, scale: Float) {
        gs.isEnemyNear = false
        gs.isEnemyVeryNear = false

        val hitDistSq = (scale * 0.045f) * (scale * 0.045f)

        for (i in 0 until n) {
            ex[i] += evx[i] * dt
            ey[i] += evy[i] * dt

            if (ey[i] < gs.cameraY && evy[i] < 0) evy[i] = -evy[i]
            if (ey[i] > gs.cameraY + height && evy[i] > 0) evy[i] = -evy[i]

            if (gs.gameMode == 2 && ex[i] < gs.firewallWorldX) {
                spawn(i, gs, width, height)
                vis[i] = 0f
                continue
            }

            if (ex[i] < gs.cameraX - width) {
                spawn(i, gs, width, height)
                vis[i] = 0f
                continue
            }

            if (ex[i] > gs.cameraX + width * 2f) {
                spawn(i, gs, width, height)
                vis[i] = 0f
                continue
            }

            val dx = gs.px - ex[i]
            val dy = gs.py - ey[i]
            val d2 = dx * dx + dy * dy

            if (type[i] == 1) {
                if (d2 > 0f) {
                    val eDist = sqrt(d2)
                    val chaseSpeed = scale * (if (gs.isOverclocked) -0.5f else if (gs.difficulty == 0) 0.3f else 0.4f)
                    evx[i] = (evx[i] * 0.95f) + ((dx / eDist) * chaseSpeed * 0.05f)
                    evy[i] = (evy[i] * 0.95f) + ((dy / eDist) * chaseSpeed * 0.05f)
                }

                if (d2 < hitDistSq * 15f) gs.isEnemyNear = true
                if (d2 < hitDistSq * 4f) gs.isEnemyVeryNear = true
            }

            if ((gs.pulse && d2 in gs.innerRSq..gs.outerRSq) || d2 < gs.passiveAuraRadiusSq) {
                vis[i] = 1f
            }

            vis[i] *= gs.fadeMultiplier
        }
    }

    fun updateBoss(dt: Float, gs: GameState, width: Float, scale: Float) {
        if (!gs.bossActive) return

        val bdx = gs.px - gs.bossX
        val bdy = gs.py - gs.bossY
        val bDistSq = bdx * bdx + bdy * bdy
        val bDist = sqrt(bDistSq)

        val bSpeed = scale * (if (gs.bossType == 3 || gs.bossType == 4) 0.6f else 0.3f) * (if (gs.difficulty == 0) 0.7f else 1.0f)

        if (bDist > 0f) {
            var vx = (bdx / bDist) * bSpeed
            var vy = (bdy / bDist) * bSpeed

            when (gs.bossType) {
                1 -> vy += sin(gs.timeSinceStart * 4f) * scale * 0.4f
                2 -> {
                    if (gs.timeSinceStart % 2.5f < 0.2f) { vx *= 4.5f; vy *= 4.5f }
                }
                3 -> {
                    vx += (Random.nextFloat() - 0.5f) * scale * 0.8f
                    vy += (Random.nextFloat() - 0.5f) * scale * 0.8f
                }
                4 -> {
                    vy += cos(gs.timeSinceStart * 5f) * scale * 0.6f
                    vx += sin(gs.timeSinceStart * 3f) * scale * 0.3f
                }
            }

            gs.bossX += vx * dt
            gs.bossY += vy * dt
        }

        if (gs.difficulty == 1) {
            if ((gs.pulse && bDistSq in gs.innerRSq..gs.outerRSq) || bDistSq < gs.passiveAuraRadiusSq) {
                gs.bossVis = 1.0f
            }
            gs.bossVis *= gs.fadeMultiplier
            if (gs.bossVis < 0.05f) gs.bossVis = 0.05f
        } else {
            gs.bossVis = 1.0f
        }

        if ((gs.bossType == 2 || gs.bossType == 4) && Random.nextFloat() < 0.6f * dt) {
            gs.empFlashTimer = 1.0f
            EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 100)
            StoryProtocol.showIngameMessage(R.string.msg_emp_detected, 1f)
        }

        val screenBx = gs.bossX - gs.cameraX
        if (screenBx < -scale * 0.4f) gs.bossX = gs.cameraX - scale * 0.4f
        if (screenBx > width + scale * 0.4f) gs.bossX = gs.cameraX + width + scale * 0.4f
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

    // FIXED: ALL Y coordinates now correctly subtract cameraY
    fun drawEntities(c: Canvas, gs: GameState, width: Float, scale: Float) {
        val entityRadius = scale * 0.03f
        val baseVisProgression = max(0f, 0.20f - (gs.timeSinceStart * 0.002f))

        // 1. Draw Powerups
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

        // 2. Draw Normal Enemies
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
                    else -> {
                        p.color = (a shl 24) or (GameColors.YELLOW and 0xFFFFFF)
                        entityPath.reset()
                        entityPath.moveTo(screenEx, screenEy - entitySize)
                        entityPath.lineTo(screenEx + entitySize, screenEy)
                        entityPath.lineTo(screenEx, screenEy + entitySize)
                        entityPath.lineTo(screenEx - entitySize, screenEy)
                        entityPath.close()
                        c.drawPath(entityPath, p)
                    }
                }
            }
        }

        // 3. Draw Boss
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