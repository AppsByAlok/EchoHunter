package com.appsbyalok.echohunter.data

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.pow

// Identifiers for all available firmware exploits/upgrades
enum class UpgradeType {
    MAX_HP,             // ROOT_KERNEL.EXE (+1 Max HP)
    THRUSTER_OPTIMIZE,  // OVERRIDE.SYS (Speed +5%)
    PULSE_FREQUENCY,    // SONAR_PING.BAT (Pulse CD Multiplier)
    SPIKE_PAYLOAD,      // MALWARE_SPIKE.SH (Attack Fire Rate Multiplier)
    OVERCLOCK_DUR,      // BRUTE_FORCE.BIN (Overclock +0.5s)
    DATA_MAGNET,        // PACKET_SNIFFER.EXE (Magnet Radius)
    COMPRESSION_ALGO,   // CRYPTO_SIPHON.BAT (Data Bonus +10%)
    STEALTH_CAMO        // STEALTH_DRIVER.SYS (Camo detection radius reduction)
}

data class UpgradeConfig(
    val type: UpgradeType,
    val nameStr: String,
    val descStr: String,
    val maxLevel: Int,
    val baseCostKB: Long,
    val costMultiplier: Float
)

object UpgradeSystem {
    private lateinit var prefs: SharedPreferences
    private val currentLevels = mutableMapOf<UpgradeType, Int>()

    // Hacker Themed Shop Catalog
    val catalog = mapOf(
        UpgradeType.MAX_HP to UpgradeConfig(UpgradeType.MAX_HP, "ROOT_KERNEL.EXE", "Grants +1 extra System HP.", 5, 500L, 2.0f),
        UpgradeType.THRUSTER_OPTIMIZE to UpgradeConfig(UpgradeType.THRUSTER_OPTIMIZE, "OVERRIDE.SYS", "Boosts Drone movement speed by 5%.", 10, 100L, 1.4f),
        UpgradeType.PULSE_FREQUENCY to UpgradeConfig(UpgradeType.PULSE_FREQUENCY, "SONAR_PING.BAT", "Reduces Sonar scan cooldown by 10%.", 5, 150L, 1.5f),
        UpgradeType.SPIKE_PAYLOAD to UpgradeConfig(UpgradeType.SPIKE_PAYLOAD, "MALWARE_SPIKE.SH", "Increases Attack fire rate by 10%.", 6, 250L, 1.6f),
        UpgradeType.OVERCLOCK_DUR to UpgradeConfig(UpgradeType.OVERCLOCK_DUR, "BRUTE_FORCE.BIN", "Extends Overclock duration by 0.5s.", 10, 300L, 1.5f),
        UpgradeType.DATA_MAGNET to UpgradeConfig(UpgradeType.DATA_MAGNET, "PACKET_SNIFFER.EXE", "Increases Data drop magnetic radius.", 5, 200L, 1.8f),
        UpgradeType.COMPRESSION_ALGO to UpgradeConfig(UpgradeType.COMPRESSION_ALGO, "CRYPTO_SIPHON.BAT", "Grants +10% bonus to all Data extracted.", 10, 400L, 1.7f),
        UpgradeType.STEALTH_CAMO to UpgradeConfig(UpgradeType.STEALTH_CAMO, "STEALTH_DRIVER.SYS", "Reduces enemy detection range by 5% per level.", 10, 350L, 1.6f)
    )

    fun init(context: Context) {
        prefs = context.getSharedPreferences("EchoUpgrades", Context.MODE_PRIVATE)
        for (type in UpgradeType.entries) {
            currentLevels[type] = prefs.getInt("upg_${type.name}", 0)
        }
    }

    fun getLevel(type: UpgradeType): Int = currentLevels[type] ?: 0

    fun getNextLevelCost(type: UpgradeType): Long {
        val currentLvl = getLevel(type)
        val config = catalog[type] ?: return 9999999L
        if (currentLvl >= config.maxLevel) return -1L // Maxed out
        return (config.baseCostKB * config.costMultiplier.pow(currentLvl)).toLong()
    }

    fun purchaseUpgrade(type: UpgradeType): Boolean {
        val currentLvl = getLevel(type)
        val config = catalog[type] ?: return false

        if (currentLvl >= config.maxLevel) return false

        val cost = getNextLevelCost(type)
        if (SaveManager.spendData(cost)) {
            val newLvl = currentLvl + 1
            currentLevels[type] = newLvl
            prefs.edit().putInt("upg_${type.name}", newLvl).apply()
            return true
        }
        return false
    }

    // --- Buff Accessors ---

    fun getBonusMaxHp(): Int = getLevel(UpgradeType.MAX_HP)

    fun getSpeedMultiplier(): Float = 1.0f + (getLevel(UpgradeType.THRUSTER_OPTIMIZE) * 0.05f)

    // NAYA: Pulse / Sonar Cooldown Multiplier
    fun getPulseCooldownMultiplier(): Float {
        // Level 5 max = 0.5f (50% faster Sonar scans)
        return 1.0f - (getLevel(UpgradeType.PULSE_FREQUENCY) * 0.1f)
    }

    // NAYA: Attack Speed Accessor
    fun getSpikeCooldownMultiplier(): Float {
        // Reduces cooldown up to 60% at max level (makes it shoot very fast!)
        return 1.0f - (getLevel(UpgradeType.SPIKE_PAYLOAD) * 0.1f)
    }

    fun getBonusOverclockTime(): Float = getLevel(UpgradeType.OVERCLOCK_DUR) * 0.5f

    fun getDataMagnetRadiusMultiplier(): Float = 1.0f + (getLevel(UpgradeType.DATA_MAGNET) * 0.5f)

    fun getRewardBonusPercent(): Float = getLevel(UpgradeType.COMPRESSION_ALGO) * 0.10f

    fun getStealthDetectionMultiplier(): Float = 1.0f - (getLevel(UpgradeType.STEALTH_CAMO) * 0.05f)

    fun clearAllData() {
        // 1. Map values reset to 0
        for (type in UpgradeType.entries) {
            currentLevels[type] = 0
        }
        // 2. Clear SharedPrefs completely
        if (::prefs.isInitialized) {
            prefs.edit().clear().apply()
        }
    }

    fun debugMaxAll() {
        for (type in UpgradeType.entries) {
            val config = catalog[type] ?: continue
            currentLevels[type] = config.maxLevel
            prefs.edit().putInt("upg_${type.name}", config.maxLevel).apply()
        }
    }
}