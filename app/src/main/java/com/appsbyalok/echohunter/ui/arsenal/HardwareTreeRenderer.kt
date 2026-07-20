package com.appsbyalok.echohunter.ui.arsenal

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.GameColors

class HardwareTreeRenderer {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    val nodes = mutableListOf<HardwareNode>()
    private val schematicArea = RectF()

    init {
        // --- CORE & SYSTEM (The Root) ---
        nodes.add(HardwareNode("core", "PROBE-7 CORE", NodeType.CORE, "Central processing unit. Essential for all operations.", 0, 0.5f, 0.5f, isUnlocked = true).apply {
            stats.add(UpgradeStat("speed", "Neural Sync (Spd)", 1, 5, 400))
            stats.add(UpgradeStat("hp", "Hull Integrity", 1, 10, 500))
        })
        
        // --- LOGIC & AIM BRANCH (Manual Override is now the Foundation) ---
        nodes.add(HardwareNode("sys_aim_manual", "MANUAL OVERRIDE", NodeType.CORE, "Foundation combat logic. Enables manual aiming and Tactical Pause.", 1000, 0.65f, 0.6f, listOf("core")).apply {
            stats.add(UpgradeStat("pause_time", "Tactical Buffer", 1, 5, 400))
        })
        nodes.add(HardwareNode("sys_aim_auto", "AUTO-TARGETER", NodeType.CORE, "Advanced targeting software. Automatically locks onto threats.", 2500, 0.8f, 0.7f, listOf("sys_aim_manual")).apply {
            stats.add(UpgradeStat("lock_speed", "Acquisition Spd", 1, 5, 600))
            stats.add(UpgradeStat("prioritization", "Threat Sorting", 1, 3, 1000))
        })

        // --- WEAPONS BRANCH ---
        nodes.add(HardwareNode("w_blaster", "SPIKE DRIVER", NodeType.WEAPON, "Default kinetic blaster. High reliability.", 0, 0.5f, 0.35f, listOf("core"), isUnlocked = true).apply {
            stats.add(UpgradeStat("dmg", "Impact Yield", 1, 10, 300))
            stats.add(UpgradeStat("spd", "Cycle Rate", 1, 10, 250))
            stats.add(UpgradeStat("mag", "Cell Capacity", 1, 5, 500))
        })
        
        nodes.add(HardwareNode("w_shotgun", "SCATTER SHELL", NodeType.WEAPON, "Close-range area denial. High spread.", 1500, 0.35f, 0.2f, listOf("w_blaster")).apply {
            stats.add(UpgradeStat("dmg", "Pellet Impact", 1, 8, 450))
            stats.add(UpgradeStat("spread", "Choke Adjust", 1, 5, 400))
            stats.add(UpgradeStat("reload", "Quick Cycle", 1, 5, 600))
        })
        nodes.add(HardwareNode("shotgun_burn", "THERMITE SLUGS", NodeType.WEAPON, "Exclusive route: shots ignite enemies, dealing damage over time.", 3500, 0.22f, 0.12f, listOf("w_shotgun"), exclusiveGroup = "shotgun_route").apply {
            stats.add(UpgradeStat("burn_dmg", "Thermal Yield", 1, 5, 800))
        })
        nodes.add(HardwareNode("shotgun_shock", "TESLA BUCKSHOT", NodeType.WEAPON, "Exclusive route: shots chain electric damage between close enemies.", 3500, 0.28f, 0.3f, listOf("w_shotgun"), exclusiveGroup = "shotgun_route").apply {
            stats.add(UpgradeStat("chain_range", "Jump Radius", 1, 5, 800))
        })
        
        nodes.add(HardwareNode("w_sniper", "ARC BOLT", NodeType.WEAPON, "Heavy rail-driver. Hold to charge for massive piercing damage.", 2500, 0.65f, 0.2f, listOf("w_blaster")).apply {
            stats.add(UpgradeStat("charge_dmg", "Peak Voltage", 1, 10, 800))
            stats.add(UpgradeStat("charge_spd", "Charge Velocity", 1, 5, 1000))
            stats.add(UpgradeStat("beam_width", "Focus Lens", 1, 3, 1500))
            stats.add(UpgradeStat("reload", "Coolant Vent", 1, 5, 700))
        })
        nodes.add(HardwareNode("sniper_arc", "ARC CONDUIT", NodeType.WEAPON, "Exclusive route: charged bolts widen into an electric arc.", 4000, 0.78f, 0.12f, listOf("w_sniper"), exclusiveGroup = "sniper_route").apply {
            stats.add(UpgradeStat("arc_width", "Arc Aperture", 1, 5, 900))
        })
        nodes.add(HardwareNode("sniper_rail", "RAIL ACCELERATOR", NodeType.WEAPON, "Exclusive route: charged bolts become narrow, fast rail slugs.", 4000, 0.72f, 0.3f, listOf("w_sniper"), exclusiveGroup = "sniper_route").apply {
            stats.add(UpgradeStat("velocity", "Rail Velocity", 1, 5, 900))
        })
        
        // Weapon Capacity Node (Carry 3 weapons)
        nodes.add(HardwareNode("sys_carry_w", "ARSENAL EXPANSION", NodeType.CORE, "Enables Neural Link for 3 concurrent weapon slots.", 5000, 0.5f, 0.1f, listOf("w_shotgun", "w_sniper"), prerequisiteMode = PrerequisiteMode.ANY))

        // --- TRAPS & UTILITY BRANCH ---
        nodes.add(HardwareNode("u_decoy", "HOLOMIMIC", NodeType.UTILITY, "Default distraction tool. Distracts nearby enemies.", 0, 0.35f, 0.65f, listOf("core"), isUnlocked = true).apply {
            stats.add(UpgradeStat("dur", "Emitter Life", 1, 5, 350))
            stats.add(UpgradeStat("range", "Signal Radius", 1, 5, 300))
        })
        
        nodes.add(HardwareNode("u_emp", "FLUX MINE", NodeType.UTILITY, "EMP disruption field. Disables spawner nodes and shields.", 2200, 0.2f, 0.75f, listOf("u_decoy")).apply {
            stats.add(UpgradeStat("stun", "Pulse Duration", 1, 5, 700))
            stats.add(UpgradeStat("radius", "Blast Radius", 1, 5, 600))
            stats.add(UpgradeStat("dmg", "Systems Shock", 1, 5, 800))
        })
        nodes.add(HardwareNode("emp_freeze", "CRYOGENIC BURST", NodeType.UTILITY, "Exclusive route: EMP field freezes enemies in place for a long duration.", 3500, 0.12f, 0.82f, listOf("u_emp"), exclusiveGroup = "emp_route").apply {
            stats.add(UpgradeStat("freeze_dur", "Sub-Zero Time", 1, 5, 900))
        })
        nodes.add(HardwareNode("emp_vulnerability", "VIRAL PAYLOAD", NodeType.UTILITY, "Exclusive route: EMP field makes enemies take 50% more damage from all sources.", 3500, 0.08f, 0.7f, listOf("u_emp"), exclusiveGroup = "emp_route").apply {
            stats.add(UpgradeStat("vuln_dur", "Infection Window", 1, 5, 900))
        })
        
        // Trap Capacity Node
        nodes.add(HardwareNode("sys_carry_t", "UTILITY BELT", NodeType.CORE, "Optimized storage for multiple trap types.", 3500, 0.15f, 0.6f, listOf("u_emp")))

        nodes.add(HardwareNode("u_cloak", "VOID SHADOW", NodeType.UTILITY, "Passive stealth coating. Reduces enemy detection range.", 4000, 0.25f, 0.9f, listOf("u_emp")).apply {
            stats.add(UpgradeStat("efficiency", "Visual Dampening", 1, 5, 1200))
        })

        // --- DEFENSE BRANCH ---
        nodes.add(HardwareNode("d_plating", "NANO ARMOR", NodeType.DEFENSE, "Reactive composite plating. Focuses on specialized defense protocols.", 1200, 0.75f, 0.5f, listOf("core")).apply {
            stats.add(UpgradeStat("regen", "Auto-Repair", 1, 5, 800))
        })
        nodes.add(HardwareNode("d_shield", "PULSE SHIELD", NodeType.DEFENSE, "Energy barrier that absorbs incoming fire.", 3500, 0.9f, 0.5f, listOf("d_plating")).apply {
            stats.add(UpgradeStat("cap", "Shield Capacity", 1, 5, 900))
            stats.add(UpgradeStat("regen", "Recharge Rate", 1, 5, 1100))
        })
    }

    fun draw(c: Canvas, x: Float, y: Float, w: Float, h: Float, scale: Float, gs: GameState, hitId: String?) {
        schematicArea.set(x, y, x + w, y + h)

        // Sync nodes with SaveManager
        for (node in nodes) {
            node.isUnlocked = com.appsbyalok.echohunter.data.SaveManager.isNodeUnlocked(node.id)
            node.integrity = com.appsbyalok.echohunter.data.SaveManager.getIntegrity(node.id)
            node.isDamaged = node.integrity < 100f
            for (stat in node.stats) {
                stat.level = com.appsbyalok.echohunter.data.SaveManager.getStatLevel(node.id, stat.id)
            }
        }

        // Draw Area Background
        p.color = 0x1100FFFF
        p.style = Paint.Style.FILL
        c.drawRect(schematicArea, p)
        
        // Draw Grid
        p.color = 0x0A00FFFF
        p.strokeWidth = 1f
        val step = scale * 0.1f
        var gx = schematicArea.left
        while (gx < schematicArea.right) {
            c.drawLine(gx, schematicArea.top, gx, schematicArea.bottom, p)
            gx += step
        }
        var gy = schematicArea.top
        while (gy < schematicArea.bottom) {
            c.drawLine(schematicArea.left, gy, schematicArea.right, gy, p)
            gy += step
        }

        // Draw Connections
        p.style = Paint.Style.STROKE
        p.strokeWidth = scale * 0.005f
        for (node in nodes) {
            val nx = schematicArea.left + node.normX * schematicArea.width()
            val ny = schematicArea.top + node.normY * schematicArea.height()
            
            for (parentId in node.parents) {
                val parent = nodes.find { it.id == parentId } ?: continue
                val px = schematicArea.left + parent.normX * schematicArea.width()
                val py = schematicArea.top + parent.normY * schematicArea.height()
                
                // Connection color based on parent unlock status
                p.color = if (parent.isUnlocked && node.isUnlocked) GameColors.PULSE 
                          else if (parent.isUnlocked) 0x88AAAAAA.toInt()
                          else 0x33AAAAAA
                
                c.drawLine(px, py, nx, ny, p)
            }
        }

        // Draw Nodes
        val nodeSize = scale * 0.05f
        for (node in nodes) {
            val nx = schematicArea.left + node.normX * schematicArea.width()
            val ny = schematicArea.top + node.normY * schematicArea.height()
            node.bounds.set(nx - nodeSize, ny - nodeSize, nx + nodeSize, ny + nodeSize)

            val isPressed = hitId == node.id
            
            // Parent Unlocked check (can we buy this?)
            val canBuy = canUnlock(node)

            // Outer Ring
            p.style = Paint.Style.STROKE
            p.strokeWidth = scale * 0.003f
            p.color = if (node.isUnlocked) GameColors.PULSE else if (canBuy) 0xFFAAAAAA.toInt() else 0x44AAAAAA
            if (isPressed) p.color = Color.WHITE
            c.drawCircle(nx, ny, nodeSize, p)

            // Inner Shape
            p.style = Paint.Style.FILL
            p.color = when(node.type) {
                NodeType.CORE -> GameColors.PULSE
                NodeType.WEAPON -> GameColors.RED
                NodeType.UTILITY -> GameColors.YELLOW
                NodeType.DEFENSE -> GameColors.CLARITY
            }
            if (!node.isUnlocked) p.color = 0x44AAAAAA
            
            drawNodeIcon(c, nx, ny, nodeSize * 0.6f, node.type, p)
            
            // Selection highlight
            if (node.isSelected) {
                p.style = Paint.Style.STROKE
                p.color = Color.WHITE
                p.strokeWidth = scale * 0.005f
                c.drawCircle(nx, ny, nodeSize * 1.2f, p)
            }

            // Damage Glitch Effect
            if (node.isDamaged && node.isUnlocked) {
                p.color = GameColors.RED
                p.style = Paint.Style.STROKE
                c.drawCircle(nx + (Math.random()*4-2).toFloat(), ny + (Math.random()*4-2).toFloat(), nodeSize * 1.1f, p)
            }
        }
    }

    private fun drawNodeIcon(c: Canvas, x: Float, y: Float, size: Float, type: NodeType, paint: Paint) {
        when(type) {
            NodeType.CORE -> c.drawCircle(x, y, size, paint)
            NodeType.WEAPON -> {
                val path = Path()
                path.moveTo(x, y - size)
                path.lineTo(x + size, y + size)
                path.lineTo(x - size, y + size)
                path.close()
                c.drawPath(path, paint)
            }
            NodeType.UTILITY -> {
                c.drawRect(x - size, y - size, x + size, y + size, paint)
            }
            NodeType.DEFENSE -> {
                val path = Path()
                path.moveTo(x, y - size)
                path.lineTo(x + size, y)
                path.lineTo(x, y + size)
                path.lineTo(x - size, y)
                path.close()
                c.drawPath(path, paint)
            }
        }
    }

    fun hitTest(x: Float, y: Float): HardwareNode? {
        return nodes.find { it.bounds.contains(x, y) }
    }

    fun getSelectedNode() = nodes.find { it.isSelected }

    fun selectNode(id: String?) {
        nodes.forEach { it.isSelected = it.id == id }
    }

    fun canUnlock(node: HardwareNode): Boolean {
        val parentsReady = when (node.prerequisiteMode) {
            PrerequisiteMode.ALL -> node.parents.all { id -> nodes.find { it.id == id }?.isUnlocked == true }
            PrerequisiteMode.ANY -> node.parents.any { id -> nodes.find { it.id == id }?.isUnlocked == true }
        }
        val groupAvailable = node.exclusiveGroup == null || nodes.none {
            it.id != node.id && it.exclusiveGroup == node.exclusiveGroup && it.isUnlocked
        }
        return parentsReady && groupAvailable
    }

    fun resetSubtree(root: HardwareNode): Pair<List<HardwareNode>, Long> {
        val affectedIds = mutableSetOf(root.id)
        var changed = true
        while (changed) {
            changed = false
            nodes.filter { node -> node.parents.any { it in affectedIds } && affectedIds.add(node.id) }
                .forEach { changed = true }
        }
        val affected = nodes.filter { it.id in affectedIds && it.isUnlocked }
        val refund = affected.sumOf { node -> node.cost.toLong() + node.getSpentOnStats().toLong() }
        return affected to refund
    }
}
