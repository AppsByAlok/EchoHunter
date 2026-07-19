package com.appsbyalok.echohunter.ui.arsenal

import android.graphics.RectF
import kotlin.math.pow

enum class NodeType { CORE, WEAPON, UTILITY, DEFENSE }
enum class PrerequisiteMode { ALL, ANY }

data class UpgradeStat(
    val id: String,
    val name: String,
    var level: Int = 1,
    val maxLevel: Int = 5,
    val baseCost: Int
) {
    fun getCost(): Int = (baseCost * 1.5f.pow(level - 1)).toInt()
}

data class HardwareNode(
    val id: String,
    val name: String,
    val type: NodeType,
    val description: String,
    val cost: Int,
    val normX: Float,
    val normY: Float,
    val parents: List<String> = emptyList(),
    val prerequisiteMode: PrerequisiteMode = PrerequisiteMode.ALL,
    /** Nodes in one group are mutually exclusive until one is reset. */
    val exclusiveGroup: String? = null,
    var isUnlocked: Boolean = false,
    var isSelected: Boolean = false,
    
    val stats: MutableList<UpgradeStat> = mutableListOf(),
    var integrity: Float = 100f,
    var isDamaged: Boolean = false
) {
    val bounds = RectF()
    
    fun getRepairCost(): Int {
        val base = if (cost <= 0) 500 else cost
        val missingIntegrity = 100f - integrity
        return (missingIntegrity * (base * 0.01f)).toInt().coerceAtLeast(10)
    }

    fun getSpentOnStats(): Int = stats.sumOf { stat ->
        (1 until stat.level).sumOf { level -> (stat.baseCost * 1.5f.pow(level - 1)).toInt() }
    }

    fun takeDamage(amount: Float) {
        integrity = (integrity - amount).coerceAtLeast(0f)
        if (integrity < 100f) isDamaged = true
    }

    fun repair(amount: Float) {
        integrity = (integrity + amount).coerceAtMost(100f)
        if (integrity >= 100f) isDamaged = false
    }
}
