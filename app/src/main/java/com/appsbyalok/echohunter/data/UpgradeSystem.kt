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
    SONAR_DUR,           // NEW: Enemies stay visible longer
    RESONANCE_CHAMBER   // NEW: Clean Sweep Chain Radius
}

data class UpgradeConfig(
    val type: UpgradeType,
    val nameStr: String,
    val descStr: String,
    val usageStr: String,
    val loreStr: String,
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
        UpgradeType.MAX_HP to UpgradeConfig(
            UpgradeType.MAX_HP, "KERNEL_HARDENING.EXE", "System Integrity +1 HP.",
            "Increases survival time in high-density combat zones.",
            "Old-world code recovered from a burnt-out mainframe. Crude but effective.",
            5, 500L, 2.2f
        ),
        UpgradeType.THRUSTER_OPTIMIZE to UpgradeConfig(
            UpgradeType.THRUSTER_OPTIMIZE, "CLOCK_BOOST.SYS", "Movement Velocity +5%.",
            "Crucial for outrunning Elite Sentinels and navigating narrow corridors.",
            "Bypassing safety limiters on the propulsion sub-routine. Watch for heat spikes.",
            10, 100L, 1.4f
        ),
        UpgradeType.DATA_MAGNET to UpgradeConfig(
            UpgradeType.DATA_MAGNET, "ATTRACT_LOOPS.ASM", "Data Pickup Radius +50%.",
            "Reduces risk by collecting data without needing to move close to enemy remains.",
            "A magnetic pulse that hums with the hunger of a thousand dead servers.",
            5, 400L, 1.5f
        ),
        UpgradeType.COMPRESSION_ALGO to UpgradeConfig(
            UpgradeType.COMPRESSION_ALGO, "LZSS_PACKER.BIN", "All Upgrade Costs -10%.",
            "Essential for late-game scaling. Buy this early to save millions of KB.",
            "Efficiency is the only law in the digital void. Pack it tight.",
            5, 2000L, 2.0f
        ),
        UpgradeType.NANITE_REPAIR to UpgradeConfig(
            UpgradeType.NANITE_REPAIR, "AUTO_PATCHER.BIN", "Passive Repair: 1 HP every 30-10s.",
            "Allows recovery during stealth phases or long chases.",
            "Microscopic bots that stitch your code back together while you run.",
            4, ONE_TB / 2, 3.5f
        ),
        UpgradeType.QUANTUM_CORE to UpgradeConfig(
            UpgradeType.QUANTUM_CORE, "QUANTUM_WALL.SYS", "[ELITE] Massive Integrity +5 HP.",
            "Turns you into a tank. Necessary for the deepest layers of the Network.",
            "Existential redundancy. You exist in two states: Alive and Very Alive.",
            3, ONE_TB, 5.0f
        ),
        UpgradeType.DATA_SYNDICATE to UpgradeConfig(
            UpgradeType.DATA_SYNDICATE, "NEURAL_NET_SIPHON.EXE", "[ELITE] Data Harvest Yield +400%.",
            "The ultimate economic upgrade. Floods your system with raw data.",
            "You are no longer a scavenger. You are the apex predator of information.",
            5, TEN_TB, 10.0f
        ),
        UpgradeType.OVERCLOCK_DUR to UpgradeConfig(
            UpgradeType.OVERCLOCK_DUR, "BRUTE_FORCE.BIN", "Overclock active time +1.0s.",
            "Increases the window of absolute invincibility and power.",
            "Pushing the silicon to its breaking point. Reality starts to lag.",
            10, 500L, 1.5f
        ),
        UpgradeType.OPTIC_SENSORS to UpgradeConfig(
            UpgradeType.OPTIC_SENSORS, "PHOTON_COLLECTOR.SYS", "Light Radius +15%.",
            "Helps spot traps and enemies in the pitch-black outer sectors.",
            "The digital eye sees what the physical soul cannot comprehend.",
            5, 600L, 1.8f
        ),
        
        // --- ENFORCER BRANCH ---
        UpgradeType.SPIKE_PAYLOAD to UpgradeConfig(
            UpgradeType.SPIKE_PAYLOAD, "FIREWALL_BREACHER.SH", "Spike Launch Speed +15%.",
            "Faster projectiles mean you hit fast-moving scouts more reliably.",
            "A script designed to punch through even the most hardened encryption.",
            6, 300L, 1.8f
        ),
        UpgradeType.CRIT_CHANCE to UpgradeConfig(
            UpgradeType.CRIT_CHANCE, "EXPLOIT_KIT.SH", "Crit Chance +10% (2x Damage).",
            "Significantly boosts DPS against high-health Boss units.",
            "Finding the zero-day vulnerability in every enemy's logic.",
            5, 1000L, 2.5f
        ),
        UpgradeType.KINETIC_OVERLOAD to UpgradeConfig(
            UpgradeType.KINETIC_OVERLOAD, "MOMENTUM_LEAK.PY", "Crits trigger Area Explosions.",
            "Massive crowd control. One crit can wipe out an entire swarm.",
            "When the logic fails, the energy has to go somewhere. Boom.",
            5, 5000L, 2.2f
        ),
        UpgradeType.MULTITHREAD_SPIKES to UpgradeConfig(
            UpgradeType.MULTITHREAD_SPIKES, "RECURSIVE_SCRIPTS.SH", "[ELITE] Extra Spike per shot.",
            "Force multiplier. Doubles or triples your total damage output.",
            "Parallel processing for destruction. Why shoot once when you can shoot forever?",
            3, ONE_TB / 2, 4.0f
        ),
        UpgradeType.COMBO_EXTENDER to UpgradeConfig(
            UpgradeType.COMBO_EXTENDER, "BUFFER_STABILIZER.SYS", "Combo Decay Timer +2.0s.",
            "Keeps your reward multiplier high even during quiet exploration.",
            "Holding onto the data flow just a little bit longer. Don't let go.",
            5, 2000L, 2.0f
        ),
        
        // --- GHOST BRANCH ---
        UpgradeType.PULSE_FREQUENCY to UpgradeConfig(
            UpgradeType.PULSE_FREQUENCY, "PING_OPTIMIZER.BAT", "Sonar Recharge Speed +10%.",
            "Spam Sonar more often to keep enemies visible constantly.",
            "Sending out echoes into the dark. Something always answers.",
            5, 150L, 1.6f
        ),
        UpgradeType.STEALTH_CAMO to UpgradeConfig(
            UpgradeType.STEALTH_CAMO, "PACKET_DISSOLVER.EXE", "Enemy Detection Range -10%.",
            "Essential for Ghost runs. Move past sentries without engaging.",
            "You are a ghost in the machine. A whisper in a hurricane.",
            5, 1200L, 1.8f
        ),
        UpgradeType.TRAP_COOLDOWN to UpgradeConfig(
            UpgradeType.TRAP_COOLDOWN, "OVERCLOCK_DECOY.SYS", "Decoy Cooldown Speed +15%.",
            "Deploy decoys more frequently to distract pursuers.",
            "False flags and digital phantoms. They're chasing a shadow.",
            5, 800L, 1.7f
        ),
        UpgradeType.SHIELD_RECOVERY to UpgradeConfig(
            UpgradeType.SHIELD_RECOVERY, "RECOVERY_PROTOCOL.EXE", "Shield Regen Speed +15%.",
            "Reduces downtime between skirmishes. Keep moving.",
            "The barrier hums with renewed energy. You are protected.",
            5, 600L, 2.0f
        ),
        UpgradeType.GHOST_PROTOCOL to UpgradeConfig(
            UpgradeType.GHOST_PROTOCOL, "ZERO_DAY.VOID", "[ELITE] Post-hit I-Frames +1.5s.",
            "Provides a massive safety net after taking damage to reposition.",
            "The system forgets you exist for a split second. Use it well.",
            5, ONE_TB / 4, 3.0f
        ),
        UpgradeType.SONAR_RANGE to UpgradeConfig(
            UpgradeType.SONAR_RANGE, "WIDE_PING.BAT", "Sonar Scan Radius +20%.",
            "See threats coming from much further away.",
            "The echo travels further than the eye can see.",
            5, 400L, 1.7f
        ),
        UpgradeType.SILENT_SONAR to UpgradeConfig(
            UpgradeType.SILENT_SONAR, "ACOUSTIC_DAMPING.EXE", "Sonar Sound Alert Range -20%.",
            "Enemies won't hear your sonar pings, keeping you hidden.",
            "A silent scream in the digital dark. Only you hear the truth.",
            5, 1000L, 1.8f
        ),
        UpgradeType.SONAR_DUR to UpgradeConfig(
            UpgradeType.SONAR_DUR, "PERSISTENT_TRACE.BIN", "Enemies stay visible +1.5s.",
            "Reduces the need for frequent pings, saving focus/energy.",
            "The memory of the enemy lingers on your reticle.",
            5, 500L, 1.6f
        ),
        UpgradeType.RESONANCE_CHAMBER to UpgradeConfig(
            UpgradeType.RESONANCE_CHAMBER, "RESONANCE_CHAMBER.IO", "Clean Sweep Chain Radius +0.5m.",
            "Increases the range of cascading compiler destructions.",
            "Vibrations in the network. One fall, they all fall.",
            5, 1500L, 2.0f
        )
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
    fun getShieldRecoveryMultiplier(): Float = 1.0f / (1.0f + getLevel(UpgradeType.SHIELD_RECOVERY) * 0.15f)
    
    fun getSpikeCooldownMultiplier(): Float = 1.0f / (1.0f + getLevel(UpgradeType.SPIKE_PAYLOAD) * 0.15f)
    
    // SONAR COOLDOWN: Base 3s, -10% per level (Level 5 = 1.5s)
    fun getPulseCooldownMultiplier(): Float = 1.0f - (getLevel(UpgradeType.PULSE_FREQUENCY) * 0.10f)
    
    // TRAP COOLDOWN: Base 8s, -15% per level (Level 5 = 2s)
    fun getTrapCooldownMultiplier(): Float = 1.0f - (getLevel(UpgradeType.TRAP_COOLDOWN) * 0.15f)

    fun getSonarRangeMultiplier(): Float = 1.0f + (getLevel(UpgradeType.SONAR_RANGE) * 0.20f)
    fun getSonarSilenceMultiplier(): Float = 1.0f - (getLevel(UpgradeType.SILENT_SONAR) * 0.20f)
    fun getSonarDurationBonus(): Float = 2.0f + (getLevel(UpgradeType.SONAR_DUR) * 1.5f) // Base 2.0s + bonus

    fun getRewardBonusPercent(): Float = getLevel(UpgradeType.DATA_SYNDICATE) * 4.0f
    fun getCritDamageMultiplier(): Int = if (getLevel(UpgradeType.KINETIC_OVERLOAD) > 0) 3 else 2
}
