package com.feather.calculator.logic

import java.math.BigDecimal
import java.math.RoundingMode
import java.lang.StringBuilder
import java.lang.IllegalArgumentException
import kotlin.text.isDigit

class CalculatorEngine {

    companion object {
        const val ERROR_TAG = "ERROR:"
        private const val DIVIDE_BY_ZERO_ERROR_MSG = "can't divide by zero"
        private const val SYNTAX_ERROR_MSG = "Syntax Error"
        private const val MATH_DOMAIN_ERROR_MSG = "Math Error"
        private const val OVERFLOW_ERROR_MSG = "Overflow"

        const val DIVIDE_BY_ZERO_ERROR = ERROR_TAG + DIVIDE_BY_ZERO_ERROR_MSG
        const val SYNTAX_ERROR = ERROR_TAG + SYNTAX_ERROR_MSG
        const val MATH_DOMAIN_ERROR = ERROR_TAG + MATH_DOMAIN_ERROR_MSG
        const val OVERFLOW_ERROR = ERROR_TAG + OVERFLOW_ERROR_MSG
    }

    private var currentExpression: String = ""
    private var cursorPosition: Int = 0
    private val parser = ExpressionParser()

    val ALL_OPERATORS = listOf(ExpressionParser.STD_PLUS, ExpressionParser.STD_MINUS, ExpressionParser.MULTIPLY, ExpressionParser.DIVIDE)
    private val NON_UNARY_START_OPERATORS = listOf(ExpressionParser.STD_PLUS, ExpressionParser.MULTIPLY, ExpressionParser.DIVIDE, ExpressionParser.CLOSE_BRACE)
    private val DIGITS_AND_PERCENT = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", ExpressionParser.PERCENT)
    private val BRACES = listOf(ExpressionParser.OPEN_BRACE, ExpressionParser.CLOSE_BRACE)
    private val IMPLICIT_MULTIPLY_CHARS = DIGITS_AND_PERCENT + listOf(ExpressionParser.CLOSE_BRACE)

    private val LIVE_TRIMMABLE_SYMBOLS: List<String> = ALL_OPERATORS +
                                                   listOf(
                                                       ExpressionParser.STD_MULTIPLY,
                                                       ExpressionParser.DECIMAL_POINT,
                                                       ExpressionParser.PERCENT
                                                   )


    fun loadState(expression: String, cursor: Int) {
        currentExpression = expression
        cursorPosition = cursor.coerceIn(0, currentExpression.length)
    }

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

    private fun applyImplicitMultiplication(expression: String): String {
        val result = StringBuilder()

        val digitsAndPercentChars = DIGITS_AND_PERCENT.map { it.first() }
        val closeBraceChar = ExpressionParser.CLOSE_BRACE.first()
        val openBraceChar = ExpressionParser.OPEN_BRACE.first()
        
        val functionPrefixes = ExpressionParser.FUNCTION_PREFIXES

        for (i in expression.indices) {
            val current = expression[i]

            if (i > 0) {
                val previous = expression[i - 1]

                val implicitBeforeOpenBrace = (digitsAndPercentChars.contains(previous) || previous == closeBraceChar) &&
                                              current == openBraceChar

                val implicitAfterCloseBrace = previous == closeBraceChar &&
                                              (digitsAndPercentChars.contains(current) || current == openBraceChar)
                
                val isPrecededByImpliedMultChar = digitsAndPercentChars.contains(previous) || previous == closeBraceChar
                
                val isFollowedByFunction = functionPrefixes.any { prefix ->
                    expression.startsWith(prefix, i)
                }
                
                val implicitBeforeFunction = isPrecededByImpliedMultChar && isFollowedByFunction


                if (implicitBeforeOpenBrace || implicitAfterCloseBrace || implicitBeforeFunction) {
                    result.append(ExpressionParser.STD_MULTIPLY)
                }
            }

            result.append(current)
        }
        return result.toString()
    }

    private fun insert(input: String, selectionStart: Int, selectionEnd: Int) {
        val length = currentExpression.length
        val clampedStart = selectionStart.coerceIn(0, length)
        val clampedEnd = selectionEnd.coerceIn(0, length)

        val prefix = currentExpression.substring(0, clampedStart)
        val suffix = currentExpression.substring(clampedEnd)

        currentExpression = prefix + input + suffix
        cursorPosition = clampedStart + input.length
    }


    private fun containsDecimalPoint(cursorPos: Int): Boolean {
        if (currentExpression.isEmpty()) return false

        var i = cursorPos - 1
        while (i >= 0) {
            val char = currentExpression[i].toString()
            if (char == ExpressionParser.DECIMAL_POINT) return true
            if (ALL_OPERATORS.contains(char) || BRACES.contains(char) || char == ExpressionParser.PERCENT) break
            i--
        }

        var i2 = cursorPos
        while (i2 < currentExpression.length) {
            val char = currentExpression[i2].toString()
            if (char == ExpressionParser.DECIMAL_POINT) return true
            if (ALL_OPERATORS.contains(char) || BRACES.contains(char) || char == ExpressionParser.PERCENT) break
            i2++
        }

        return false
    }

    fun toggleSign(selectionStart: Int, selectionEnd: Int) {
        if (currentExpression.isEmpty() || selectionStart != selectionEnd) return

        var start = selectionStart

        while (start > 0 && (currentExpression[start - 1].isDigit() || currentExpression[start - 1] == ExpressionParser.DECIMAL_POINT.first() || currentExpression[start - 1] == ExpressionParser.PERCENT.first())) {
            start--
        }

        val hasUnaryMinus = start > 0 && currentExpression[start - 1] == ExpressionParser.STD_MINUS.first() &&
                            (start == 1 || ALL_OPERATORS.contains(currentExpression.getOrNull(start - 2)?.toString()) || currentExpression[start - 2] == ExpressionParser.OPEN_BRACE.first())

        when {
            hasUnaryMinus -> {
                currentExpression = currentExpression.substring(0, start - 1) + currentExpression.substring(start)
                cursorPosition = selectionStart - 1
            }
            else -> {
                val charBefore = currentExpression.getOrNull(start - 1)?.toString()
                val isSafeInsertion = charBefore == null || ALL_OPERATORS.contains(charBefore) || charBefore == ExpressionParser.OPEN_BRACE

                if (isSafeInsertion) {
                    currentExpression = currentExpression.substring(0, start) + ExpressionParser.STD_MINUS + currentExpression.substring(start)
                    cursorPosition = selectionStart + 1
                }
            }
        }
    }

    fun appendInput(input: String, selectionStart: Int, selectionEnd: Int) {

        val charBefore = currentExpression.getOrNull(selectionStart - 1)?.toString()
        val charAfter = currentExpression.getOrNull(selectionEnd)?.toString()
        val isAtEnd = selectionStart == currentExpression.length

        if (currentExpression.isEmpty() && selectionStart == 0 && NON_UNARY_START_OPERATORS.contains(input)) {
            return
        }

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

        if (ALL_OPERATORS.contains(input)) {
            val lastChar = currentExpression.getOrNull(selectionStart - 1)?.toString()

            if (lastChar != null && ALL_OPERATORS.contains(lastChar) && input != ExpressionParser.STD_MINUS) {
                val prefix = currentExpression.substring(0, selectionStart - 1)
                val suffix = currentExpression.substring(selectionEnd)
                currentExpression = prefix + input + suffix
                cursorPosition = selectionStart
                return
            }
        }

        if (input == ExpressionParser.PERCENT && currentExpression.endsWith(ExpressionParser.PERCENT) && isAtEnd) {
            return
        }

        if (input == ExpressionParser.DECIMAL_POINT) {
            val checkPos = if (selectionStart != selectionEnd) selectionStart else selectionStart
            if (containsDecimalPoint(checkPos)) {
                return
            }
        }

        insert(input, selectionStart, selectionEnd)
    }

    fun clear() {
        currentExpression = ""
        cursorPosition = 0
    }

    fun backspace(selectionStart: Int, selectionEnd: Int) {
        if (currentExpression.isEmpty()) {
            return
        }

        if (selectionStart != selectionEnd) {
            val prefix = currentExpression.substring(0, selectionStart)
            val suffix = currentExpression.substring(selectionEnd)
            currentExpression = prefix + suffix
            cursorPosition = selectionStart

        } else if (selectionStart > 0) {
            val prefix = currentExpression.substring(0, selectionStart - 1)
            val suffix = currentExpression.substring(selectionStart)
            currentExpression = prefix + suffix
            cursorPosition = selectionStart - 1
        }
    }

    fun appendParentheses(selectionStart: Int, selectionEnd: Int) {
        if (selectionStart != selectionEnd) {
            val prefix = currentExpression.substring(0, selectionStart)
            val selected = currentExpression.substring(selectionStart, selectionEnd)
            val suffix = currentExpression.substring(selectionEnd)

            currentExpression = "$prefix($selected)$suffix"
            cursorPosition = selectionEnd + 2
        } else {
            val prefix = currentExpression.substring(0, cursorPosition)
            val suffix = currentExpression.substring(cursorPosition)

            currentExpression = "$prefix()$suffix"
            cursorPosition += 1
        }
    }

    fun setCursorPosition(newCursorPosition: Int) {
        cursorPosition = newCursorPosition.coerceIn(0, currentExpression.length)
    }

    fun getExpression(): String = currentExpression

    fun getCursorPosition(): Int = cursorPosition
    
    fun calculateLiveResult(isDegreesMode: Boolean): String {
        var expressionToEvaluate = currentExpression

        var balance = getBraceBalance()
        while (balance > 0) {
            expressionToEvaluate += ExpressionParser.CLOSE_BRACE
            balance--
        }
        expressionToEvaluate = applyImplicitMultiplication(expressionToEvaluate)

        var tempExpression = expressionToEvaluate

        while (tempExpression.isNotEmpty()) {
            try {
                val result = parser.evaluate(tempExpression, isDegreesMode)
                return formatResult(result)
            } catch (e: ArithmeticException) {
                if (tempExpression.endsWith(ExpressionParser.STD_DIVIDE)) {
                    tempExpression = tempExpression.substring(0, tempExpression.length - 1)
                    continue
                }
                 return DIVIDE_BY_ZERO_ERROR
            } catch (e: IllegalArgumentException) {
                if (e.message?.contains("Math Domain Error") == true || e.message?.contains("Factorial") == true) {
                    return MATH_DOMAIN_ERROR
                }
            } catch (e: Exception) {
                if (e.message?.contains(OVERFLOW_ERROR_MSG) == true) {
                    return OVERFLOW_ERROR
                }
            }

            if (tempExpression.isEmpty()) return ""
            
            val lastChar = tempExpression.last().toString()
            val isSafeToTrim = LIVE_TRIMMABLE_SYMBOLS.contains(lastChar)

            if (!isSafeToTrim && tempExpression.length == expressionToEvaluate.length) {
                return SYNTAX_ERROR
            }

            tempExpression = tempExpression.substring(0, tempExpression.length - 1)
        }

        return ""
    }

    fun finalizeCalculation(isDegreesMode: Boolean): String {

        var finalExpression = currentExpression
        var balance = getBraceBalance()
        while (balance > 0) {
            finalExpression += ExpressionParser.CLOSE_BRACE
            balance--
        }

        finalExpression = applyImplicitMultiplication(finalExpression)

        return try {
            val result = parser.evaluate(finalExpression, isDegreesMode)

            val formattedResult = formatResult(result)
            currentExpression = formattedResult
            cursorPosition = formattedResult.length
            return formattedResult
        } catch (e: ArithmeticException) {
            cursorPosition = finalExpression.length
            return DIVIDE_BY_ZERO_ERROR
        } catch (e: IllegalArgumentException) {
            currentExpression = ""
            cursorPosition = 0
            return MATH_DOMAIN_ERROR
        } catch (e: Exception) {
            if (e.message?.contains(OVERFLOW_ERROR_MSG) == true) {
                currentExpression = ""
                cursorPosition = 0
                return OVERFLOW_ERROR
            }
            
            currentExpression = ""
            cursorPosition = 0
            return SYNTAX_ERROR
        }
    }

    private fun formatResult(value: BigDecimal): String {
        val cleanedValue = value.stripTrailingZeros()
        
        val ABS_VALUE_FOR_SCIENTIFIC_UNDERFLOW_BOUNDARY = BigDecimal("1E-16")

        if (cleanedValue.compareTo(BigDecimal.ZERO) == 0) {
            return "0"
        }

        val absValue = cleanedValue.abs()
        
        val plainStringForCheck = absValue.toPlainString()
        val integerPart = plainStringForCheck.substringBefore(ExpressionParser.DECIMAL_POINT)

        val isOverflow = integerPart.length > 16 
        
        val isUnderflow = absValue.compareTo(ABS_VALUE_FOR_SCIENTIFIC_UNDERFLOW_BOUNDARY) < 0

        val shouldUseScientificNotation = isOverflow || isUnderflow

        if (shouldUseScientificNotation) {
            return cleanedValue.toString().replace('E', 'e')
        }

        val MAX_DECIMAL_PLACES = 15
        val roundedValue = cleanedValue.setScale(MAX_DECIMAL_PLACES, RoundingMode.HALF_UP)
        
        var formatted = roundedValue.toPlainString()

        if (formatted.contains(ExpressionParser.DECIMAL_POINT)) {
            formatted = formatted.trimEnd('0')
            if (formatted.endsWith(ExpressionParser.DECIMAL_POINT.first())) {
                formatted = formatted.trimEnd(ExpressionParser.DECIMAL_POINT.first())
            }
        }

        return formatted
    }
}
