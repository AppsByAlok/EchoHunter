package com.appsbyalok.echohunter.data

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.pow

enum class UpgradeType {
    // ARCHITECT (SYSTEM)
    MAX_HP,             
    THRUSTER_OPTIMIZE,  
    DATA_MAGNET,        
    COMPRESSION_ALGO,
    NANITE_REPAIR,      // NEW: Passive HP Regen
    QUANTUM_CORE,       // ELITE: Massive HP
    DATA_SYNDICATE,     // ELITE: 10TB Data God
    OVERCLOCK_DUR,      // Extends Overclock
    OPTIC_SENSORS,      // NEW: Passive Vision Radius
    
    // ENFORCER (COMBAT)
    SPIKE_PAYLOAD,      
    CRIT_CHANCE,        
    MULTITHREAD_SPIKES, 
    COMBO_EXTENDER,     // NEW: Longer combo windows
    KINETIC_OVERLOAD,   // NEW: Mini-explosions on hit
    
    // GHOST (TACTICAL)
    PULSE_FREQUENCY,    
    STEALTH_CAMO,       
    SHIELD_RECOVERY,    
    TRAP_COOLDOWN,      
    GHOST_PROTOCOL,      // ELITE: Massive I-Frames
    SONAR_RANGE,        // NEW: Increased Pulse Radius
    SILENT_SONAR,       // NEW: Reduced Enemy Alert Range
    SONAR_DUR           // NEW: Enemies stay visible longer
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

    private const val ONE_TB = 1024L * 1024L * 1024L
    private const val TEN_TB = 10L * 1024L * 1024L * 1024L

    val catalog = mapOf(
        // --- ARCHITECT BRANCH ---
        UpgradeType.MAX_HP to UpgradeConfig(UpgradeType.MAX_HP, "KERNEL_HARDENING.EXE", "Base health integrity +1.", 5, 500L, 2.2f),
        UpgradeType.THRUSTER_OPTIMIZE to UpgradeConfig(UpgradeType.THRUSTER_OPTIMIZE, "CLOCK_BOOST.SYS", "Movement speed +5%.", 10, 100L, 1.4f),
        UpgradeType.DATA_MAGNET to UpgradeConfig(UpgradeType.DATA_MAGNET, "ATTRACT_LOOPS.ASM", "Pulls data from further away.", 5, 400L, 1.5f),
        UpgradeType.COMPRESSION_ALGO to UpgradeConfig(UpgradeType.COMPRESSION_ALGO, "LZSS_PACKER.BIN", "All costs reduced by 10%.", 5, 2000L, 2.0f),
        UpgradeType.NANITE_REPAIR to UpgradeConfig(UpgradeType.NANITE_REPAIR, "AUTO_PATCHER.BIN", "Passively repairs 1 HP every 25s.", 4, ONE_TB / 2, 3.5f),
        UpgradeType.QUANTUM_CORE to UpgradeConfig(UpgradeType.QUANTUM_CORE, "QUANTUM_WALL.SYS", "Ultra-dense health layers.", 3, ONE_TB, 5.0f),
        UpgradeType.DATA_SYNDICATE to UpgradeConfig(UpgradeType.DATA_SYNDICATE, "NEURAL_NET_SIPHON.EXE", "Total data control. 500% Rewards.", 5, TEN_TB, 10.0f),
        UpgradeType.OVERCLOCK_DUR to UpgradeConfig(UpgradeType.OVERCLOCK_DUR, "BRUTE_FORCE.BIN", "Extends Overclock duration by 1.0s.", 10, 500L, 1.5f),
        UpgradeType.OPTIC_SENSORS to UpgradeConfig(UpgradeType.OPTIC_SENSORS, "PHOTON_COLLECTOR.SYS", "Passive vision radius +15%.", 5, 600L, 1.8f),
        
        // --- ENFORCER BRANCH ---
        UpgradeType.SPIKE_PAYLOAD to UpgradeConfig(UpgradeType.SPIKE_PAYLOAD, "FIREWALL_BREACHER.SH", "Fire rate +15%.", 6, 300L, 1.8f),
        UpgradeType.CRIT_CHANCE to UpgradeConfig(UpgradeType.CRIT_CHANCE, "EXPLOIT_KIT.SH", "10% Crit chance (2x Dmg).", 5, 1000L, 2.5f),
        UpgradeType.KINETIC_OVERLOAD to UpgradeConfig(UpgradeType.KINETIC_OVERLOAD, "MOMENTUM_LEAK.PY", "Crits trigger small explosions.", 5, 5000L, 2.2f),
        UpgradeType.MULTITHREAD_SPIKES to UpgradeConfig(UpgradeType.MULTITHREAD_SPIKES, "RECURSIVE_SCRIPTS.SH", "Extra projectiles per shot.", 3, ONE_TB / 2, 4.0f),
        UpgradeType.COMBO_EXTENDER to UpgradeConfig(UpgradeType.COMBO_EXTENDER, "BUFFER_STABILIZER.SYS", "Adds +2s to combo break timer.", 5, 2000L, 2.0f),
        
        // --- GHOST BRANCH ---
        UpgradeType.PULSE_FREQUENCY to UpgradeConfig(UpgradeType.PULSE_FREQUENCY, "PING_OPTIMIZER.BAT", "Sonar CD -10%.", 5, 150L, 1.6f),
        UpgradeType.STEALTH_CAMO to UpgradeConfig(UpgradeType.STEALTH_CAMO, "PACKET_DISSOLVER.EXE", "Detection range reduced 10%.", 5, 1200L, 1.8f),
        UpgradeType.TRAP_COOLDOWN to UpgradeConfig(UpgradeType.TRAP_COOLDOWN, "OVERCLOCK_DECOY.SYS", "Decoy/Trap CD -15%.", 5, 800L, 1.7f),
        UpgradeType.SHIELD_RECOVERY to UpgradeConfig(UpgradeType.SHIELD_RECOVERY, "RECOVERY_PROTOCOL.EXE", "Shield regen +15%.", 5, 600L, 2.0f),
        UpgradeType.GHOST_PROTOCOL to UpgradeConfig(UpgradeType.GHOST_PROTOCOL, "ZERO_DAY.VOID", "Massive I-Frames on hit.", 5, ONE_TB / 4, 3.0f),
        UpgradeType.SONAR_RANGE to UpgradeConfig(UpgradeType.SONAR_RANGE, "WIDE_PING.BAT", "Sonar scan radius +20%.", 5, 400L, 1.7f),
        UpgradeType.SILENT_SONAR to UpgradeConfig(UpgradeType.SILENT_SONAR, "ACOUSTIC_DAMPING.EXE", "Sonar alert range -20%.", 5, 1000L, 1.8f),
        UpgradeType.SONAR_DUR to UpgradeConfig(UpgradeType.SONAR_DUR, "PERSISTENT_TRACE.BIN", "Enemies visible for +1.5s.", 5, 500L, 1.6f)
    )

    fun init(context: Context) {
        prefs = context.getSharedPreferences("EchoUpgrades", Context.MODE_PRIVATE)
        for (type in UpgradeType.entries) currentLevels[type] = prefs.getInt("upg_${type.name}", 0)
    }

    fun getLevel(type: UpgradeType): Int = currentLevels[type] ?: 0

    fun getNextLevelCost(type: UpgradeType): Long {
        val currentLvl = getLevel(type)
        val config = catalog[type] ?: return 999999999999L
        if (currentLvl >= config.maxLevel) return -1L

        val discount = 1.0f - (getLevel(UpgradeType.COMPRESSION_ALGO) * 0.1f)
        return (config.baseCostKB * config.costMultiplier.pow(currentLvl) * discount).toLong()
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

    fun debugMaxAll() {
        for ((type, config) in catalog) {
            currentLevels[type] = config.maxLevel
            prefs.edit().putInt("upg_${type.name}", config.maxLevel).apply()
        }
    }

    fun clearAllData() {
        for (type in UpgradeType.entries) {
            currentLevels[type] = 0
            prefs.edit().putInt("upg_${type.name}", 0).apply()
        }
    }

    // Accessors for Systems
    fun getBonusMaxHp(): Int = getLevel(UpgradeType.MAX_HP) + (getLevel(UpgradeType.QUANTUM_CORE) * 5)
    fun getBonusOverclockTime(): Float = getLevel(UpgradeType.OVERCLOCK_DUR) * 1.0f
    fun getRegenInterval(): Float = if (getLevel(UpgradeType.NANITE_REPAIR) > 0) 30f / getLevel(UpgradeType.NANITE_REPAIR) else -1f
    fun getComboBonusTime(): Float = getLevel(UpgradeType.COMBO_EXTENDER) * 2.0f
    fun getRewardMultiplier(): Float = 1.0f + (getLevel(UpgradeType.DATA_SYNDICATE) * 4.0f)
    fun getMultiShotCount(): Int = getLevel(UpgradeType.MULTITHREAD_SPIKES)
    fun getCritChance(): Float = getLevel(UpgradeType.CRIT_CHANCE) * 0.10f

    fun getIframeDurationBonus(): Float = getLevel(UpgradeType.GHOST_PROTOCOL) * 1.5f
    fun getSpeedMultiplier(): Float = 1.0f + (getLevel(UpgradeType.THRUSTER_OPTIMIZE) * 0.05f)
    fun getVisionRadiusMultiplier(): Float = 1.0f + (getLevel(UpgradeType.OPTIC_SENSORS) * 0.15f)
    fun getDataMagnetRadiusMultiplier(): Float = 1.0f + (getLevel(UpgradeType.DATA_MAGNET) * 0.5f)
    fun getStealthDetectionMultiplier(): Float = 1.0f - (getLevel(UpgradeType.STEALTH_CAMO) * 0.15f)
    
    fun getSpikeCooldownMultiplier(): Float = 1.0f / (1.0f + getLevel(UpgradeType.SPIKE_PAYLOAD) * 0.15f)
    fun getPulseCooldownMultiplier(): Float = 1.0f - (getLevel(UpgradeType.PULSE_FREQUENCY) * 0.10f)
    fun getTrapCooldownMultiplier(): Float = 1.0f - (getLevel(UpgradeType.TRAP_COOLDOWN) * 0.15f)

    fun getSonarRangeMultiplier(): Float = 1.0f + (getLevel(UpgradeType.SONAR_RANGE) * 0.20f)
    fun getSonarSilenceMultiplier(): Float = 1.0f - (getLevel(UpgradeType.SILENT_SONAR) * 0.20f)
    fun getSonarDurationBonus(): Float = 2.0f + (getLevel(UpgradeType.SONAR_DUR) * 1.5f) // Base 2.0s + bonus

    fun getRewardBonusPercent(): Float = getLevel(UpgradeType.DATA_SYNDICATE) * 4.0f
    fun getCritDamageMultiplier(): Int = if (getLevel(UpgradeType.KINETIC_OVERLOAD) > 0) 3 else 2
}
