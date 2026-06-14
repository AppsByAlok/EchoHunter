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
    var activeAttackMode = AttackMode.DEFAULT

    // Raw Input (Filled by TouchController)
    var isAttackTouching = false
    var attackTouchX = 0f
    var attackTouchY = 0f

    // Derived Logic (Calculated by InputSystem)
    var attackRequested = false
    var aimDirX = 0f
    var aimDirY = 0f
    var attackPullDist = 0f // For manual aim visualization
    
    // Arsenal
    var currentWeapon = 1 
    var currentTrap = 2 
    var isTrapPressed = false
    var isOverclockPressed = false
    var isSonarPressed = false
    var isAutoSonarLocked = false
}
