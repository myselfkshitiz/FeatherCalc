package com.feather.calculator.system

import android.content.Context
import com.feather.calculator.logic.SavedState

class PreferenceManager(context: Context) {

    private val PREFS_NAME = "CalculatorPrefs"
    private val KEY_EXPRESSION = "expression"
    private val KEY_CURSOR = "cursor"
    private val KEY_TEXT_SIZE = "text_size_sp"
    private val KEY_IS_DEGREES_MODE = "is_degrees_mode" // ADD THIS KEY

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Loads the saved calculation state and text size.
     */
    // ADD defaultIsDegreesMode parameter
    fun loadState(defaultTextSizeSp: Float, defaultIsDegreesMode: Boolean): SavedState {
        val savedExpression = prefs.getString(KEY_EXPRESSION, "") ?: ""
        val savedCursor = prefs.getInt(KEY_CURSOR, savedExpression.length)
        val savedTextSizeSp = prefs.getFloat(KEY_TEXT_SIZE, defaultTextSizeSp)
        // LOAD THE STATE
        val savedIsDegreesMode = prefs.getBoolean(KEY_IS_DEGREES_MODE, defaultIsDegreesMode)

        return SavedState(
            expression = savedExpression,
            cursor = savedCursor,
            textSizeSp = savedTextSizeSp,
            isDegreesMode = savedIsDegreesMode // PASS THE STATE
        )
    }

    /**
     * Saves the current calculation state and text size.
     */
    // ADD isDegreesMode parameter
    fun saveState(expression: String, cursor: Int, textSizeSp: Float, isDegreesMode: Boolean) {
        prefs.edit().apply {
            putString(KEY_EXPRESSION, expression)
            putInt(KEY_CURSOR, cursor)
            putFloat(KEY_TEXT_SIZE, textSizeSp)
            putBoolean(KEY_IS_DEGREES_MODE, isDegreesMode) // SAVE THE STATE
            apply()
        }
    }
}
