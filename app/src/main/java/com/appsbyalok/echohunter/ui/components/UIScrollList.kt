package com.appsbyalok.echohunter.ui.components

import android.graphics.RectF

class UIScrollList<T> {
    val scroller = UIScrollView()
    private val itemRects = mutableMapOf<T, RectF>()

    fun clear() {
        itemRects.clear()
    }

    fun put(key: T, rect: RectF) {
        itemRects[key] = rect
    }

    fun hit(x: Float, y: Float): T? {
        if (!scroller.viewport.contains(x, y)) return null
        val localX = x - scroller.viewport.left
        val localY = y - (scroller.viewport.top + scroller.scrollY)
        for ((key, rect) in itemRects) {
            if (rect.contains(localX, localY)) return key
        }
        return null
    }

    fun isDragging(): Boolean = scroller.isDragging || scroller.isDraggingScrollbar
}
