package com.feather.calculator.logic

// Imports the required data structures from the new dedicated file
import com.feather.calculator.logic.CalculatorState
import com.feather.calculator.logic.SavedState
// Note: CalculatorEngine and ExpressionParser are in the same package

/**
 * Acts as the centralized brain for the calculator, mediating between the UI
 * (MainActivity) and the core engine (CalculatorEngine).
 */
class CalculationController {
    private val engine = CalculatorEngine()
    private var resultFinalized: Boolean = false

    // Uses the imported CalculatorState
    var currentState: CalculatorState = CalculatorState()
        private set(value) {
            field = value
        }

    /**
     * Loads state from SharedPreferences/SavedState and initializes the engine.
     */
    fun loadSavedState(saved: SavedState) { // Uses the imported SavedState
        // These methods were missing in your local engine file.
        engine.loadState(saved.expression, saved.cursor) 
        // Recalculate and update the state based on the loaded expression
        updateState()
    }

    /**
     * Handles all keypad input, determining the appropriate action for the engine.
     */
    fun handleInput(value: String, selectionStart: Int, selectionEnd: Int) {
        // If the last calculation was finalized, clear the expression before starting a new one,
        // unless the input is an operator that continues the last result.
        if (resultFinalized && value !in engine.ALL_OPERATORS) {
            engine.clear()
            resultFinalized = false
        } else if (resultFinalized && value in engine.ALL_OPERATORS) {
            resultFinalized = false
        }

        // Delegate input handling to the engine
        when (value) {
            "CLR" -> engine.clear()
            "DEL" -> engine.backspace(selectionStart, selectionEnd)
            "=" -> {
                finalizeCalculation()
                return // Finalize calculation updates state internally
            }
            ExpressionParser.OPEN_BRACE + ExpressionParser.CLOSE_BRACE -> engine.appendParentheses(selectionStart, selectionEnd) // This is now guaranteed to exist
            else -> engine.appendInput(value, selectionStart, selectionEnd)
        }

        updateState()
    }

    /**
     * Manually updates the cursor position (e.g., when the user taps the EditText).
     */
    fun updateCursor(newCursorPosition: Int) {
        engine.setCursorPosition(newCursorPosition) // This is now guaranteed to exist
        updateState()
    }

    /**
     * Calculates the final result and sets the state to finalized.
     */
    private fun finalizeCalculation() {
        // Finalize calculation in the engine
        val finalResult = engine.finalizeCalculation()

        // Update the state based on the final engine state
        currentState = CalculatorState(
            expression = engine.getExpression(),
            liveResult = finalResult,
            cursorPosition = engine.getCursorPosition(),
            isResultFinalized = true // Mark as finalized
        )
        resultFinalized = true
    }

    /**
     * Recalculates the live result and updates the internal state property.
     */
    private fun updateState() {
        val liveResult = engine.calculateLiveResult()
        currentState = CalculatorState(
            expression = engine.getExpression(),
            liveResult = liveResult,
            cursorPosition = engine.getCursorPosition(),
            isResultFinalized = false // Ensure false unless explicitly finalized
        )
    }
}