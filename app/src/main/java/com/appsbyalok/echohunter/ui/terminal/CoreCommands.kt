package com.appsbyalok.echohunter.ui.terminal

import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.EchoAudioManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class HelpCommand : TerminalCommand {
    override val name = "HELP"
    override val description = "Lists available commands and usage info"
    override fun execute(args: List<String>, context: CommandContext): String {
        val gs = context.gameState
        val unlockedCmds = CommandRegistry.getAllCommands(gs)
            .filter { it.isUnlocked(gs) }
            .sortedBy { it.name }
        
        val sb = StringBuilder("=== NANO-OS HELP SYSTEM ===\n")
        sb.append("OBJECTIVE: ${if (gs.currentLevel == 1 && SaveManager.maxCampaignLevel == 1) "Access Archives or CONFIG." else "Clear Ring ${gs.currentLevel}."}\n\n")
        
        sb.append("AVAILABLE COMMANDS:\n")
        unlockedCmds.forEach { cmd ->
            sb.append("  ${cmd.name.padEnd(10)} - ${cmd.description}\n")
        }

        sb.append("\nUPCOMING UNLOCKS:\n")
        if (SaveManager.maxCampaignLevel <= 2) sb.append("  - SCAN: Ring 2\n")
        if (!SaveManager.isHardModeUnlocked) sb.append("  - SUDO/GOD/DEBUG: Complete 3 Story Acts\n")
        if (!SaveManager.isStoryModeUnlocked) sb.append("  - STORY_LOG: Reach Ring 15\n")

        sb.append("\nTIP: Type math like '12 * 4' directly.\n")
        return sb.toString()
    }
}

class ClearCommand : TerminalCommand {
    override val name = "CLEAR"
    override val description = "Clears the terminal screen"
    override val aliases = listOf("CLS", "CLR")
    override fun execute(args: List<String>, context: CommandContext): String? {
        context.terminalLines.clear()
        return null
    }
}

class ExitCommand : TerminalCommand {
    override val name = "EXIT"
    override val description = "Exits the terminal"
    override val aliases = listOf("QUIT")
    override fun execute(args: List<String>, context: CommandContext): String? {
        context.onExit()
        return null
    }
}

class DirCommand : TerminalCommand {
    override val name = "DIR"
    override val description = "Lists files in the current directory"
    override val aliases = listOf("LS")
    override fun execute(args: List<String>, context: CommandContext): String {
        val path = if (args.isNotEmpty()) args[0].uppercase() else "/ROOT"
        
        if (path != "/ROOT" && path != "ROOT" && path != "/") {
            return "ERR: DIRECTORY '$path' NOT FOUND."
        }

        val sb = StringBuilder("FILES IN $path:\n")
        context.files.forEach { (name, size) ->
            sb.append("  ${name.padEnd(18)} $size\n")
        }
        return sb.toString().trimEnd()
    }
}

class DateCommand : TerminalCommand {
    override val name = "DATE"
    override val description = "Displays the current system date and time"
    override fun execute(args: List<String>, context: CommandContext): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }
}

class VerCommand : TerminalCommand {
    override val name = "VER"
    override val description = "Displays NANO-OS version information"
    override fun execute(args: List<String>, context: CommandContext): String {
        return """
            
            NANO-OS v4.2.1-STABLE
            BUILD: 2024.11.12-RELEASE
            KERNEL: ECHO-CORE-9000
        """.trimIndent()
    }
}

class WhoamiCommand : TerminalCommand {
    override val name = "WHOAMI"
    override val description = "Displays current user and host information"
    override fun execute(args: List<String>, context: CommandContext): String {
        val auth = if (SaveManager.isHardModeUnlocked) "ROOT/ADMIN" else "USER/READ-ONLY"
        val uuid = UUID.randomUUID().toString().take(8).uppercase()
        return """
            USER: PROBE-7
            HOST: ECHO_NET_CENTRAL
            AUTH: $auth
            UUID: $uuid
        """.trimIndent()
    }
}

class ScanCommand : TerminalCommand {
    override val name = "SCAN"
    override val description = "Scans surrounding sectors for activity"
    override fun isUnlocked(gs: GameState): Boolean = gs.currentLevel >= 2 || SaveManager.maxCampaignLevel > 2
    override fun execute(args: List<String>, context: CommandContext): String {
        return """
            SCANNING NODES...
            FOUND: ${context.gameState.currentSector} ACTIVE SECTORS
            SIGNAL: STABLE
        """.trimIndent()
    }
}

class CatCommand : TerminalCommand {
    override val name = "CAT"
    override val description = "Displays the contents of a file"
    override fun execute(args: List<String>, context: CommandContext): String {
        if (args.isEmpty()) return "USAGE: CAT [FILENAME]"
        return when (val filename = args[0].uppercase()) {
            "ROOT_TERM.SH" -> """
                #!/BIN/NANO-OS
                ECHO 'INITIATING ECHO PROTOCOL...'
                EXEC ./KERNEL_DUMP.BIN --FORCE
            """.trimIndent()
            "USER_DATA.LOG" -> """
                10.24.01 | NODE_ACCESS_GRANTED
                10.24.05 | DATA_STREAM_STABLE
                10.25.12 | WARNING: ECHO_DETECTION
            """.trimIndent()
            "ECHO_PROTOCOL.CFG" -> """
                [PROTOCOL]
                VERSION=4.2
                ENCRYPTION=AES-ECHO-256
            """.trimIndent()
            "PROJECT_ECHO.DOC" -> """
                SUBJECT: PROJECT ECHO
                STATUS: PROBE-7 DEPLOYED
            """.trimIndent()
            "REDACTED_MSG.TXT" -> """
                MESSAGE: DON'T BELIEVE THE OS.
                THE ECHO IS NOT THE ENEMY.
            """.trimIndent()
            "KERNEL_DUMP.BIN" -> "ERR: CANNOT READ BINARY DATA"
            else -> "ERR: FILE '$filename' NOT FOUND"
        }
    }
}

class EchoCommand : TerminalCommand {
    override val name = "ECHO"
    override val description = "Echoes input text back to the terminal"
    override fun execute(args: List<String>, context: CommandContext): String {
        return args.joinToString(" ")
    }
}

class SudoCommand : TerminalCommand {
    override val name = "SUDO"
    override val description = "Executes a command with superuser privileges"
    override fun execute(args: List<String>, context: CommandContext): String {
        if (SaveManager.isHardModeUnlocked) {
            if (args.isEmpty()) return "USAGE: SUDO [COMMAND]"
            val subCmd = args[0].uppercase()
            return "ROOT ACCESS GRANTED. EXECUTING '$subCmd'..."
        }
        return "ERR: USER NOT IN SUDOERS FILE."
    }
}

class RebootCommand : TerminalCommand {
    override val name = "REBOOT"
    override val description = "Reboots the NANO-OS system"
    override fun execute(args: List<String>, context: CommandContext): String {
        Handler(Looper.getMainLooper()).postDelayed({
            context.terminalLines.clear()
            context.terminalLines.add("NANO-OS [Version 4.2.0-ROOT]")
            context.terminalLines.add("SYSTEM REBOOT SUCCESSFUL.")
        }, 1500)
        return "REBOOTING SYSTEM..."
    }
}

class UnlockAllCommand : TerminalCommand {
    override val name = "UNLOCK_ALL"
    override val description = "Debug: Overrides and unlocks all systems"
    override fun isUnlocked(gs: GameState): Boolean = SaveManager.isHardModeUnlocked
    override fun execute(args: List<String>, context: CommandContext): String {
        SaveManager.debugUnlockAll()
        EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 100)
        return "SYSTEM OVERRIDE SUCCESSFUL."
    }
}

class DataInjectCommand : TerminalCommand {
    override val name = "DATA_INJECT"
    override val description = "Debug: Injects 512 MB of data"
    override fun isUnlocked(gs: GameState): Boolean = SaveManager.isHardModeUnlocked
    override fun execute(args: List<String>, context: CommandContext): String {
        SaveManager.addData(512000L)
        return "PAYLOAD INJECTED: +512.0 MB"
    }
}

class SysInfoCommand : TerminalCommand {
    override val name = "SYS_INFO"
    override val description = "Displays current system resource and level status"
    override fun execute(args: List<String>, context: CommandContext): String {
        return """
            HOST: PROBE-7
            OS: NANO-OS v4.2.0
            DATA: ${SaveManager.formatDataString(SaveManager.dataCoinsKB)}
            LEVEL: ${context.gameState.currentLevel}
        """.trimIndent()
    }
}

class GodModeCommand : TerminalCommand {
    override val name = "GOD"
    override val description = "Toggles invincibility mode"
    override fun isUnlocked(gs: GameState): Boolean = SaveManager.isHardModeUnlocked
    override fun execute(args: List<String>, context: CommandContext): String {
        context.gameState.modGodMode = !context.gameState.modGodMode
        return "GOD MODE: ${if (context.gameState.modGodMode) "ENABLED" else "DISABLED"}"
    }
}

class ConfigCommand : TerminalCommand {
    override val name = "CONFIG"
    override val description = "Manage game settings: SOUND, VIBE, FX"
    override val aliases = listOf("SETTINGS", "SET")
    override fun execute(args: List<String>, context: CommandContext): String {
        if (args.isEmpty()) {
            return """
                SYSTEM CONFIGURATION:
                SOUND  : ${if (SaveManager.isSoundEnabled) "ON" else "OFF"}
                VIBE   : ${if (SaveManager.isVibrationEnabled) "ON" else "OFF"}
                FX     : ${if (SaveManager.isEffectsEnabled) "ON" else "OFF"}
                
                USAGE: CONFIG [KEY] [ON/OFF]
            """.trimIndent()
        }
        
        if (args.size < 2) return "ERR: MISSING VALUE (ON/OFF)"
        
        val key = args[0].uppercase()
        val value = args[1].uppercase() == "ON"
        
        return when (key) {
            "SOUND" -> {
                SaveManager.setSoundEnabled(value)
                "SOUND SYSTEM ${if (value) "INITIALIZED" else "DISABLED"}"
            }
            "VIBE", "VIBRATION" -> {
                SaveManager.setVibrationEnabled(value)
                "HAPTIC FEEDBACK ${if (value) "ACTIVE" else "MUTED"}"
            }
            "FX", "EFFECTS" -> {
                SaveManager.setEffectsEnabled(value)
                "VISUAL EFFECTS ${if (value) "MAXIMIZED" else "OPTIMIZED (MINIMAL)"}"
            }
            else -> "ERR: UNKNOWN CONFIG KEY '$key'"
        }
    }
}

class LevelCommand : TerminalCommand {
    override val name = "START"
    override val description = "Jump to a specific Ring (Level)"
    override val aliases = listOf("GOTO", "LEVEL")
    override fun execute(args: List<String>, context: CommandContext): String {
        if (args.isEmpty()) return "USAGE: START [LEVEL_NUMBER]"
        val level = args[0].toIntOrNull() ?: return "ERR: INVALID LEVEL NUMBER"
        
        if (level !in 1..200) return "ERR: LEVEL OUT OF RANGE (1-200)"
        
        context.gameState.currentLevel = level
        // We might want to close terminal and start the game immediately
        // But for now, we just set the level. 
        // Returning a message is better.
        return "SECTOR LOCKED. RING $level READY FOR INITIALIZATION."
    }
}

class StoryLogCommand : TerminalCommand {
    override val name = "STORY_LOG"
    override val description = "Displays all story text (requires Story completion)"
    override fun isUnlocked(gs: GameState): Boolean = SaveManager.isStoryModeUnlocked
    override fun execute(args: List<String>, context: CommandContext): String {
        if (SaveManager.unlockedStoryStreak < 3) {
            return "ERR: ENCRYPTION DETECTED. COMPLETE ALL 3 ACTS TO DECRYPT LOGS."
        }
        
        val androidContext = context.androidContext
        val sb = StringBuilder("--- DECRYPTED STORY LOGS ---\n")
        
        val allStoryRes = StoryProtocol.storyIntroLines + StoryProtocol.storyMidLines + 
                        StoryProtocol.storyPerfectEnding + StoryProtocol.storyNeutralEnding + 
                        StoryProtocol.badEndingLines
                        
        allStoryRes.forEach { resId ->
            try {
                val text = androidContext.getString(resId)
                sb.append("- $text\n\n")
            } catch (e: Exception) {
                // Skip if resource not found or other error
            }
        }
        
        return sb.toString().trim()
    }
}
