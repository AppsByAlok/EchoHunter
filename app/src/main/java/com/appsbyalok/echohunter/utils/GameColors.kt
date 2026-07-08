package com.appsbyalok.echohunter.utils

// Shared constants for all Renderers and Systems
object GameColors {
    const val BG = 0xFF08080C.toInt()
    const val GRID = 0xFF141420.toInt()
    const val PULSE = 0xFF00FFFF.toInt()
    const val RED = 0xFFFF2A4D.toInt()
    const val YELLOW = 0xFFFFD700.toInt()
    const val TEXT = 0xFFEEEEEE.toInt()
    const val HP = 0xFF00FF7F.toInt()
    const val CLARITY = 0xFFFFFFFF.toInt()
    const val SHIELD = 0xFFAA00FF.toInt()
    const val OVERCLOCK = 0xFFFF5500.toInt()
    const val BOSS = 0xFFFF00FF.toInt()
    const val COOLANT = 0xFF00AAFF.toInt()

    // High-performance Manual ARGB Blend Logic via Bitwise Masks
    fun mixColors(colorA: Int, colorB: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val aA = (colorA shr 24 and 0xFF)
        val aR = (colorA shr 16 and 0xFF)
        val aG = (colorA shr 8 and 0xFF)
        val aB = colorA and 0xFF

        val bA = (colorB shr 24 and 0xFF)
        val bR = (colorB shr 16 and 0xFF)
        val bG = (colorB shr 8 and 0xFF)
        val bbB = colorB and 0xFF

        val outA = ((aA * inverseRatio) + (bA * ratio)).toInt()
        val outR = ((aR * inverseRatio) + (bR * ratio)).toInt()
        val outG = ((aG * inverseRatio) + (bG * ratio)).toInt()
        val outB = ((aB * inverseRatio) + (bbB * ratio)).toInt()

        return (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
    }
}