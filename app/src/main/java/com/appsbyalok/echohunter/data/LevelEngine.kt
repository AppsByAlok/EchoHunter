package com.appsbyalok.echohunter.data

import kotlin.math.min
import kotlin.math.pow

// Represents individual gameplay components that can overlap cleanly
enum class LevelFeature {
    CLASSIC,       // Baseline Default: Score/Data collect to win
    MAZE,          // Structural Variation: Tight/Complex path layout (% 6)
    DARKNESS,      // Environmental Modifier: Lights out, Sonar needed (% 8)
    BOSS,          // Prime 5: Warden Arena entity encounter
    ELIMINATION,   // Prime 7: Terminate high-value target security arrays
    ESCAPE,        // Prime 11: Secure target threshold then locate escape gate
    DEFENSE,       // Prime 13: Protect the central core protocol
    BOMB,          // Prime 17: Plant a Logic Bomb and defend it
    SPECIAL,       // Narrative anomaly elements
    ADMIN_BONUS    // Level 100 Easter Egg override
}

data class LevelConfig(
    val features: Set<LevelFeature>,
    val targetScore: Int,
    val speedMultiplier: Float,
    val hpMultiplier: Float,
    val spawnRateMultiplier: Float,
    val aiIntelligence: Float,
    val clearRewardKB: Long
)

object LevelEngine {

    /**
     * Algorithmic Generation using Prime (Objectives) and Even (Modifiers) Matrix.
     */
    private fun determineLevelFeatures(level: Int): Set<LevelFeature> {
        // Core Easter Egg
        if (level % 100 == 0) return setOf(LevelFeature.ADMIN_BONUS)

        // Level 1 is always a safe tutorial infiltration
        if (level == 1) return setOf(LevelFeature.CLASSIC)

        val activeFeatures = mutableSetOf<LevelFeature>()

        // --- 1. THE PRIME PROTOCOL (Primary Objectives) ---
        if (level % 5 == 0)  activeFeatures.add(LevelFeature.BOSS)
        if (level % 7 == 0)  activeFeatures.add(LevelFeature.ELIMINATION)
        if (level % 11 == 0) activeFeatures.add(LevelFeature.ESCAPE)
        if (level % 13 == 0) activeFeatures.add(LevelFeature.DEFENSE)
        if (level % 17 == 0) activeFeatures.add(LevelFeature.BOMB)

        // --- 2. THE EVEN PROTOCOL (Environmental Modifiers) ---
        if (level % 6 == 0) activeFeatures.add(LevelFeature.MAZE)
        if (level % 8 == 0) activeFeatures.add(LevelFeature.DARKNESS)

        // --- 3. LOGIC RESOLUTION ---
        // Boss in a Maze is now ALLOWED! No manual removals.

        // Check if the level has any Main Objective
        val hasPrimaryObjective = activeFeatures.contains(LevelFeature.BOSS) ||
                activeFeatures.contains(LevelFeature.ELIMINATION) ||
                activeFeatures.contains(LevelFeature.ESCAPE) ||
                activeFeatures.contains(LevelFeature.DEFENSE) ||
                activeFeatures.contains(LevelFeature.BOMB)

        // If no primary objective exists, the base mode defaults to CLASSIC
        // This ensures levels like 6 (Maze only) become Classic + Maze
        if (!hasPrimaryObjective) {
            activeFeatures.add(LevelFeature.CLASSIC)
        }

        return activeFeatures
    }

    fun getLevelConfig(level: Int): LevelConfig {
        val features = determineLevelFeatures(level)

        // Balanced and playable scaling thresholds
        val speedMult = min(2.5f, 1.0f + (level * 0.012f))
        val hpMult = 1.0f + (level * 0.04f)
        val spawnRateMult = min(4.0f, 1.0f + (level * 0.018f))

        // AI Intelligence tuning (Level 1 starts dumb, Level 100 is expert)
        val aiIntel = min(1.0f, 0.2f + (level / 100f) * 0.8f)

        // Cap score limits to maintain smooth game sessions
        val targetScore = min(200, 15 + (level * 2))

        // Compounding roguelite reward matrix
        var clearReward = (60L * (1.08).pow(level.toDouble() / 2.0).toLong()).coerceAtLeast(100L)

        var featureMult = 1.0
        if (features.contains(LevelFeature.BOSS)) featureMult *= 2.5
        if (features.contains(LevelFeature.DEFENSE)) featureMult *= 1.5
        if (features.contains(LevelFeature.ESCAPE)) featureMult *= 1.3
        if (features.contains(LevelFeature.BOMB)) featureMult *= 1.8 // Bomb reward modifier
        if (features.contains(LevelFeature.DARKNESS)) featureMult *= 1.2 // Bonus for playing in the dark

        clearReward = (clearReward * featureMult).toLong()
        clearReward += (clearReward * UpgradeSystem.getRewardBonusPercent()).toLong()

        if (features.contains(LevelFeature.ADMIN_BONUS)) {
            return LevelConfig(features, 0, 1.1f, 1f, 0.4f, 0f, 153600L)
        }

        return LevelConfig(
            features = features,
            targetScore = targetScore,
            speedMultiplier = speedMult,
            hpMultiplier = hpMult,
            spawnRateMultiplier = spawnRateMult,
            aiIntelligence = aiIntel,
            clearRewardKB = clearReward
        )
    }

    fun getKillRewardKB(level: Int, isBoss: Boolean): Long {
        val base = if (isBoss) 400L + (level * 15L) else 5L + (level / 6L)
        return base + (base * UpgradeSystem.getRewardBonusPercent()).toLong()
    }
}