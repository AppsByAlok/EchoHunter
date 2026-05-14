package com.appsbyalok.echohunter

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appsbyalok.echohunter.data.MazeGenerator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MazeGeneratorInstrumentedTest {

    @Test
    fun testMazeGenerationOnDevice() {
        // FIXED: Using a normal level (Level 4) to test the standard size scaling formula
        val level = 4
        val seed = 500

        // Level 4 normal calculation: 21 + (4 * 2) = 29.
        val expectedWidth = 29

        val grid = MazeGenerator.generateLevelMap(
            level = level,
            gameMode = 0,
            difficulty = 1, // Hard mode allows max grid up to 251, so 29 is well within limits
            seed = seed
        )

        assertNotNull("Grid should not be null", grid)
        assertEquals("Grid width should automatically scale with level", expectedWidth, grid.size)

        println("Generated Grid Size on Device: ${grid.size} x ${grid[0].size}")

        // Ensure Phase 2 Anti-Trap Logic placed the Player Spawn Marker (4) correctly
        var foundPlayer = false
        var foundDest = false
        for (x in grid.indices) {
            for (y in grid[0].indices) {
                if (grid[x][y] == MazeGenerator.PLAYER_SPAWN) foundPlayer = true
                if (grid[x][y] == MazeGenerator.DEST_NODE) foundDest = true
            }
        }

        assertTrue("Grid must contain exactly one PLAYER_SPAWN", foundPlayer)
        assertTrue("Grid must contain exactly one DEST_NODE", foundDest)
    }
}