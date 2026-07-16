package com.appsbyalok.echohunter.input

enum class HudAction { ATTACK, TRAP, OVERCLOCK, SONAR, PAUSE }

enum class HudVisualType { STANDARD, COMPACT, LABELED }

enum class HudInputBehavior { TAP, HOLD }

enum class MovementMode { FLOATING, STATIC }

data class HudControl(
    val id: String,
    var action: HudAction,
    var x: Float,
    var y: Float,
    var scale: Float = 1f,
    var visualType: HudVisualType = HudVisualType.STANDARD,
    var inputBehavior: HudInputBehavior = HudInputBehavior.TAP
) {
    fun copyMutable() = copy()
}

data class MovementControl(
    var mode: MovementMode = MovementMode.FLOATING,
    var x: Float = 0.18f,
    var y: Float = 0.82f,
    var scale: Float = 1f,
    var zoneLeft: Float = 0f,
    var zoneTop: Float = 0.35f,
    var zoneRight: Float = 0.5f,
    var zoneBottom: Float = 1f
) {
    fun copyMutable() = copy()
}

data class ManualAimControl(
    var mode: MovementMode = MovementMode.FLOATING,
    var x: Float = 0.76f,
    var y: Float = 0.68f,
    var scale: Float = 1f,
    var zoneLeft: Float = 0.5f,
    var zoneTop: Float = 0.28f,
    var zoneRight: Float = 1f,
    var zoneBottom: Float = 1f
) {
    fun copyMutable() = copy()
}

data class HudLayoutProfile(
    val controls: MutableList<HudControl>,
    var movement: MovementControl,
    var manualAim: ManualAimControl = ManualAimControl()
) {
    fun copyMutable() = HudLayoutProfile(controls.map { it.copyMutable() }.toMutableList(), movement.copyMutable(), manualAim.copyMutable())

    fun normalize(): HudLayoutProfile {
        controls.forEach {
            it.x = it.x.coerceIn(0f, 1f)
            it.y = it.y.coerceIn(0f, 1f)
            it.scale = it.scale.coerceIn(MIN_CONTROL_SCALE, MAX_CONTROL_SCALE)
            if (it.action != HudAction.ATTACK) it.inputBehavior = HudInputBehavior.TAP
        }
        movement.scale = movement.scale.coerceIn(MIN_CONTROL_SCALE, MAX_CONTROL_SCALE)
        movement.x = movement.x.coerceIn(0f, 1f)
        movement.y = movement.y.coerceIn(0f, 1f)
        movement.zoneLeft = movement.zoneLeft.coerceIn(0f, 0.9f)
        movement.zoneRight = movement.zoneRight.coerceIn(movement.zoneLeft + 0.1f, 1f)
        movement.zoneTop = movement.zoneTop.coerceIn(0f, 0.9f)
        movement.zoneBottom = movement.zoneBottom.coerceIn(movement.zoneTop + 0.1f, 1f)
        
        manualAim.scale = manualAim.scale.coerceIn(MIN_CONTROL_SCALE, MAX_CONTROL_SCALE)
        manualAim.x = manualAim.x.coerceIn(0f, 1f)
        manualAim.y = manualAim.y.coerceIn(0f, 1f)
        manualAim.zoneLeft = manualAim.zoneLeft.coerceIn(0f, 0.9f)
        manualAim.zoneRight = manualAim.zoneRight.coerceIn(manualAim.zoneLeft + 0.1f, 1f)
        manualAim.zoneTop = manualAim.zoneTop.coerceIn(0f, 0.9f)
        manualAim.zoneBottom = manualAim.zoneBottom.coerceIn(manualAim.zoneTop + 0.1f, 1f)
        return this
    }

    fun canAdd(action: HudAction): Boolean =
        controls.size < MAX_ACTION_BUTTONS && controls.count { it.action == action } < MAX_DUPLICATES_PER_ACTION

    fun canRemove(control: HudControl): Boolean =
        when (control.action) {
            HudAction.ATTACK, HudAction.PAUSE -> controls.count { it.action == control.action } > 1
            else -> true
        }

    companion object {
        const val MAX_ACTION_BUTTONS = 6
        const val MAX_DUPLICATES_PER_ACTION = 2
        const val MIN_CONTROL_SCALE = 0.65f
        const val MAX_CONTROL_SCALE = 1.5f

        fun defaults(isPortrait: Boolean): HudLayoutProfile {
            val combatX = if (isPortrait) 0.84f else 0.86f
            val sideX = if (isPortrait) 0.62f else 0.64f
            val controls = mutableListOf(
                HudControl("attack_1", HudAction.ATTACK, combatX, 0.84f, inputBehavior = HudInputBehavior.HOLD),
                HudControl("overclock_1", HudAction.OVERCLOCK, combatX, 0.60f),
                HudControl("trap_1", HudAction.TRAP, sideX, 0.84f),
                HudControl("sonar_1", HudAction.SONAR, sideX, 0.60f),
                HudControl("pause_1", HudAction.PAUSE, 0.90f, 0.10f, 0.8f, HudVisualType.COMPACT)
            )
            return HudLayoutProfile(controls, MovementControl(), ManualAimControl()).normalize()
        }
    }
}
