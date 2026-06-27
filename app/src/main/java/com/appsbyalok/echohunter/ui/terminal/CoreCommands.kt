package com.appsbyalok.echohunter.ui.terminal

import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import com.appsbyalok.echohunter.data.SaveManager
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
        val unlockedCmds = CommandRegistry.getAllCommands(context.gameState)
            .filter { it.isUnlocked(context.gameState) }
            .map { it.name }
            .sorted()
        
        val sb = StringBuilder("AVAILABLE COMMANDS:\n")
        unlockedCmds.chunked(4).forEach { chunk ->
            sb.append("  ${chunk.joinToString(", ")}\n")
        }
        sb.append("\nMATH: Type expression like '5 + 5'")
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
        val filename = args[0].uppercase()
        return when (filename) {
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
