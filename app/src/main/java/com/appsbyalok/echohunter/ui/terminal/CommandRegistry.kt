package com.appsbyalok.echohunter.ui.terminal

import com.appsbyalok.echohunter.engine.GameState

object CommandRegistry {
    private val commands = mutableMapOf<String, TerminalCommand>()

    /**
     * Register a new command to the terminal system.
     */
    fun register(command: TerminalCommand) {
        commands[command.name.uppercase()] = command
        command.aliases.forEach { alias ->
            commands[alias.uppercase()] = command
        }
    }

    /**
     * Get a command by its name or alias.
     */
    fun getCommand(name: String): TerminalCommand? = commands[name.uppercase()]

    /**
     * Get all currently unlocked commands.
     */
    fun getAllCommands(gs: GameState): List<TerminalCommand> {
        return commands.values.distinct().filter { it.isUnlocked(gs) }
    }

    /**
     * Clear registry (useful for testing or full re-initialization).
     */
    fun clear() {
        commands.clear()
    }
}
