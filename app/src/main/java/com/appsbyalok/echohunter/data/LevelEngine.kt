package com.appsbyalok.echohunter.data

import kotlin.math.min
import kotlin.math.pow

// Represents individual gameplay components that can overlap cleanly
enum class LevelFeature {
    CLASSIC,       // Baseline Default: Score/Data collect to win (%2 uses this layout)
    MAZE,          // Structural Variation: Tight/Complex path layout (%4 secondary modifier)
    DEFENSE,       // Prime 3: Protect the central core protocol
    BOSS,          // Prime 5: Warden Arena entity encounter
    ESCAPE,        // Prime 7: Secure target threshold then locate escape gate
    ELIMINATION,   // Prime 11: Terminate high-value target security arrays
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
     * Rearranged Frequencies using Prime distribution with optimized structural weights.
     */
    private fun determineLevelFeatures(level: Int): Set<LevelFeature> {
        if (level % 100 == 0) return setOf(LevelFeature.ADMIN_BONUS)

        val activeFeatures = mutableSetOf<LevelFeature>()

        // 1. Structural Modifiers & Core Prime Assignments
        if (level % 3 == 0)  activeFeatures.add(LevelFeature.DEFENSE)
        if (level % 5 == 0)  activeFeatures.add(LevelFeature.BOSS)
        if (level % 7 == 0)  activeFeatures.add(LevelFeature.ESCAPE)
        if (level % 11 == 0) activeFeatures.add(LevelFeature.ELIMINATION)

        // %2 is now clean Classic template. MAZE is restricted to rare %4 logic for variety
        if (level % 4 == 0 && !activeFeatures.contains(LevelFeature.BOSS)) {
            activeFeatures.add(LevelFeature.MAZE)
        }

        // Conflict Resolution: Boss maps must remain spacious arenas
        if (activeFeatures.contains(LevelFeature.BOSS)) {
            activeFeatures.remove(LevelFeature.MAZE)
        }

        // Fallback safety layer
        if (activeFeatures.isEmpty() || (activeFeatures.size == 1 && activeFeatures.contains(LevelFeature.MAZE))) {
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

    fun getDefenseTimer(level: Int): Float {
        val minTime = 10f
        val maxTime = 300f
        val curveConstant = 250f
        return minTime + (maxTime - minTime) * (level.toFloat() / (level.toFloat() + curveConstant))
    }

    fun getKillRewardKB(level: Int, isBoss: Boolean): Long {
        val base = if (isBoss) 400L + (level * 15L) else 5L + (level / 6L)
        return base + (base * UpgradeSystem.getRewardBonusPercent()).toLong()
    }
}