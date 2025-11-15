package com.feather.calculator.system

import android.content.Context
import com.feather.calculator.logic.SavedState

/**
 * Manages all application state persistence using SharedPreferences.
 * Decouples disk I/O logic from the Activity for better modularity and testability.
 */
class PreferenceManager(context: Context) {

    private val PREFS_NAME = "CalculatorPrefs"
    private val KEY_EXPRESSION = "expression"
    private val KEY_CURSOR = "cursor"
    private val KEY_TEXT_SIZE = "text_size_sp"

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // FIX: The redundant 'CalculatorData' data class has been removed.

    /**
     * Loads the saved calculation state and text size.
     * Uses default values if no data is found.
     */
    // FIX: Changed return type from CalculatorData to SavedState
    fun loadState(defaultTextSizeSp: Float): SavedState {
        val savedExpression = prefs.getString(KEY_EXPRESSION, "") ?: ""
        val savedCursor = prefs.getInt(KEY_CURSOR, savedExpression.length)
        val savedTextSizeSp = prefs.getFloat(KEY_TEXT_SIZE, defaultTextSizeSp)

        // FIX: Changed instantiation from CalculatorData to SavedState
        return SavedState(
            expression = savedExpression,
            cursor = savedCursor,
            textSizeSp = savedTextSizeSp
        )
    }

    /**
     * Saves the current calculation state and text size.
     */
    fun saveState(expression: String, cursor: Int, textSizeSp: Float) {
        prefs.edit().apply {
            putString(KEY_EXPRESSION, expression)
            putInt(KEY_CURSOR, cursor)
            putFloat(KEY_TEXT_SIZE, textSizeSp)
            apply()
        }
    }
}
