package com.appsbyalok.echohunter.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.appsbyalok.echohunter.utils.GameColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

class UIScrollView {
    val viewport = RectF()
    var scrollY = 0f
    var maxScroll = 0f
    
    private var touchDownY = 0f
    private var lastTouchY = 0f
    private var scrollVelocity = 0f
    private var lastTouchTime = 0L
    
    var isDragging = false
    var isDraggingScrollbar = false
    
    // A flag to indicate if a significant scroll occurred during the current gesture
    var hasMovedSignificantly = false
    private var maybeDragging = false
    
    private val scrollbarRect = RectF()
    private val p = Paint().apply { isAntiAlias = true }

    fun updatePhysics(dt: Float) {
        if (!isDragging && !isDraggingScrollbar && abs(scrollVelocity) > 0.5f) {
            // Normalize physics to 60 FPS (approx 0.0166s per frame)
            val frameScale = dt / 0.0166f
            scrollY += scrollVelocity * frameScale
            scrollVelocity *= 0.95f.pow(frameScale)
            clampScroll()
        }
    }

    private fun clampScroll() {
        if (scrollY > 0f) {
            scrollY = 0f
            scrollVelocity = 0f
        } else if (scrollY < -maxScroll) {
            scrollY = -maxScroll
            scrollVelocity = 0f
        }
    }

    fun onTouch(x: Float, y: Float, action: Int, scale: Float): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = false
                isDraggingScrollbar = false
                maybeDragging = false
                if (viewport.contains(x, y)) {
                    hasMovedSignificantly = false
                    val scrollbarHitArea = RectF(scrollbarRect.left - scale * 0.05f, viewport.top, scrollbarRect.right + scale * 0.05f, viewport.bottom)
                    if (maxScroll > 0 && scrollbarHitArea.contains(x, y)) {
                        isDraggingScrollbar = true
                        hasMovedSignificantly = true
                        return true
                    } else {
                        touchDownY = y
                        lastTouchY = y
                        lastTouchTime = System.currentTimeMillis()
                        scrollVelocity = 0f
                        maybeDragging = true
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val currentTime = System.currentTimeMillis()
                val dy = y - lastTouchY
                
                if (isDraggingScrollbar) {
                    val viewHeight = viewport.height()
                    val thumbHeight = max(scale * 0.1f, viewHeight * (viewHeight / (maxScroll + viewHeight)))
                    val scrollRange = viewHeight - thumbHeight
                    if (scrollRange > 0) {
                        val relativeY = (y - viewport.top - thumbHeight / 2f).coerceIn(0f, scrollRange)
                        scrollY = -(relativeY / scrollRange) * maxScroll
                    }
                } else if (maybeDragging) {
                    // Reduced slop threshold to 0.04f * scale
                    if (!isDragging && abs(y - touchDownY) > scale * 0.04f) {
                        isDragging = true
                        hasMovedSignificantly = true
                        lastTouchY = y 
                    }
                    if (isDragging) {
                        scrollY += dy
                        clampScroll()
                        val dtMs = (currentTime - lastTouchTime).coerceAtLeast(1)
                        scrollVelocity = (dy / dtMs.toFloat()) * 16.6f
                    }
                }
                lastTouchY = y
                lastTouchTime = currentTime
                return isDragging || isDraggingScrollbar
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasInteracting = isDragging || isDraggingScrollbar
                isDragging = false
                isDraggingScrollbar = false
                maybeDragging = false
                return wasInteracting
            }
        }
        return false
    }

    fun begin(c: Canvas) {
        c.save()
        c.clipRect(viewport)
        c.translate(viewport.left, viewport.top + scrollY)
    }

    fun end(c: Canvas, contentHeight: Float, scale: Float, insetR: Float = 0f) {
        c.restore()
        maxScroll = max(0f, contentHeight - viewport.height())
        drawScrollbar(c, scale, insetR)
    }

    private fun drawScrollbar(c: Canvas, scale: Float, insetR: Float) {
        if (maxScroll <= 0) return
        val viewHeight = viewport.height()
        val thumbHeight = max(scale * 0.1f, viewHeight * (viewHeight / (maxScroll + viewHeight)))
        val scrollRange = viewHeight - thumbHeight
        val thumbY = viewport.top + (-scrollY / maxScroll) * scrollRange
        
        val scrollbarWidth = scale * 0.015f
        val scrollbarMargin = scale * 0.01f
        scrollbarRect.set(viewport.right - scrollbarWidth - scrollbarMargin - insetR, thumbY, viewport.right - scrollbarMargin - insetR, thumbY + thumbHeight)

        // Track
        p.style = Paint.Style.FILL
        p.color = 0x22FFFFFF
        c.drawRoundRect(viewport.right - scrollbarWidth - scrollbarMargin - insetR, viewport.top, viewport.right - scrollbarMargin - insetR, viewport.bottom, scale * 0.005f, scale * 0.005f, p)
        
        // Thumb
        p.color = if (isDraggingScrollbar) GameColors.HP else 0x88FFFFFF.toInt()
        c.drawRoundRect(scrollbarRect, scale * 0.005f, scale * 0.005f, p)
    }
}
