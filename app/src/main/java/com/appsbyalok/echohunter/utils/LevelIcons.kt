package com.appsbyalok.echohunter.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.appsbyalok.echohunter.data.LevelFeature
import kotlin.math.min

object LevelIcons {
    // Reusable path optimized for Garbage Collection (No stutter)
    private val iconPath = Path()

    /**
     * High-performance custom Icon drawing engine.
     * @param bgColor Used for "cutouts" (like eyes in the skull) so it perfectly blends with the dynamic card background.
     */
    fun drawMicroIcon(c: Canvas, feature: LevelFeature, rect: RectF, p: Paint, bgColor: Int) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = min(rect.width(), rect.height()) / 2f

        val originalStyle = p.style
        val originalStrokeW = p.strokeWidth
        val originalColor = p.color

        iconPath.reset()

        when (feature) {
            LevelFeature.CLASSIC -> {
                // Data Node (Hollow Diamond with a dot)
                p.style = Paint.Style.STROKE
                p.strokeWidth = r * 0.3f
                iconPath.moveTo(cx, cy - r * 0.8f)
                iconPath.lineTo(cx + r * 0.8f, cy)
                iconPath.lineTo(cx, cy + r * 0.8f)
                iconPath.lineTo(cx - r * 0.8f, cy)
                iconPath.close()
                c.drawPath(iconPath, p)
                p.style = Paint.Style.FILL
                c.drawCircle(cx, cy, r * 0.25f, p)
            }
            LevelFeature.MAZE -> {
                // Digital Labyrinth (Circuit-like Hash)
                p.style = Paint.Style.STROKE
                p.strokeWidth = r * 0.25f
                // Outer box
                c.drawRect(cx - r * 0.7f, cy - r * 0.7f, cx + r * 0.7f, cy + r * 0.7f, p)
                // Inner walls
                c.drawLine(cx - r * 0.3f, cy - r * 0.7f, cx - r * 0.3f, cy + r * 0.1f, p)
                c.drawLine(cx + r * 0.3f, cy + r * 0.7f, cx + r * 0.3f, cy - r * 0.1f, p)
            }
            LevelFeature.DEFENSE -> {
                // Shield with Central Core
                p.style = Paint.Style.STROKE
                p.strokeWidth = r * 0.25f
                iconPath.moveTo(cx - r * 0.8f, cy - r * 0.8f)
                iconPath.lineTo(cx + r * 0.8f, cy - r * 0.8f)
                iconPath.lineTo(cx + r * 0.8f, cy + r * 0.1f)
                iconPath.lineTo(cx, cy + r * 0.9f) // Shield point
                iconPath.lineTo(cx - r * 0.8f, cy + r * 0.1f)
                iconPath.close()
                c.drawPath(iconPath, p)

                // Protected Core
                p.style = Paint.Style.FILL
                c.drawCircle(cx, cy, r * 0.25f, p)
            }
            LevelFeature.BOSS -> {
                // Menacing Skull
                p.style = Paint.Style.FILL
                // Skull Dome
                c.drawCircle(cx, cy - r * 0.2f, r * 0.8f, p)
                // Skull Jaw
                c.drawRect(cx - r * 0.45f, cy, cx + r * 0.45f, cy + r * 0.8f, p)

                // Cutouts for eyes and nose
                p.color = bgColor
                c.drawCircle(cx - r * 0.35f, cy - r * 0.1f, r * 0.25f, p) // Left Eye
                c.drawCircle(cx + r * 0.35f, cy - r * 0.1f, r * 0.25f, p) // Right Eye
                iconPath.moveTo(cx, cy + r * 0.2f) // Nose slit
                iconPath.lineTo(cx - r * 0.1f, cy + r * 0.5f)
                iconPath.lineTo(cx + r * 0.1f, cy + r * 0.5f)
                iconPath.close()
                c.drawPath(iconPath, p)
            }
            LevelFeature.ESCAPE -> {
                // Double Chevron (Fast Forward / Exit)
                p.style = Paint.Style.STROKE
                p.strokeWidth = r * 0.35f
                p.strokeJoin = Paint.Join.MITER

                // Arrow 1
                iconPath.moveTo(cx - r * 0.7f, cy - r * 0.6f)
                iconPath.lineTo(cx - r * 0.1f, cy)
                iconPath.lineTo(cx - r * 0.7f, cy + r * 0.6f)

                // Arrow 2
                iconPath.moveTo(cx + r * 0.1f, cy - r * 0.6f)
                iconPath.lineTo(cx + r * 0.7f, cy)
                iconPath.lineTo(cx + r * 0.1f, cy + r * 0.6f)

                c.drawPath(iconPath, p)
            }
            LevelFeature.ELIMINATION -> {
                // Sniper Target / Crosshair
                p.style = Paint.Style.STROKE
                p.strokeWidth = r * 0.25f
                c.drawCircle(cx, cy, r * 0.7f, p)
                // Ticks extending outward
                c.drawLine(cx, cy - r * 0.4f, cx, cy - r, p) // Top
                c.drawLine(cx, cy + r * 0.4f, cx, cy + r, p) // Bottom
                c.drawLine(cx - r * 0.4f, cy, cx - r, cy, p) // Left
                c.drawLine(cx + r * 0.4f, cy, cx + r, cy, p) // Right

                p.style = Paint.Style.FILL
                c.drawCircle(cx, cy, r * 0.15f, p) // Center Dot
            }
            LevelFeature.SPECIAL -> {
                // Warning Triangle with Exclamation Mark (!)
                p.style = Paint.Style.FILL
                iconPath.moveTo(cx, cy - r * 0.9f)
                iconPath.lineTo(cx + r * 0.9f, cy + r * 0.8f)
                iconPath.lineTo(cx - r * 0.9f, cy + r * 0.8f)
                iconPath.close()
                c.drawPath(iconPath, p)

                // Exclamation Cutout
                p.color = bgColor
                p.strokeCap = Paint.Cap.ROUND
                p.style = Paint.Style.STROKE
                p.strokeWidth = r * 0.25f
                c.drawLine(cx, cy - r * 0.3f, cx, cy + r * 0.2f, p) // Line
                p.style = Paint.Style.FILL
                c.drawCircle(cx, cy + r * 0.6f, r * 0.12f, p) // Dot
            }
            LevelFeature.ADMIN_BONUS -> {
                // Crown (Admin access)
                p.style = Paint.Style.FILL
                iconPath.moveTo(cx - r * 0.8f, cy + r * 0.7f)
                iconPath.lineTo(cx + r * 0.8f, cy + r * 0.7f)
                iconPath.lineTo(cx + r * 0.9f, cy - r * 0.6f) // Right peak
                iconPath.lineTo(cx + r * 0.3f, cy)            // Right dip
                iconPath.lineTo(cx, cy - r * 0.9f)            // Center peak
                iconPath.lineTo(cx - r * 0.3f, cy)            // Left dip
                iconPath.lineTo(cx - r * 0.9f, cy - r * 0.6f) // Left peak
                iconPath.close()
                c.drawPath(iconPath, p)
            }
        }

        // Restore original paint settings
        p.style = originalStyle
        p.strokeWidth = originalStrokeW
        p.color = originalColor
        p.strokeJoin = Paint.Join.ROUND
        p.strokeCap = Paint.Cap.BUTT
    }
}