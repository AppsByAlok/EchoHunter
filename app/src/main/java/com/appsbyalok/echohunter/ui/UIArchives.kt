package com.appsbyalok.echohunter.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ToneGenerator
import android.view.MotionEvent
import com.appsbyalok.echohunter.data.LevelEngine
import com.appsbyalok.echohunter.data.LevelFeature
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.EchoAudioManager
import com.appsbyalok.echohunter.utils.GameColors
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.abs
import kotlin.math.max

enum class FeatureFilterMode(val label: String) {
    ANY("OR: ANY"),
    ALL("AND: ALL"),
    EXACT("EXACT")
}

class UIArchives {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private var scrollY = 0f
    private var maxScroll = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private var scrollVelocity = 0f
    private var lastTouchTime = 0L

    private val levelButtons = mutableMapOf<Int, RectF>()
    private val buttonPool = mutableListOf<RectF>()
    private val closeBtnRect = RectF()
    private val loadMoreRect = RectF()
    private val cachedList = mutableListOf<Int>() // Only modified by UI thread
    private val resultQueue = ConcurrentLinkedQueue<Int>()
    private var lastMaxLevel = -1

    @Volatile
    private var isScanning = false
    @Volatile
    private var activeScanId = 0
    @Volatile
    private var totalScanned = 0
    @Volatile
    private var totalMatches = 0
    @Volatile
    private var currentSector = 0
    private val scanExecutor = Executors.newSingleThreadExecutor()
    private var scanFuture: Future<*>? = null

    @Volatile
    private var lastScannedLevel = -1
    @Volatile
    private var lastFinishedIndex = -1
    private val seenFeaturesMasks = Collections.synchronizedSet(HashSet<Int>())
    @Volatile
    private var sortedFinishedIds: List<Int>? = null

    private val autoNextBoxRect = RectF()
    private val autoPilotBoxRect = RectF()
    private var hitOnDown = -1
    private var listTop = 0f
    private var listBottom = 0f

    // Garbage Collector stutter variables optimized out using object pools
    private val reusableRect = RectF()
    private val badgeRect = RectF()
    private val featureEnumValues = LevelFeature.entries.toTypedArray()

    private var isFilterExpanded = false
    private val filterToggleBtnRect = RectF()

    private var filterUniqueOnly = false
    private var filterFinishedOnly = false
    private var sortDescending = true
    private var featureFilterMode = FeatureFilterMode.ANY
    private val selectedFeatures = mutableSetOf<LevelFeature>()

    private val uniqueBoxRect = RectF()
    private val finishedBoxRect = RectF()
    private val sortBoxRect = RectF()
    private val filterModeRect = RectF()
    private val featureRects = mutableMapOf<LevelFeature, RectF>()
    private val featureRectPool = mutableListOf<RectF>()

    private var headerGradient: android.graphics.LinearGradient? = null
    private var footerGradient: android.graphics.LinearGradient? = null
    private var progressGradient: android.graphics.RadialGradient? = null
    private var lastGradientParams = ""
    private var totalStars = 0
    private var levelsCleared = 0

    private fun generateNodeList(append: Boolean = false) {
        val currentMax = SaveManager.maxCampaignLevel
        
        if (!append) {
            scanFuture?.cancel(true)
            cachedList.clear()
            resultQueue.clear()
            seenFeaturesMasks.clear()
            lastScannedLevel = -1
            lastFinishedIndex = -1
            lastMaxLevel = currentMax
            sortedFinishedIds = null
            totalScanned = 0
            totalMatches = 0
            currentSector = 0

            val stats = SaveManager.getGlobalStats()
            levelsCleared = stats.first
            totalStars = stats.second
        } else {
            if (isScanning) return 

            val isDone = if (filterFinishedOnly) {
                val list = sortedFinishedIds
                list != null && lastFinishedIndex >= list.size - 1
            } else {
                if (sortDescending) {
                    lastScannedLevel != -1 && lastScannedLevel < 1
                } else {
                    lastScannedLevel != -1 && lastScannedLevel > currentMax
                }
            }
            if (isDone) return
        }

        val selectedMask = selectedFeatures.fold(0) { acc, feat -> acc or (1 shl feat.ordinal) }
        val mode = featureFilterMode
        val uniqueOnly = filterUniqueOnly
        val finishedOnly = filterFinishedOnly
        val descending = sortDescending
        
        isScanning = true
        val scanId = ++activeScanId
        scanFuture = scanExecutor.submit {
            try {
                var chunkMatches = 0
                if (finishedOnly) {
                    var list = sortedFinishedIds
                    if (list == null) {
                        val finishedIds = SaveManager.getFinishedLevelIds()
                        list = if (descending) finishedIds.sortedDescending() else finishedIds.sorted()
                        sortedFinishedIds = list
                    }
                    var i = lastFinishedIndex + 1

                    while (i < list.size && !Thread.currentThread().isInterrupted && chunkMatches < 200) {
                        if (activeScanId != scanId) return@submit
                        val lvl = list[i]
                        lastFinishedIndex = i
                        i++
                        if (lvl > currentMax) continue
                        
                        totalScanned++
                        currentSector = lvl / 1000
                        val mask = LevelEngine.getFeaturesMask(lvl)
                        var match = true
                        if (selectedMask != 0) {
                            match = when (mode) {
                                FeatureFilterMode.ANY -> (mask and selectedMask) != 0
                                FeatureFilterMode.ALL -> (mask and selectedMask) == selectedMask
                                FeatureFilterMode.EXACT -> mask == selectedMask
                            }
                        }
                        if (match && uniqueOnly) {
                            if (seenFeaturesMasks.contains(mask)) match = false
                            else seenFeaturesMasks.add(mask)
                        }

                        if (match) {
                            resultQueue.add(lvl)
                            totalMatches++
                            chunkMatches++
                        }
                        
                        // Adaptive breather: only sleep if we are finding matches too fast
                        if (chunkMatches % 10 == 0) Thread.sleep(1)
                    }
                } else {
                    val step = if (descending) -1 else 1
                    var lvl = if (lastScannedLevel == -1) (if (descending) currentMax else 1) else lastScannedLevel

                    while ((if (descending) lvl >= 1 else lvl <= currentMax) && !Thread.currentThread().isInterrupted && chunkMatches < 200) {
                        if (activeScanId != scanId) return@submit
                        totalScanned++
                        currentSector = lvl / 1000
                        val mask = LevelEngine.getFeaturesMask(lvl)
                        var match = true
                        if (selectedMask != 0) {
                            match = when (mode) {
                                FeatureFilterMode.ANY -> (mask and selectedMask) != 0
                                FeatureFilterMode.ALL -> (mask and selectedMask) == selectedMask
                                FeatureFilterMode.EXACT -> mask == selectedMask
                            }
                        }
                        if (match && uniqueOnly) {
                            if (seenFeaturesMasks.contains(mask)) match = false
                            else seenFeaturesMasks.add(mask)
                        }

                        if (match) {
                            resultQueue.add(lvl)
                            totalMatches++
                            chunkMatches++
                        }

                        lvl += step
                        lastScannedLevel = lvl
                        
                        if (totalScanned % 500 == 0) {
                            Thread.yield()
                            Thread.sleep(1) // Periodical breather instead of every level
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (activeScanId == scanId) isScanning = false
            }
        }
    }

    fun draw(c: Canvas, width: Float, height: Float, gs: GameState, scale: Float) {
        c.drawColor(0xEE050508.toInt()) // Dark cyber hacking base background

        // --- SCANLINE EFFECT ---
        p.style = Paint.Style.STROKE
        p.strokeWidth = scale * 0.002f
        p.color = 0x0AFFFFFF
        var slY = 0f
        while (slY < height) {
            c.drawLine(0f, slY, width, slY, p)
            slY += scale * 0.012f
        }

        if (!isDragging && abs(scrollVelocity) > 0.5f) {
            scrollY += scrollVelocity
            scrollVelocity *= 0.96f

            if (scrollY > 0f) {
                scrollY = 0f
                scrollVelocity = 0f
            } else if (scrollY < -maxScroll) {
                scrollY = -maxScroll
                scrollVelocity = 0f
            }
        }

        // --- Dynamic Header Height ---
        listTop = if (isFilterExpanded) scale * 0.76f else scale * 0.36f
        listBottom = height - scale * 0.13f

        if (SaveManager.maxCampaignLevel != lastMaxLevel || (cachedList.isEmpty() && resultQueue.isEmpty() && lastScannedLevel == -1 && !isScanning)) generateNodeList(false)

        // Consume items from result queue (max 50 per frame to keep UI smooth)
        var consumed = 0
        while (resultQueue.isNotEmpty() && consumed < 50) {
            cachedList.add(resultQueue.poll()!!)
            consumed++
        }

        // Header Render with improved Glow
        val headerParams = "H:$listTop"
        if (headerGradient == null || lastGradientParams != headerParams) {
            headerGradient = android.graphics.LinearGradient(0f, 0f, 0f, listTop, 0xDD001A1A.toInt(), 0x00000000, android.graphics.Shader.TileMode.CLAMP)
        }
        p.style = Paint.Style.FILL
        p.shader = headerGradient
        c.drawRect(0f, 0f, width, listTop, p)
        p.shader = null

        pText.textSize = scale * 0.07f
        pText.color = GameColors.PULSE
        pText.setShadowLayer(15f, 0f, 0f, GameColors.PULSE) // Glow effect
        c.drawText("SYSTEM ARCHIVES", width / 2f, scale * 0.11f, pText)
        pText.clearShadowLayer()

        // Stats Summary
        pText.textSize = scale * 0.025f
        pText.color = GameColors.CLARITY
        val statsStr = String.format(Locale.ENGLISH, "CLEARED: %d  |  STARS: %d", levelsCleared, totalStars)
        c.drawText(statsStr, width / 2f, scale * 0.145f, pText)

        val boxWidth = if (width > height) scale * 0.35f else scale * 0.38f

        // Auto-Next Checkbox Button Layout
        autoNextBoxRect.set(width / 2f - boxWidth, scale * 0.16f, width / 2f - scale * 0.015f, scale * 0.25f)
        val autoNextColor = if (SaveManager.isAutoNextLevelEnabled) GameColors.HP else GameColors.RED
        p.style = Paint.Style.FILL; p.color = if (hitOnDown == -3) GameColors.mixColors(autoNextColor, 0, 0.4f) else 0x1A000000
        c.drawRoundRect(autoNextBoxRect, scale * 0.01f, scale * 0.01f, p)
        p.style = Paint.Style.STROKE; p.color = autoNextColor; p.strokeWidth = scale * 0.004f
        c.drawRoundRect(autoNextBoxRect, scale * 0.01f, scale * 0.01f, p)
        pText.textSize = scale * 0.032f; pText.color = autoNextColor
        c.drawText(if (SaveManager.isAutoNextLevelEnabled) "AUTO-NEXT: ON" else "AUTO-NEXT: OFF", autoNextBoxRect.centerX(), autoNextBoxRect.centerY() + scale * 0.011f, pText)

        // Autopilot Checkbox Button Layout
        autoPilotBoxRect.set(width / 2f + scale * 0.015f, scale * 0.16f, width / 2f + boxWidth, scale * 0.25f)
        val autoPilotColor = if (gs.isAutoPilotActive) GameColors.HP else GameColors.RED
        p.style = Paint.Style.FILL; p.color = if (hitOnDown == -4) GameColors.mixColors(autoPilotColor, 0, 0.4f) else 0x1A000000
        c.drawRoundRect(autoPilotBoxRect, scale * 0.01f, scale * 0.01f, p)
        p.style = Paint.Style.STROKE; p.color = autoPilotColor; p.strokeWidth = scale * 0.004f
        c.drawRoundRect(autoPilotBoxRect, scale * 0.01f, scale * 0.01f, p)
        pText.textSize = scale * 0.032f; pText.color = autoPilotColor
        c.drawText(if (gs.isAutoPilotActive) "AUTOPILOT: ON" else "AUTOPILOT: OFF", autoPilotBoxRect.centerX(), autoPilotBoxRect.centerY() + scale * 0.011f, pText)

        // --- FILTER TOGGLE BUTTON ---
        val filterRow2Y = scale * 0.27f
        val filterRow2H = scale * 0.07f
        filterToggleBtnRect.set(width / 2f - boxWidth, filterRow2Y, width / 2f + boxWidth, filterRow2Y + filterRow2H)

        p.style = Paint.Style.FILL; p.color = if (isFilterExpanded) 0x4400AAFF else 0x1A000000
        c.drawRoundRect(filterToggleBtnRect, scale * 0.01f, scale * 0.01f, p)
        p.style = Paint.Style.STROKE; p.color = if (isFilterExpanded) GameColors.COOLANT else 0xFF888888.toInt()
        p.strokeWidth = scale * 0.003f
        c.drawRoundRect(filterToggleBtnRect, scale * 0.01f, scale * 0.01f, p)
        pText.textSize = scale * 0.028f; pText.color = if (isFilterExpanded) GameColors.COOLANT else 0xFFFFFFFF.toInt()
        val filterLabel = if (isFilterExpanded) "CLOSE FILTERS ▲" else "OPEN ARCHIVE FILTERS ▼"
        c.drawText(filterLabel, filterToggleBtnRect.centerX(), filterToggleBtnRect.centerY() + scale * 0.01f, pText)

        if (isFilterExpanded) {
            // --- FILTERS ROW 3 ---
            val row3Y = filterRow2Y + filterRow2H + scale * 0.02f
            val row3H = scale * 0.08f
            val gap = scale * 0.015f
            val quadW = (boxWidth * 2f - 3 * gap) / 4f

            uniqueBoxRect.set(width / 2f - boxWidth, row3Y, width / 2f - boxWidth + quadW, row3Y + row3H)
            finishedBoxRect.set(uniqueBoxRect.right + gap, row3Y, uniqueBoxRect.right + gap + quadW, row3Y + row3H)
            sortBoxRect.set(finishedBoxRect.right + gap, row3Y, finishedBoxRect.right + gap + quadW, row3Y + row3H)
            filterModeRect.set(sortBoxRect.right + gap, row3Y, width / 2f + boxWidth, row3Y + row3H)

            // Unique Button
            p.style = Paint.Style.FILL; p.color = if (filterUniqueOnly) 0x4400FF00 else 0x1A000000
            c.drawRoundRect(uniqueBoxRect, scale * 0.01f, scale * 0.01f, p)
            p.style = Paint.Style.STROKE; p.color = if (filterUniqueOnly) GameColors.HP else 0xFF888888.toInt()
            c.drawRoundRect(uniqueBoxRect, scale * 0.01f, scale * 0.01f, p)
            pText.textSize = scale * 0.022f; pText.color = if (filterUniqueOnly) GameColors.HP else 0xFFFFFFFF.toInt()
            c.drawText("UNIQUE", uniqueBoxRect.centerX(), uniqueBoxRect.centerY() + scale * 0.008f, pText)

            // Finished Button
            p.style = Paint.Style.FILL; p.color = if (filterFinishedOnly) 0x4400FF00 else 0x1A000000
            c.drawRoundRect(finishedBoxRect, scale * 0.01f, scale * 0.01f, p)
            p.style = Paint.Style.STROKE; p.color = if (filterFinishedOnly) GameColors.HP else 0xFF888888.toInt()
            c.drawRoundRect(finishedBoxRect, scale * 0.01f, scale * 0.01f, p)
            c.drawText("CLEARED", finishedBoxRect.centerX(), finishedBoxRect.centerY() + scale * 0.008f, pText)

            // Sort Button
            p.style = Paint.Style.FILL; p.color = 0x1A000000
            c.drawRoundRect(sortBoxRect, scale * 0.01f, scale * 0.01f, p)
            p.style = Paint.Style.STROKE; p.color = GameColors.PULSE
            c.drawRoundRect(sortBoxRect, scale * 0.01f, scale * 0.01f, p)
            pText.color = GameColors.PULSE
            c.drawText(if (sortDescending) "DESC" else "ASC", sortBoxRect.centerX(), sortBoxRect.centerY() + scale * 0.008f, pText)

            // Filter Mode Cycle Button
            p.style = Paint.Style.FILL; p.color = 0x1A000000
            c.drawRoundRect(filterModeRect, scale * 0.01f, scale * 0.01f, p)
            p.style = Paint.Style.STROKE; p.color = GameColors.COOLANT
            c.drawRoundRect(filterModeRect, scale * 0.01f, scale * 0.01f, p)
            pText.color = GameColors.COOLANT
            c.drawText(featureFilterMode.label, filterModeRect.centerX(), filterModeRect.centerY() + scale * 0.008f, pText)

            // --- FEATURE CHIPS ROW 4 & 5 ---
            val chipStartY = row3Y + row3H + scale * 0.02f
            val totalFilterWidth = boxWidth * 2f + scale * 0.03f
            val chipGap = scale * 0.012f
            val chipW = (totalFilterWidth - 5 * chipGap) / 6f
            val chipH = scale * 0.055f

            featureEnumValues.forEachIndexed { index, feat ->
                val r = index / 6
                val colInRow = index % 6
                val cx = width / 2f - boxWidth + colInRow * (chipW + chipGap)
                val cy = chipStartY + r * (chipH + chipGap)
                
                val rect = featureRects.getOrPut(feat) { RectF() }
                rect.set(cx, cy, cx + chipW, cy + chipH)

                val isSelected = selectedFeatures.contains(feat)
                p.style = Paint.Style.FILL; p.color = if (isSelected) 0x6600AAFF else 0x1A000000
                c.drawRoundRect(rect, scale * 0.005f, scale * 0.005f, p)
                p.style = Paint.Style.STROKE; p.color = if (isSelected) GameColors.COOLANT else 0x44FFFFFF
                c.drawRoundRect(rect, scale * 0.005f, scale * 0.005f, p)

                pText.textSize = scale * 0.018f; pText.color = if (isSelected) GameColors.COOLANT else 0xFFCCCCCC.toInt()
                val shortName = if (feat.name.length > 7) feat.name.take(5) + ".." else feat.name
                c.drawText(shortName, rect.centerX(), rect.centerY() + scale * 0.007f, pText)
            }
        } else {
            if (featureRects.isNotEmpty()) {
                featureRectPool.addAll(featureRects.values)
                featureRects.clear()
            }
            uniqueBoxRect.setEmpty()
            finishedBoxRect.setEmpty()
            sortBoxRect.setEmpty()
            filterModeRect.setEmpty()
        }

        // Dynamic Row Generation Layout Config
        val startY = listTop + scale * 0.02f + scrollY
        val boxSize = scale * 0.16f
        val gap = scale * 0.035f
        val columns = if (width > height) 6 else 4
        val totalW = columns * boxSize + (columns - 1) * gap
        val startX = (width - totalW) / 2f

        // Double-buffering level buttons to prevent race conditions during touch
        val oldLevelButtons = HashMap(levelButtons)
        levelButtons.clear()

        if (cachedList.isEmpty() && (isScanning || resultQueue.isNotEmpty())) {
            pText.textSize = scale * 0.032f; pText.color = GameColors.COOLANT
            c.drawText("PENETRATING ARCHIVES...", width / 2f, listTop + (listBottom - listTop) / 2f, pText)
            
            pText.textSize = scale * 0.022f; pText.color = 0xAAFFFFFF.toInt()
            val totalScannedStr = String.format(Locale.ENGLISH, "%,d", totalScanned)
            c.drawText("SCANNED: $totalScannedStr", width / 2f, listTop + (listBottom - listTop) / 2f + scale * 0.05f, pText)
        } else if (cachedList.isEmpty() && !isScanning) {
            pText.textSize = scale * 0.032f; pText.color = GameColors.RED
            c.drawText("NO MATCHING PROTOCOLS FOUND", width / 2f, listTop + (listBottom - listTop) / 2f, pText)
        }

        var col = 0
        var row = 0

        c.save()
        c.clipRect(0f, listTop, width, listBottom)

        for (lvl in cachedList) {
            val cx = startX + col * (boxSize + gap)
            val cy = startY + row * (boxSize + gap)

            reusableRect.set(cx, cy, cx + boxSize, cy + boxSize)

            if (reusableRect.bottom >= listTop && reusableRect.top <= listBottom) {
                // Reuse rect from old map or pool
                val rect = oldLevelButtons.remove(lvl) ?: if (buttonPool.isNotEmpty()) buttonPool.removeAt(buttonPool.size - 1) else RectF()
                rect.set(reusableRect)
                levelButtons[lvl] = rect

                val mask = LevelEngine.getFeaturesMask(lvl)
                val isNextNode = (lvl == SaveManager.maxCampaignLevel)

                // --- 1. DYNAMIC ACCENT TONE BLENDING ENGINE ---
                var mixedColor = GameColors.GRID
                var colorCount = 0

                for (f in featureEnumValues) {
                    if ((mask and (1 shl f.ordinal)) != 0) {
                        val featureColor = when (f) {
                            LevelFeature.CLASSIC -> GameColors.PULSE
                            LevelFeature.MAZE -> GameColors.TEXT
                            LevelFeature.DARKNESS -> GameColors.DARKNESS
                            LevelFeature.BOSS -> GameColors.BOSS
                            LevelFeature.ESCAPE -> GameColors.YELLOW
                            LevelFeature.ELIMINATION -> GameColors.RED
                            LevelFeature.DEFENSE -> GameColors.SHIELD
                            LevelFeature.SPECIAL -> GameColors.OVERCLOCK
                            LevelFeature.ADMIN_BONUS -> GameColors.HP
                            LevelFeature.BOMB -> 0xFFFF0000.toInt()
                            LevelFeature.CLEAN_SWEEP -> GameColors.COOLANT
                        }
                        mixedColor = if (colorCount == 0) featureColor else GameColors.mixColors(mixedColor, featureColor, 0.5f)
                        colorCount++
                    }
                }

                val baseBgTone = when {
                    isNextNode -> 0xFF052510.toInt()
                    hitOnDown == lvl -> 0xFF222222.toInt()
                    else -> GameColors.BG
                }
                val finalBgColor = GameColors.mixColors(baseBgTone, mixedColor, 0.25f)

                // Background Matrix Render
                p.style = Paint.Style.FILL; p.color = finalBgColor
                c.drawRoundRect(reusableRect, scale * 0.015f, scale * 0.015f, p)

                // Circuit Board Framing Borders
                p.style = Paint.Style.STROKE
                p.strokeWidth = if (hitOnDown == lvl) scale * 0.006f else scale * 0.003f
                p.color = when {
                    isNextNode -> GameColors.HP
                    hitOnDown == lvl -> GameColors.CLARITY
                    else -> GameColors.mixColors(0x22FFFFFF, mixedColor, 0.4f)
                }
                c.drawRoundRect(rect, scale * 0.015f, scale * 0.015f, p)

                // --- 2. MICRO-CHIP ICONS ROW LAYOUT ---
                if (colorCount > 0) {
                    var badgeSize = boxSize * 0.22f
                    var badgeGap = boxSize * 0.04f
                    var totalBadgesW = (colorCount * badgeSize) + ((colorCount - 1) * badgeGap)

                    // Auto-scaling for many features to prevent overflow
                    val maxAllowedW = boxSize * 0.88f
                    if (totalBadgesW > maxAllowedW) {
                        val shrinkFactor = maxAllowedW / totalBadgesW
                        badgeSize *= shrinkFactor
                        badgeGap *= shrinkFactor
                        totalBadgesW = (colorCount * badgeSize) + ((colorCount - 1) * badgeGap)
                    }

                    var currentBadgeX = rect.centerX() - (totalBadgesW / 2f)
                    // Push icons slightly lower if there's more room, or higher if text is big
                    val badgeY = rect.bottom - badgeSize - (boxSize * 0.08f)

                    for (f in featureEnumValues) {
                        if ((mask and (1 shl f.ordinal)) != 0) {
                            p.color = when (f) {
                                LevelFeature.CLASSIC -> GameColors.PULSE
                                LevelFeature.MAZE -> GameColors.TEXT
                                LevelFeature.DARKNESS -> GameColors.DARKNESS
                                LevelFeature.BOSS -> GameColors.BOSS
                                LevelFeature.ESCAPE -> GameColors.YELLOW
                                LevelFeature.ELIMINATION -> GameColors.RED
                                LevelFeature.DEFENSE -> GameColors.SHIELD
                                LevelFeature.SPECIAL -> GameColors.OVERCLOCK
                                LevelFeature.ADMIN_BONUS -> GameColors.HP
                                LevelFeature.BOMB -> 0xFFFF0000.toInt()
                                LevelFeature.CLEAN_SWEEP -> GameColors.COOLANT
                            }

                            badgeRect.set(currentBadgeX, badgeY, currentBadgeX + badgeSize, badgeY + badgeSize)
                            com.appsbyalok.echohunter.utils.LevelIcons.drawMicroIcon(c, f, badgeRect, p, finalBgColor)

                            currentBadgeX += badgeSize + badgeGap
                        }
                    }
                }

                // --- 3. HARDWARE SIGNAL TEXT LAYER ---
                pText.color = GameColors.CLARITY
                val lvlStr = lvl.toString()

                // Dynamic sizing based on string length and measured width
                val maxTextWidth = boxSize * 0.82f
                pText.textSize = scale * 0.045f
                val measured = pText.measureText(lvlStr)
                if (measured > maxTextWidth) {
                    pText.textSize *= (maxTextWidth / measured)
                }

                // Adjust vertical position based on feature count to avoid overlap
                val textYOffset = if (colorCount > 5) boxSize * 0.14f else boxSize * 0.05f
                c.drawText(lvlStr, rect.centerX(), rect.centerY() - textYOffset, pText)

                // Stars display
                val stars = SaveManager.getLevelStars(lvl)
                if (stars > 0) {
                    pText.color = GameColors.YELLOW
                    pText.textSize = scale * 0.025f
                    val starText = "★".repeat(stars)
                    c.drawText(starText, rect.centerX(), rect.centerY() + boxSize * 0.15f, pText)
                }
            }

            col++
            if (col >= columns) { col = 0; row++ }
        }
        
        // Return remaining old rects to pool
        buttonPool.addAll(oldLevelButtons.values)
        oldLevelButtons.clear()

        // --- LOAD MORE / SCANNING STATUS ---
        loadMoreRect.setEmpty()
        
        val isDone = if (filterFinishedOnly) {
            val list = sortedFinishedIds
            list != null && lastFinishedIndex >= list.size - 1
        } else {
            if (sortDescending) {
                lastScannedLevel != -1 && lastScannedLevel < 1
            } else {
                lastScannedLevel != -1 && lastScannedLevel > SaveManager.maxCampaignLevel
            }
        }

        if (!isDone || isScanning || resultQueue.isNotEmpty()) {
            val cx = startX + col * (boxSize + gap)
            val cy = startY + row * (boxSize + gap)
            loadMoreRect.set(cx, cy, cx + boxSize, cy + boxSize)

            if (loadMoreRect.bottom >= listTop && loadMoreRect.top <= listBottom) {
                if (!isScanning && resultQueue.isEmpty()) {
                    p.style = Paint.Style.FILL; p.color = if (hitOnDown == -10) 0x4400AAFF else 0x1A000000
                    c.drawRoundRect(loadMoreRect, scale * 0.015f, scale * 0.015f, p)
                    p.style = Paint.Style.STROKE; p.color = GameColors.COOLANT; p.strokeWidth = scale * 0.003f
                    c.drawRoundRect(loadMoreRect, scale * 0.015f, scale * 0.015f, p)

                    pText.color = GameColors.COOLANT; pText.textSize = scale * 0.025f
                    c.drawText("FETCH", loadMoreRect.centerX(), loadMoreRect.centerY() - scale * 0.01f, pText)
                    c.drawText("NEXT", loadMoreRect.centerX(), loadMoreRect.centerY() + scale * 0.02f, pText)
                } else {
                    // Just show a scanning card
                    p.style = Paint.Style.STROKE; p.color = 0x44FFFFFF; p.strokeWidth = scale * 0.002f
                    c.drawRoundRect(loadMoreRect, scale * 0.015f, scale * 0.015f, p)
                    pText.color = 0x88FFFFFF.toInt(); pText.textSize = scale * 0.02f
                    c.drawText("DECODING...", loadMoreRect.centerX(), loadMoreRect.centerY() + scale * 0.007f, pText)
                }
            }
            row++
        } else if (cachedList.isNotEmpty()) {
            // End of Archives message
            val cy = startY + (row + 0.5f) * (boxSize + gap)
            pText.color = 0x44FFFFFF; pText.textSize = scale * 0.025f
            c.drawText("── END OF ARCHIVES ──", width / 2f, cy, pText)
            row++
        }
        c.restore()

        // --- SCANNING PROGRESS BAR ---
        if (isScanning || resultQueue.isNotEmpty()) {
            p.style = Paint.Style.FILL
            val barH = scale * 0.008f
            val maxLvl = SaveManager.maxCampaignLevel
            val totalToScan = if (filterFinishedOnly) (sortedFinishedIds?.size ?: maxLvl) else maxLvl
            val scanProgress = if (totalToScan > 0) totalScanned.toFloat() / totalToScan else 0f
            
            // YouTube-style red progress bar
            p.color = 0x44FF0000
            c.drawRect(0f, listTop, width, listTop + barH, p)
            
            p.color = 0xFFFF0000.toInt()
            c.drawRect(0f, listTop, width * scanProgress, listTop + barH, p)
            
            // Glowing tip
            val progParams = "P:${width * scanProgress}:$listTop"
            if (progressGradient == null || lastGradientParams != progParams) {
                progressGradient = android.graphics.RadialGradient(
                    width * scanProgress, listTop + barH / 2f,
                    scale * 0.05f,
                    0xFFFF0000.toInt(), 0x00FF0000,
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            p.shader = progressGradient
            c.drawCircle(width * scanProgress, listTop + barH / 2f, scale * 0.03f, p)
            p.shader = null
            lastGradientParams = progParams

            // Textual feedback
            pText.textSize = scale * 0.018f; pText.color = 0xAAFFFFFF.toInt(); pText.textAlign = Paint.Align.RIGHT
            val sectorText = if (currentSector > 0) "SECTOR $currentSector | " else ""
            val totalScannedStr = String.format(Locale.ENGLISH, "%,d", totalScanned)
            c.drawText("${sectorText}MATCHES: $totalMatches  |  SCANNED: $totalScannedStr", width - scale * 0.02f, listTop - scale * 0.01f, pText)
            pText.textAlign = Paint.Align.CENTER
        }

        val totalHeight = (row + 1) * (boxSize + gap)
        maxScroll = max(0f, totalHeight - (listBottom - listTop) + scale * 0.1f)

        // Footer Background Glow
        val footerParams = "F:$listBottom:$height"
        if (footerGradient == null || lastGradientParams != footerParams) {
            footerGradient = android.graphics.LinearGradient(0f, listBottom, 0f, height, 0x00000000, 0xCC1A0000.toInt(), android.graphics.Shader.TileMode.CLAMP)
        }
        p.style = Paint.Style.FILL
        p.shader = footerGradient
        c.drawRect(0f, listBottom, width, height, p)
        p.shader = null
        lastGradientParams = footerParams

        // Micro-console Terminate Core System Button
        closeBtnRect.set(width / 2f - scale * 0.16f, height - scale * 0.10f, width / 2f + scale * 0.16f, height - scale * 0.03f)
        p.style = Paint.Style.FILL; p.color = if (hitOnDown == -2) 0xFF660A0A.toInt() else 0xFF220505.toInt()
        c.drawRoundRect(closeBtnRect, scale * 0.01f, scale * 0.01f, p)
        p.style = Paint.Style.STROKE; p.color = GameColors.RED; p.strokeWidth = scale * 0.004f
        c.drawRoundRect(closeBtnRect, scale * 0.01f, scale * 0.01f, p)

        pText.color = GameColors.RED; pText.textSize = scale * 0.035f
        if (hitOnDown == -2) pText.setShadowLayer(10f, 0f, 0f, GameColors.RED)
        c.drawText("DISCONNECT", closeBtnRect.centerX(), closeBtnRect.centerY() + scale * 0.012f, pText)
        pText.clearShadowLayer()
    }

    private var touchDownX = 0f
    private var touchDownY = 0f

    private fun getHitId(x: Float, y: Float): Int {
        if (closeBtnRect.contains(x, y)) return -2
        if (autoNextBoxRect.contains(x, y)) return -3
        if (autoPilotBoxRect.contains(x, y)) return -4
        if (filterToggleBtnRect.contains(x, y)) return -9
        if (loadMoreRect.contains(x, y)) return -10

        if (isFilterExpanded) {
            if (uniqueBoxRect.contains(x, y)) return -5
            if (finishedBoxRect.contains(x, y)) return -6
            if (sortBoxRect.contains(x, y)) return -7
            if (filterModeRect.contains(x, y)) return -11

            for ((feat, rect) in featureRects) {
                if (rect.contains(x, y)) return -20 - feat.ordinal
            }
        }

        if (y in listTop..listBottom) {
            for ((lvl, rect) in levelButtons) {
                if (rect.contains(x, y)) return lvl
            }
        }

        return -1
    }

    fun onTouch(x: Float, y: Float, action: Int, scale: Float, gs: GameState, onSelect: (Int) -> Unit, onBack: () -> Unit): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = x
                touchDownY = y
                lastTouchY = y
                lastTouchTime = System.currentTimeMillis()
                scrollVelocity = 0f
                isDragging = false
                hitOnDown = getHitId(x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                val currentTime = System.currentTimeMillis()
                val dt = currentTime - lastTouchTime
                val dy = y - lastTouchY
                val dx = x - touchDownX

                val distSq = dx * dx + (y - touchDownY) * (y - touchDownY)
                val threshold = scale * scale * 0.05f

                if (hitOnDown <= -2) {
                    // Static buttons take precedence.
                    if (distSq > threshold) {
                        hitOnDown = -1
                    }
                } else {
                    if (abs(dy) > scale * 0.02f) {
                        isDragging = true
                        hitOnDown = -1
                    } else if (distSq > threshold) {
                        hitOnDown = -1
                    }

                    if (isDragging || hitOnDown == -1) {
                        // VELOCITY CALCULATION
                        if (dt > 0) {
                            val rawVelocity = (dy / dt.toFloat()) * 20f
                            scrollVelocity = (scrollVelocity * 0.4f) + (rawVelocity * 0.6f)
                        }
                        scrollY += dy
                        // Hard stops check
                        if (scrollY > 0f) {
                            scrollY = 0f
                            scrollVelocity = 0f
                        }
                        if (scrollY < -maxScroll) {
                            scrollY = -maxScroll
                            scrollVelocity = 0f
                        }
                    }
                }

                lastTouchY = y
                lastTouchTime = currentTime
            }

            MotionEvent.ACTION_UP -> {
                if (!isDragging && hitOnDown != -1) {
                    val hitOnUp = getHitId(x, y)

                    if (hitOnUp != -1 && hitOnUp == hitOnDown) {
                        when (hitOnUp) {
                            -2 -> {
                                lastMaxLevel = -1 // Reset for next open
                                scanFuture?.cancel(true)
                                EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 100)
                                onBack()
                            }
                            -10 -> {
                                generateNodeList(true)
                                EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 50)
                            }
                            -3 -> {
                                SaveManager.setAutoNextLevel(!SaveManager.isAutoNextLevelEnabled)
                                EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 50)
                            }
                            -4 -> {
                                gs.isAutoPilotActive = !gs.isAutoPilotActive
                                if (gs.isAutoPilotActive) {
                                    gs.autoPilotTimer = 600f
                                    EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 150)
                                    gs.showGlobalMessage("AUTOPILOT ENGAGED.\nSELECT A LEVEL TO START.", 3f)
                                } else {
                                    gs.autoPilotTimer = 0f
                                    EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 50)
                                }
                            }
                            -9 -> {
                                isFilterExpanded = !isFilterExpanded
                                EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 50)
                            }
                            -5 -> {
                                filterUniqueOnly = !filterUniqueOnly
                                generateNodeList(false)
                                EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 50)
                            }
                            -6 -> {
                                filterFinishedOnly = !filterFinishedOnly
                                generateNodeList(false)
                                EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 50)
                            }
                            -7 -> {
                                sortDescending = !sortDescending
                                generateNodeList(false)
                                EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 50)
                            }
                            -11 -> {
                                val values = FeatureFilterMode.entries
                                featureFilterMode = values[(featureFilterMode.ordinal + 1) % values.size]
                                generateNodeList(false)
                                EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 50)
                            }
                            else -> {
                                if (hitOnUp <= -20) {
                                    val featIndex = -(hitOnUp + 20)
                                    if (featIndex in featureEnumValues.indices) {
                                        val feat = featureEnumValues[featIndex]
                                        if (selectedFeatures.contains(feat)) selectedFeatures.remove(feat)
                                        else selectedFeatures.add(feat)
                                        generateNodeList(false)
                                        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_PIP, 50)
                                    }
                                } else {
                                    EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 100)
                                    onSelect(hitOnUp)
                                }
                            }
                        }
                    }
                }
                isDragging = false
                hitOnDown = -1
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                hitOnDown = -1
            }
        }
        return true
    }
}