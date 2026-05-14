package com.appsbyalok.echohunter

import com.appsbyalok.echohunter.engine.GameState
import org.junit.Assert.assertTrue
import org.junit.Test

class CollisionUnitTest {

    @Test
    fun testPlayerSlidingOnWall() {
        // 1. Setup GameState
        val gs = GameState()
        gs.tileSize = 100f
        gs.gameMode = 0 // Strictly enforce campaign mode (solid outer boundaries)

        // 2. Create a Mock Grid (0 = Path, 1 = Wall)
        // X goes down, Y goes right in this array representation
        gs.gridMap = arrayOf(
            intArrayOf(1, 1, 1, 1),
            intArrayOf(1, 0, 0, 1), // Player is in a corridor
            intArrayOf(1, 0, 0, 1),
            intArrayOf(1, 1, 1, 1)
        )

        // Grid bounds
        gs.mapWidth = 400f
        gs.mapHeight = 400f

        // 3. Place player right next to the right wall
        // Tile size is 100. Right wall starts at X=300.
        // Player radius is roughly 15f (at scale 1000).
        gs.px = 280f
        gs.py = 150f

        val initialY = gs.py

        // 4. Force Joystick to move Top-Right (Diagonally into the wall)
        gs.joyDirX = 1f  // Move Right (into wall)
        gs.joyDirY = 1f  // Move Down (free path)

        // Simulate realistic game frames (60FPS)
        val dt = 0.016f
        val scale = 1000f

        // Run movement for 10 frames
        for (i in 1..10) {
            gs.updatePlayerMovement(dt, 1080f, 1920f, scale)
        }

        // 5. ASSERTIONS (Test Checks)

        // Check X: Player should NOT cross into the wall (X >= 300 minus hitbox)
        assertTrue("Player crossed the wall! X is ${gs.px}", gs.px < 300f)

        // Check Y: Player SHOULD slide down successfully because Y axis is clear
        assertTrue("Player did not slide on Y axis! Y is ${gs.py}", gs.py > initialY)

        println("Slide Test Passed! Final Pos: X=${gs.px}, Y=${gs.py}")
    }
}