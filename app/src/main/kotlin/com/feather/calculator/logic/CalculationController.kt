package com.feather.calculator.logic

import com.feather.calculator.logic.CalculatorState
import com.feather.calculator.logic.SavedState

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
        
        // FIX FOR BUG 1: Determine if the previous state was a fatal error
        val wasPreviousResultError = resultFinalized && currentState.liveResult.startsWith(CalculatorEngine.ERROR_TAG)
        
        // Determine if a non-operator was pressed after finalization
        val inputShouldClear = resultFinalized && value !in engine.ALL_OPERATORS

        // If the expression was finalized AND the new input is a number/brace/etc. (inputShouldClear)
        // OR if the expression resulted in an error (wasPreviousResultError), clear the engine fully.
        if (inputShouldClear || wasPreviousResultError) {
            engine.clear()
            resultFinalized = false
        } else if (resultFinalized && value in engine.ALL_OPERATORS) {
            // Continuation case: an operator was pressed, so continue with the result
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
