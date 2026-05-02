package com.appsbyalok.echohunter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.random.Random

// Game Mode 0: Endless Waves (Survive as long as possible)
class EndlessMode : GameModeStrategy {
    override val modeId = 0
    private var lastWave = -1
    private var waveStr = ""

    override fun getIntroLines() = StoryProtocol.endlessIntroLines

    override fun updateCameraAndMovement(dt: Float, gs: GameState, width: Float, scale: Float) {
        val screenPx = gs.px - gs.cameraX

        // Endless Mode Camera Logic: Camera player ko follow
        if (screenPx > width * 0.6f) gs.cameraX += (screenPx - width * 0.6f) * 5f * dt
        else if (screenPx < width * 0.2f && gs.cameraX > 0f) gs.cameraX += (screenPx - width * 0.2f) * 5f * dt
        if (gs.cameraX < 0f) gs.cameraX = 0f

        // Player bounds
        if (gs.px < gs.cameraX) gs.px = gs.cameraX
        if (gs.px > gs.cameraX + width) gs.px = gs.cameraX + width
    }

    override fun checkProgression(
        context: Context,
        gs: GameState, scale: Float,
        onTriggerBoss: (Int, Float) -> Unit,
        onSetStory: (IntArray, Int) -> Unit
    ) {
        // Har 50 score par new wave, har 5 wave par Boss
        if (gs.score > gs.wave * 50 && !gs.bossActive) {
            gs.wave++
            gs.sectorFlash = 0.5f
            StoryProtocol.showIngameMessage(StoryProtocol.endlessPopups[Random.nextInt(StoryProtocol.endlessPopups.size)])
            if (gs.wave % 5 == 0) onTriggerBoss(Random.nextInt(0, 5), scale)
        }
    }

    override fun getEnemySpawnPosition(gs: GameState, width: Float, height: Float, scale: Float): Pair<Float, Float> {
        // Enemies random directions se spawn
        val x = if (Random.nextBoolean()) {
            gs.cameraX + Random.nextFloat() * width
        } else {
            gs.cameraX + if (Random.nextBoolean()) -scale * 0.1f else width + scale * 0.1f
        }
        val y = if (Random.nextBoolean()) -scale * 0.1f else height + scale * 0.1f
        return Pair(x, y)
    }

    override fun drawModeSpecificWorld(c: Canvas, gs: GameState, width: Float, height: Float, scale: Float, p: Paint) {
        // No specific world graphics for endless mode yet
    }

    override fun drawModeSpecificHUD(context: Context, c: Canvas, gs: GameState, width: Float, height: Float, scale: Float, pText: Paint) {
        // Draw Wave Number in Top Center
        if (gs.wave != lastWave) {
            waveStr = context.getString(R.string.wave_count, gs.wave)
            lastWave = gs.wave
        }
        val topMargin = scale * 0.06f
        val centerY = topMargin + scale * 0.02f
        pText.color = GameColors.CLARITY
        pText.textSize = scale * 0.04f
        c.drawText(waveStr, width / 2f, centerY, pText)
    }
}