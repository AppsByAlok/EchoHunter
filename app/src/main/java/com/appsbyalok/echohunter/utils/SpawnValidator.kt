package com.appsbyalok.echohunter.utils

import com.appsbyalok.echohunter.engine.GameState
/**
 * Utility to validate spawn positions to prevent entities from clipping into walls
 * or spawning too close to the player.
 */
object SpawnValidator {

    /**
     * Checks if a position is valid for spawning an entity.
     * 
     * @param x X coordinate to check
     * @param y Y coordinate to check
     * @param radius Radius of the entity
     * @param gs Current GameState
     * @param minPlayerDist Minimum distance from the player
     * @return true if the position is valid
     */
    fun isValid(x: Float, y: Float, radius: Float, gs: GameState, minPlayerDist: Float = 0f): Boolean {
        // 1. Check map boundaries and walls
        if (isCollidingWithWall(x, y, radius, gs)) {
            return false
        }

        // 2. Check distance from player
        if (minPlayerDist > 0f) {
            val dx = x - gs.px
            val dy = y - gs.py
            if (dx * dx + dy * dy < minPlayerDist * minPlayerDist) {
                return false
            }
        }

        return true
    }

    /**
     * Finds a valid spawn position near a target point.
     * 
     * @param targetX Preferred X coordinate
     * @param targetY Preferred Y coordinate
     * @param radius Radius of the entity
     * @param gs Current GameState
     * @param maxAttempts Maximum number of attempts to find a valid spot
     * @param searchRadius How far to search for a valid spot
     * @return A Pair of coordinates (x, y) if found, or null if no valid spot found
     */
    fun findValidNear(
        targetX: Float, 
        targetY: Float, 
        radius: Float, 
        gs: GameState, 
        maxAttempts: Int = 10,
        searchRadius: Float = 100f
    ): Pair<Float, Float>? {
        if (isValid(targetX, targetY, radius, gs)) {
            return Pair(targetX, targetY)
        }

        val random = java.util.Random()
        for (i in 0 until maxAttempts) {
            val angle = random.nextFloat() * 6.2831855f
            val dist = random.nextFloat() * searchRadius
            val nx = targetX + kotlin.math.cos(angle) * dist
            val ny = targetY + kotlin.math.sin(angle) * dist

            if (isValid(nx, ny, radius, gs)) {
                return Pair(nx, ny)
            }
        }

        return null
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
                } else {
                    // Out of bounds is considered a wall collision
                    return true
                }
            }
        }
        return false
    }
}
