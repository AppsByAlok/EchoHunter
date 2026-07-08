package com.appsbyalok.echohunter.input

enum class AttackMode {
    DEFAULT,    // Forward facing (Classic)
    AUTO_AIM,   // Nearest enemy
    MANUAL_AIM  // Joystick / Directional
}

class ControlsState {
    var isMoveJoyActive = false
    var moveDirX = 0f
    var moveDirY = 0f

    // --- ATTACK SYSTEM ---
    var activeAttackMode = AttackMode.AUTO_AIM // Default to AUTO_AIM now

    // Raw Input (Filled by TouchController)
    var isAttackTouching = false
    var attackTouchX = 0f
    var attackTouchY = 0f

    // Derived Logic (Calculated by InputSystem)
    var attackRequested = false
    var aimDirX = 0f
    var aimDirY = 0f
    var attackPullDist = 0f // For manual aim visualization
    
    // Touchpad Manual Aim
    var manualAimActive = false
    var manualAimTouchX = 0f
    var manualAimTouchY = 0f
    var manualAimDeltaX = 0f
    var manualAimDeltaY = 0f

    var isWeaponMenuOpen = false
    var isTrapMenuOpen = false
    var isSonarMenuOpen = false
    
    var selectedWeaponIdx = -1
    var selectedTrapIdx = -1
    var selectedSonarIdx = -1

    var trapTouchX = 0f
    var trapTouchY = 0f
    var sonarTouchX = 0f
    var sonarTouchY = 0f
    
    // Progression
    var isManualAimUnlocked = false
    
    // Arsenal
    var currentWeapon = 1 
    var currentTrap = 2 
    var isTrapPressed = false
    var trapRequested = false
    var isOverclockPressed = false
    var isSonarPressed = false
    var isAutoSonarLocked = false
}
