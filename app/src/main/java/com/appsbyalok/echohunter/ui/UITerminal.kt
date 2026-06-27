package com.appsbyalok.echohunter.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.ui.terminal.CatCommand
import com.appsbyalok.echohunter.ui.terminal.ClearCommand
import com.appsbyalok.echohunter.ui.terminal.CommandContext
import com.appsbyalok.echohunter.ui.terminal.CommandRegistry
import com.appsbyalok.echohunter.ui.terminal.DataInjectCommand
import com.appsbyalok.echohunter.ui.terminal.DateCommand
import com.appsbyalok.echohunter.ui.terminal.DirCommand
import com.appsbyalok.echohunter.ui.terminal.EchoCommand
import com.appsbyalok.echohunter.ui.terminal.ExitCommand
import com.appsbyalok.echohunter.ui.terminal.GodModeCommand
import com.appsbyalok.echohunter.ui.terminal.HelpCommand
import com.appsbyalok.echohunter.ui.terminal.RebootCommand
import com.appsbyalok.echohunter.ui.terminal.ScanCommand
import com.appsbyalok.echohunter.ui.terminal.SudoCommand
import com.appsbyalok.echohunter.ui.terminal.SysInfoCommand
import com.appsbyalok.echohunter.ui.terminal.UnlockAllCommand
import com.appsbyalok.echohunter.ui.terminal.VerCommand
import com.appsbyalok.echohunter.ui.terminal.WhoamiCommand
import com.appsbyalok.echohunter.utils.EchoAudioManager
import com.appsbyalok.echohunter.utils.GameColors
import java.util.Calendar
import java.util.Locale

class UITerminal {
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }
    private val pBg = Paint().apply { isAntiAlias = true }

    private val lines = mutableListOf<String>()
    private var currentInput = ""
    
    private val files = mapOf(
        "ROOT_TERM.SH" to "1.2 KB",
        "ECHO_PROTOCOL.CFG" to "4.0 KB",
        "USER_DATA.LOG" to "128 KB",
        "PROJECT_ECHO.DOC" to "64 KB",
        "REDACTED_MSG.TXT" to "2.1 KB",
        "KERNEL_DUMP.BIN" to "1024 KB",
        "SECTOR_MAP.IMG" to "5.5 MB"
    )
    private val keyRects = mutableMapOf<String, RectF>()
    private val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    private val handler = Handler(Looper.getMainLooper())
    private val delRunnable = object : Runnable {
        override fun run() {
            if (currentInput.isNotEmpty()) {
                currentInput = currentInput.substring(0, currentInput.length - 1)
                EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_PIP, 30)
                handler.postDelayed(this, 100)
            } else {
                handler.removeCallbacks(this)
            }
        }
    }
    
    private var isSymbolMode = false
    private var isShiftActive = false
    private var isCapsLock = false
    private var isCtrlActive = false
    private var isAltActive = false
    private var lastShiftTapTime = 0L

    private val commandHistory = mutableListOf<String>()
    private var historyIndex = -1
    
    // Autocomplete state for TAB cycling
    private var autocompleteMatches = listOf<String>()
    private var autocompleteIndex = -1
    private var autocompleteBase = ""
    private var lastGeneratedInput = ""

    // Alphabetic Keyboard Layout
    private val kbAlpha = listOf(
        listOf("CTRL", "ALT", "HELP", "EXIT"),
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        listOf("SHIFT", "Z", "X", "C", "V", "B", "N", "M", "DEL"),
        listOf("?123", "TAB", "SPACE", "ENTER")
    )

    // Symbols Keyboard Layout
    private val kbSymbol = listOf(
        listOf("CTRL", "ALT", "HELP", "EXIT"),
        listOf("+", "-", "*", "/", "=", "_", ".", ",", "?", "!"),
        listOf("[", "]", "(", ")", "{", "}", "<", ">", "\\"),
        listOf("@", "#", "$", "%", "^", "&", "|", "'", "\""),
        listOf("SHIFT", "~", ":", ";", "...", "DEL"),
        listOf("ABC", "TAB", "SPACE", "ENTER")
    )

    init {
        // Register all commands
        CommandRegistry.register(HelpCommand())
        CommandRegistry.register(ClearCommand())
        CommandRegistry.register(ExitCommand())
        CommandRegistry.register(DirCommand())
        CommandRegistry.register(DateCommand())
        CommandRegistry.register(VerCommand())
        CommandRegistry.register(WhoamiCommand())
        CommandRegistry.register(ScanCommand())
        CommandRegistry.register(CatCommand())
        CommandRegistry.register(EchoCommand())
        CommandRegistry.register(SudoCommand())
        CommandRegistry.register(RebootCommand())
        CommandRegistry.register(UnlockAllCommand())
        CommandRegistry.register(DataInjectCommand())
        CommandRegistry.register(SysInfoCommand())
        CommandRegistry.register(GodModeCommand())

        lines.add("NANO-OS [Version 4.2.0-ROOT]")
        lines.add("(c) $currentYear ECHO CORP. ALL RIGHTS RESERVED.")
        lines.add("")
        lines.add("TERMINAL INITIALIZED. TYPE 'HELP' FOR CMDS.")
    }

    private fun getKeyWeight(key: String): Float {
        return when (key) {
            "SPACE" -> 4.5f // Large space bar like Android
            "ENTER" -> 2.0f
            "CTRL", "ALT", "HELP", "EXIT" -> 2.5f
            "SHIFT", "DEL", "?123", "ABC", "TAB" -> 1.5f
            else -> 1f
        }
    }

    fun draw(c: Canvas, targetW: Float, targetH: Float, scale: Float, gs: GameState) {
        c.drawColor(0xFF050505.toInt())

        val isLandscape = targetW > targetH
        val kbHeight = if (isLandscape) targetH * 0.68f else targetH * 0.5f
        val startY = targetH - kbHeight

        val padding = scale * 0.04f
        val textSize = scale * 0.03f
        pText.textSize = textSize
        val lineHeight = textSize * 1.5f

        // Available height for terminal lines and prompt
        // We reserve space for the prompt and some margin above keyboard
        val availableHeight = startY - padding * 2 - lineHeight
        val dynamicMaxLines = (availableHeight / lineHeight).toInt().coerceAtLeast(1)

        // Draw Terminal Output
        var currY = padding + textSize
        pText.textAlign = Paint.Align.LEFT
        pText.color = GameColors.HP

        val visibleLines = if (lines.size > dynamicMaxLines) lines.takeLast(dynamicMaxLines) else lines
        for (line in visibleLines) {
            c.drawText(line, padding, currY, pText)
            currY += lineHeight
        }

        // Draw Input Prompt
        val blink = (System.currentTimeMillis() / 600) % 2 == 0L
        val prompt = "> $currentInput"
        pText.color = 0xFFFFFFFF.toInt()
        
        // Ensure prompt doesn't go under keyboard
        val promptY = currY.coerceAtMost(startY - padding * 0.5f)
        c.drawText(prompt, padding, promptY, pText)

        // Block Cursor
        if (blink) {
            val promptWidth = pText.measureText(prompt)
            c.drawRect(padding + promptWidth + 2f, promptY - textSize * 0.8f,
                       padding + promptWidth + textSize * 0.6f, promptY + textSize * 0.2f, pText)
        }

        // Draw Keyboard
        drawKeyboard(c, targetW, targetH, scale, kbHeight, startY)
    }

    private fun drawKeyboard(c: Canvas, targetW: Float, targetH: Float, scale: Float, kbHeight: Float, startY: Float) {
        val currentKb = if (isSymbolMode) kbSymbol else kbAlpha
        val isLandscape = targetW > targetH
        val kbPaddingH = if (isLandscape) targetW * 0.18f else scale * 0.02f
        val kbPaddingV = scale * 0.02f
        val keyGap = scale * 0.01f

        val rows = currentKb.size
        val rowHeight = (kbHeight - kbPaddingV * 2 - keyGap * (rows - 1)) / rows

        keyRects.clear()

        for (r in 0 until rows) {
            val row = currentKb[r]
            val totalWeight = row.sumOf { getKeyWeight(it).toDouble() }.toFloat()
            val availableWidth = targetW - kbPaddingH * 2 - keyGap * (row.size - 1)
            val unitWidth = availableWidth / totalWeight

            val currY = startY + kbPaddingV + r * (rowHeight + keyGap)
            var currentX = kbPaddingH

            for (key in row) {
                val weight = getKeyWeight(key)
                val keyWidth = unitWidth * weight
                val rect = RectF(currentX, currY, currentX + keyWidth, currY + rowHeight)
                keyRects[key] = rect

                // Button colors
                pBg.color = when (key) {
                    "ENTER" -> 0xFF00AA00.toInt() // Green
                    "EXIT" -> 0xFFAA0000.toInt() // Red
                    "DEL" -> 0xFFCC3333.toInt()
                    "SCAN", "DIR", "HELP" -> 0xFF0055AA.toInt() // Blue
                    "SHIFT" -> if (isCapsLock) 0xFF00AAFF.toInt() else if (isShiftActive) 0xFF888888.toInt() else 0xFF444444.toInt()
                    "CTRL" -> if (isCtrlActive) 0xFF888888.toInt() else 0xFF444444.toInt()
                    "ALT" -> if (isAltActive) 0xFF888888.toInt() else 0xFF444444.toInt()
                    "TAB", "?123", "ABC" -> 0xFF444444.toInt() // Dark Gray
                    "SPACE" -> 0xFF222222.toInt() // Dark Background
                    else -> 0xFF111111.toInt()
                }
                pBg.alpha = 240

                c.drawRoundRect(rect, scale * 0.005f, scale * 0.005f, pBg)
                
                // Key Border/Shadow
                pBg.style = Paint.Style.STROKE
                pBg.color = 0xFF555555.toInt()
                pBg.strokeWidth = scale * 0.001f
                c.drawRoundRect(rect, scale * 0.005f, scale * 0.005f, pBg)
                pBg.style = Paint.Style.FILL
                
                pText.color = 0xFFFFFFFF.toInt()
                pText.textAlign = Paint.Align.CENTER
                pText.textSize = if (key.length > 2) rowHeight * 0.25f else rowHeight * 0.4f
                
                // Special Icons/Text
                val displayText = when(key) {
                    "SPACE" -> ""
                    "DEL" -> "⌫"
                    "ENTER" -> "⏎"
                    else -> {
                        if (key.length == 1 && key[0].isLetter()) {
                            if (isShiftActive || isCapsLock) key.uppercase() else key.lowercase()
                        } else key
                    }
                }
                c.drawText(displayText, rect.centerX(), rect.centerY() + pText.textSize * 0.35f, pText)
                
                currentX += keyWidth + keyGap
            }
        }
    }

    fun onTouch(x: Float, y: Float, action: Int, gs: GameState, onExit: () -> Unit): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                for ((key, rect) in keyRects) {
                    if (rect.contains(x, y)) {
                        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_PIP, 50)
                        if (key == "DEL") {
                            // Initial deletion
                            if (currentInput.isNotEmpty()) {
                                currentInput = currentInput.substring(0, currentInput.length - 1)
                            }
                            // Schedule repeating deletion
                            handler.removeCallbacks(delRunnable)
                            handler.postDelayed(delRunnable, 500)
                        } else {
                            handleKeyPress(key, gs, onExit)
                        }
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(delRunnable)
            }
        }
        return true
    }

    private fun handleKeyPress(key: String, gs: GameState, onExit: () -> Unit) {
        if (key != "TAB" && key != "SHIFT" && key != "CTRL" && key != "ALT") {
            autocompleteMatches = emptyList()
            autocompleteIndex = -1
            lastGeneratedInput = ""
        }
        when (key) {
            "ENTER" -> {
                if (currentInput.isNotEmpty()) {
                    if (commandHistory.isEmpty() || commandHistory.last() != currentInput) {
                        commandHistory.add(currentInput)
                    }
                    historyIndex = commandHistory.size
                    processCommand(currentInput, gs, onExit)
                    currentInput = ""
                }
                isShiftActive = false
                isCtrlActive = false
                isAltActive = false
            }
            "EXIT" -> onExit()
            "SPACE" -> currentInput += " "
            "TAB" -> handleAutocomplete(gs)
            "?123", "ABC" -> {
                isSymbolMode = !isSymbolMode
                isShiftActive = false
                isCtrlActive = false
                isAltActive = false
            }
            "SHIFT" -> {
                val now = System.currentTimeMillis()
                if (now - lastShiftTapTime < 300) {
                    isCapsLock = !isCapsLock
                    isShiftActive = false
                } else {
                    isShiftActive = !isShiftActive
                    if (isShiftActive) isCapsLock = false
                }
                lastShiftTapTime = now
            }
            "CTRL" -> isCtrlActive = !isCtrlActive
            "ALT" -> isAltActive = !isAltActive
            "SCAN", "DIR", "HELP" -> processCommand(key, gs, onExit)
            "..." -> currentInput += "..."
            else -> {
                if (isCtrlActive) {
                    when (key.uppercase()) {
                        "C" -> currentInput = ""
                        "L" -> lines.clear()
                        "P" -> if (commandHistory.isNotEmpty()) {
                            historyIndex = (historyIndex - 1).coerceAtLeast(0)
                            currentInput = commandHistory[historyIndex]
                        }
                        "N" -> if (commandHistory.isNotEmpty()) {
                            historyIndex = (historyIndex + 1).coerceAtMost(commandHistory.size)
                            currentInput = if (historyIndex < commandHistory.size) commandHistory[historyIndex] else ""
                        }
                    }
                    isCtrlActive = false
                } else if (isAltActive) {
                    when (key) {
                        "." -> if (commandHistory.isNotEmpty()) {
                            val lastCmd = commandHistory.last()
                            val lastArg = lastCmd.trim().split(" ").last()
                            currentInput += lastArg
                        }
                    }
                    isAltActive = false
                } else {
                    if (currentInput.length < 40) {
                        var char = key
                        if (char.length == 1 && char[0].isLetter()) {
                            char = if (isShiftActive || isCapsLock) char.uppercase() else char.lowercase()
                        }
                        currentInput += char
                    }
                    if (isShiftActive) isShiftActive = false
                }
            }
        }
    }

    private fun handleAutocomplete(gs: GameState) {
        if (currentInput.isEmpty()) return

        // Cycle through existing matches if input hasn't changed manually
        if (autocompleteMatches.isNotEmpty() && currentInput == lastGeneratedInput) {
            autocompleteIndex = (autocompleteIndex + 1) % autocompleteMatches.size
            val nextMatch = autocompleteMatches[autocompleteIndex]
            currentInput = autocompleteBase + nextMatch + (if (autocompleteBase.isEmpty()) " " else "")
            lastGeneratedInput = currentInput
            return
        }

        // Search for new matches
        val isTrailingSpace = currentInput.endsWith(" ")
        val parts = currentInput.trim().uppercase().split(" ").filter { it.isNotEmpty() }

        if (parts.isEmpty()) return

        if (parts.size == 1 && !isTrailingSpace) {
            val prefix = parts[0]
            val matches = CommandRegistry.getAllCommands(gs)
                .flatMap { listOf(it.name) + it.aliases }
                .filter { it.startsWith(prefix) }
                .distinct()
                .sorted()
            
            if (matches.isNotEmpty()) {
                autocompleteMatches = matches
                autocompleteIndex = 0
                autocompleteBase = ""
                currentInput = matches[0] + " "
                lastGeneratedInput = currentInput
            }
        } else if (parts[0] == "CAT") {
            val prefix = if (parts.size > 1) parts[1] else ""
            val matches = files.keys.filter { it.startsWith(prefix) }
            if (matches.isNotEmpty()) {
                autocompleteMatches = matches
                autocompleteIndex = 0
                autocompleteBase = "CAT "
                currentInput = "CAT ${matches[0]}"
                lastGeneratedInput = currentInput
            }
        }
    }

    private fun tryCalculate(cmd: String): String? {
        val expression = cmd.replace(" ", "").replace("X", "*", ignoreCase = true).replace("÷", "/")
        if (!expression.any { it.isDigit() } || !expression.any { "+-*/()".contains(it) }) return null

        // Implicit multiplication: 3( -> 3*(, )6 -> )*6, )( -> )*(
        val sb = StringBuilder()
        for (i in expression.indices) {
            sb.append(expression[i])
            if (i < expression.length - 1) {
                val curr = expression[i]
                val next = expression[i + 1]
                if ((curr.isDigit() && next == '(') || (curr == ')' && next.isDigit()) || (curr == ')' && next == '(')) {
                    sb.append('*')
                }
            }
        }
        val finalExpr = sb.toString()
        var pos = -1
        var ch = -1

        fun nextChar() { ch = if (++pos < finalExpr.length) finalExpr[pos].code else -1 }
        fun eat(charToEat: Int): Boolean {
            if (ch == charToEat) { nextChar(); return true }
            return false
        }

        fun parseExpression(): Double {
            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()
                val x: Double
                val startPos = pos
                if (eat('('.code)) {
                    x = parseExpression()
                    eat(')'.code)
                } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
                    while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
                    x = finalExpr.substring(startPos, pos).toDouble()
                } else throw RuntimeException()
                return x
            }
            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    if (eat('*'.code)) x *= parseFactor()
                    else if (eat('/'.code)) {
                        val d = parseFactor()
                        if (d == 0.0) throw ArithmeticException("DIV/0")
                        x /= d
                    } else return x
                }
            }
            var x = parseTerm()
            while (true) {
                if (eat('+'.code)) x += parseTerm()
                else if (eat('-'.code)) x -= parseTerm()
                else return x
            }
        }

        return try {
            nextChar()
            val result = parseExpression()
            if (pos < finalExpr.length) return null
            val s = result.toString()
            if (s.endsWith(".0")) s.substringBefore(".0")
            else String.format(Locale.US, "%.3f", result).trimEnd('0').trimEnd('.')
        } catch (e: ArithmeticException) { "ERR: DIV/0" } catch (e: Exception) { null }
    }

    private fun processCommand(cmd: String, gs: GameState, onExit: () -> Unit) {
        lines.add("> $cmd")
        
        // 1. Math Calculation Check (Special Fallback)
        val calcResult = tryCalculate(cmd)
        if (calcResult != null) {
            lines.add("= $calcResult")
            return
        }

        val parts = cmd.trim().split(" ").filter { it.isNotEmpty() }
        if (parts.isEmpty()) return
        
        val mainCmdName = parts[0].uppercase()
        val args = if (parts.size > 1) parts.drop(1) else emptyList()
        
        val command = CommandRegistry.getCommand(mainCmdName)
        
        if (command != null) {
            if (command.isUnlocked(gs)) {
                val context = CommandContext(
                    gameState = gs,
                    terminalLines = lines,
                    onExit = onExit,
                    files = files
                )
                val result = command.execute(args, context)
                if (result != null) {
                    lines.addAll(result.split("\n"))
                }
            } else {
                lines.add("ERR: ACCESS DENIED. COMMAND LOCKED.")
            }
        } else {
            lines.add("ERR: UNKNOWN COMMAND '$mainCmdName'")
        }

        if (lines.size > 50) {
            val toRemove = lines.size - 50
            repeat(toRemove) { lines.removeAt(0) }
        }
    }
}
