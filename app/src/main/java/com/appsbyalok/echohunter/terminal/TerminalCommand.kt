package com.appsbyalok.echohunter.terminal

import com.appsbyalok.echohunter.engine.GameState

/**
 * Interface for all terminal commands.
 */
interface TerminalCommand {
    val name: String
    val description: String
    val aliases: List<String> get() = emptyList()

    /**
     * Check if the command is currently available to the player.
     */
    fun isUnlocked(gs: GameState): Boolean = true

    /**
     * Executes the command logic.
     * @return String to display in the terminal, or null if handled manually via context.
     */
    fun execute(args: List<String>, context: CommandContext): String?
}

/**
 * Context provided to commands during execution.
 * Allows commands to interact with the game state and terminal UI.
 */
data class CommandContext(
    val gameState: GameState,
    val terminalLines: MutableList<String>,
    val onExit: () -> Unit,
    val files: Map<String, String> // Virtual File System placeholder
)
