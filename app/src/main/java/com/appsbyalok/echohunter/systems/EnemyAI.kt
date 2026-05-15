package com.appsbyalok.echohunter.systems

import com.appsbyalok.echohunter.engine.GameState
import kotlin.math.sqrt

// EnemyAI: Modular class strictly for Pathfinding and Line-of-Sight math
class EnemyAI {
    private var heatMap: Array<IntArray>? = null
    private val qX = IntArray(25000) // Fast pre-allocated BFS queue
    private val qY = IntArray(25000)

    fun updateHeatMap(gs: GameState) {
        val grid = gs.gridMap ?: return
        val w = grid.size; val h = grid[0].size

        if (heatMap == null || heatMap!!.size != w || heatMap!![0].size != h) {
            heatMap = Array(w) { IntArray(h) }
        }

        // Initialize with high distance (9999)
        for(x in 0 until w) for(y in 0 until h) heatMap!![x][y] = 9999

        val ts = gs.tileSize

        // NAYA: Agar Decoy active hai, toh saare dushman Decoy ki taraf attract honge!
        val targetX = if (gs.isDecoyActive) gs.decoyX else gs.px
        val targetY = if (gs.isDecoyActive) gs.decoyY else gs.py

        // Agar Camouflage (Invisibility) active hai aur decoy nahi hai, toh dushman tumhe nahi dhoondh payenge
        if (gs.isCamouflaged && !gs.isDecoyActive) return

        val pxC = (targetX / ts).toInt().coerceIn(0, w - 1)
        val pyC = (targetY / ts).toInt().coerceIn(0, h - 1)

        var head = 0; var tail = 0
        qX[tail] = pxC; qY[tail] = pyC; tail++
        heatMap!![pxC][pyC] = 0

        val dirsX = intArrayOf(0, 0, -1, 1)
        val dirsY = intArrayOf(-1, 1, 0, 0)

        // Breadth-First Search (Flow Field logic)
        while (head < tail && tail < 25000) {
            val cx = qX[head]; val cy = qY[head]; head++
            val dist = heatMap!![cx][cy]

            for (d in 0..3) {
                val nx = cx + dirsX[d]; val ny = cy + dirsY[d]
                if (nx in 0 until w && ny in 0 until h && grid[nx][ny] != 1) {
                    if (heatMap!![nx][ny] > dist + 1) {
                        heatMap!![nx][ny] = dist + 1
                        qX[tail] = nx; qY[tail] = ny; tail++
                    }
                }
            }
        }
    }

    fun steerByHeatMap(ex: Float, ey: Float, evx: Float, evy: Float, speed: Float, gs: GameState): Pair<Float, Float> {
        val hm = heatMap ?: return Pair(evx, evy)
        val ts = gs.tileSize
        val cx = (ex / ts).toInt()
        val cy = (ey / ts).toInt()

        if (cx !in hm.indices || cy !in hm[0].indices) return Pair(evx, evy)

        var bestVal = hm[cx][cy]
        var targetX = ex
        var targetY = ey

        val dirsX = intArrayOf(0, 0, -1, 1)
        val dirsY = intArrayOf(-1, 1, 0, 0)

        for (d in 0..3) {
            val nx = cx + dirsX[d]; val ny = cy + dirsY[d]
            if (nx in hm.indices && ny in hm[0].indices && hm[nx][ny] < bestVal) {
                bestVal = hm[nx][ny]
                targetX = nx * ts + (ts / 2f)
                targetY = ny * ts + (ts / 2f)
            }
        }

        val dx = targetX - ex; val dy = targetY - ey
        val dist = sqrt(dx * dx + dy * dy)
        return if (dist > 0f) {
            Pair((evx * 0.8f) + ((dx / dist) * speed * 0.2f), (evy * 0.8f) + ((dy / dist) * speed * 0.2f))
        } else Pair(evx, evy)
    }

    fun hasLineOfSight(x0: Float, y0: Float, x1: Float, y1: Float, gs: GameState): Boolean {
        // STEALTH: Invisible player cannot be seen
        if (gs.isCamouflaged) return false

        val grid = gs.gridMap ?: return true
        val ts = gs.tileSize
        val steps = 15
        val dx = (x1 - x0) / steps
        val dy = (y1 - y0) / steps

        // Raycasting to check walls
        for (i in 0..steps) {
            val cx = ((x0 + dx * i) / ts).toInt()
            val cy = ((y0 + dy * i) / ts).toInt()
            if (cx in grid.indices && cy in grid[0].indices) {
                if (grid[cx][cy] == 1) return false // Wall is blocking the view!
            }
        }
        return true
    }
}