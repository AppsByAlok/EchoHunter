package com.appsbyalok.echohunter.data

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
    val targetScore: Long,
    val speedMultiplier: Float,
    val hpMultiplier: Float,
    val spawnRateMultiplier: Float,
    val aiIntelligence: Float,
    val clearRewardKB: Long
)

object LevelEngine {

    /**
     * Saturated Growth Curve Utility
     * Formula: Base + (MaxAdd * Level) / (Level + K)
     */
    fun getSaturatedValue(level: Int, base: Float, maxAdd: Float, curveConstant: Float): Float {
        return base + (maxAdd * level) / (level + curveConstant)
    }

    /**
     * Algorithmic Generation using Prime (Objectives) and Even (Modifiers) Matrix.
     */
    private fun determineLevelFeatures(level: Int): Set<LevelFeature> {
        // Core Easter Egg
        if (level % 100 == 0) return setOf(LevelFeature.ADMIN_BONUS, LevelFeature.BOSS)

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

    fun getLevelConfig(level: Int, difficulty: Int = 0): LevelConfig {
        val features = determineLevelFeatures(level)

        // --- DIFFICULTY-BASED SCALING ---
        val isHard = difficulty == 1
        
        // Speed: Normal is slow (max 1.4x), Hard is fast (max 2.0x)
        val speedBase = if (isHard) 1.1f else 1.0f
        val speedMax = if (isHard) 0.9f else 0.4f
        val speedMult = getSaturatedValue(level, speedBase, speedMax, if (isHard) 200f else 400f)

        // HP: Normal (max 8x), Hard (max 20x)
        val hpMax = if (isHard) 19.0f else 7.0f
        val hpMult = getSaturatedValue(level, 1.0f, hpMax, 300f)

        // Spawn Rate: Hard has much more enemies
        val spawnMax = if (isHard) 3.0f else 1.2f
        val spawnRateMult = getSaturatedValue(level, 1.0f, spawnMax, 300f)

        // AI Intelligence: Normal stays "dumb" longer
        val aiMax = if (isHard) 0.9f else 0.5f
        val aiIntel = getSaturatedValue(level, 0.15f, aiMax, if (isHard) 80f else 250f)

        val targetScore = getSaturatedValue(level, 50f, 1950f, 400f).toLong() // Max 2000 score

        // --- NAYA: SMOOTH REWARD SCALING (Prevents Long Overflow) ---
        val baseClear = 100f
        val maxAddClear = 50000f // Plateau at 50k base reward
        var clearReward = getSaturatedValue(level, baseClear, maxAddClear, 300f).toLong()

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
        val base = if (isBoss) {
            getSaturatedValue(level, 400f, 1600f, 200f).toLong() // Max 2000 KB for Boss
        } else {
            getSaturatedValue(level, 5f, 45f, 300f).toLong() // Max 50 KB for Normal
        }
        return base + (base * UpgradeSystem.getRewardBonusPercent()).toLong()
    }
}