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
    private val clearFiltersBoxRect = RectF()
    private val featureRects = mutableMapOf<LevelFeature, RectF>()
    private val featureRectPool = mutableListOf<RectF>()

    private var headerGradient: android.graphics.LinearGradient? = null
    private var footerGradient: android.graphics.LinearGradient? = null
    private var progressGradient: android.graphics.RadialGradient? = null
    private var lastGradientParams = ""
    private var totalStars = 0
    private var levelsCleared = 0

    private data class ArchiveGridLayout(
        val startY: Float,
        val boxSize: Float,
        val gap: Float,
        val columns: Int,
        val startX: Float
    )

    private data class ArchiveGridCursor(var col: Int = 0, var row: Int = 0)

    private data class ArchiveLayoutMetrics(
        val isLandscape: Boolean,
        val isTablet: Boolean,
        val panelWidth: Float,
        val headerButtonTop: Float,
        val headerButtonHeight: Float,
        val filterToggleTop: Float,
        val filterToggleHeight: Float,
        val controlGap: Float,
        val expandedFilterTop: Float,
        val expandedFilterHeight: Float,
        val chipColumns: Int,
        val gridColumns: Int,
        val gridBoxSize: Float,
        val gridGap: Float,
        val footerHeight: Float
    )

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

    private fun drawBackground(c: Canvas, width: Float, height: Float, scale: Float) {
        c.drawColor(0xEE050508.toInt()) // Dark cyber hacking base background

        p.style = Paint.Style.STROKE
        p.strokeWidth = scale * 0.002f
        p.color = 0x0AFFFFFF
        var slY = 0f
        while (slY < height) {
            c.drawLine(0f, slY, width, slY, p)
            slY += scale * 0.012f
        }
    }

    private fun updateScrollMomentum() {
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
    }

    private fun buildLayoutMetrics(width: Float, height: Float, scale: Float): ArchiveLayoutMetrics {
        val isLandscape = width > height
        val minSide = minOf(width, height)
        val aspect = minSide / max(width, height)
        val isTablet = minSide >= 760f && aspect >= 0.60f
        val panelWidth = minOf(width - scale * 0.04f, when {
            isTablet && isLandscape -> scale * 1.38f
            isTablet -> scale * 0.88f
            isLandscape -> scale * 1.05f
            else -> scale * 0.80f
        })
        val headerButtonHeight = (if (isLandscape) scale * 0.065f else scale * 0.082f).coerceAtLeast(44f)
        val filterToggleHeight = (if (isLandscape) scale * 0.055f else scale * 0.068f).coerceAtLeast(40f)
        val controlGap = (scale * if (isLandscape) 0.012f else 0.016f).coerceAtLeast(8f)
        val headerButtonTop = if (isLandscape) scale * 0.145f else scale * 0.16f
        val filterToggleTop = headerButtonTop + headerButtonHeight + controlGap
        val expandedFilterTop = filterToggleTop + filterToggleHeight + controlGap
        val expandedFilterHeight = if (isLandscape) scale * 0.062f else scale * 0.074f
        val chipColumns = when {
            isTablet && isLandscape -> 8
            isLandscape -> 7
            isTablet -> 6
            else -> 5
        }
        val gridGap = scale * if (isTablet) 0.03f else 0.035f
        val availableGridWidth = width - scale * 0.08f
        val idealGridBoxSize = scale * if (isTablet) 0.145f else 0.16f
        val gridColumns = (((availableGridWidth + gridGap) / (idealGridBoxSize + gridGap)).toInt()).coerceIn(
            minimumValue = if (isLandscape) 6 else 3,
            maximumValue = when {
                isTablet && isLandscape -> 12
                isLandscape -> 10
                isTablet -> 6
                else -> 4
            }
        )
        val gridBoxSize = minOf(idealGridBoxSize, (availableGridWidth - (gridColumns - 1) * gridGap) / gridColumns)
        val footerHeight = (if (isLandscape) scale * 0.105f else scale * 0.13f).coerceAtLeast(64f)

        return ArchiveLayoutMetrics(
            isLandscape = isLandscape,
            isTablet = isTablet,
            panelWidth = panelWidth,
            headerButtonTop = headerButtonTop,
            headerButtonHeight = headerButtonHeight,
            filterToggleTop = filterToggleTop,
            filterToggleHeight = filterToggleHeight,
            controlGap = controlGap,
            expandedFilterTop = expandedFilterTop,
            expandedFilterHeight = expandedFilterHeight,
            chipColumns = chipColumns,
            gridColumns = gridColumns,
            gridBoxSize = gridBoxSize,
            gridGap = gridGap,
            footerHeight = footerHeight
        )
    }

    private fun updateArchiveData(height: Float, scale: Float, metrics: ArchiveLayoutMetrics) {
        val chipRows = ((featureEnumValues.size + metrics.chipColumns - 1) / metrics.chipColumns).coerceAtLeast(1)
        val expandedBottom = metrics.expandedFilterTop + metrics.expandedFilterHeight + metrics.controlGap + chipRows * (scale * 0.052f + metrics.controlGap)
        val collapsedTop = metrics.filterToggleTop + metrics.filterToggleHeight + metrics.controlGap
        val targetListTop = if (isFilterExpanded) expandedBottom else collapsedTop
        val maxAllowedTop = height - metrics.footerHeight - scale * 0.24f
        listTop = targetListTop.coerceAtMost(maxAllowedTop).coerceAtLeast(scale * if (metrics.isLandscape) 0.27f else 0.32f)
        listBottom = height - metrics.footerHeight
        clearFiltersBoxRect.setEmpty()

        if (SaveManager.maxCampaignLevel != lastMaxLevel || (cachedList.isEmpty() && resultQueue.isEmpty() && lastScannedLevel == -1 && !isScanning)) generateNodeList(false)

        var consumed = 0
        while (resultQueue.isNotEmpty() && consumed < 50) {
            cachedList.add(resultQueue.poll()!!)
            consumed++
        }
    }

    private fun hasActiveFilters(): Boolean {
        return filterUniqueOnly || filterFinishedOnly || featureFilterMode != FeatureFilterMode.ANY || selectedFeatures.isNotEmpty()
    }

    private fun clearAllFilters() {
        filterUniqueOnly = false
        filterFinishedOnly = false
        featureFilterMode = FeatureFilterMode.ANY
        selectedFeatures.clear()
        scrollY = 0f
        scrollVelocity = 0f
        generateNodeList(false)
    }

    private fun drawHeader(c: Canvas, width: Float, scale: Float, metrics: ArchiveLayoutMetrics) {
        val headerParams = "H:$listTop"
        if (headerGradient == null || lastGradientParams != headerParams) {
            headerGradient = android.graphics.LinearGradient(0f, 0f, 0f, listTop, 0xDD001A1A.toInt(), 0x00000000, android.graphics.Shader.TileMode.CLAMP)
        }
        p.style = Paint.Style.FILL
        p.shader = headerGradient
        c.drawRect(0f, 0f, width, listTop, p)
        p.shader = null

        pText.textSize = scale * if (metrics.isLandscape) 0.058f else 0.07f
        pText.color = GameColors.PULSE
        pText.setShadowLayer(15f, 0f, 0f, GameColors.PULSE) // Glow effect
        c.drawText("SYSTEM ARCHIVES", width / 2f, scale * if (metrics.isLandscape) 0.085f else 0.11f, pText)
        pText.clearShadowLayer()

        pText.textSize = scale * if (metrics.isLandscape) 0.021f else 0.025f
        pText.color = GameColors.CLARITY
        val statsStr = String.format(Locale.ENGLISH, "CLEARED: %d  |  STARS: %d", levelsCleared, totalStars)
        c.drawText(statsStr, width / 2f, scale * if (metrics.isLandscape) 0.116f else 0.145f, pText)
    }

    private fun drawHeaderButtons(c: Canvas, width: Float, gs: GameState, scale: Float, metrics: ArchiveLayoutMetrics): Float {
        val boxWidth = metrics.panelWidth / 2f

        autoNextBoxRect.set(width / 2f - boxWidth, metrics.headerButtonTop, width / 2f - metrics.controlGap / 2f, metrics.headerButtonTop + metrics.headerButtonHeight)
        val autoNextColor = if (SaveManager.isAutoNextLevelEnabled) GameColors.HP else GameColors.RED
        p.style = Paint.Style.FILL; p.color = if (hitOnDown == -3) GameColors.mixColors(autoNextColor, 0, 0.4f) else 0x1A000000
        c.drawRoundRect(autoNextBoxRect, scale * 0.01f, scale * 0.01f, p)
        p.style = Paint.Style.STROKE; p.color = autoNextColor; p.strokeWidth = scale * 0.004f
        c.drawRoundRect(autoNextBoxRect, scale * 0.01f, scale * 0.01f, p)
        pText.textSize = scale * if (metrics.isLandscape) 0.026f else 0.032f; pText.color = autoNextColor
        c.drawText(if (SaveManager.isAutoNextLevelEnabled) "AUTO-NEXT: ON" else "AUTO-NEXT: OFF", autoNextBoxRect.centerX(), autoNextBoxRect.centerY() + scale * 0.011f, pText)

        autoPilotBoxRect.set(width / 2f + metrics.controlGap / 2f, metrics.headerButtonTop, width / 2f + boxWidth, metrics.headerButtonTop + metrics.headerButtonHeight)
        val autoPilotColor = if (gs.isAutoPilotActive) GameColors.HP else GameColors.RED
        p.style = Paint.Style.FILL; p.color = if (hitOnDown == -4) GameColors.mixColors(autoPilotColor, 0, 0.4f) else 0x1A000000
        c.drawRoundRect(autoPilotBoxRect, scale * 0.01f, scale * 0.01f, p)
        p.style = Paint.Style.STROKE; p.color = autoPilotColor; p.strokeWidth = scale * 0.004f
        c.drawRoundRect(autoPilotBoxRect, scale * 0.01f, scale * 0.01f, p)
        pText.textSize = scale * if (metrics.isLandscape) 0.026f else 0.032f; pText.color = autoPilotColor
        c.drawText(if (gs.isAutoPilotActive) "AUTOPILOT: ON" else "AUTOPILOT: OFF", autoPilotBoxRect.centerX(), autoPilotBoxRect.centerY() + scale * 0.011f, pText)

        return boxWidth
    }

    private fun drawFilters(c: Canvas, width: Float, boxWidth: Float, scale: Float, metrics: ArchiveLayoutMetrics) {
        val filterRow2Y = metrics.filterToggleTop
        val filterRow2H = metrics.filterToggleHeight
        filterToggleBtnRect.set(width / 2f - boxWidth, filterRow2Y, width / 2f + boxWidth, filterRow2Y + filterRow2H)

        p.style = Paint.Style.FILL; p.color = if (isFilterExpanded) 0x4400AAFF else 0x1A000000
        c.drawRoundRect(filterToggleBtnRect, scale * 0.01f, scale * 0.01f, p)
        p.style = Paint.Style.STROKE; p.color = if (isFilterExpanded) GameColors.COOLANT else 0xFF888888.toInt()
        p.strokeWidth = scale * 0.003f
        c.drawRoundRect(filterToggleBtnRect, scale * 0.01f, scale * 0.01f, p)
        pText.textSize = scale * if (metrics.isLandscape) 0.023f else 0.028f; pText.color = if (isFilterExpanded) GameColors.COOLANT else 0xFFFFFFFF.toInt()
        val filterLabel = if (isFilterExpanded) "CLOSE FILTERS ▲" else "OPEN ARCHIVE FILTERS ▼"
        c.drawText(filterLabel, filterToggleBtnRect.centerX(), filterToggleBtnRect.centerY() + scale * 0.01f, pText)

        if (isFilterExpanded) {
            drawExpandedFilters(c, width, boxWidth, scale, metrics)
        } else {
            clearFilterRects()
        }
    }

    private fun drawExpandedFilters(c: Canvas, width: Float, boxWidth: Float, scale: Float, metrics: ArchiveLayoutMetrics) {
        val row3Y = metrics.expandedFilterTop
        val row3H = metrics.expandedFilterHeight
        val gap = metrics.controlGap
        val activeFilters = hasActiveFilters()
        val controlCount = if (activeFilters) 5 else 4
        val controlW = (boxWidth * 2f - (controlCount - 1) * gap) / controlCount

        uniqueBoxRect.set(width / 2f - boxWidth, row3Y, width / 2f - boxWidth + controlW, row3Y + row3H)
        finishedBoxRect.set(uniqueBoxRect.right + gap, row3Y, uniqueBoxRect.right + gap + controlW, row3Y + row3H)
        sortBoxRect.set(finishedBoxRect.right + gap, row3Y, finishedBoxRect.right + gap + controlW, row3Y + row3H)
        filterModeRect.set(sortBoxRect.right + gap, row3Y, width / 2f + boxWidth, row3Y + row3H)
        if (activeFilters) {
            filterModeRect.set(sortBoxRect.right + gap, row3Y, sortBoxRect.right + gap + controlW, row3Y + row3H)
            clearFiltersBoxRect.set(filterModeRect.right + gap, row3Y, width / 2f + boxWidth, row3Y + row3H)
        } else {
            clearFiltersBoxRect.setEmpty()
        }

        p.style = Paint.Style.FILL; p.color = if (filterUniqueOnly) 0x4400FF00 else 0x1A000000
        c.drawRoundRect(uniqueBoxRect, scale * 0.01f, scale * 0.01f, p)
        p.style = Paint.Style.STROKE; p.color = if (filterUniqueOnly) GameColors.HP else 0xFF888888.toInt()
        c.drawRoundRect(uniqueBoxRect, scale * 0.01f, scale * 0.01f, p)
        pText.textSize = scale * if (metrics.isLandscape) 0.018f else 0.022f; pText.color = if (filterUniqueOnly) GameColors.HP else 0xFFFFFFFF.toInt()
        c.drawText("UNIQUE", uniqueBoxRect.centerX(), uniqueBoxRect.centerY() + scale * 0.008f, pText)

        p.style = Paint.Style.FILL; p.color = if (filterFinishedOnly) 0x4400FF00 else 0x1A000000
        c.drawRoundRect(finishedBoxRect, scale * 0.01f, scale * 0.01f, p)
        p.style = Paint.Style.STROKE; p.color = if (filterFinishedOnly) GameColors.HP else 0xFF888888.toInt()
        c.drawRoundRect(finishedBoxRect, scale * 0.01f, scale * 0.01f, p)
        c.drawText("CLEARED", finishedBoxRect.centerX(), finishedBoxRect.centerY() + scale * 0.008f, pText)

        p.style = Paint.Style.FILL; p.color = 0x1A000000
        c.drawRoundRect(sortBoxRect, scale * 0.01f, scale * 0.01f, p)
        p.style = Paint.Style.STROKE; p.color = GameColors.PULSE
        c.drawRoundRect(sortBoxRect, scale * 0.01f, scale * 0.01f, p)
        pText.color = GameColors.PULSE
        c.drawText(if (sortDescending) "DESC" else "ASC", sortBoxRect.centerX(), sortBoxRect.centerY() + scale * 0.008f, pText)

        p.style = Paint.Style.FILL; p.color = 0x1A000000
        c.drawRoundRect(filterModeRect, scale * 0.01f, scale * 0.01f, p)
        p.style = Paint.Style.STROKE; p.color = GameColors.COOLANT
        c.drawRoundRect(filterModeRect, scale * 0.01f, scale * 0.01f, p)
        pText.color = GameColors.COOLANT
        c.drawText(featureFilterMode.label, filterModeRect.centerX(), filterModeRect.centerY() + scale * 0.008f, pText)

        if (activeFilters) {
            p.style = Paint.Style.FILL; p.color = if (hitOnDown == -12) 0x66330000 else 0x1A000000
            c.drawRoundRect(clearFiltersBoxRect, scale * 0.01f, scale * 0.01f, p)
            p.style = Paint.Style.STROKE; p.color = GameColors.RED
            c.drawRoundRect(clearFiltersBoxRect, scale * 0.01f, scale * 0.01f, p)
            pText.color = GameColors.RED
            c.drawText("CLEAR", clearFiltersBoxRect.centerX(), clearFiltersBoxRect.centerY() + scale * 0.008f, pText)
        }

        drawFeatureChips(c, width, boxWidth, row3Y, row3H, scale, metrics)
    }

    private fun drawFeatureChips(c: Canvas, width: Float, boxWidth: Float, row3Y: Float, row3H: Float, scale: Float, metrics: ArchiveLayoutMetrics) {
        val chipStartY = row3Y + row3H + metrics.controlGap
        val totalFilterWidth = boxWidth * 2f + scale * 0.03f
        val chipGap = metrics.controlGap * 0.75f
        val chipColumns = metrics.chipColumns
        val chipW = (totalFilterWidth - (chipColumns - 1) * chipGap) / chipColumns
        val chipH = scale * if (metrics.isLandscape) 0.047f else 0.055f

        featureEnumValues.forEachIndexed { index, feat ->
            val r = index / chipColumns
            val colInRow = index % chipColumns
            val cx = width / 2f - boxWidth + colInRow * (chipW + chipGap)
            val cy = chipStartY + r * (chipH + chipGap)

            val rect = featureRects.getOrPut(feat) { RectF() }
            rect.set(cx, cy, cx + chipW, cy + chipH)

            val isSelected = selectedFeatures.contains(feat)
            p.style = Paint.Style.FILL; p.color = if (isSelected) 0x6600AAFF else 0x1A000000
            c.drawRoundRect(rect, scale * 0.005f, scale * 0.005f, p)
            p.style = Paint.Style.STROKE; p.color = if (isSelected) GameColors.COOLANT else 0x44FFFFFF
            c.drawRoundRect(rect, scale * 0.005f, scale * 0.005f, p)

            pText.textSize = scale * if (metrics.isLandscape) 0.015f else 0.018f; pText.color = if (isSelected) GameColors.COOLANT else 0xFFCCCCCC.toInt()
            val maxNameLength = if (metrics.isLandscape) 6 else 7
            val shortName = if (feat.name.length > maxNameLength) feat.name.take(maxNameLength - 2) + ".." else feat.name
            c.drawText(shortName, rect.centerX(), rect.centerY() + scale * 0.007f, pText)
        }
    }

    private fun clearFilterRects() {
        if (featureRects.isNotEmpty()) {
            featureRectPool.addAll(featureRects.values)
            featureRects.clear()
        }
        uniqueBoxRect.setEmpty()
        finishedBoxRect.setEmpty()
        sortBoxRect.setEmpty()
        filterModeRect.setEmpty()
        clearFiltersBoxRect.setEmpty()
    }

    private fun createGridLayout(width: Float, metrics: ArchiveLayoutMetrics): ArchiveGridLayout {
        val boxSize = metrics.gridBoxSize
        val gap = metrics.gridGap
        val columns = metrics.gridColumns
        val totalW = columns * boxSize + (columns - 1) * gap
        val startX = (width - totalW) / 2f
        return ArchiveGridLayout(
            startY = listTop + gap * 0.65f + scrollY,
            boxSize = boxSize,
            gap = gap,
            columns = columns,
            startX = startX
        )
    }

    private fun drawEmptyState(c: Canvas, width: Float, scale: Float) {
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
    }

    private fun drawScrollableContent(c: Canvas, width: Float, layout: ArchiveGridLayout, scale: Float): Int {
        val oldLevelButtons = HashMap(levelButtons)
        levelButtons.clear()

        val cursor = ArchiveGridCursor()

        c.save()
        c.clipRect(0f, listTop, width, listBottom)
        drawGrid(c, layout, oldLevelButtons, cursor, scale)

        buttonPool.addAll(oldLevelButtons.values)
        oldLevelButtons.clear()

        drawLoadMore(c, width, layout, cursor, scale)
        c.restore()

        return cursor.row
    }

    private fun drawGrid(c: Canvas, layout: ArchiveGridLayout, oldLevelButtons: MutableMap<Int, RectF>, cursor: ArchiveGridCursor, scale: Float) {
        for (lvl in cachedList) {
            val cx = layout.startX + cursor.col * (layout.boxSize + layout.gap)
            val cy = layout.startY + cursor.row * (layout.boxSize + layout.gap)

            reusableRect.set(cx, cy, cx + layout.boxSize, cy + layout.boxSize)

            if (reusableRect.bottom >= listTop && reusableRect.top <= listBottom) {
                drawLevelCard(c, lvl, oldLevelButtons, layout.boxSize, scale)
            }

            cursor.col++
            if (cursor.col >= layout.columns) {
                cursor.col = 0
                cursor.row++
            }
        }
    }

    private fun drawLevelCard(c: Canvas, lvl: Int, oldLevelButtons: MutableMap<Int, RectF>, boxSize: Float, scale: Float) {
        val rect = oldLevelButtons.remove(lvl) ?: if (buttonPool.isNotEmpty()) buttonPool.removeAt(buttonPool.size - 1) else RectF()
        rect.set(reusableRect)
        levelButtons[lvl] = rect

        val mask = LevelEngine.getFeaturesMask(lvl)
        val isNextNode = (lvl == SaveManager.maxCampaignLevel)
        var mixedColor = GameColors.GRID
        var colorCount = 0

        for (f in featureEnumValues) {
            if ((mask and (1 shl f.ordinal)) != 0) {
                val featureColor = getFeatureColor(f)
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

        p.style = Paint.Style.FILL; p.color = finalBgColor
        c.drawRoundRect(reusableRect, scale * 0.015f, scale * 0.015f, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = if (hitOnDown == lvl) scale * 0.006f else scale * 0.003f
        p.color = when {
            isNextNode -> GameColors.HP
            hitOnDown == lvl -> GameColors.CLARITY
            else -> GameColors.mixColors(0x22FFFFFF, mixedColor, 0.4f)
        }
        c.drawRoundRect(rect, scale * 0.015f, scale * 0.015f, p)

        drawLevelFeatureBadges(c, mask, colorCount, rect, boxSize, finalBgColor)
        drawLevelText(c, lvl, colorCount, rect, boxSize, scale)
    }

    private fun getFeatureColor(feature: LevelFeature): Int {
        return when (feature) {
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
    }

    private fun drawLevelFeatureBadges(c: Canvas, mask: Int, colorCount: Int, rect: RectF, boxSize: Float, finalBgColor: Int) {
        if (colorCount <= 0) return

        var badgeSize = boxSize * 0.22f
        var badgeGap = boxSize * 0.04f
        var totalBadgesW = (colorCount * badgeSize) + ((colorCount - 1) * badgeGap)
        val maxAllowedW = boxSize * 0.88f
        if (totalBadgesW > maxAllowedW) {
            val shrinkFactor = maxAllowedW / totalBadgesW
            badgeSize *= shrinkFactor
            badgeGap *= shrinkFactor
            totalBadgesW = (colorCount * badgeSize) + ((colorCount - 1) * badgeGap)
        }

        var currentBadgeX = rect.centerX() - (totalBadgesW / 2f)
        val badgeY = rect.bottom - badgeSize - (boxSize * 0.08f)

        for (f in featureEnumValues) {
            if ((mask and (1 shl f.ordinal)) != 0) {
                p.color = getFeatureColor(f)
                badgeRect.set(currentBadgeX, badgeY, currentBadgeX + badgeSize, badgeY + badgeSize)
                com.appsbyalok.echohunter.utils.LevelIcons.drawMicroIcon(c, f, badgeRect, p, finalBgColor)
                currentBadgeX += badgeSize + badgeGap
            }
        }
    }

    private fun drawLevelText(c: Canvas, lvl: Int, colorCount: Int, rect: RectF, boxSize: Float, scale: Float) {
        pText.color = GameColors.CLARITY
        val lvlStr = lvl.toString()
        val maxTextWidth = boxSize * 0.82f
        pText.textSize = scale * 0.045f
        val measured = pText.measureText(lvlStr)
        if (measured > maxTextWidth) {
            pText.textSize *= (maxTextWidth / measured)
        }

        val textYOffset = if (colorCount > 5) boxSize * 0.14f else boxSize * 0.05f
        c.drawText(lvlStr, rect.centerX(), rect.centerY() - textYOffset, pText)

        val stars = SaveManager.getLevelStars(lvl)
        if (stars > 0) {
            pText.color = GameColors.YELLOW
            pText.textSize = scale * 0.025f
            val starText = "★".repeat(stars)
            c.drawText(starText, rect.centerX(), rect.centerY() + boxSize * 0.15f, pText)
        }
    }

    private fun drawLoadMore(c: Canvas, width: Float, layout: ArchiveGridLayout, cursor: ArchiveGridCursor, scale: Float) {
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
            val cx = layout.startX + cursor.col * (layout.boxSize + layout.gap)
            val cy = layout.startY + cursor.row * (layout.boxSize + layout.gap)
            loadMoreRect.set(cx, cy, cx + layout.boxSize, cy + layout.boxSize)

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
                    p.style = Paint.Style.STROKE; p.color = 0x44FFFFFF; p.strokeWidth = scale * 0.002f
                    c.drawRoundRect(loadMoreRect, scale * 0.015f, scale * 0.015f, p)
                    pText.color = 0x88FFFFFF.toInt(); pText.textSize = scale * 0.02f
                    c.drawText("DECODING...", loadMoreRect.centerX(), loadMoreRect.centerY() + scale * 0.007f, pText)
                }
            }
            cursor.row++
        } else if (cachedList.isNotEmpty()) {
            if (cursor.col > 0) {
                cursor.row++
            }
            cursor.row++

            val cy = layout.startY + (cursor.row + 0.5f) * (layout.boxSize + layout.gap)
            pText.color = 0x44FFFFFF; pText.textSize = scale * 0.025f
            c.drawText("── END OF ARCHIVES ──", width / 2f, cy, pText)
            cursor.row++
        }
    }

    private fun drawProgress(c: Canvas, width: Float, scale: Float) {
        if (isScanning || resultQueue.isNotEmpty()) {
            p.style = Paint.Style.FILL
            val barH = scale * 0.008f
            val maxLvl = SaveManager.maxCampaignLevel
            val totalToScan = if (filterFinishedOnly) (sortedFinishedIds?.size ?: maxLvl) else maxLvl
            val scanProgress = if (totalToScan > 0) totalScanned.toFloat() / totalToScan else 0f

            p.color = 0x44FF0000
            c.drawRect(0f, listTop, width, listTop + barH, p)

            p.color = 0xFFFF0000.toInt()
            c.drawRect(0f, listTop, width * scanProgress, listTop + barH, p)

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

            pText.textSize = scale * 0.018f; pText.color = 0xAAFFFFFF.toInt(); pText.textAlign = Paint.Align.RIGHT
            val sectorText = if (currentSector > 0) "SECTOR $currentSector | " else ""
            val totalScannedStr = String.format(Locale.ENGLISH, "%,d", totalScanned)
            c.drawText("${sectorText}MATCHES: $totalMatches  |  SCANNED: $totalScannedStr", width - scale * 0.02f, listTop - scale * 0.01f, pText)
            pText.textAlign = Paint.Align.CENTER
        }
    }

    private fun updateMaxScroll(row: Int, layout: ArchiveGridLayout, scale: Float) {
        val totalHeight = (row + 1) * (layout.boxSize + layout.gap)
        maxScroll = max(0f, totalHeight - (listBottom - listTop) + scale * 0.1f)
    }

    private fun drawFooter(c: Canvas, width: Float, height: Float, scale: Float) {
        val footerParams = "F:$listBottom:$height"
        if (footerGradient == null || lastGradientParams != footerParams) {
            footerGradient = android.graphics.LinearGradient(0f, listBottom, 0f, height, 0x00000000, 0xCC1A0000.toInt(), android.graphics.Shader.TileMode.CLAMP)
        }
        p.style = Paint.Style.FILL
        p.shader = footerGradient
        c.drawRect(0f, listBottom, width, height, p)
        p.shader = null
        lastGradientParams = footerParams

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

    fun draw(c: Canvas, width: Float, height: Float, gs: GameState, scale: Float) {
        val metrics = buildLayoutMetrics(width, height, scale)
        drawBackground(c, width, height, scale)
        updateScrollMomentum()
        updateArchiveData(height, scale, metrics)

        drawHeader(c, width, scale, metrics)
        val boxWidth = drawHeaderButtons(c, width, gs, scale, metrics)
        drawFilters(c, width, boxWidth, scale, metrics)

        val layout = createGridLayout(width, metrics)
        drawEmptyState(c, width, scale)
        val row = drawScrollableContent(c, width, layout, scale)
        drawProgress(c, width, scale)
        updateMaxScroll(row, layout, scale)
        drawFooter(c, width, height, scale)
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
            if (clearFiltersBoxRect.contains(x, y)) return -12

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
                            -12 -> {
                                clearAllFilters()
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
