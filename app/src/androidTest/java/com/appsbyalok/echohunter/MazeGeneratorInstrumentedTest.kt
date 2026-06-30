package com.appsbyalok.echohunter

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appsbyalok.echohunter.data.MazeGenerator
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MazeGeneratorInstrumentedTest {

    @Test
    fun testLevel143ConnectivityAndSpawn() {
        val level = 143
        val gameMode = 0 // Campaign
        val difficulty = 1 // Hard

        // 1. Check Grid Generation
        val grid = MazeGenerator.generateLevelMap(level, gameMode, difficulty, System.currentTimeMillis().toInt())
        assertNotNull("Grid should not be null for Level 143", grid)
        
        // 2. Connectivity Test: Can we reach DEST_NODE (Core) from PLAYER_SPAWN?
        var startX = -1
        var startY = -1
        var endX = -1
        var endY = -1

        for (x in grid.indices) {
            for (y in grid[0].indices) {
                if (grid[x][y] == MazeGenerator.PLAYER_SPAWN) { startX = x; startY = y }
                if (grid[x][y] == MazeGenerator.DEST_NODE) { endX = x; endY = y }
            }
        }

        assertTrue("Player spawn must exist", startX != -1)
        assertTrue("Destination node must exist", endX != -1)

        // Simple BFS to check if path exists
        val visited = Array(grid.size) { BooleanArray(grid[0].size) }
        val queue = mutableListOf<Pair<Int, Int>>()
        queue.add(startX to startY)
        visited[startX][startY] = true

        var pathFound = false
        while (queue.isNotEmpty()) {
            val (currX, currY) = queue.removeAt(0)
            if (currX == endX && currY == endY) {
                pathFound = true
                break
            }

            val dirs = arrayOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)
            for (d in dirs) {
                val nx = currX + d.first
                val ny = currY + d.second
                if (nx in grid.indices && ny in grid[0].indices && !visited[nx][ny] && grid[nx][ny] != MazeGenerator.WALL) {
                    visited[nx][ny] = true
                    queue.add(nx to ny)
                }
            }
        }

        assertTrue("Path must exist from spawn to core in Level 143", pathFound)
    }
}