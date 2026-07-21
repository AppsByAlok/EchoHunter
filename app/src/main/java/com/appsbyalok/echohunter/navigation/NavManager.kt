package com.appsbyalok.echohunter.navigation

import android.os.Bundle
import com.appsbyalok.echohunter.engine.GameState

/**
 * Handles Global Navigation Stack for nested menus.
 * Prevents navigation traps by tracking the path taken by the user.
 */
class NavManager(private val gs: GameState) {
    private val navigationStack = mutableListOf<Int>()

    /**
     * Pushes the current state to history if it's a menu state.
     */
    fun pushCurrentState() {
        val currentState = gs.state
        // Only track menu states (0: Main, 10-17: Submenus)
        if (currentState == 0 || currentState in 10..17) {
            if (navigationStack.isEmpty() || navigationStack.last() != currentState) {
                navigationStack.add(currentState)
            }
        }
    }

    /**
     * Returns the previous state from the stack or -1 if empty.
     */
    fun popPreviousState(): Int {
        if (navigationStack.isNotEmpty()) {
            return navigationStack.removeAt(navigationStack.size - 1)
        }
        return -1
    }

    /**
     * Clears all navigation history.
     */
    fun clearHistory() {
        navigationStack.clear()
    }

    /**
     * Saves the navigation stack to a bundle for process death recovery.
     */
    fun saveState(outState: Bundle) {
        outState.putIntArray("navigationStack", navigationStack.toIntArray())
    }

    /**
     * Restores the navigation stack from a bundle.
     */
    fun restoreState(savedInstanceState: Bundle?) {
        savedInstanceState?.getIntArray("navigationStack")?.let {
            navigationStack.clear()
            navigationStack.addAll(it.toList())
        }
    }

    fun isStackEmpty(): Boolean = navigationStack.isEmpty()
}
