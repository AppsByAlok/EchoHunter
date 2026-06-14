package com.appsbyalok.echohunter.systems

import com.appsbyalok.echohunter.engine.GameState
import kotlin.math.sqrt

/**
 * Centralized Input System to handle movement and aiming logic.
 * Decouples raw touch/AI data from the actual game movement.
 */
class InputSystem(private val gs: GameState) {

    fun update(dt: Float, scale: Float) {
        // If movement joystick is active, use its values
        if (gs.controls.isMoveJoyActive) {
            processMovement(gs.controls.moveDirX, gs.controls.moveDirY)
        } 
        
        // Manual Aiming Logic (Future Attack Joystick)
        // If we have an attack joystick active, it overrides movement facing
        if (gs.controls.isManualAimUnlocked) {
            // Implementation for Commit 3
        }
    }

    private fun processMovement(dx: Float, dy: Float) {
        if (dx != 0f || dy != 0f) {
            gs.lastFacingX = dx
            gs.lastFacingY = dy
        }
    }

    /**
     * Helper to calculate normalized directions
     */
    fun getNormalizedDir(dx: Float, dy: Float): Pair<Float, Float> {
        val dist = sqrt(dx * dx + dy * dy)
        return if (dist > 0) {
            Pair(dx / dist, dy / dist)
        } else {
            Pair(0f, 0f)
        }
    }
}
