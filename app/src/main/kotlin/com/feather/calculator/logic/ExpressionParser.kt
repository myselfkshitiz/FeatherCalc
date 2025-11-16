package com.feather.calculator.logic

import kotlin.math.round
import java.lang.IllegalArgumentException 
import java.lang.StringBuilder 
import kotlin.text.isDigit 

/**
 * A basic expression parser that supports standard arithmetic operations and parentheses.
 * Uses a recursive approach to solve nested parentheses before linear evaluation.
 */
class ExpressionParser {

    companion object {
        const val MULTIPLY = "×" // Display multiply
        const val DIVIDE = "÷" // Display divide
        const val PERCENT = "%"
        const val OPEN_BRACE = "("
        const val CLOSE_BRACE = ")"
        
        // Standard arithmetic operators used for internal calculation
        const val STD_MULTIPLY = "*" // Internal multiply
        const val STD_DIVIDE = "/" // Internal divide
        const val STD_MINUS = "-" // Internal minus
        const val STD_PLUS = "+" // Internal plus
        const val DECIMAL_POINT = "." 

        private val BINARY_OPERATORS = listOf(STD_PLUS, STD_MINUS, STD_MULTIPLY, STD_DIVIDE)
        private val UNARY_FOLLOWERS = listOf(OPEN_BRACE, STD_MULTIPLY, STD_DIVIDE, STD_PLUS, STD_MINUS)
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
            .replace(STD_PLUS, STD_PLUS) 

        // 2. Handle parentheses recursively (solves innermost parentheses first)
        while (mathExpression.contains(OPEN_BRACE)) {
            val start = mathExpression.lastIndexOf(OPEN_BRACE)
            if (start == -1) break 

            // Find the corresponding closing brace
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
                // This exception should not be reached if CalculatorEngine handles balance check
                throw IllegalArgumentException("Mismatched parentheses") 
            }

            val innerExpression = mathExpression.substring(start + 1, end)
            
            // Recursively evaluate the inner expression
            val innerResult = evaluate(innerExpression)

            // Replace the (expression) with the result
            mathExpression = mathExpression.substring(0, start) + innerResult.toString() + mathExpression.substring(end + 1)
        }

        // 3. Tokenize, handle percentages, and evaluate the flat expression
        val tokens = tokenize(mathExpression)
        val percentHandledTokens = handlePercentage(tokens)

        // 4. Final arithmetic evaluation
        return evaluateNoParentheses(percentHandledTokens)
    }
    
    // --- Core Arithmetic Evaluation Logic ---

    /**
     * Solves a flat arithmetic expression (no parentheses) following the correct order of operations.
     */
    private fun evaluateNoParentheses(tokens: List<String>): Double {
        if (tokens.isEmpty()) return 0.0
        if (tokens.size == 1) return tokens[0].toDouble()
        if (tokens.size % 2 == 0) throw IllegalArgumentException("Invalid token count after percentage handling.")
        
        // Pass 1: Process Multiplication (*) and Division (/)
        val tokensPass1 = processOperators(tokens, listOf(STD_MULTIPLY, STD_DIVIDE))
        
        // Pass 2: Process Addition (+) and Subtraction (-)
        val tokensPass2 = processOperators(tokensPass1, listOf(STD_PLUS, STD_MINUS))
        
        // The result must be a single number
        if (tokensPass2.size != 1) {
             // This indicates an underlying syntax error that wasn't caught by the tokenizer (e.g., 5- or 5 5)
             throw IllegalArgumentException("Invalid expression structure after arithmetic evaluation.")
        }
        return tokensPass2[0].toDouble()
    }

    /**
     * Helper to perform a single pass of arithmetic operations (e.g., all * and /).
     */
    private fun processOperators(tokens: List<String>, operators: List<String>): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        
        // Initialize the first result token if the list isn't empty
        if (tokens.isNotEmpty()) {
            result.add(tokens[0])
            i++
        }
        
        while (i < tokens.size) {
            val operator = tokens[i]
            
            if (operators.contains(operator)) {
                // Operator is in the current pass, perform calculation
                if (result.isEmpty() || i + 1 >= tokens.size) {
                    throw IllegalArgumentException("Syntax Error: Operator misuse.")
                }

                val operand1 = result.removeLast().toDouble()
                val operand2 = tokens[i + 1].toDouble()
                var res = 0.0

                when (operator) {
                    STD_MULTIPLY -> res = operand1 * operand2
                    STD_DIVIDE -> res = operand1 / operand2
                    STD_PLUS -> res = operand1 + operand2
                    STD_MINUS -> res = operand1 - operand2
                }
                
                result.add(res.toString())
                i += 2 // Skip the operator and the operand
            } else {
                // Operator is not in the current pass (e.g., saw a '+' in the * / pass)
                // Add the current token (which is an operator from the *next* pass)
                result.add(operator)
                
                // Add the subsequent number token
                if (i + 1 >= tokens.size) {
                     // Should be impossible due to prior checks, but safe
                     throw IllegalArgumentException("Syntax Error: Missing operand.")
                }
                result.add(tokens[i + 1])
                i += 2 // Skip the operator and the operand
            }
        }
        
        return result
    }

    // --- Tokenization Logic (Corrected for Char vs. String issue) ---

    /**
     * Breaks the expression into a list of number, operator, and parenthesis tokens.
     */
    private fun tokenize(expression: String): MutableList<String> {
        val result = mutableListOf<String>()
        var i = 0
        var isUnary = true 

        while (i < expression.length) {
            val char = expression[i] // Read as Char
            val charStr = char.toString()

            when {
                // Ignore spaces
                char.isWhitespace() -> { // Check Char for whitespace
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

                        // Consume the entire number part
                        while (i < expression.length && (expression[i].isDigit() || expression[i].toString() == DECIMAL_POINT)) {
                            numberStr += expression[i]
                            i++
                        }
                        
                        // Unary operator must be followed by a number
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

                // Braces
                charStr == OPEN_BRACE -> {
                    result.add(charStr)
                    isUnary = true
                }
                charStr == CLOSE_BRACE -> {
                    result.add(charStr)
                    isUnary = false
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

                // Percentage
                charStr == PERCENT -> {
                    result.add(charStr)
                    isUnary = false 
                }

                else -> throw IllegalArgumentException("Unknown symbol: '$charStr'")
            }
            i++
        }
        
        // Final sanity check 
        if (BINARY_OPERATORS.contains(result.lastOrNull())) {
            throw IllegalArgumentException("Expression cannot end with a binary operator.")
        }
        
        return result
    }

    /**
     * Handles percentage logic:
     * - 50% -> 0.5
     * - 100 + 50% -> 100 + (100 * 0.5)
     */
    private fun handlePercentage(tokens: List<String>): List<String> {
        val result = mutableListOf<String>()
        var i = 0

        while (i < tokens.size) {
            val token = tokens[i]

            if (token == PERCENT) {
                if (result.isEmpty() || !result.last().isNumberToken()) {
                    throw IllegalArgumentException("Percentage must follow a number.")
                }
                
                // Get the number preceding the '%'
                val percentValue = result.removeLast().toDouble()

                // Check for base operation (e.g., 100 + 10%)
                if (result.isNotEmpty() && (result.last() == STD_PLUS || result.last() == STD_MINUS)) {
                    val operator = result.removeLast()
                    
                    // Must be preceded by a base number (Handle cases like "+50%")
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
    
    // Extension function for cleaner number check
    private fun String.isNumberToken(): Boolean {
        // Checks if the token is a number (starts with a digit, decimal, or unary sign)
        return this.isNotEmpty() && (this[0].isDigit() || this[0].toString() == DECIMAL_POINT || this[0].toString() == STD_MINUS || this[0].toString() == STD_PLUS)
    }
}
