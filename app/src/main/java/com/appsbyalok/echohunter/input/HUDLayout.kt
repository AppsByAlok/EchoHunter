package com.appsbyalok.echohunter.input

import android.graphics.RectF
import kotlin.math.max

data class ResolvedHudControl(
    val control: HudControl,
    val x: Float,
    val y: Float,
    val radius: Float
)

class HUDLayout {
    val controls = mutableListOf<ResolvedHudControl>()
    val movementZone = RectF()
    val manualAimZone = RectF()
    val manualAimRect = RectF()
    val bossHpRect = RectF()

    var movementMode = MovementMode.FLOATING
        private set
    var movementX = 0f
        private set
    var movementY = 0f
        private set
    var movementRadius = 0f
        private set
    var manualAimMode = MovementMode.FLOATING
        private set
    var manualAimX = 0f
        private set
    var manualAimY = 0f
        private set
    var manualAimRadius = 0f
        private set

    // Compatibility anchors remain available to combat systems that calculate radial menus.
    var btnRadius = 0f
    var atkX = 0f
    var atkY = 0f
    var ovrX = 0f
    var ovrY = 0f
    var trapX = 0f
    var trapY = 0f
    var pulseX = 0f
    var pulseY = 0f
    var pauseX = 0f
    var pauseY = 0f

    var safeInsetTop = 0f
    var safeInsetBottom = 0f
    var safeInsetLeft = 0f
    var safeInsetRight = 0f

    fun resolve(profile: HudLayoutProfile, width: Float, height: Float, scale: Float) {
        val safeLeft = safeInsetLeft
        val safeTop = safeInsetTop
        val safeWidth = max(1f, width - safeInsetLeft - safeInsetRight)
        val safeHeight = max(1f, height - safeInsetTop - safeInsetBottom)
        val baseRadius = scale * 0.11f

        controls.clear()
        profile.normalize().controls.forEach { control ->
            val radius = baseRadius * control.scale
            val minX = safeLeft + radius
            val maxX = safeLeft + safeWidth - radius
            val minY = safeTop + radius
            val maxY = safeTop + safeHeight - radius
            val x = (safeLeft + control.x * safeWidth).coerceIn(minX, maxX)
            val y = (safeTop + control.y * safeHeight).coerceIn(minY, maxY)
            controls += ResolvedHudControl(control, x, y, radius)
        }

        movementMode = profile.movement.mode
        movementRadius = scale * 0.15f * profile.movement.scale
        movementX = (safeLeft + profile.movement.x * safeWidth)
            .coerceIn(safeLeft + movementRadius, safeLeft + safeWidth - movementRadius)
        movementY = (safeTop + profile.movement.y * safeHeight)
            .coerceIn(safeTop + movementRadius, safeTop + safeHeight - movementRadius)
        movementZone.set(
            safeLeft + profile.movement.zoneLeft * safeWidth,
            safeTop + profile.movement.zoneTop * safeHeight,
            safeLeft + profile.movement.zoneRight * safeWidth,
            safeTop + profile.movement.zoneBottom * safeHeight
        )
        manualAimMode = profile.manualAim.mode
        manualAimRadius = scale * 0.15f * profile.manualAim.scale
        manualAimX = (safeLeft + profile.manualAim.x * safeWidth)
            .coerceIn(safeLeft + manualAimRadius, safeLeft + safeWidth - manualAimRadius)
        manualAimY = (safeTop + profile.manualAim.y * safeHeight)
            .coerceIn(safeTop + manualAimRadius, safeTop + safeHeight - manualAimRadius)
        manualAimZone.set(
            safeLeft + profile.manualAim.zoneLeft * safeWidth,
            safeTop + profile.manualAim.zoneTop * safeHeight,
            safeLeft + profile.manualAim.zoneRight * safeWidth,
            safeTop + profile.manualAim.zoneBottom * safeHeight
        )
        manualAimRect.set(manualAimZone)

        btnRadius = controls.firstOrNull { it.control.action == HudAction.ATTACK }?.radius ?: baseRadius
        setCompatibilityAnchors()
    }

    fun controlAt(x: Float, y: Float): ResolvedHudControl? = controls.asReversed().firstOrNull {
        val dx = x - it.x
        val dy = y - it.y
        dx * dx + dy * dy <= it.radius * 1.2f * it.radius * 1.2f
    }

    fun actionAnchor(action: HudAction): ResolvedHudControl? = controls.firstOrNull { it.control.action == action }

    fun setActionAnchor(resolved: ResolvedHudControl) {
        when (resolved.control.action) {
            HudAction.ATTACK -> { atkX = resolved.x; atkY = resolved.y; btnRadius = resolved.radius }
            HudAction.OVERCLOCK -> { ovrX = resolved.x; ovrY = resolved.y }
            HudAction.TRAP -> { trapX = resolved.x; trapY = resolved.y }
            HudAction.SONAR -> { pulseX = resolved.x; pulseY = resolved.y }
            HudAction.PAUSE -> { pauseX = resolved.x; pauseY = resolved.y }
        }
    }

    fun isMovementHit(x: Float, y: Float): Boolean = when (movementMode) {
        MovementMode.FLOATING -> movementZone.contains(x, y)
        MovementMode.STATIC -> {
            val dx = x - movementX
            val dy = y - movementY
            dx * dx + dy * dy <= movementRadius * movementRadius
        }
    }

    fun isManualAimHit(x: Float, y: Float): Boolean = when (manualAimMode) {
        MovementMode.FLOATING -> manualAimZone.contains(x, y)
        MovementMode.STATIC -> {
            val dx = x - manualAimX
            val dy = y - manualAimY
            dx * dx + dy * dy <= manualAimRadius * manualAimRadius
        }
    }

    private fun setCompatibilityAnchors() {
        HudAction.entries.forEach { action -> actionAnchor(action)?.let(::setActionAnchor) }
    }
}
