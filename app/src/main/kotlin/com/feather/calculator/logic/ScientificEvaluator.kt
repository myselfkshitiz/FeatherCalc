package com.feather.calculator.logic

// ScientificEvaluator.kt
import java.math.BigDecimal
import java.math.RoundingMode
import java.lang.IllegalArgumentException
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan // ADDED
import kotlin.math.ln // ADDED (safer alternative for natural log)

/**
 * Handles evaluation of scientific constants, powers, roots, and trigonometric/logarithmic
 * functions within a given expression string.
 * This should be run before the main arithmetic evaluation.
 */
class ScientificEvaluator(private val parser: ExpressionParser) {

    // Define MathContext for scientific precision (e.g., 50 significant digits)
    private val SCIENTIFIC_MATH_CONTEXT = java.math.MathContext(50, RoundingMode.HALF_UP)

    companion object {
        private const val DEG_TO_RAD = Math.PI / 180.0
        private const val EULER_VALUE = "2.7182818284590452353602874713526624977572470936999" // High-precision 'e'
        private const val PI_VALUE = "3.1415926535897932384626433832795028841971693993751" // High-precision 'pi'

        // Function prefixes
        const val SIN = "sin("
        const val COS = "cos("
        const val TAN = "tan("
        const val LN = "ln("
        const val LOG = "log("
        const val SQRT = "√("
        
        // Constants
        const val PI = ExpressionParser.PI
        const val EULER = ExpressionParser.EULER
        
        // Operators
        const val POWER = ExpressionParser.POWER // ^
        const val FACTORIAL = ExpressionParser.FACTORIAL // !
    }

    /**
     * Replaces scientific constants and solves all functions (sin, cos, etc.) recursively.
     * @param expression The raw expression string.
     * @param isDegreesMode True if the calculator is in degree mode for trig functions.
     * @return The expression with all scientific elements resolved to number strings.
     */
    fun preProcess(expression: String, isDegreesMode: Boolean): String {
        // 1. Replace constants
        var processedExpression = expression
            .replace(PI, PI_VALUE)
            .replace(EULER, EULER_VALUE)

        // 2. Resolve functions and square roots iteratively. (Innermost first)
        
        // Loop through the expression to find and resolve functions
        val functionPrefixes = listOf(SIN, COS, TAN, LN, LOG, SQRT)
        processedExpression = resolveFunctionArguments(processedExpression, functionPrefixes, isDegreesMode)

        // 3. Resolve Power (a^b) and Factorial (a!) after all functions are solved, but before main arithmetic
        processedExpression = resolvePowers(processedExpression)
        processedExpression = resolveFactorials(processedExpression)

        return processedExpression
    }

    /**
     * Iteratively finds, evaluates, and replaces function blocks like sin(...) with their results.
     */
    private fun resolveFunctionArguments(expression: String, prefixes: List<String>, isDegreesMode: Boolean): String {
        var currentExpression = expression
        var foundFunction = true

        // Loop until no more functions are found. This handles nested functions (e.g., sin(cos(1)))
        while (foundFunction) {
            foundFunction = false
            var bestMatchIndex = Int.MAX_VALUE
            var bestMatchPrefix = ""

            // Find the rightmost (innermost) function call
            for (prefix in prefixes) {
                val lastIndex = currentExpression.lastIndexOf(prefix)
                if (lastIndex != -1 && lastIndex < bestMatchIndex) {
                    bestMatchIndex = lastIndex
                    bestMatchPrefix = prefix
                    foundFunction = true
                }
            }

            if (foundFunction) {
                // Find the end of the argument, which is the matching closing parenthesis
                val startIndex = bestMatchIndex + bestMatchPrefix.length
                
                var balance = 1
                var endIndex = startIndex
                while (endIndex < currentExpression.length && balance != 0) {
                    when (currentExpression[endIndex].toString()) {
                        ExpressionParser.OPEN_BRACE -> balance++
                        ExpressionParser.CLOSE_BRACE -> balance--
                    }
                    if (balance != 0) endIndex++
                }

                if (balance != 0) {
                    throw IllegalArgumentException("Mismatched parentheses for function: $bestMatchPrefix")
                }
                
                // The substring from startIndex to endIndex is the argument *expression*
                val innerExpression = currentExpression.substring(startIndex, endIndex)
                
                // Recursively evaluate the argument expression (must resolve all its parts)
                // We use parser.evaluate to handle nested arithmetic and sub-functions
                val argumentResult = parser.evaluate(innerExpression, isDegreesMode = isDegreesMode)

                // Perform the final scientific operation
                val finalResult = calculateScientificFunction(bestMatchPrefix, argumentResult, isDegreesMode)
                
                // Replace the entire block (prefix + argument + closing brace) with the result
                currentExpression = currentExpression.substring(0, bestMatchIndex) + 
                                    finalResult.toPlainString() + 
                                    currentExpression.substring(endIndex + 1) // +1 to skip the closing brace
            }
        }
        return currentExpression
    }

    /**
     * Performs the actual function calculation on a single BigDecimal argument.
     * FIX: Ensures Degrees/Radians is handled correctly for trig functions.
     */
    private fun calculateScientificFunction(prefix: String, arg: BigDecimal, isDegreesMode: Boolean): BigDecimal {
        return try {
            val doubleArg = arg.toDouble()
            
            // Check for potential overflow/underflow before calculation
            if (doubleArg.isInfinite() || doubleArg.isNaN()) {
                throw RuntimeException("Overflow/Underflow in function argument: ${prefix.trim('(')} of $arg")
            }
            
            val result: Double = when (prefix) {
                SIN -> if (isDegreesMode) sin(doubleArg * DEG_TO_RAD) else sin(doubleArg)
                COS -> if (isDegreesMode) cos(doubleArg * DEG_TO_RAD) else cos(doubleArg)
                TAN -> if (isDegreesMode) tan(doubleArg * DEG_TO_RAD) else tan(doubleArg) 
                LN -> ln(doubleArg) // Natural log
                LOG -> log10(doubleArg) // Log base 10
                SQRT -> sqrt(doubleArg)
                else -> throw IllegalArgumentException("Unknown scientific function: $prefix")
            }

            // Convert the result back to BigDecimal with high precision
            BigDecimal(result, SCIENTIFIC_MATH_CONTEXT)
        } catch (e: Exception) {
            // Handle math domain errors (e.g., log(-1), sqrt(-1)) or runtime overflow
            throw IllegalArgumentException("Math Domain Error: ${prefix.trim('(')} of $arg")
        }
    }
    
    /**
     * Iteratively resolves all power operations (a^b) from left to right.
     * FIX: This function correctly finds and evaluates the power operator.
     */
    private fun resolvePowers(expression: String): String {
        var currentExpression = expression
        while (currentExpression.contains(POWER)) {
            val powerIndex = currentExpression.indexOf(POWER)

            // 1. Find the base (operand before '^')
            val baseEndIndex = powerIndex
            val baseStartIndex = findNumberStart(currentExpression, baseEndIndex - 1)
            
            // 2. Find the exponent (operand after '^')
            val exponentStartIndex = powerIndex + 1
            val exponentEndIndex = findNumberEnd(currentExpression, exponentStartIndex)
            
            if (baseStartIndex == -1 || exponentEndIndex == -1) {
                throw IllegalArgumentException("Syntax Error: Missing operand for power operator.")
            }
            
            val baseStr = currentExpression.substring(baseStartIndex, baseEndIndex)
            val exponentStr = currentExpression.substring(exponentStartIndex, exponentEndIndex + 1)

            // Calculate the power
            val base = baseStr.toBigDecimal()
            val exponent = exponentStr.toBigDecimal()

            val result = try {
                val doubleBase = base.toDouble()
                val doubleExponent = exponent.toDouble()
                
                // Check for potential overflow/underflow before calculation
                if (doubleBase.isInfinite() || doubleExponent.isInfinite()) {
                     throw RuntimeException("Overflow in power calculation: $base^$exponent")
                }
                
                // Use the standard library for Double power calculation (supports fractional exponents)
                BigDecimal(doubleBase.pow(doubleExponent), SCIENTIFIC_MATH_CONTEXT)
            } catch (e: Exception) {
                throw IllegalArgumentException("Math Error in power calculation: $base^$exponent. ${e.message}")
            }

            // Replace base^exponent with the result
            currentExpression = currentExpression.substring(0, baseStartIndex) + 
                                result.toPlainString() + 
                                currentExpression.substring(exponentEndIndex + 1)
        }
        return currentExpression
    }

    /**
     * Iteratively resolves all factorial operations (a!) from left to right.
     */
    private fun resolveFactorials(expression: String): String {
        var currentExpression = expression
        while (currentExpression.contains(FACTORIAL)) {
            val factIndex = currentExpression.indexOf(FACTORIAL)
            
            // 1. Find the base (operand before '!')
            val baseEndIndex = factIndex
            val baseStartIndex = findNumberStart(currentExpression, baseEndIndex - 1)

            if (baseStartIndex == -1) {
                throw IllegalArgumentException("Syntax Error: Missing operand for factorial operator.")
            }
            
            val baseStr = currentExpression.substring(baseStartIndex, baseEndIndex)
            
            // Calculate the factorial
            val base = baseStr.toBigDecimal()

            // Factorial is defined only for non-negative integers
            if (base.scale() > 0 || base.signum() < 0) {
                 throw IllegalArgumentException("Math Error: Factorial must be of a non-negative integer.")
            }
            
            val result = factorial(base.intValueExact())

            // Replace base! with the result
            currentExpression = currentExpression.substring(0, baseStartIndex) + 
                                result.toPlainString() + 
                                currentExpression.substring(factIndex + 1) // +1 to skip the '!'
        }
        return currentExpression
    }
    
    /**
     * Recursive factorial calculation.
     */
    private fun factorial(n: Int): BigDecimal {
        return when {
            n < 0 -> throw IllegalArgumentException("Factorial of negative number.")
            n == 0 || n == 1 -> BigDecimal.ONE
            else -> BigDecimal.valueOf(n.toLong()).multiply(factorial(n - 1))
        }
    }
    
    /**
     * Helper to find the starting index of a number token, searching backward from 'endIndex'.
     * Handles unsigned and signed numbers (e.g., in 2^-3 or -5).
     */
    private fun findNumberStart(expression: String, endIndex: Int): Int {
        var i = endIndex
        while (i >= 0) {
            val char = expression[i]

            // Check for digits or decimal point
            if (char.isDigit() || char.toString() == ExpressionParser.DECIMAL_POINT) {
                i--
            } 
            // Check for a valid unary sign position
            else if ((char.toString() == ExpressionParser.STD_MINUS || char.toString() == ExpressionParser.STD_PLUS) && 
                       (i == 0 || expression.getOrNull(i - 1)?.toString() == ExpressionParser.OPEN_BRACE || ExpressionParser.BINARY_OPERATORS.contains(expression.getOrNull(i - 1)?.toString()))) {
                // Unary sign: check if it's preceded by an operator, open brace, or is at the start.
                i-- // Include the sign and continue search
            } else {
                return i + 1 // Start index is the character AFTER the non-number symbol
            }
        }
        return i + 1 // If loop finishes, the start is 0
    }
    
    /**
     * Helper to find the ending index of a number token, searching forward from 'startIndex'.
     * Handles signed numbers, allowing a sign only at the very start of the search (e.g., exponent in a^b).
     */
    private fun findNumberEnd(expression: String, startIndex: Int): Int {
        var i = startIndex
        while (i < expression.length) {
            val char = expression[i]
            val charStr = char.toString()

            // 1. Allow an initial sign for the number (e.g., the exponent in 2^-3 starts with '-')
            if (i == startIndex && (charStr == ExpressionParser.STD_MINUS || charStr == ExpressionParser.STD_PLUS)) {
                i++
                continue
            }

            // 2. Scan forward for digits and decimal point
            if (char.isDigit() || charStr == ExpressionParser.DECIMAL_POINT) {
                i++
            } else {
                // End of the number reached
                return i - 1 // End index is the character BEFORE the non-number symbol
            }
        }
        // If the loop finishes, the number ends at the end of the expression
        return i - 1
    }
}
