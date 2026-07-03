package com.appsbyalok.echohunter.systems

import com.appsbyalok.echohunter.engine.GameState
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Extension functions to separate Camera logic from GameState data.
 */

/**
 * Centralized Cinematic Trigger: Tracks a focus object, zooms in, and holds during slo-mo.
 * @param tx Target X
 * @param ty Target Y
 * @param zoom Target Zoom level during focus
 * @param duration Hold duration (uses slowMoTimer)
 * @param hitStop Brief freeze on arrival
 * @param shake Visual vibration intensity
 */
fun GameState.triggerCinematicFocus(
    tx: Float,
    ty: Float,
    zoom: Float = 1.3f,
    duration: Float = 1.5f,
    hitStop: Float = 0.2f,
    shake: Float = 0f
) {
    cameraFocusX = tx
    cameraFocusY = ty
    cameraFocusWeight = 1.0f
    targetZoom = zoom
    slowMoTimer = duration
    hitStopTimer = hitStop
    if (shake > 0f) shakeCamera(shake)
}

/**
 * Triggers a camera shake effect.
 * Intensity decays automatically in GameState.updateTimers based on dt and scale.
 */
fun GameState.shakeCamera(intensity: Float) {
    // We use max to ensure a new smaller shake doesn't override a big ongoing one
    shakeAmount = max(shakeAmount, intensity)
}

/**
 * Shared Camera Engine: Handles Lead, Zoom, Cinematic Focus, and Tracking.
 */
fun GameState.updateCameraLogic(dt: Float, width: Float, height: Float, baseZoom: Float = 1.0f, leadMult: Float = 0.12f) {
    // 1. DYNAMIC ZOOM
    val speed = sqrt((controls.moveDirX * controls.moveDirX + controls.moveDirY * controls.moveDirY).toDouble()).toFloat()
    val currentBaseZoom = if (cameraFocusWeight > 0.5f) targetZoom else baseZoom

    val dynTargetZoom = when {
        cameraFocusWeight > 0.5f -> targetZoom
        bossActive -> currentBaseZoom * 0.85f
        speed > 0.1f -> currentBaseZoom * 0.95f
        else -> currentBaseZoom
    }
    cameraZoom += (dynTargetZoom - cameraZoom) * 3.5f * dt

    // 2. SMOOTH LEAD (Look-ahead)
    val leadDistance = width * leadMult
    val targetLeadX = lastFacingX * leadDistance
    val targetLeadY = lastFacingY * leadDistance
    camLeadX += (targetLeadX - camLeadX) * 2.5f * dt
    camLeadY += (targetLeadY - camLeadY) * 2.5f * dt

    // 3. FOCUS LERPING
    var targetX = px + camLeadX
    var targetY = py + camLeadY

    if (cameraFocusWeight > 0f) {
        targetX = px * (1f - cameraFocusWeight) + cameraFocusX * cameraFocusWeight
        targetY = py * (1f - cameraFocusWeight) + cameraFocusY * cameraFocusWeight

        // Auto-return logic: Start decaying weight when slo-mo is nearly finished
        if (slowMoTimer < 0.4f) {
            cameraFocusWeight = 0f.coerceAtLeast(cameraFocusWeight - dt * 2.5f)
        }
    }

    // 4. CAMERA TRACKING & BOUNDARIES
    val centerX = targetX - (width / 2f) / cameraZoom
    val centerY = targetY - (height / 2f) / cameraZoom

    // FIX: Sub-pixel camera jitter by using a higher lerp factor for standard tracking 
    // and syncing it exactly with player movement.
    val lerpFactor = (if (cameraFocusWeight > 0.1f) 20f else 12f) * dt
    cameraX += (centerX - cameraX) * lerpFactor.coerceAtMost(1.0f)
    cameraY += (centerY - cameraY) * lerpFactor.coerceAtMost(1.0f)

    val maxCamX = 0f.coerceAtLeast(mapWidth - width / cameraZoom)
    val maxCamY = 0f.coerceAtLeast(mapHeight - height / cameraZoom)
    cameraX = cameraX.coerceIn(0f, maxCamX)
    cameraY = cameraY.coerceIn(0f, maxCamY)
}