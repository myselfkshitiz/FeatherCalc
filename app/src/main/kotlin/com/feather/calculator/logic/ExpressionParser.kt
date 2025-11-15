package com.feather.calculator.logic

import kotlin.math.round

/**
 * A basic expression parser that supports standard arithmetic operations and parentheses.
 * Uses a recursive approach to solve nested parentheses before linear evaluation.
 */
class ExpressionParser {

    companion object {
        const val MULTIPLY = "×"
        const val DIVIDE = "÷"
        const val PERCENT = "%"
        const val OPEN_BRACE = "("
        const val CLOSE_BRACE = ")"
        
        // Standard arithmetic operators used for internal calculation
        const val STD_MULTIPLY = "*"
        const val STD_DIVIDE = "/"
        const val STD_MINUS = "-"
        const val STD_PLUS = "+"
        const val DECIMAL_POINT = "." // Added for cleaner access in CalculatorEngine
    }

    /**
     * Evaluates an arithmetic expression string, handling parentheses recursively.
     */
    fun evaluate(expression: String): Double {
        if (expression.isEmpty()) return 0.0

        // 1. Replace display symbols with standard math symbols
        var mathExpression = expression
            .replace(MULTIPLY, STD_MULTIPLY)
            .replace(DIVIDE, STD_DIVIDE)
            .replace("−", STD_MINUS) // Handle any potential non-standard minus
            .replace(STD_PLUS, STD_PLUS) // Redundant but harmless

        // 2. Handle parentheses recursively (solves innermost parentheses first)
        while (mathExpression.contains(OPEN_BRACE)) {
            val start = mathExpression.lastIndexOf(OPEN_BRACE)
            
            // Find the matching closing brace after the starting brace
            var end = -1
            var braceCount = 1
            for (i in start + 1 until mathExpression.length) {
                when (mathExpression[i].toString()) {
                    OPEN_BRACE -> braceCount++
                    CLOSE_BRACE -> braceCount--
                }
                if (braceCount == 0) {
                    end = i
                    break
                }
            }
            
            if (end == -1) throw IllegalArgumentException("Mismatched braces")

            // Extract the sub-expression and recursively evaluate
            val subExpression = mathExpression.substring(start + 1, end)
            val result = evaluate(subExpression)
            
            // Replace the sub-expression and braces with the result
            val prefix = mathExpression.substring(0, start)
            val suffix = mathExpression.substring(end + 1)
            mathExpression = prefix + result.toString() + suffix
        }

        // 3. Final linear evaluation on the simplified, parenthesis-free expression
        val tokens = tokenize(mathExpression)
        return evaluateNoParentheses(tokens)
    }
    
    /**
     * Tokenizes a math expression string into a list of numbers and operators.
     * Correctly handles unary minus.
     */
    private fun tokenize(expression: String): MutableList<String> {
        val tokens = mutableListOf<String>()
        var currentNumber = ""
        var i = 0

        while (i < expression.length) {
            val char = expression[i]

            // Determine if the '-' is a unary minus (part of a number)
            val isUnaryMinus = char == STD_MINUS.first() &&
                (i == 0 || tokens.isEmpty() && currentNumber.isEmpty() || // Start of expression
                tokens.isNotEmpty() && !tokens.last().isNumberToken() && tokens.last() != CLOSE_BRACE && tokens.last() != PERCENT || // After an operator/non-number
                expression[i - 1].toString() == OPEN_BRACE) // After an opening brace

            if (char.isDigit() || char == DECIMAL_POINT.first() || (isUnaryMinus && currentNumber.isEmpty())) {
                // Append digit, decimal point, or the unary minus starting a new number
                currentNumber += char
            } else {
                if (currentNumber.isNotEmpty()) {
                    tokens.add(currentNumber)
                    currentNumber = ""
                }
                if (!char.isWhitespace()) {
                    tokens.add(char.toString())
                }
            }
            i++
        }
        
        if (currentNumber.isNotEmpty()) {
            tokens.add(currentNumber)
        }
        
        return tokens
    }

    /**
     * Helper to check if a token represents a number (including negative numbers).
     */
    private fun String.isNumberToken() = this.toDoubleOrNull() != null
    
    /**
     * Evaluates a list of tokens that contains only operators (+, -, *, /) and numbers.
     * Handles operator precedence: *, / before +, -.
     */
    private fun evaluateNoParentheses(tokens: MutableList<String>): Double {
        
        // 1. Token Cleanup: Convert percentages
        var currentTokens = convertPercentage(tokens)

        if (currentTokens.isEmpty()) return 0.0
        
        // 2. First Pass: Handle Multiplication and Division
        var i = 0
        while (i < currentTokens.size) {
            val token = currentTokens[i]
            if (token == STD_MULTIPLY || token == STD_DIVIDE) {
                // Must have operands
                if (i == 0 || i + 1 >= currentTokens.size) {
                    throw IllegalArgumentException("Invalid expression structure for MD")
                }
                
                val num1 = currentTokens[i - 1].toDouble()
                val num2 = currentTokens[i + 1].toDouble()
                
                val result = when (token) {
                    STD_MULTIPLY -> num1 * num2
                    STD_DIVIDE -> {
                        if (num2 == 0.0) return Double.POSITIVE_INFINITY // Explicit zero check
                        num1 / num2
                    }
                    else -> 0.0
                }
                
                // Replace [num1, op, num2] with [result] and adjust index
                currentTokens.removeAt(i + 1) // remove num2
                currentTokens.removeAt(i)     // remove op
                currentTokens[i - 1] = result.toString() // replace num1 with result
                i -= 1 // Backtrack to check the replaced result and the next token
            }
            i++
        }
        
        // 3. Second Pass: Handle Addition and Subtraction
        
        if (currentTokens.isEmpty()) return 0.0
        
        // Start with the first number. This number may be negative (unary minus).
        var result = currentTokens[0].toDouble() 

        i = 1 // Start from the first operator
        while (i < currentTokens.size - 1) {
            val op = currentTokens[i]
            
            // Safely get the number following the operator
            val number = currentTokens[i + 1].toDouble()
            
            when (op) {
                STD_PLUS -> result += number
                STD_MINUS -> result -= number
                else -> {
                    // This should not happen if M/D pass was successful
                    throw IllegalArgumentException("Unexpected token during AS evaluation: $op")
                }
            }
            i += 2 // Move to the next operator
        }
        
        return result
    }

    /**
     * Converts percentage tokens (e.g., "50%", "25%+10%") into their evaluated numeric values.
     * The logic for "base-percentage" (e.g., 100+10%) is implemented here.
     */
    private fun convertPercentage(tokens: MutableList<String>): MutableList<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (token == PERCENT) {
                // Must be preceded by a number
                if (result.isEmpty() || !result.last().isNumberToken()) {
                    throw IllegalArgumentException("Percentage must follow a number")
                }
                
                val percentValue = result.removeLast().toDouble()
                
                // Check for base operation (e.g., 100 + 10%)
                if (result.isNotEmpty() && (result.last() == STD_PLUS || result.last() == STD_MINUS)) {
                    val operator = result.removeLast()
                    
                    // Must be preceded by a base number
                    if (result.isEmpty() || !result.last().isNumberToken()) {
                        // Expression was "+50%" or "-50%". Treat as a standard percentage (0.5 or -0.5).
                        result.add(operator) // Restore operator
                        result.add((percentValue / 100.0).toString())
                    } else {
                        val base = result.removeLast().toDouble()
                        
                        // Calculate percentage of the base: base * (percentValue / 100)
                        val calculatedPercentAmount = base * (percentValue / 100.0)
                        
                        // Restore: base, operator, and the calculated amount
                        result.add(base.toString())
                        result.add(operator)
                        result.add(calculatedPercentAmount.toString())
                    }
                } else {
                    // Standard percentage: 50% becomes 0.5
                    result.add((percentValue / 100.0).toString())
                }
            } else {
                result.add(token)
            }
            i++
        }
        return result
    }
}