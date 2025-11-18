package com.feather.calculator.logic

/**
 * Acts as the centralized brain for the calculator, mediating between the UI
 * (MainActivity) and the core engine (CalculatorEngine).
 */
class CalculationController {
    private val engine = CalculatorEngine()
    private var resultFinalized: Boolean = false

    // State property to track the calculator's mode
    var isDegreesMode: Boolean = false
        private set

    companion object {
        val FUNCTION_PREFIXES = listOf("√(", "sin(", "cos(", "tan(", "ln(", "log(")
    }

    var currentState: CalculatorState = CalculatorState()
        private set(value) {
            field = value
        }

    /**
     * Loads state from SharedPreferences/SavedState and initializes the engine.
     */
    fun loadSavedState(saved: SavedState) {
        engine.loadState(saved.expression, saved.cursor)
        this.isDegreesMode = saved.isDegreesMode
        updateState()
    }

    /**
     * Toggles the calculation mode between Degrees and Radians.
     */
    fun setDegreesMode(inDegrees: Boolean) {
        isDegreesMode = inDegrees
        updateState() // Recalculate live result with new mode
    }

    /**
     * Handles all keypad input, determining the appropriate action for the engine.
     */
    // In CalculationController.kt

    /**
     * Handles all keypad input, determining the appropriate action for the engine.
     */
    fun handleInput(value: String, selectionStart: Int, selectionEnd: Int) {
        
        // --- 1. Handle Immediate Action Commands (like CLR/DEL) first ---
        when (value) {
            "CLR", "AC" -> {
                engine.clear()
                resultFinalized = false // Ensure state is reset
                updateState()
                return // Stop processing other logic
            }
            "DEL" -> {
                // DEL works regardless of resultFinalized, but we can keep it inside the if/else
                // if we want DEL to not trigger clearing the finalized state for a new input.
            }
        }
        // --- End Immediate Action Commands ---

        val wasPreviousResultError = currentState.liveResult.startsWith(CalculatorEngine.ERROR_TAG)
        
        if (resultFinalized && !wasPreviousResultError) {
            // If previous result was final and not error, clear the state for new input
            when (value) {
                in engine.ALL_OPERATORS -> {
                    // Operator, keep the result as the starting expression
                    engine.appendInput(value, engine.getExpression().length, engine.getExpression().length)
                }
                else -> {
                    // Digit/Function/Brace, clear and start new expression
                    engine.clear()
                    engine.appendInput(value, 0, 0)
                }
            }
            resultFinalized = false
        } else {
            // Standard input (including DEL logic if we don't move it)
            when (value) {
                // We moved CLR/AC up top.
                "DEL" -> engine.backspace(selectionStart, selectionEnd)
                ExpressionParser.STD_MINUS + ExpressionParser.STD_PLUS -> engine.toggleSign(selectionStart, selectionEnd)
                ExpressionParser.OPEN_BRACE + ExpressionParser.CLOSE_BRACE -> engine.appendParentheses(selectionStart, selectionEnd)
                else -> engine.appendInput(value, selectionStart, selectionEnd)
            }
        }

        updateState()
    }


    /**
     * Manually updates the cursor position (e.g., when the user taps the EditText).
     */
    fun updateCursor(newCursorPosition: Int) {
        engine.setCursorPosition(newCursorPosition)
        updateState()
    }

    /**
     * Calculates the final result and sets the state to finalized.
     */
    fun finalizeCalculation() {
        val finalResult = engine.finalizeCalculation(isDegreesMode)

        currentState = CalculatorState(
            expression = engine.getExpression(),
            liveResult = finalResult,
            cursorPosition = engine.getCursorPosition(),
            isResultFinalized = true
        )
        resultFinalized = true
    }

    /**
     * Recalculates the live result and updates the internal state property.
     */
    private fun updateState() {
        val liveResult = engine.calculateLiveResult(isDegreesMode)
        
        currentState = CalculatorState(
            expression = engine.getExpression(),
            liveResult = liveResult,
            cursorPosition = engine.getCursorPosition(),
            isResultFinalized = false
        )
    }
    
    // Removed: fun toggleResultExpansion()
}
