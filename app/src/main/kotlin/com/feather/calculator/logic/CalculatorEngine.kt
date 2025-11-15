package com.feather.calculator.logic

import kotlin.math.round

/**
 * Handles the calculation logic for the calculator.
 * It manages the current expression string and computes a result.
 * This class is decoupled from Android components and UI state.
 */
class CalculatorEngine {

    companion object {
        private const val DIVIDE_BY_ZERO_ERROR = "can't divide by zero"
    }

    private var currentExpression: String = ""
    private var cursorPosition: Int = 0
    private val parser = ExpressionParser()

    // Operator groups for validation
    val ALL_OPERATORS = listOf(ExpressionParser.STD_PLUS, ExpressionParser.STD_MINUS, ExpressionParser.MULTIPLY, ExpressionParser.DIVIDE)
    private val NON_UNARY_START_OPERATORS = listOf(ExpressionParser.STD_PLUS, ExpressionParser.MULTIPLY, ExpressionParser.DIVIDE, ExpressionParser.CLOSE_BRACE)
    private val DIGITS_AND_PERCENT = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", ExpressionParser.PERCENT)
    private val BRACES = listOf(ExpressionParser.OPEN_BRACE, ExpressionParser.CLOSE_BRACE)
    private val IMPLICIT_MULTIPLY_CHARS = DIGITS_AND_PERCENT + listOf(ExpressionParser.CLOSE_BRACE)

    /**
     * Initializes the engine with a previously saved expression and cursor position.
     */
    fun loadState(expression: String, cursor: Int) {
        currentExpression = expression
        // Ensure the loaded cursor position is valid within the loaded expression length
        cursorPosition = cursor.coerceIn(0, currentExpression.length)
    }

    /**
     * Calculates the current balance of open vs. closed braces.
     */
    fun getBraceBalance(): Int {
        var balance = 0
        for (char in currentExpression) {
            when (char) {
                ExpressionParser.OPEN_BRACE.first() -> balance++
                ExpressionParser.CLOSE_BRACE.first() -> balance--
            }
        }
        return balance
    }

    /**
     * Inserts the given input string into the current expression, handling selection.
     */
    private fun insert(input: String, selectionStart: Int, selectionEnd: Int) {
        val prefix = currentExpression.substring(0, selectionStart)
        val suffix = currentExpression.substring(selectionEnd)

        currentExpression = prefix + input + suffix
        cursorPosition = selectionStart + input.length
    }

    /**
     * Checks if the number segment immediately surrounding the cursor position
     * already contains a decimal point.
     */
    private fun containsDecimalPoint(cursorPos: Int): Boolean {
        if (currentExpression.isEmpty()) return false

        // Search backward from the cursor to the nearest operator/brace
        var i = cursorPos - 1
        while (i >= 0) {
            val char = currentExpression[i].toString()
            if (char == ExpressionParser.DECIMAL_POINT) return true
            if (ALL_OPERATORS.contains(char) || BRACES.contains(char) || char == ExpressionParser.PERCENT) break
            i--
        }

        // Search forward from the cursor to the nearest operator/brace
        var i2 = cursorPos
        while (i2 < currentExpression.length) {
            val char = currentExpression[i2].toString()
            if (char == ExpressionParser.DECIMAL_POINT) return true
            if (ALL_OPERATORS.contains(char) || BRACES.contains(char) || char == ExpressionParser.PERCENT) break
            i2++
        }

        return false
    }

    /**
     * Toggles the sign of the number segment at the cursor position.
     */
    fun toggleSign(selectionStart: Int, selectionEnd: Int) {
        // Logic remains the same, ensuring it's operating purely on the expression string
        if (currentExpression.isEmpty() || selectionStart != selectionEnd) return

        var start = selectionStart

        // 1. Find the start of the number segment
        while (start > 0 && (currentExpression[start - 1].isDigit() || currentExpression[start - 1] == ExpressionParser.DECIMAL_POINT.first() || currentExpression[start - 1] == ExpressionParser.PERCENT.first())) {
            start--
        }

        // 2. Check for an existing unary minus (simplified check)
        val hasUnaryMinus = start > 0 && currentExpression[start - 1] == ExpressionParser.STD_MINUS.first() &&
                            (start == 1 || ALL_OPERATORS.contains(currentExpression.getOrNull(start - 2)?.toString()) || currentExpression[start - 2] == ExpressionParser.OPEN_BRACE.first())

        when {
            hasUnaryMinus -> {
                // Remove the unary minus
                currentExpression = currentExpression.substring(0, start - 1) + currentExpression.substring(start)
                cursorPosition = selectionStart - 1
            }
            else -> {
                // Add the unary minus if insertion point is valid
                val charBefore = currentExpression.getOrNull(start - 1)?.toString()
                val isSafeInsertion = charBefore == null || ALL_OPERATORS.contains(charBefore) || charBefore == ExpressionParser.OPEN_BRACE

                if (isSafeInsertion) {
                    currentExpression = currentExpression.substring(0, start) + ExpressionParser.STD_MINUS + currentExpression.substring(start)
                    cursorPosition = selectionStart + 1
                }
            }
        }
    }

    /**
     * Appends a character (digit, operator, etc.) to the current expression,
     * considering the cursor position and selection.
     */
    fun appendInput(input: String, selectionStart: Int, selectionEnd: Int) {

        val charBefore = currentExpression.getOrNull(selectionStart - 1)?.toString()
        val charAfter = currentExpression.getOrNull(selectionEnd)?.toString()
        val isAtEnd = selectionStart == currentExpression.length

        // 1. Initial State Check
        if (currentExpression.isEmpty() && selectionStart == 0 && NON_UNARY_START_OPERATORS.contains(input)) {
            return
        }

        // --- Brace Input Logic ---
        when (input) {
            ExpressionParser.OPEN_BRACE -> {
                var prefixToInsert = ""
                if (IMPLICIT_MULTIPLY_CHARS.contains(charBefore)) {
                    prefixToInsert = ExpressionParser.MULTIPLY
                }
                insert(prefixToInsert + ExpressionParser.OPEN_BRACE, selectionStart, selectionEnd)
                return
            }
            ExpressionParser.CLOSE_BRACE -> {
                if (getBraceBalance() <= 0) return
                if (ALL_OPERATORS.contains(charBefore) || charBefore == ExpressionParser.OPEN_BRACE) return

                var suffixToInsert = ""
                if (DIGITS_AND_PERCENT.contains(charAfter) || charAfter == ExpressionParser.OPEN_BRACE) {
                    suffixToInsert = ExpressionParser.MULTIPLY
                }

                val prefix = currentExpression.substring(0, selectionStart)
                val suffix = currentExpression.substring(selectionEnd)

                currentExpression = prefix + ExpressionParser.CLOSE_BRACE + suffixToInsert + suffix
                cursorPosition = selectionStart + 1
                return
            }
        }
        // --- End Brace Input Logic ---

        // Operator Replacement Check (Handles all operators including subtraction)
        if (ALL_OPERATORS.contains(input)) {
            val lastChar = currentExpression.getOrNull(selectionStart - 1)?.toString()

            // Rule: If the character just before the cursor is a binary operator (+, ×, ÷, -),
            // AND the new input is also a non-minus binary operator, replace the old one.
            if (lastChar != null && ALL_OPERATORS.contains(lastChar) && input != ExpressionParser.STD_MINUS) {
                // Replace previous operator with new operator
                val prefix = currentExpression.substring(0, selectionStart - 1)
                val suffix = currentExpression.substring(selectionEnd)
                currentExpression = prefix + input + suffix
                cursorPosition = selectionStart
                return
            }
        }

        // 3. Percentage Check
        if (input == ExpressionParser.PERCENT && currentExpression.endsWith(ExpressionParser.PERCENT) && isAtEnd) {
            return
        }

        // Decimal Point Check
        if (input == ExpressionParser.DECIMAL_POINT) {
            val checkPos = if (selectionStart != selectionEnd) selectionStart else selectionStart
            if (containsDecimalPoint(checkPos)) {
                return
            }
        }

        // 4. Standard Insertion Logic
        insert(input, selectionStart, selectionEnd)
    }

    /**
     * Clears the entire expression.
     */
    fun clear() {
        currentExpression = ""
        cursorPosition = 0
    }

    /**
     * Removes the last character or the selected range from the expression.
     */
    fun backspace(selectionStart: Int, selectionEnd: Int) {
        if (currentExpression.isEmpty()) {
            return
        }

        if (selectionStart != selectionEnd) {
            // Case 1: Text is selected (delete selection)
            val prefix = currentExpression.substring(0, selectionStart)
            val suffix = currentExpression.substring(selectionEnd)
            currentExpression = prefix + suffix
            cursorPosition = selectionStart

        } else if (selectionStart > 0) {
            // Case 2: No text is selected (delete character before cursor)
            val prefix = currentExpression.substring(0, selectionStart - 1)
            val suffix = currentExpression.substring(selectionStart)
            currentExpression = prefix + suffix
            cursorPosition = selectionStart - 1
        }
    }

    /**
     * FIX: Adds parentheses around the current selection or inserts '()' at the cursor position.
     */
    fun appendParentheses(selectionStart: Int, selectionEnd: Int) {
        // If text is selected, wrap it in parentheses.
        if (selectionStart != selectionEnd) {
            val prefix = currentExpression.substring(0, selectionStart)
            val selected = currentExpression.substring(selectionStart, selectionEnd)
            val suffix = currentExpression.substring(selectionEnd)

            currentExpression = "$prefix($selected)$suffix"
            // Keep cursor at the end of the selection, now end of closing brace.
            cursorPosition = selectionEnd + 2
        } else {
            // No selection, insert '()' at the cursor.
            val prefix = currentExpression.substring(0, cursorPosition)
            val suffix = currentExpression.substring(cursorPosition)

            currentExpression = "$prefix()$suffix"
            // Move cursor inside the new parentheses.
            cursorPosition += 1
        }
    }

    /**
     * FIX: Public method to set the cursor position.
     */
    fun setCursorPosition(newCursorPosition: Int) {
        // Coerce ensures the cursor is always between 0 and the expression length.
        cursorPosition = newCursorPosition.coerceIn(0, currentExpression.length)
    }

    /**
     * Returns the current expression string for the expression TextView.
     */
    fun getExpression(): String = currentExpression

    /**
     * Returns the internal cursor position.
     */
    fun getCursorPosition(): Int = cursorPosition

    /**
     * Evaluates the current expression and returns the live result as a formatted string.
     */
    fun calculateLiveResult(): String {
        // Automatically close any unclosed braces for live calculation
        var expressionToEvaluate = currentExpression
        var balance = getBraceBalance()
        while (balance > 0) {
            expressionToEvaluate += ExpressionParser.CLOSE_BRACE
            balance--
        }

        return try {
            val result = parser.evaluate(expressionToEvaluate)

            when {
                result.isInfinite() -> DIVIDE_BY_ZERO_ERROR
                result.isNaN() -> ""
                else -> formatResult(result)
            }
        } catch (e: Exception) {
            // General syntax error (e.g., expression ending with operator)
            ""
        }
    }

    /**
     * Performs the final calculation (e.g., when '=' is pressed).
     */
    fun finalizeCalculation(): String {

        // If there are unclosed braces, attempt to close them before finalizing.
        var finalExpression = currentExpression
        var balance = getBraceBalance()
        while (balance > 0) {
            finalExpression += ExpressionParser.CLOSE_BRACE
            balance--
        }

        return try {
            val result = parser.evaluate(finalExpression)

            when {
                result.isInfinite() -> {
                    // Keep the expression so the user can edit the '0'
                    cursorPosition = finalExpression.length
                    return DIVIDE_BY_ZERO_ERROR
                }
                result.isNaN() -> {
                    // Handle general errors like syntax issues (NaN)
                    currentExpression = ""
                    cursorPosition = 0
                    return "Syntax Error"
                }
                else -> {
                    // Success case
                    val formattedResult = formatResult(result)
                    currentExpression = formattedResult
                    cursorPosition = formattedResult.length
                    return formattedResult
                }
            }
        } catch (e: Exception) {
            // Handle parsing exceptions (e.g., mismatched braces)
            currentExpression = ""
            cursorPosition = 0
            return "Syntax Error"
        }
    }

    /**
     * Formats a Double into a clean string, removing unnecessary trailing zeros.
     */
    private fun formatResult(value: Double): String {
        val isInteger = value == round(value)

        return if (isInteger) {
            String.format("%.0f", value)
        } else {
            // Use higher precision, then trim trailing zeros and the final decimal point.
            // Using trimEnd('.') is safe because the format specifier will place a '.' if decimals exist.
            String.format("%.10f", value).trimEnd('0').trimEnd(ExpressionParser.DECIMAL_POINT.first())
        }
    }
}
