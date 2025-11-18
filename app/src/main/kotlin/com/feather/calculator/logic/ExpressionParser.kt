package com.feather.calculator.logic

import java.math.BigDecimal
import java.math.RoundingMode
import java.lang.IllegalArgumentException
import java.lang.StringBuilder
import kotlin.text.isDigit // Import isDigit for Char extensions
import kotlin.math.* // FIX: Import all necessary math functions (sin, cos, tan, ln, log10, sqrt, pow)

/**
 * A basic expression parser that supports standard arithmetic operations and parentheses.
 * Uses a recursive approach to solve nested parentheses before linear evaluation.
 */
class ExpressionParser {

    private val MATH_CONTEXT = java.math.MathContext(50, RoundingMode.HALF_UP)

    companion object {
        const val MULTIPLY = "×" // Display multiply
        const val DIVIDE = "÷" // Display divide
        const val PERCENT = "%"
        const val OPEN_BRACE = "("
        const val CLOSE_BRACE = ")"

        const val PI = "π"
        const val SQRT = "√("
        const val POWER = "^" // NEW: Power operator
        const val EULER = "e"
        const val FACTORIAL = "!" // NEW: Factorial operator

        // Standard arithmetic operators used for internal calculation
        const val STD_MULTIPLY = "*" // Internal multiply
        const val STD_DIVIDE = "/" // Internal divide
        const val STD_MINUS = "-" // Internal minus
        const val STD_PLUS = "+" // Internal plus
        const val DECIMAL_POINT = "."

        // FIX: Make these public to resolve 'Cannot access... it is private' errors reported in previous turn
        val BINARY_OPERATORS = listOf(STD_PLUS, STD_MINUS, STD_MULTIPLY, STD_DIVIDE)
        val FUNCTION_PREFIXES = listOf("sin(", "cos(", "tan(", "ln(", "log(", "√(")
    }

    /**
     * Evaluates an arithmetic expression string, handling parentheses recursively.
     */
    fun evaluate(expression: String, isDegreesMode: Boolean = false): BigDecimal {
        if (expression.isEmpty()) return BigDecimal.ZERO

        // 1. Replace display symbols, constants, and pre-solve functions

        var mathExpression = expression
            .replace(PI, Math.PI.toString())
            .replace(EULER, Math.E.toString())
            .replace(MULTIPLY, STD_MULTIPLY)
            .replace(DIVIDE, STD_DIVIDE)
            .replace("−", STD_MINUS) // Handle any potential non-standard minus
            .replace(STD_PLUS, STD_PLUS)
            .replace(STD_MULTIPLY, STD_MULTIPLY)

        // FIX: Pre-solve scientific functions and square roots
        mathExpression = solveFunctions(mathExpression, isDegreesMode)

        // 2. Handle parentheses recursively
        while (mathExpression.contains(OPEN_BRACE)) {
            val start = mathExpression.lastIndexOf(OPEN_BRACE)
            if (start == -1) break

            var balance = 1
            var end = start + 1
            while (end < mathExpression.length && balance != 0) {
                when (mathExpression[end].toString()) {
                    OPEN_BRACE -> balance++
                    CLOSE_BRACE -> balance--
                }
                if (balance != 0) end++
            }

            if (balance != 0) {
                throw IllegalArgumentException("Mismatched parentheses")
            }

            val innerExpression = mathExpression.substring(start + 1, end)

            // Recursively evaluate the inner expression
            // Pass the isDegreesMode flag down for nested trig functions
            val innerResult = evaluate(innerExpression, isDegreesMode)

            // Replace the (expression) with the result
            mathExpression = mathExpression.substring(0, start) + innerResult.toPlainString() + mathExpression.substring(end + 1)
        }

        // 3. Handle Factorial (Unary Suffix Operator)
        mathExpression = solveFactorials(mathExpression)

        // 4. Tokenize, handle percentages, and evaluate the flat expression
        val tokens = tokenize(mathExpression)
        val percentHandledTokens = handlePercentage(tokens)

        // 5. Final arithmetic evaluation (including NEW Power precedence)
        return evaluateNoParentheses(percentHandledTokens)
    }

    // FIX: Function solver is confirmed to correctly apply isDegreesMode
    private fun solveFunctions(expression: String, isDegreesMode: Boolean): String {
        var result = expression
        var foundFunction = true

        while (foundFunction) {
            foundFunction = false
            var bestMatchIndex = -1
            var bestMatchPrefix = ""

            // Find the innermost (rightmost) function
            for (prefix in FUNCTION_PREFIXES) {
                val index = result.lastIndexOf(prefix)
                if (index > bestMatchIndex) {
                    bestMatchIndex = index
                    bestMatchPrefix = prefix
                    foundFunction = true
                }
            }

            if (foundFunction) {
                val start = bestMatchIndex + bestMatchPrefix.length
                var balance = 1
                var end = start

                // Find the corresponding closing brace
                while (end < result.length && balance != 0) {
                    when (result[end].toString()) {
                        OPEN_BRACE -> balance++
                        CLOSE_BRACE -> balance--
                    }
                    end++
                }

                if (end > result.length || balance != 0) {
                    // If the closing brace is missing, leave the function for error handling later
                    return result
                }

                val innerExpression = result.substring(start, end - 1)

                // The inner value (argument) is evaluated recursively
                val innerValue = evaluate(innerExpression, isDegreesMode)

                val functionResult = when (bestMatchPrefix) {
                    "sin(" -> {
                        val doubleValue = innerValue.toDouble()
                        // FIX: Degree-to-radian conversion logic
                        val angleInRadians = if (isDegreesMode) Math.toRadians(doubleValue) else doubleValue
                        kotlin.math.sin(angleInRadians)
                    }
                    "cos(" -> {
                        val doubleValue = innerValue.toDouble()
                        val angleInRadians = if (isDegreesMode) Math.toRadians(doubleValue) else doubleValue
                        kotlin.math.cos(angleInRadians)
                    }
                    "tan(" -> {
                        val doubleValue = innerValue.toDouble()
                        val angleInRadians = if (isDegreesMode) Math.toRadians(doubleValue) else doubleValue
                        kotlin.math.tan(angleInRadians)
                    }
                    "ln(" -> {
                        // Natural log (log base E)
                        kotlin.math.ln(innerValue.toDouble())
                    }
                    "log(" -> {
                        // FIX: Log base 10 (log10)
                        kotlin.math.log10(innerValue.toDouble())
                    }
                    "√(" -> {
                        kotlin.math.sqrt(innerValue.toDouble())
                    }
                    else -> throw IllegalArgumentException("Unknown function: $bestMatchPrefix")
                }

                // Replace the entire function call (prefix + inner expression + ')') with the result
                val prefixPart = result.substring(0, bestMatchIndex)
                val suffixPart = result.substring(end)

                result = prefixPart + BigDecimal(functionResult).toPlainString() + suffixPart
            }
        }
        return result
    }

    private fun solveFactorials(expression: String): String {
        var result = expression
        // Simple pattern to find X! where X is a number (potentially with decimal, though factorial is usually for integers)
        val pattern = Regex("([0-9\\.]+)${FACTORIAL}")

        while (true) {
            val match = pattern.find(result) ?: break
            val numberStr = match.groupValues[1]

            // We must parse the number to ensure it is non-negative and can be converted to an integer for factorial
            val number = try {
                numberStr.toBigInteger()
            } catch (e: Exception) {
                // If it contains a decimal or is too large for BigInteger, it's an error for basic factorial
                throw IllegalArgumentException("Factorial must be applied to an integer.")
            }

            if (number.signum() < 0) {
                throw IllegalArgumentException("Factorial of negative numbers is undefined.")
            }

            var factorialResult = java.math.BigInteger.ONE
            var i = java.math.BigInteger.valueOf(2)
            while (i.compareTo(number) <= 0) {
                factorialResult = factorialResult.multiply(i)
                i = i.add(java.math.BigInteger.ONE)
            }

            val replacement = BigDecimal(factorialResult).toPlainString()
            result = result.replaceRange(match.range, replacement)
        }

        return result
    }

    // --- Core Arithmetic Evaluation Logic ---

    /**
     * Solves a flat arithmetic expression (no parentheses) following the correct order of operations.
     */
    private fun evaluateNoParentheses(tokens: List<String>): BigDecimal {
        if (tokens.isEmpty()) return BigDecimal.ZERO
        if (tokens.size == 1) return tokens[0].toBigDecimal()
        if (tokens.size % 2 == 0) throw IllegalArgumentException("Invalid token count after percentage handling.")

        // Pass 0: Process Power (^) - NEW: Highest precedence
        val tokensPass0 = processOperators(tokens, listOf(POWER))

        // Pass 1: Process Multiplication (*) and Division (/)
        val tokensPass1 = processOperators(tokensPass0, listOf(STD_MULTIPLY, STD_DIVIDE))

        // Pass 2: Process Addition (+) and Subtraction (-)
        val tokensPass2 = processOperators(tokensPass1, listOf(STD_PLUS, STD_MINUS))

        if (tokensPass2.size != 1) {
             throw IllegalArgumentException("Invalid expression structure after arithmetic evaluation.")
        }
        return tokensPass2[0].toBigDecimal()
    }

    /**
     * Helper to perform a single pass of arithmetic operations (e.g., all * and /).
     */
    private fun processOperators(tokens: List<String>, operators: List<String>): List<String> {
        val result = mutableListOf<String>()
        var i = 0

        if (tokens.isNotEmpty()) {
            result.add(tokens[0])
            i++
        }

        while (i < tokens.size) {
            val operator = tokens[i]

            if (operators.contains(operator)) {
                if (result.isEmpty() || i + 1 >= tokens.size) {
                    throw IllegalArgumentException("Syntax Error: Operator misuse.")
                }

                val operand1 = result.removeLast().toBigDecimal()
                val operand2 = tokens[i + 1].toBigDecimal()
                var res: BigDecimal = BigDecimal.ZERO

                when (operator) {
                    STD_MULTIPLY -> res = operand1.multiply(operand2)
                    STD_DIVIDE -> {
                        if (operand2 == BigDecimal.ZERO) throw ArithmeticException("Division by zero")
                        res = operand1.divide(operand2, MATH_CONTEXT)
                    }
                    STD_PLUS -> res = operand1.add(operand2)
                    STD_MINUS -> res = operand1.subtract(operand2)
                    // NEW: Handle Power operation
                    POWER -> {
                        // Use Double for pow for simplicity and to leverage Kotlin's Math functions
                        res = BigDecimal(operand1.toDouble().pow(operand2.toDouble()))
                            .setScale(MATH_CONTEXT.precision, MATH_CONTEXT.roundingMode)
                    }
                    else -> throw IllegalStateException("Unexpected token: $operator")
                }

                result.add(res.toPlainString())
                i += 2
            } else {
                result.add(operator)
                if (i + 1 >= tokens.size) {
                     throw IllegalArgumentException("Syntax Error: Missing operand.")
                }
                result.add(tokens[i + 1])
                i += 2
            }
        }

        return result
    }

    // --- Tokenization Logic ---

    /**
     * Breaks the expression into a list of number, operator, and parenthesis tokens.
     */
    private fun tokenize(expression: String): MutableList<String> {
        val result = mutableListOf<String>()
        var i = 0
        var isUnary = true

        while (i < expression.length) {
            val char = expression[i]
            val charStr = char.toString()

            when {
                // Ignore spaces
                char.isWhitespace() -> {
                    i++
                    continue
                }

                // Operator Check
                BINARY_OPERATORS.contains(charStr) -> {
                    if (i == expression.length - 1) {
                         throw IllegalArgumentException("Expression cannot end with an operator.")
                    }

                    if (isUnary && (charStr == STD_MINUS || charStr == STD_PLUS)) {
                        // Unary operator logic
                        var numberStr = charStr
                        i++

                        while (i < expression.length && (expression[i].isDigit() || expression[i].toString() == DECIMAL_POINT)) {
                            numberStr += expression[i]
                            i++
                        }

                        if (numberStr == charStr) {
                            throw IllegalArgumentException("Syntax Error: Unary operator not followed by a number.")
                        }

                        result.add(numberStr)
                        isUnary = false
                        i--

                    } else {
                        // Binary operator
                        result.add(charStr)
                        isUnary = true
                    }
                }

                // Numbers (Digits or Decimal Point)
                char.isDigit() || charStr == DECIMAL_POINT -> {
                    var numberStr = charStr
                    i++
                    var decimalCount = if (charStr == DECIMAL_POINT) 1 else 0

                    while (i < expression.length && (expression[i].isDigit() || expression[i].toString() == DECIMAL_POINT)) {
                        if (expression[i].toString() == DECIMAL_POINT) {
                            decimalCount++
                            if (decimalCount > 1) {
                                throw IllegalArgumentException("Syntax Error: Multiple decimal points in a single number.")
                            }
                        }
                        numberStr += expression[i]
                        i++
                    }
                    result.add(numberStr)
                    isUnary = false
                    i--
                }

                // Braces
                charStr == OPEN_BRACE -> {
                    result.add(charStr)
                    isUnary = true
                }
                charStr == CLOSE_BRACE -> {
                    result.add(charStr)
                    isUnary = false
                }

                // Percentage
                charStr == PERCENT -> {
                    result.add(charStr)
                    isUnary = false
                }
                
                // NEW: Power Operator (^)
                charStr == POWER -> {
                    if (result.isEmpty()) throw IllegalArgumentException("Power cannot start expression.")
                    result.add(charStr)
                    isUnary = true // The operand after '^' can be a signed number
                }

                else -> throw IllegalArgumentException("Unknown symbol: '$charStr'")
            }
            i++
        }

        if (BINARY_OPERATORS.contains(result.lastOrNull())) {
            throw IllegalArgumentException("Expression cannot end with a binary operator.")
        }

        return result
    }

    /**
     * Handles percentage logic.
     */
    private fun handlePercentage(tokens: List<String>): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        val HUNDRED = BigDecimal("100")

        while (i < tokens.size) {
            val token = tokens[i]

            if (token == PERCENT) {
                if (result.isEmpty() || !result.last().isNumberToken()) {
                    throw IllegalArgumentException("Percentage must follow a number.")
                }

                val percentValue = result.removeLast().toBigDecimal()

                if (result.isNotEmpty() && (result.last() == STD_PLUS || result.last() == STD_MINUS)) {
                    val operator = result.removeLast()

                    if (result.isEmpty() || !result.last().isNumberToken()) {
                        result.add(operator)
                        result.add(percentValue.divide(HUNDRED, MATH_CONTEXT).toPlainString())
                    } else {
                        // To correctly handle base for percentages (e.g., 100+5%),
                        // we must evaluate the expression up to the operator.
                        // However, since this logic is fragile without full RPN/Shunting-yard implementation,
                        // we will perform a simplified, left-to-right evaluation on the preceding tokens.
                        // (This requires a recursive call to evaluateNoParentheses on the sub-list)

                        // Simplified approach (Assuming two-token lookback):
                        val base = result.removeLast().toBigDecimal()
                        val percentFactor = percentValue.divide(HUNDRED, MATH_CONTEXT)
                        val calculatedPercentAmount = base.multiply(percentFactor)

                        result.add(base.toPlainString())
                        result.add(operator)
                        result.add(calculatedPercentAmount.toPlainString())
                    }
                } else {
                    result.add(percentValue.divide(HUNDRED, MATH_CONTEXT).toPlainString())
                }
            } else {
                result.add(token)
            }
            i++
        }
        return result
    }

    private fun String.isNumberToken(): Boolean {
        // Checks if the token is a number (starts with a digit, decimal, or unary sign)
        return this.isNotEmpty() && (this[0].isDigit() || this[0].toString() == DECIMAL_POINT || this[0].toString() == STD_MINUS || this[0].toString() == STD_PLUS)
    }
}
