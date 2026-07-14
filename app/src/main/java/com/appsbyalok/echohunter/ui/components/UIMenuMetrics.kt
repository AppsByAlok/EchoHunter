package com.appsbyalok.echohunter.ui.components

import android.graphics.RectF
import com.appsbyalok.echohunter.data.SaveManager

data class UIMenuMetrics(
    val width: Float,
    val height: Float,
    val scale: Float,
    val insetTop: Float = SaveManager.lastInsetTop,
    val insetBottom: Float = SaveManager.lastInsetBottom,
    val insetLeft: Float = SaveManager.lastInsetLeft,
    val insetRight: Float = SaveManager.lastInsetRight
) {
    val isPortrait: Boolean = height > width
    val isLandscape: Boolean = width > height
    val usableLeft: Float = insetLeft
    val usableRight: Float = width - insetRight
    val usableWidth: Float = usableRight - usableLeft
    val headerHeight: Float = (if (isPortrait) scale * 0.12f else scale * 0.08f) + insetTop
    val footerHeight: Float = scale * 0.13f + insetBottom

    fun contentViewport(topPadding: Float = scale * 0.04f, bottomPadding: Float = scale * 0.02f): RectF {
        return RectF(0f, headerHeight + topPadding, width, height - footerHeight - bottomPadding)
    }

    fun centeredFooterButton(widthFactor: Float = 0.45f, heightFactor: Float = 0.08f, bottomGap: Float = scale * 0.04f): RectF {
        val buttonWidth = scale * widthFactor
        val buttonHeight = scale * heightFactor
        val bottom = height - insetBottom - bottomGap
        return RectF(width / 2f - buttonWidth / 2f, bottom - buttonHeight, width / 2f + buttonWidth / 2f, bottom)
    }
}
