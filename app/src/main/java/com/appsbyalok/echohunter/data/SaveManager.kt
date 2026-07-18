package com.appsbyalok.echohunter.data

import android.content.Context
import android.content.SharedPreferences
import com.appsbyalok.echohunter.input.HudAction
import com.appsbyalok.echohunter.input.HudControl
import com.appsbyalok.echohunter.input.HudInputBehavior
import com.appsbyalok.echohunter.input.HudLayoutProfile
import com.appsbyalok.echohunter.input.HudVisualType
import com.appsbyalok.echohunter.input.ManualAimControl
import com.appsbyalok.echohunter.input.MovementControl
import com.appsbyalok.echohunter.input.MovementMode
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.max

object SaveManager {
    private lateinit var prefs: SharedPreferences

    var dataCoinsKB: Long = 0L
        private set

    var totalData: Long = 0L
        private set

    var maxCampaignLevel: Int = 1
        private set

    var isAutoNextLevelEnabled: Boolean = false
        private set

    // --- GAME SETTINGS ---
    var isSoundEnabled: Boolean = true
        private set
    var isVibrationEnabled: Boolean = true
        private set
    var isEffectsEnabled: Boolean = true
        private set

    // --- ORIENTATION SETTINGS ---
    // 0: Auto-Rotate, 1: Portrait, 2: Landscape, 3: Device Default
    var screenOrientation: Int = 3
        private set

    // --- TERMINAL SETTINGS ---
    var terminalTheme: String = "DARK"
        set(value) {
            field = value
            prefs.edit().putString("terminalTheme", value).apply()
        }
    var typewriterSpeed: Int = 3
        set(value) {
            field = value
            prefs.edit().putInt("typewriterSpeed", value).apply()
        }
    var fontSize: String = "NORMAL"
        set(value) {
            field = value
            prefs.edit().putString("fontSize", value).apply()
        }

    var activeAttackMode: Int = 1 // Default to AUTO_AIM (index 1)
        private set
    var activeWeapon: Int = 1
        private set
    var activeTrap: Int = 2
        private set
    var isAutoPilotEnabled: Boolean = false
        private set

    // Temporary session cache for UI layout (Notch handling)
    var lastInsetTop = 0f
    var lastInsetBottom = 0f
    var lastInsetLeft = 0f
    var lastInsetRight = 0f

    fun setAutoNextLevel(enabled: Boolean) {
        isAutoNextLevelEnabled = enabled
        prefs.edit().putBoolean("isAutoNextLevelEnabled", isAutoNextLevelEnabled).apply()
    }

    fun setSoundEnabled(enabled: Boolean) {
        isSoundEnabled = enabled
        prefs.edit().putBoolean("isSoundEnabled", isSoundEnabled).apply()
    }

    fun setVibrationEnabled(enabled: Boolean) {
        isVibrationEnabled = enabled
        prefs.edit().putBoolean("isVibrationEnabled", isVibrationEnabled).apply()
    }

    fun setEffectsEnabled(enabled: Boolean) {
        isEffectsEnabled = enabled
        prefs.edit().putBoolean("isEffectsEnabled", isEffectsEnabled).apply()
    }

    fun setScreenOrientation(mode: Int) {
        screenOrientation = mode
        prefs.edit().putInt("screenOrientation", screenOrientation).apply()
    }

    fun setAttackMode(mode: Int) {
        activeAttackMode = mode
        prefs.edit().putInt("attackMode", activeAttackMode).apply()
    }

    fun setActiveWeapon(weapon: Int) {
        activeWeapon = weapon
        prefs.edit().putInt("activeWeapon", activeWeapon).apply()
    }

    fun setActiveTrap(trap: Int) {
        activeTrap = trap
        prefs.edit().putInt("activeTrap", activeTrap).apply()
    }

    fun setAutoPilotEnabled(enabled: Boolean) {
        isAutoPilotEnabled = enabled
        prefs.edit().putBoolean("isAutoPilotEnabled", isAutoPilotEnabled).apply()
    }

    fun loadHudLayoutProfile(isPortrait: Boolean): HudLayoutProfile {
        val key = if (isPortrait) HUD_LAYOUT_PORTRAIT_KEY else HUD_LAYOUT_LANDSCAPE_KEY
        val saved = prefs.getString(key, null) ?: return HudLayoutProfile.defaults(isPortrait)
        return runCatching { decodeHudLayout(saved) }.getOrElse { HudLayoutProfile.defaults(isPortrait) }
    }

    fun saveHudLayoutProfile(isPortrait: Boolean, profile: HudLayoutProfile) {
        val key = if (isPortrait) HUD_LAYOUT_PORTRAIT_KEY else HUD_LAYOUT_LANDSCAPE_KEY
        prefs.edit().putString(key, encodeHudLayout(profile.copyMutable().normalize())).apply()
    }

    fun resetHudLayoutProfile(isPortrait: Boolean) {
        val key = if (isPortrait) HUD_LAYOUT_PORTRAIT_KEY else HUD_LAYOUT_LANDSCAPE_KEY
        prefs.edit().remove(key).apply()
    }

    private fun encodeHudLayout(profile: HudLayoutProfile): String {
        val root = JSONObject().put("version", 1)
        root.put("movement", JSONObject().apply {
            put("mode", profile.movement.mode.name)
            put("x", profile.movement.x.toDouble())
            put("y", profile.movement.y.toDouble())
            put("scale", profile.movement.scale.toDouble())
            put("left", profile.movement.zoneLeft.toDouble())
            put("top", profile.movement.zoneTop.toDouble())
            put("right", profile.movement.zoneRight.toDouble())
            put("bottom", profile.movement.zoneBottom.toDouble())
        })
        root.put("manualAim", JSONObject().apply {
            put("mode", profile.manualAim.mode.name)
            put("x", profile.manualAim.x.toDouble())
            put("y", profile.manualAim.y.toDouble())
            put("scale", profile.manualAim.scale.toDouble())
            put("left", profile.manualAim.zoneLeft.toDouble())
            put("top", profile.manualAim.zoneTop.toDouble())
            put("right", profile.manualAim.zoneRight.toDouble())
            put("bottom", profile.manualAim.zoneBottom.toDouble())
        })
        root.put("controls", JSONArray().apply {
            profile.controls.forEach { control -> put(JSONObject().apply {
                put("id", control.id)
                put("action", control.action.name)
                put("x", control.x.toDouble())
                put("y", control.y.toDouble())
                put("scale", control.scale.toDouble())
                put("visual", control.visualType.name)
                put("input", control.inputBehavior.name)
            }) }
        })
        return root.toString()
    }

    private fun decodeHudLayout(raw: String): HudLayoutProfile {
        val root = JSONObject(raw)
        if (root.optInt("version", 0) != 1) error("Unsupported HUD layout version")
        val movementJson = root.getJSONObject("movement")
        val movement = MovementControl(
            mode = enumOrDefault(movementJson.optString("mode"), MovementMode.FLOATING),
            x = movementJson.optDouble("x", 0.18).toFloat(),
            y = movementJson.optDouble("y", 0.82).toFloat(),
            scale = movementJson.optDouble("scale", 1.0).toFloat(),
            zoneLeft = movementJson.optDouble("left", 0.0).toFloat(),
            zoneTop = movementJson.optDouble("top", 0.35).toFloat(),
            zoneRight = movementJson.optDouble("right", 0.5).toFloat(),
            zoneBottom = movementJson.optDouble("bottom", 1.0).toFloat()
        )
        val manualAimJson = root.optJSONObject("manualAim")
        val manualAim = ManualAimControl(
            mode = enumOrDefault(manualAimJson?.optString("mode") ?: "", MovementMode.FLOATING),
            x = manualAimJson?.optDouble("x", 0.76)?.toFloat() ?: 0.76f,
            y = manualAimJson?.optDouble("y", 0.68)?.toFloat() ?: 0.68f,
            scale = manualAimJson?.optDouble("scale", 1.0)?.toFloat() ?: 1f,
            zoneLeft = manualAimJson?.optDouble("left", 0.5)?.toFloat() ?: 0.5f,
            zoneTop = manualAimJson?.optDouble("top", 0.28)?.toFloat() ?: 0.28f,
            zoneRight = manualAimJson?.optDouble("right", 1.0)?.toFloat() ?: 1f,
            zoneBottom = manualAimJson?.optDouble("bottom", 1.0)?.toFloat() ?: 1f
        )
        val controls = root.getJSONArray("controls").let { array ->
            MutableList(array.length()) { index ->
                val item = array.getJSONObject(index)
                HudControl(
                    id = item.optString("id", "control_$index"),
                    action = enumOrDefault(item.optString("action"), HudAction.ATTACK),
                    x = item.optDouble("x", 0.5).toFloat(),
                    y = item.optDouble("y", 0.5).toFloat(),
                    scale = item.optDouble("scale", 1.0).toFloat(),
                    visualType = enumOrDefault(item.optString("visual"), HudVisualType.STANDARD),
                    inputBehavior = enumOrDefault(item.optString("input"), HudInputBehavior.TAP)
                )
            }
        }
        return HudLayoutProfile(controls, movement, manualAim).normalize()
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
    // --- NAYA STREAK SYSTEM (ROGUELITE) ---
    var currentStoryStreak: Int = 0
        private set
    var unlockedStoryStreak: Int = 0
        private set

    var currentHardStreak: Int = 0
        private set
    var unlockedHardStreak: Int = 0
        private set

    val isStoryModeUnlocked: Boolean
        get() = maxCampaignLevel > 15

    // Hard Mode unlocks permanently after clearing all 3 Acts (Streak >= 3)
    val isHardModeUnlocked: Boolean
        get() = unlockedStoryStreak >= 3

    val isManualAimUnlocked: Boolean
        get() = unlockedStoryStreak >= 1 || maxCampaignLevel > 20

    var highScore: Long = 0L
        private set
    var previousScore: Long = 0L
        private set

    // --- TUTORIAL SETTINGS ---
    var isUiTutorialSeen: Boolean = false
        private set
    var isGameTutorialCompleted: Boolean = false
        private set
    var tutorialStep: Int = 0 // For tracking specific steps in game tutorial

    fun setUiTutorialSeen(seen: Boolean) {
        isUiTutorialSeen = seen
        prefs.edit().putBoolean("isUiTutorialSeen", seen).apply()
    }

    fun setGameTutorialCompleted(completed: Boolean) {
        isGameTutorialCompleted = completed
        prefs.edit().putBoolean("isGameTutorialCompleted", completed).apply()
    }

    fun resetTutorials() {
        isUiTutorialSeen = false
        isGameTutorialCompleted = false
        tutorialStep = 0
        prefs.edit()
            .putBoolean("isUiTutorialSeen", false)
            .putBoolean("isGameTutorialCompleted", false)
            .putInt("tutorialStep", 0)
            .apply()
    }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("EchoSaveInfo", Context.MODE_PRIVATE)

        dataCoinsKB = getLongSafe("dataCoinsKB")
        totalData = getLongSafe("totalData")
        maxCampaignLevel = prefs.getInt("maxCampaignLevel", 1)
        isAutoNextLevelEnabled = prefs.getBoolean("isAutoNextLevelEnabled", false)
        isSoundEnabled = prefs.getBoolean("isSoundEnabled", true)
        isVibrationEnabled = prefs.getBoolean("isVibrationEnabled", true)
        isEffectsEnabled = prefs.getBoolean("isEffectsEnabled", true)
        screenOrientation = prefs.getInt("screenOrientation", 0)
        
        terminalTheme = prefs.getString("terminalTheme", "DARK") ?: "DARK"
        typewriterSpeed = prefs.getInt("typewriterSpeed", 3)
        fontSize = prefs.getString("fontSize", "NORMAL") ?: "NORMAL"

        activeAttackMode = prefs.getInt("attackMode", 1) // Default to AUTO_AIM
        activeWeapon = prefs.getInt("activeWeapon", 1)
        activeTrap = prefs.getInt("activeTrap", 2)
        isAutoPilotEnabled = prefs.getBoolean("isAutoPilotEnabled", false)

        // Load Tutorials
        isUiTutorialSeen = prefs.getBoolean("isUiTutorialSeen", false)
        isGameTutorialCompleted = prefs.getBoolean("isGameTutorialCompleted", false)
        tutorialStep = prefs.getInt("tutorialStep", 0)

        // Load Streaks
        currentStoryStreak = prefs.getInt("currStory", 0)
        unlockedStoryStreak = prefs.getInt("unlockStory", 0)
        currentHardStreak = prefs.getInt("currHard", 0)
        unlockedHardStreak = prefs.getInt("unlockHard", 0)

        highScore = getLongSafe("highScore")
        previousScore = getLongSafe("previousScore")
    }

    /**
     * Safely retrieves a Long value from SharedPreferences.
     * If the value was previously stored as an Int (migration), it converts and saves it as a Long.
     */
    private fun getLongSafe(key: String, defaultValue: Long = 0L): Long {
        return try {
            // Case 1: Value is already Long (Normal flow)
            prefs.getLong(key, defaultValue)
        } catch (_: Exception) {
            try {
                // Case 2: Value is Int (Legacy data)
                val legacyValue = prefs.getInt(key, defaultValue.toInt())
                val migratedValue = legacyValue.toLong()
                
                // Auto-migrate: overwrite with Long for next time
                prefs.edit().putLong(key, migratedValue).apply()
                migratedValue
            } catch (_: Exception) {
                // Case 3: Value is something else or corrupted
                defaultValue
            }
        }
    }

    fun addData(kbAmount: Long) {
        dataCoinsKB += kbAmount
        totalData = (dataCoinsKB / 1024L)
        prefs.edit()
            .putLong("dataCoinsKB", dataCoinsKB)
            .putLong("totalData", totalData)
            .apply()
    }

    fun spendData(kbCost: Long): Boolean {
        if (dataCoinsKB >= kbCost) {
            dataCoinsKB -= kbCost
            totalData = (dataCoinsKB / 1024L)
            prefs.edit()
                .putLong("dataCoinsKB", dataCoinsKB)
                .putLong("totalData", totalData)
                .apply()
            return true
        }
        return false
    }

    fun updateCampaignProgress(levelCleared: Int) {
        if (levelCleared >= maxCampaignLevel) {
            maxCampaignLevel = if (levelCleared < Int.MAX_VALUE) levelCleared + 1 else Int.MAX_VALUE
            prefs.edit().putInt("maxCampaignLevel", maxCampaignLevel).apply()
        }
    }

    fun saveLevelStats(level: Int, timeSeconds: Float, stars: Int, recordsMask: Int = 0, isHard: Boolean = false) {
        val existingStars = prefs.getInt("lvl_${level}_stars", 0)
        val existingTime = prefs.getFloat("lvl_${level}_time", Float.MAX_VALUE)
        val existingRecords = prefs.getInt("lvl_${level}_records", 0)

        val editor = prefs.edit()
        if (stars > existingStars) {
            editor.putInt("lvl_${level}_stars", stars)
        }
        if (timeSeconds < existingTime) {
            editor.putFloat("lvl_${level}_time", timeSeconds)
        }
        
        // Merge records (logical OR) so once a record is achieved, it's permanent
        editor.putInt("lvl_${level}_records", existingRecords or recordsMask)

        // Save mode-specific stats for "Compare Stats" feature
        val modeKey = if (isHard) "hard" else "norm"
        val modeStars = prefs.getInt("lvl_${level}_stars_$modeKey", 0)
        val modeTime = prefs.getFloat("lvl_${level}_time_$modeKey", Float.MAX_VALUE)
        
        if (stars > modeStars) editor.putInt("lvl_${level}_stars_$modeKey", stars)
        if (timeSeconds < modeTime) editor.putFloat("lvl_${level}_time_$modeKey", timeSeconds)
        
        editor.apply()
    }

    fun getLevelStars(level: Int, isHard: Boolean? = null): Int {
        return if (isHard == null) prefs.getInt("lvl_${level}_stars", 0)
        else prefs.getInt("lvl_${level}_stars_${if (isHard) "hard" else "norm"}", 0)
    }

    fun getLevelTime(level: Int, isHard: Boolean? = null): Float {
        return if (isHard == null) prefs.getFloat("lvl_${level}_time", 0f)
        else prefs.getFloat("lvl_${level}_time_${if (isHard) "hard" else "norm"}", 0f)
    }

    fun getLevelRecords(level: Int): Int = prefs.getInt("lvl_${level}_records", 0)
    fun getLevelAttempts(level: Int): Int = prefs.getInt("lvl_${level}_attempts", 0)

    fun incrementLevelAttempts(level: Int, isHard: Boolean = false) {
        val attempts = getLevelAttempts(level)
        val modeKey = if (isHard) "hard" else "norm"
        val modeAttempts = prefs.getInt("lvl_${level}_attempts_$modeKey", 0)
        
        prefs.edit()
            .putInt("lvl_${level}_attempts", attempts + 1)
            .putInt("lvl_${level}_attempts_$modeKey", modeAttempts + 1)
            .apply()
    }

    fun getLevelAttempts(level: Int, isHard: Boolean): Int = prefs.getInt("lvl_${level}_attempts_${if (isHard) "hard" else "norm"}", 0)

    fun getFinishedLevelIds(): List<Int> {
        val ids = mutableListOf<Int>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("lvl_") && key.endsWith("_stars") && value is Int) {
                if (value > 0) {
                    val idStr = key.substring(4, key.length - 6)
                    idStr.toIntOrNull()?.let { ids.add(it) }
                }
            }
        }
        return ids
    }

    fun getGlobalStats(): Pair<Int, Int> {
        var totalStars = 0
        var levelsCleared = 0
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("lvl_") && key.endsWith("_stars") && value is Int) {
                if (value > 0) {
                    levelsCleared++
                    totalStars += value
                }
            }
        }
        return Pair(levelsCleared, totalStars)
    }

    // --- STREAK PROGRESSION LOGIC ---
    fun updateStoryStreak(won: Boolean, isHardMode: Boolean, playedAct: Int) {
        if (won) {
            if (!isHardMode) {
                // If they played the exact Act they needed to beat
                if (playedAct == currentStoryStreak) {
                    currentStoryStreak++
                    if (currentStoryStreak > unlockedStoryStreak) {
                        unlockedStoryStreak = currentStoryStreak
                    }
                }
            } else {
                if (playedAct == currentHardStreak) {
                    currentHardStreak++
                    if (currentHardStreak > unlockedHardStreak) {
                        unlockedHardStreak = currentHardStreak
                    }
                }
            }
        } else {
            // Loss resets current streak back to 0, but Unlocked stays safe forever!
            if (!isHardMode) currentStoryStreak = 0
            else currentHardStreak = 0
        }

        prefs.edit()
            .putInt("currStory", currentStoryStreak)
            .putInt("unlockStory", unlockedStoryStreak)
            .putInt("currHard", currentHardStreak)
            .putInt("unlockHard", unlockedHardStreak)
            .apply()
    }

    fun saveRunResult(currentScore: Long) {
        previousScore = currentScore
        if (currentScore > highScore) {
            highScore = currentScore
        }
        prefs.edit()
            .putLong("previousScore", previousScore)
            .putLong("highScore", highScore)
            .apply()
    }

    fun formatDataString(kb: Long): String {
        if (kb < 1024L) return "$kb KB"
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US,"%.1f MB", mb)
        val gb = mb / 1024.0
        if (gb < 1024.0) return String.format(Locale.US,"%.2f GB", gb)
        val tb = gb / 1024.0
        if (tb < 1024.0) return String.format(Locale.US,"%.2f TB", tb)
        val pb = tb / 1024.0
        return String.format(Locale.US,"%.2f PB", pb)
    }

    fun debugSetLevel(level: Int) {
        maxCampaignLevel = max(1, level)
        prefs.edit().putInt("maxCampaignLevel", maxCampaignLevel).apply()
    }

    fun debugModifyStoryStreak(delta: Int) {
        unlockedStoryStreak = max(0, unlockedStoryStreak + delta)
        prefs.edit().putInt("unlockStory", unlockedStoryStreak).apply()
    }

    fun debugUnlockAll() {
        maxCampaignLevel = Int.MAX_VALUE
        unlockedStoryStreak = 3
        unlockedHardStreak = 3
        dataCoinsKB = 999_999_999_999L // ~1 PB (Petabyte)
        totalData = (dataCoinsKB / 1024L)
        
        prefs.edit()
            .putInt("maxCampaignLevel", maxCampaignLevel)
            .putInt("unlockStory", unlockedStoryStreak)
            .putInt("unlockHard", unlockedHardStreak)
            .putLong("dataCoinsKB", dataCoinsKB)
            .putLong("totalData", totalData)
            .apply()
        
        UpgradeSystem.debugMaxAll()
    }

    fun clearAllData() {
        dataCoinsKB = 0L
        totalData = 0L
        maxCampaignLevel = 1
        isAutoNextLevelEnabled = false
        activeWeapon = 1
        activeTrap = 2
        isAutoPilotEnabled = false
        currentStoryStreak = 0
        unlockedStoryStreak = 0
        currentHardStreak = 0
        unlockedHardStreak = 0
        highScore = 0L
        previousScore = 0L

        prefs.edit().clear().apply()
        UpgradeSystem.clearAllData()
    }

    private const val HUD_LAYOUT_PORTRAIT_KEY = "hud_layout_v1_portrait"
    private const val HUD_LAYOUT_LANDSCAPE_KEY = "hud_layout_v1_landscape"
}
