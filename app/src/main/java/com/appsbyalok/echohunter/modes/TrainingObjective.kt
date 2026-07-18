package com.appsbyalok.echohunter.modes

import android.media.ToneGenerator
import com.appsbyalok.echohunter.data.MazeGenerator
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.input.AttackMode
import com.appsbyalok.echohunter.input.HudAction
import com.appsbyalok.echohunter.systems.EnemySystem
import com.appsbyalok.echohunter.systems.SpawnerSystem
import com.appsbyalok.echohunter.utils.EchoAudioManager
import com.appsbyalok.echohunter.utils.SpawnValidator
import kotlin.math.sqrt

/**
 * A safe, gated onboarding run. Each phase only exposes the control that is
 * being taught, while the player remains invulnerable for the whole training.
 */
class TrainingObjective : IGameObjective {
    private var phase = 0
    private var phaseTimer = 0f
    private var messageStep = 0
    private var combatTrial = 0
    private var trapTrial = 0
    private var targetIndex = -1
    private var sonarPings = 0
    private var pulseCounted = false

    private val combatModes = arrayOf(
        AttackMode.MANUAL_AIM,
        AttackMode.DIRECTIONAL,
        AttackMode.AUTO_AIM
    )
    private val weaponNames = arrayOf("SPIKE", "BLAST", "SLUG")
    private val aimNames = arrayOf("MANUAL AIM", "DIRECTIONAL AIM", "AUTO AIM")
    private val trapNames = arrayOf("CAMO", "DECOY", "EMP")

    override fun setupObjective(
        gs: GameState,
        enemySys: EnemySystem,
        spawnerSys: SpawnerSystem,
        targetW: Float,
        targetH: Float,
        scale: Float
    ) {
        phase = 0
        phaseTimer = 0f
        messageStep = 0
        combatTrial = 0
        trapTrial = 0
        targetIndex = -1
        sonarPings = 0
        pulseCounted = false

        gs.coreX = -9999f
        gs.coreY = -9999f
        gs.coreRadius = 0f
        gs.tutorialGateOpen = false
        gs.escapeGateActive = false
        gs.tutorialHighlightedEnemyIndex = -1
        gs.tutorialEnabledActions = emptySet()
        gs.controls.activeAttackMode = AttackMode.MANUAL_AIM
        gs.controls.isManualAimUnlocked = true
        gs.hp = gs.maxHp
        StoryProtocol.isBlackoutActive = false
        StoryProtocol.blackoutAlpha = 0.8f
        gs.isBlackoutActive = false

        StoryProtocol.showTypewriterMessage("TRAINING LINK ESTABLISHED. YOU ARE PROTECTED FOR THIS RUN.", 4f)
    }

    override fun updateObjective(
        dt: Float,
        gs: GameState,
        enemySys: EnemySystem,
        spawnerSys: SpawnerSystem,
        targetW: Float,
        targetH: Float,
        scale: Float
    ) {
        phaseTimer += dt
        gs.hp = gs.maxHp // Training is always god mode, including environmental damage.

        when (phase) {
            0 -> updateHudIntroduction(gs)
            1 -> updateCombatTraining(gs, enemySys, scale)
            2 -> updateTrapTraining(gs, scale)
            3 -> updateSonarEscape(gs)
            4 -> finishTraining(gs)
        }
    }

    private fun updateHudIntroduction(gs: GameState) {
        gs.objectiveLabel = "PHASE 0: HUD & BASIC CONTROLS"
        gs.objectiveProgress = (phaseTimer / 9f).coerceIn(0f, 1f)
        gs.tutorialEnabledActions = emptySet()

        when {
            messageStep == 0 && phaseTimer >= 2f -> {
                StoryProtocol.showTypewriterMessage("MOVE: DRAG ANYWHERE ON THE LEFT SIDE TO STEER THE HUNTER.", 4f)
                messageStep++
            }
            messageStep == 1 && phaseTimer >= 5f -> {
                StoryProtocol.showTypewriterMessage("ATTACK AND TRAP CONTROLS WILL UNLOCK ONLY WHEN REQUIRED.", 4f)
                messageStep++
            }
            phaseTimer >= 9f -> advancePhase()
        }
    }

    private fun updateCombatTraining(gs: GameState, enemySys: EnemySystem, scale: Float) {
        gs.tutorialEnabledActions = setOf(HudAction.ATTACK)
        gs.objectiveLabel = "PHASE 1: COMBAT ${combatTrial + 1} / ${combatModes.size}"
        gs.objectiveProgress = combatTrial.toFloat() / combatModes.size

        if (combatTrial >= combatModes.size) {
            clearTarget(gs)
            advancePhase()
            return
        }

        if (targetIndex == -1) {
            gs.controls.activeAttackMode = combatModes[combatTrial]
            gs.controls.currentWeapon = combatTrial
            StoryProtocol.showTypewriterMessage(
                "ENEMY DETECTED. ${aimNames[combatTrial]} + ${weaponNames[combatTrial]} ONLINE. PURGE THE HIGHLIGHTED TARGET.",
                5f
            )
            targetIndex = spawnTrainingTarget(gs, enemySys, scale)
            gs.tutorialHighlightedEnemyIndex = targetIndex
            return
        }

        if (targetIndex < 0 || enemySys.ex[targetIndex] < -1000f) {
            combatTrial++
            targetIndex = -1
            gs.tutorialHighlightedEnemyIndex = -1
            EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_PIP, 150)
        }
    }

    private fun updateTrapTraining(gs: GameState, scale: Float) {
        gs.tutorialEnabledActions = setOf(HudAction.TRAP)
        gs.objectiveLabel = "PHASE 2: TRAPS ${trapTrial + 1} / ${trapNames.size}"
        gs.objectiveProgress = trapTrial.toFloat() / (trapNames.size + 1)

        if (trapTrial < trapNames.size) {
            gs.controls.currentTrap = trapTrial
            if (messageStep != trapTrial) {
                StoryProtocol.showTypewriterMessage(
                    "${trapNames[trapTrial]} TRAP ONLINE. TAP TRAP TO DEPLOY IT, OR HOLD AND DRAG TO REVIEW THE TRAP WHEEL.",
                    5f
                )
                messageStep = trapTrial
            }

            if (gs.activeTraps.any { it.type == trapTrial }) {
                trapTrial++
                gs.trapCooldownTimer = 0f
                EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_PIP, 150)
            }
            return
        }

        gs.tutorialEnabledActions = emptySet()
        if (!gs.tutorialGateOpen) {
            openTransitionGate(gs, scale)
            StoryProtocol.showTypewriterMessage("GATE OPEN. CROSS THE UPLINK TO BEGIN THE DARKNESS ESCAPE.", 5f)
        }

        gs.objectiveLabel = "PHASE 2: CROSS THE TRAINING GATE"
        gs.objectiveProgress = 0.75f
        if (distanceToCore(gs) < gs.coreRadius * 1.4f) {
            beginDarkMaze(gs, scale)
        }
    }

    private fun updateSonarEscape(gs: GameState) {
        gs.tutorialEnabledActions = setOf(HudAction.SONAR)
        gs.objectiveLabel = if (sonarPings < 3) {
            "PHASE 3: SONAR CALIBRATION $sonarPings / 3"
        } else {
            "PHASE 3: FIND THE ESCAPE PORTAL"
        }
        gs.objectiveProgress = (0.75f + sonarPings.toFloat() / 12f).coerceAtMost(0.95f)

        if (gs.pulse && !pulseCounted) {
            pulseCounted = true
            sonarPings++
        } else if (!gs.pulse) {
            pulseCounted = false
        }

        if (sonarPings == 1 && messageStep != 1) {
            StoryProtocol.showTypewriterMessage("SONAR REVEALS WALLS, TARGETS, AND THE EXIT SIGNAL FOR A SHORT TIME.", 4f)
            messageStep = 1
        }

        if (sonarPings >= 3 && distanceToCore(gs) < gs.coreRadius * 1.4f) {
            advancePhase()
        }
    }

    private fun finishTraining(gs: GameState) {
        gs.tutorialEnabledActions = emptySet()
        gs.objectiveLabel = "TRAINING COMPLETE"
        gs.objectiveProgress = 1f
        if (messageStep != 1) {
            SaveManager.setGameTutorialCompleted(true)
            StoryProtocol.showTypewriterMessage("TRAINING COMPLETE. ALL HUNTER SYSTEMS ARE AVAILABLE.", 4f)
            messageStep = 1
        }
        if (phaseTimer >= 4f) gs.isLevelCleared = true
    }

    private fun spawnTrainingTarget(gs: GameState, enemySys: EnemySystem, scale: Float): Int {
        val index = (0 until enemySys.n).firstOrNull { enemySys.ex[it] < -1000f } ?: return -1
        enemySys.spawnAt(index, gs.px + scale * 0.65f, gs.py, gs, scale, 0)
        if (enemySys.ex[index] > -1000f) {
            enemySys.hp[index] = 1
            enemySys.maxHp[index] = 1
            enemySys.vis[index] = 1f
        }
        return index
    }

    private fun openTransitionGate(gs: GameState, scale: Float) {
        val position = SpawnValidator.findValidNear(
            gs.px + scale * 1.2f,
            gs.py,
            scale * 0.04f,
            gs,
            maxAttempts = 40,
            searchRadius = scale * 3f
        ) ?: Pair(gs.px + scale * 0.8f, gs.py)
        gs.coreX = position.first
        gs.coreY = position.second
        gs.coreRadius = scale * 0.13f
        gs.tutorialGateOpen = true
        gs.escapeGateActive = true
    }

    private fun beginDarkMaze(gs: GameState, scale: Float) {
        val exit = findMazeExit(gs) ?: SpawnValidator.findValidNear(
            gs.px + scale * 4f,
            gs.py + scale * 4f,
            scale * 0.04f,
            gs,
            maxAttempts = 80,
            searchRadius = scale * 12f
        ) ?: Pair(gs.px, gs.py)
        gs.coreX = exit.first
        gs.coreY = exit.second
        gs.coreRadius = scale * 0.14f
        gs.escapeGateActive = true
        StoryProtocol.isBlackoutActive = true
        StoryProtocol.blackoutAlpha = 0.93f
        gs.isBlackoutActive = true
        advancePhase()
        StoryProtocol.showTypewriterMessage("BLACKOUT ACTIVE. USE SONAR TO MAP THE MAZE AND LOCATE THE ESCAPE PORTAL.", 5f)
    }

    private fun findMazeExit(gs: GameState): Pair<Float, Float>? {
        val grid = gs.gridMap ?: return null
        for (x in grid.indices) {
            for (y in grid[x].indices) {
                if (grid[x][y] == MazeGenerator.DEST_NODE) {
                    return Pair(x * gs.tileSize + gs.tileSize / 2f, y * gs.tileSize + gs.tileSize / 2f)
                }
            }
        }
        return null
    }

    private fun distanceToCore(gs: GameState): Float {
        val dx = gs.px - gs.coreX
        val dy = gs.py - gs.coreY
        return sqrt(dx * dx + dy * dy)
    }

    private fun clearTarget(gs: GameState) {
        gs.tutorialHighlightedEnemyIndex = -1
        targetIndex = -1
    }

    private fun advancePhase() {
        phase++
        phaseTimer = 0f
        messageStep = -1
        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_PIP, 150)
    }

    fun skipStep(gs: GameState) {
        if (phase < 4) {
            clearTarget(gs)
            if (phase == 2) {
                beginDarkMaze(gs, gs.tileSize)
            } else {
                advancePhase()
            }
            StoryProtocol.showTypewriterMessage("TRAINING PHASE SKIPPED. PROCEEDING...", 2f)
        }
    }

    fun skipAll(gs: GameState) {
        clearTarget(gs)
        phase = 4
        phaseTimer = 0f
        messageStep = -1
        gs.tutorialEnabledActions = emptySet()
        gs.objectiveProgress = 1f
        StoryProtocol.showTypewriterMessage("TRAINING BYPASSED. SYSTEMS OPTIMAL.", 3f)
    }

    override fun checkWinCondition(gs: GameState): Boolean = phase >= 4 && gs.objectiveProgress >= 1f
    override fun isBossTriggerReady(gs: GameState): Boolean = false
}
