package com.appsbyalok.echohunter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.random.Random

// Game Mode 1: Story / Sector Cleanup
class StoryMode : GameModeStrategy {
    override val modeId = 1
    private var lastSector = -1
    private var sectorStr = ""
    private var targetStr = ""

    override fun getIntroLines() = StoryProtocol.storyIntroLines

    override fun updateCameraAndMovement(dt: Float, gs: GameState, width: Float, scale: Float) {
        // Camera logic is same as Endless Mode
        val screenPx = gs.px - gs.cameraX
        if (screenPx > width * 0.6f) gs.cameraX += (screenPx - width * 0.6f) * 5f * dt
        else if (screenPx < width * 0.2f && gs.cameraX > 0f) gs.cameraX += (screenPx - width * 0.2f) * 5f * dt
        if (gs.cameraX < 0f) gs.cameraX = 0f

        if (gs.px < gs.cameraX) gs.px = gs.cameraX
        if (gs.px > gs.cameraX + width) gs.px = gs.cameraX + width
    }

    override fun checkProgression(
        context: Context,
        gs: GameState, scale: Float,
        onTriggerBoss: (Int, Float) -> Unit,
        onSetStory: (IntArray, Int) -> Unit
    ) {
        // Sector Target complete hone par Boss trigger
        if (gs.score >= gs.sectorTarget && !gs.bossActive) {
            when(gs.currentSector) {
                1 -> onTriggerBoss(0, scale)
                2 -> { onSetStory(StoryProtocol.storyMidLines, 1); onTriggerBoss(1, scale) }
                3 -> { StoryProtocol.showIngameMessage(R.string.msg_sector_corrupted); onTriggerBoss(2, scale) }
                4 -> { StoryProtocol.showIngameMessage(R.string.msg_system_failing); onTriggerBoss(3, scale) }
                5 -> onTriggerBoss(4, scale)
            }
        }

        if (Random.nextDouble() < 0.005) StoryProtocol.triggerRandomGlitch(gs.score, modeId, gs.difficulty)
    }

    override fun getEnemySpawnPosition(gs: GameState, width: Float, height: Float, scale: Float): Pair<Float, Float> {
        val x = gs.cameraX + if (Random.nextBoolean()) -scale * 0.1f else width + scale * 0.1f
        val y = Random.nextFloat() * height
        return Pair(x, y)
    }

    override fun drawModeSpecificWorld(c: Canvas, gs: GameState, width: Float, height: Float, scale: Float, p: Paint) {
        // Story mode environment effects can go here
    }

    override fun drawModeSpecificHUD(context: Context, c: Canvas, gs: GameState, width: Float, height: Float, scale: Float, pText: Paint) {
        // Draw Sector info and Target score
        if (gs.currentSector != lastSector) {
            sectorStr = context.getString(R.string.sector_count, gs.currentSector)
            targetStr = context.getString(R.string.sector_target, gs.sectorTarget)
            lastSector = gs.currentSector
        }

        val topMargin = scale * 0.06f
        val centerY = topMargin + scale * 0.02f

        pText.color = GameColors.CLARITY
        pText.textSize = scale * 0.04f
        c.drawText(sectorStr, width / 2f, centerY, pText)

        pText.textSize = scale * 0.03f
        pText.color = if (gs.bossActive) GameColors.RED else GameColors.YELLOW
        val subText = if (gs.bossActive) context.getString(R.string.msg_guardian_active) else targetStr

        if (gs.bossActive) pText.setShadowLayer(10f, 0f, 0f, GameColors.RED)
        c.drawText(subText, width / 2f, centerY + scale * 0.04f, pText)
        pText.clearShadowLayer()
    }
}