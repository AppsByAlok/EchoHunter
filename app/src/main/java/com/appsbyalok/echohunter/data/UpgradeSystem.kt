package com.appsbyalok.echohunter.data

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.pow

enum class UpgradeType {
    // ARCHITECT (SYSTEM LOGIC)
    DATA_MAGNET,        
    COMPRESSION_ALGO,
    DATA_SYNDICATE,     // ELITE: Data Harvest Yield
    OVERCLOCK_DUR,      // Extends Overclock
    OPTIC_SENSORS,      // Passive Vision Radius
    
    // ENFORCER (COMBAT LOGIC)
    CRIT_CHANCE,        
    MULTITHREAD_SPIKES, 
    COMBO_EXTENDER,     // Longer combo windows
    KINETIC_OVERLOAD,   // Mini-explosions on hit
    
    // GHOST (TACTICAL LOGIC)
    PULSE_FREQUENCY,    
    TRAP_COOLDOWN,      
    GHOST_PROTOCOL,      // ELITE: Massive I-Frames
    SONAR_RANGE,        // Increased Pulse Radius
    SILENT_SONAR,       // Reduced Enemy Alert Range
    SONAR_DUR,           // Enemies stay visible longer
    RESONANCE_CHAMBER,   // Clean Sweep Chain Radius
    
    // --- FIRMWARE PATCHES (MARKET EXCLUSIVES) ---
    PATCH_OVERCLOCK_REGEN, // +25% Overclock charge rate
    PATCH_HEALTH_SIPHON,   // 5% chance to heal 1 HP on enemy kill
    PATCH_SHIELD_BURST,    // EMP Shockwave on shield depletion
    PATCH_CRIT_VAMP,       // Lifesteal on Critical Hits
    PATCH_DATA_OVERFLOW,   // 2x Data from Bosses
    PATCH_COMBO_SHIELD     // Shield recharge on Combo x10
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
        
    // --- TRAPS & UTILITY BRANCH ---
    UpgradeType.TRAP_COOLDOWN to UpgradeConfig(
        UpgradeType.TRAP_COOLDOWN, "OVERCLOCK_DECOY.SYS", "Decoy Cooldown Speed +15%.",
        "Deploy decoys more frequently to distract pursuers.",
        "False flags and digital phantoms. They're chasing a shadow.",
        5, 800L, 1.7f
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
        ),
        
        // --- FIRMWARE PATCHES ---
        UpgradeType.PATCH_OVERCLOCK_REGEN to UpgradeConfig(
            UpgradeType.PATCH_OVERCLOCK_REGEN, "PATCH_OV_GEN.FW", "Overclock Charge Rate +25%.",
            "Significantly reduces downtime between Overclock activations.",
            "A customized firmware patch that optimizes energy routing to the core.",
            1, 2500000L, 1.0f // 2.5 GB
        ),
        UpgradeType.PATCH_HEALTH_SIPHON to UpgradeConfig(
            UpgradeType.PATCH_HEALTH_SIPHON, "PATCH_SIPHON.FW", "5% Heal Chance on Kill.",
            "Adds a lifesteal-like capability to your attack modules.",
            "Experimental code that repurposes enemy data fragments for repairs.",
            1, 5000000L, 1.0f // 5 GB
        ),
        UpgradeType.PATCH_SHIELD_BURST to UpgradeConfig(
            UpgradeType.PATCH_SHIELD_BURST, "PATCH_BURST.FW", "Shield Break Shockwave.",
            "Releases a localized EMP burst when your shield is depleted.",
            "Weaponizing failure. If the shield drops, everyone else goes with it.",
            1, 3500000L, 1.0f // 3.5 GB
        ),
        UpgradeType.PATCH_CRIT_VAMP to UpgradeConfig(
            UpgradeType.PATCH_CRIT_VAMP, "PATCH_VAMP.FW", "Lifesteal on Critical Hits.",
            "Critical strikes have a 20% chance to restore 2 HP.",
            "Feeding on the flaws in their code. Their destruction is your vitality.",
            1, 7500000L, 1.0f // 7.5 GB
        ),
        UpgradeType.PATCH_DATA_OVERFLOW to UpgradeConfig(
            UpgradeType.PATCH_DATA_OVERFLOW, "PATCH_OVERFLOW.FW", "2x Boss Data Drops.",
            "Doubles the amount of data harvested from Boss-class entities.",
            "Removing the limiters on data ingestion protocols.",
            1, 10000000L, 1.0f // 10 GB
        ),
        UpgradeType.PATCH_COMBO_SHIELD to UpgradeConfig(
            UpgradeType.PATCH_COMBO_SHIELD, "PATCH_C_SHIELD.FW", "Shield Regen on Combo x10.",
            "Instantly restores 10% shield when hitting a 10x combo.",
            "Momentum is the best defense. Keep the rhythm, keep the shield.",
            1, 4500000L, 1.0f // 4.5 GB
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
    fun getBonusMaxHp(): Int {
        if (!SaveManager.isNodeUnlocked("d_plating")) return 0
        return SaveManager.getStatLevel("d_plating", "hp") - 1
    }

    fun getBonusOverclockTime(): Float = getLevel(UpgradeType.OVERCLOCK_DUR) * 1.0f
    fun getRegenInterval(): Float {
        if (!SaveManager.isNodeUnlocked("d_plating")) return -1f
        val level = SaveManager.getStatLevel("d_plating", "regen")
        if (level <= 1) return -1f
        // Level 2: 10s, Level 5: 4s (Slower, balanced for hardware)
        return 12f - level * 2f
    }
    fun getComboBonusTime(): Float = getLevel(UpgradeType.COMBO_EXTENDER) * 2.0f
    fun getRewardMultiplier(): Float = 1.0f + (getLevel(UpgradeType.DATA_SYNDICATE) * 4.0f)
    fun getMultiShotCount(): Int = getLevel(UpgradeType.MULTITHREAD_SPIKES)
    fun getCritChance(): Float = getLevel(UpgradeType.CRIT_CHANCE) * 0.10f

    fun getIframeDurationBonus(): Float = getLevel(UpgradeType.GHOST_PROTOCOL) * 1.5f
    fun getSpeedMultiplier(): Float {
        // Core is always unlocked. Base speed +5% per sync level.
        return 1.0f + (SaveManager.getStatLevel("core", "speed") - 1) * 0.05f
    }
    fun getVisionRadiusMultiplier(): Float = 1.0f + (getLevel(UpgradeType.OPTIC_SENSORS) * 0.15f)
    fun getDataMagnetRadiusMultiplier(): Float = 1.0f + (getLevel(UpgradeType.DATA_MAGNET)) * 0.5f
    fun getStealthDetectionMultiplier(): Float {
        if (!SaveManager.isNodeUnlocked("u_cloak")) return 1.0f
        // Reduces detection range by 10% per level.
        return 1.0f - (SaveManager.getStatLevel("u_cloak", "efficiency") - 1) * 0.10f
    }

    fun getShieldRecoveryMultiplier(): Float {
        if (!SaveManager.isNodeUnlocked("d_shield")) return 1.0f
        // Base 5s. Each level reduces recharge time by 10%
        return 1.0f - (SaveManager.getStatLevel("d_shield", "regen") - 1) * 0.10f
    }

    fun getShieldMaxDuration(): Float {
        if (!SaveManager.isNodeUnlocked("d_shield")) return 5.0f
        // Base 5s + 1s per capacity level
        return 5.0f + (SaveManager.getStatLevel("d_shield", "cap") - 1) * 1.0f
    }
    
    fun getSpikeCooldownMultiplier(): Float {
        // SPIKE DRIVER (w_blaster) is always unlocked.
        return 1.0f - (SaveManager.getStatLevel("w_blaster", "spd") - 1) * 0.05f
    }

    fun getShotgunSpreadMultiplier(): Float {
        if (!SaveManager.isNodeUnlocked("w_shotgun")) return 1.0f
        // Reduces spread by 10% per level (Level 5 = 60% of base spread)
        return 1.0f - (SaveManager.getStatLevel("w_shotgun", "spread") - 1) * 0.10f
    }

    fun getSniperChargeSpeedMultiplier(): Float {
        if (!SaveManager.isNodeUnlocked("w_sniper")) return 1.0f
        // Increases charge speed by 20% per level
        return 1.0f + (SaveManager.getStatLevel("w_sniper", "charge_spd") - 1) * 0.20f
    }

    fun getTrapRangeMultiplier(trapId: Int): Float {
        return when (trapId) {
            1, 4 -> { // Decoy / Sonic Decoy
                if (!SaveManager.isNodeUnlocked("u_decoy")) 1.0f
                else 1.0f + (SaveManager.getStatLevel("u_decoy", "range") - 1) * 0.15f
            }
            2 -> { // EMP
                if (!SaveManager.isNodeUnlocked("u_emp")) 1.0f
                else 1.0f + (SaveManager.getStatLevel("u_emp", "radius") - 1) * 0.20f
            }
            else -> 1.0f
        }
    }

    /**
     * Calculates the Global Combat Multiplier derived from the 'Exploit' (CRIT_CHANCE) level
     * and a Hardware Mastery bonus that scales based on all combat-related upgrades.
     * 
     * Formula: (Base Exploit) * (1 + Hardware Synergy Sum)
     * This ensures that every upgrade purchased in the Loadout provides a tangible 
     * boost to overall combat effectiveness.
     */
    fun getGlobalDamageMultiplier(): Float {
        // 1. Exploit Efficiency: EXPLOIT_KIT.SH level provides the primary multiplier.
        val exploitLevel = getLevel(UpgradeType.CRIT_CHANCE)
        val exploitBase = 1.0f + (exploitLevel * 0.12f) // Up to 1.6x at Level 5

        // 2. Hardware Synergy: Mastery bonus from all combat-related hardware levels.
        // We sum all 'dmg', 'spd', and 'charge' stats across weapons and traps.
        val masteryLevels = (SaveManager.getStatLevel("w_blaster", "dmg") - 1) +
                           (SaveManager.getStatLevel("w_blaster", "spd") - 1) +
                           (SaveManager.getStatLevel("w_shotgun", "dmg") - 1) +
                           (SaveManager.getStatLevel("w_sniper", "charge_dmg") - 1) +
                           (SaveManager.getStatLevel("u_emp", "dmg") - 1)
        
        // 3. System Protocol Synergy: Small bonus from other Enforcer/Ghost combat upgrades.
        val protocolLevels = getLevel(UpgradeType.MULTITHREAD_SPIKES) + 
                            getLevel(UpgradeType.KINETIC_OVERLOAD) +
                            getLevel(UpgradeType.RESONANCE_CHAMBER)

        val synergyMult = 1.0f + (masteryLevels * 0.035f) + (protocolLevels * 0.02f)
        
        return exploitBase * synergyMult
    }

    fun getWeaponDamage(weaponId: Int): Float {
        val globalMult = getGlobalDamageMultiplier()
        val baseDamage = when (weaponId) {
            0 -> 1.0f + (SaveManager.getStatLevel("w_blaster", "dmg") - 1) * 0.5f
            1 -> 1.2f + (SaveManager.getStatLevel("w_shotgun", "dmg") - 1) * 0.45f
            2 -> 3.5f + (SaveManager.getStatLevel("w_sniper", "charge_dmg") - 1) * 2.5f
            else -> 1.0f
        }
        val routeMultiplier = if (weaponId == 2 && SaveManager.isNodeUnlocked("sniper_arc")) 1.15f else 1.0f
        return baseDamage * globalMult * routeMultiplier
    }

    fun getSniperProjectileSpeedMultiplier(): Float =
        if (SaveManager.isNodeUnlocked("sniper_rail")) 1.45f else 1.0f

    fun getSniperBeamWidthMultiplier(): Float =
        if (SaveManager.isNodeUnlocked("sniper_arc")) 1.8f else if (SaveManager.isNodeUnlocked("sniper_rail")) 0.8f else 1.0f

    fun getTrapPower(trapId: Int): Float {
        val globalMult = getGlobalDamageMultiplier()
        return when (trapId) {
            2 -> (5.0f + (SaveManager.getStatLevel("u_emp", "dmg") - 1) * 2.5f) * globalMult // EMP Damage
            else -> 0f
        }
    }
    
    // SONAR COOLDOWN: Base 3s, -10% per level (Level 5 = 1.5s)
    fun getPulseCooldownMultiplier(): Float = 1.0f - (getLevel(UpgradeType.PULSE_FREQUENCY) * 0.10f)
    
    // TRAP COOLDOWN: Base 8s, -15% per level (Level 5 = 2s)
    fun getTrapCooldownMultiplier(): Float = 1.0f - (getLevel(UpgradeType.TRAP_COOLDOWN) * 0.15f)

    fun getSonarRangeMultiplier(): Float = 1.0f + (getLevel(UpgradeType.SONAR_RANGE) * 0.20f)
    fun getSonarSilenceMultiplier(): Float = 1.0f - (getLevel(UpgradeType.SILENT_SONAR) * 0.20f)
    fun getSonarDurationBonus(): Float = 2.0f + (getLevel(UpgradeType.SONAR_DUR) * 1.5f) // Base 2.0s + bonus

    fun getRewardBonusPercent(): Float = getLevel(UpgradeType.DATA_SYNDICATE) * 4.0f
    fun getCritDamageMultiplier(): Int = if (getLevel(UpgradeType.KINETIC_OVERLOAD) > 0) 3 else 2

    // FIRMWARE ACCESSORS
    fun hasOverclockRegenPatch(): Boolean = getLevel(UpgradeType.PATCH_OVERCLOCK_REGEN) > 0
    fun hasHealthSiphonPatch(): Boolean = getLevel(UpgradeType.PATCH_HEALTH_SIPHON) > 0
    fun hasShieldBurstPatch(): Boolean = getLevel(UpgradeType.PATCH_SHIELD_BURST) > 0
    fun hasCritVampPatch(): Boolean = getLevel(UpgradeType.PATCH_CRIT_VAMP) > 0
    fun hasDataOverflowPatch(): Boolean = getLevel(UpgradeType.PATCH_DATA_OVERFLOW) > 0
    fun hasComboShieldPatch(): Boolean = getLevel(UpgradeType.PATCH_COMBO_SHIELD) > 0
}
