package com.appsbyalok.echohunter.input

class ControlsState {
    var isMoveJoyActive = false
    var moveDirX = 0f
    var moveDirY = 0f

    var isAttackPressed = false
    var isAutoFireLocked = false

    var currentWeapon = 1 // 0: Blaster, 1: Shotgun, 2: Sniper
    var isTrapPressed = false
    var currentTrap = 2 // 0: Camo, 1: Decoy, 2: EMP

    var isOverclockPressed = false

    var isSonarPressed = false
    var isAutoSonarLocked = false
    
    // Story Unlocks (Future proofing for the user's new idea)
    var isManualAimUnlocked = true // Default true for now to maintain current gameplay
    var isTrapMenuUnlocked = true
}
