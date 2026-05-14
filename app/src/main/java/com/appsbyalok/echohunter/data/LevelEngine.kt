package com.appsbyalok.echohunter.data

import kotlin.math.min

// Defines the various types of levels/nodes in the game
enum class LevelType {
    NORMAL,        // Standard survival sweep
    DEFENSE,       // Protect the core from incoming threats
    BOSS,          // Encounter a powerful Warden boss
    DEFENSE_BOSS,  // Protect the core while fighting a Warden
    ADMIN_BONUS    // Easter egg level 100: Only loot, no enemies
}

// Blueprint for how a specific level should behave
data class LevelConfig(
    val type: LevelType,
    val targetScore: Int,
    val speedMultiplier: Float,
    val hpMultiplier: Float,
    val spawnRateMultiplier: Float,
    val clearRewardKB: Long
)

/**
 * LevelEngine: The mathematical brain of the game that generates levels,
 * difficulty scaling, and data rewards based on the current security ring.
 */
object LevelEngine {

    /**
     * Calculates and returns the full configuration for the requested level.
     */
    fun getLevelConfig(level: Int): LevelConfig {
        val type = determineLevelType(level)

        // --- Difficulty Scaling Math ---
        // Speed scales slowly and caps at 3x to ensure the game remains playable
        val speedMult = min(3.0f, 1.0f + (level * 0.015f))

        // HP scales linearly with no hard cap
        val hpMult = 1.0f + (level * 0.05f)

        // Spawn rate (how fast enemies appear) caps at 5x
        val spawnRateMult = min(5.0f, 1.0f + (level * 0.02f))

        // --- Target Score ---
        // Base target is 50, increases by 10 per level (e.g., Level 10 = 150 target)
        val targetScore = 50 + (level * 10)

        // --- Economy & Rewards ---
        // Base reward scales based on the level number
        val rewardMultiplier = 1.0 + (level * 0.1)
        var clearReward = (100 * rewardMultiplier).toLong() // Starts at ~110 KB for Level 1

        // Multiply rewards based on the mode's difficulty
        when (type) {
            LevelType.BOSS -> clearReward *= 3          // 3x loot for boss levels
            LevelType.DEFENSE -> clearReward *= 2       // 2x loot for defense levels
            LevelType.DEFENSE_BOSS -> clearReward *= 5  // 5x loot for the hardest combination
            LevelType.ADMIN_BONUS -> {
                // Admin mode overrides normal targets. It's a timed loot fest.
                // Grants a massive fixed reward (50MB = 51200 KB)
                return LevelConfig(type, 9999, 1f, 1f, 1f, 51200L)
            }
            LevelType.NORMAL -> { /* Normal multipliers apply */ }
        }

        // Apply the "Data Siphon Protocols" upgrade bonus to the final reward
        clearReward += (clearReward * UpgradeSystem.getRewardBonusPercent()).toLong()

        return LevelConfig(
            type = type,
            targetScore = targetScore,
            speedMultiplier = speedMult,
            hpMultiplier = hpMult,
            spawnRateMultiplier = spawnRateMult,
            clearRewardKB = clearReward
        )
    }

    /**
     * Determines the mode of the level based on multiples' logic.
     */
    private fun determineLevelType(level: Int): LevelType {
        return when {
            level % 100 == 0 -> LevelType.ADMIN_BONUS
            level % 15 == 0 -> LevelType.DEFENSE_BOSS // Multiple of both 3 and 5
            level % 5 == 0 -> LevelType.BOSS
            level % 3 == 0 -> LevelType.DEFENSE
            else -> LevelType.NORMAL
        }
    }

    /**
     * Calculates the data dropped when a single enemy is destroyed.
     */
    fun getKillRewardKB(level: Int, isBoss: Boolean): Long {
        // Base calculation based on enemy type and current level
        var reward = if (isBoss) {
            500L + (level * 50L) // Boss drop scaling
        } else {
            5L + (level / 2L)    // Normal enemy drop scaling
        }

        // Apply the "Data Siphon Protocols" upgrade bonus
        reward += (reward * UpgradeSystem.getRewardBonusPercent()).toLong()

        return reward
    }
}