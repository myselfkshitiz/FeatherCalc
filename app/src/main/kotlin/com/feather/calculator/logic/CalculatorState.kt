package com.feather.calculator.logic

/**
 * Represents the mutable state of the calculator UI exposed by the Controller.
 */
data class CalculatorState(
    val expression: String = "",
    val liveResult: String = "",
    val cursorPosition: Int = 0,
    val isResultFinalized: Boolean = false
)

/**
 * Data class for persistence. Used to load state from the PreferenceManager.
 */
data class SavedState(
    val expression: String,
    val cursor: Int,
    val textSizeSp: Float,
    // FIX: Add isDegreesMode for persistence
    val isDegreesMode: Boolean
)
