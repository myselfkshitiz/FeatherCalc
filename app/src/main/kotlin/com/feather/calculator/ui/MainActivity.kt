package com.feather.calculator.ui

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.feather.calculator.R
import android.widget.GridLayout
import android.content.res.ColorStateList
import android.graphics.Color
import com.google.android.material.R as MaterialR
import com.feather.calculator.logic.CalculatorState
import com.feather.calculator.logic.CalculatorEngine
import com.feather.calculator.logic.CalculationController
import com.feather.calculator.logic.ExpressionParser
import com.feather.calculator.custom_views.MorphButton
import com.feather.calculator.system.TextSizingManager
import com.feather.calculator.system.SystemUIHelper
import com.feather.calculator.system.PreferenceManager
import com.feather.calculator.system.HapticFeedbackManager

class MainActivity : AppCompatActivity() {

    // --- Private Data Structure for Keypad Configuration ---
    private data class ButtonConfig(val functionalValue: String, val drawableResId: Int)

    // --- UI Views ---
    private lateinit var textExpression: EditText
    private lateinit var scrollExpression: HorizontalScrollView
    private lateinit var textResult: TextView
    private lateinit var focusAnchor: View
    private lateinit var mainContentView: View

    // --- Managers/Controllers (The Modular Core) ---
    private val controller = CalculationController()
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var textSizingManager: TextSizingManager
    private val uiHelper: SystemUIHelper by lazy { SystemUIHelper(this) }
    private val hapticManager: HapticFeedbackManager by lazy { HapticFeedbackManager(this) }

    // --- State Properties ---
    private var colorTertiary: Int = Color.BLACK // Default color for successful result
    private var colorError: Int = Color.RED      // NEW: Color for error messages
    private lateinit var defaultExpressionColorStateList: ColorStateList

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initial UI/System Setup
        uiHelper.applyImmersiveMode()
        setContentView(R.layout.activity_main)

        // 2. Initialize Managers
        preferenceManager = PreferenceManager(this)
        textSizingManager = TextSizingManager(this)

        // 3. Initialize Views
        mainContentView = findViewById(R.id.main_content)
        textExpression = findViewById(R.id.text_expression)
        scrollExpression = findViewById(R.id.scroll_expression)
        textResult = findViewById(R.id.text_result)
        focusAnchor = findViewById(R.id.focus_anchor)

        uiHelper.applyPaddingToContent(mainContentView)

        // 4. Resolve Themed Colors
        colorTertiary = resolveThemedColor(MaterialR.attr.colorTertiary)
        // Resolve the androidx.appcompat R.attr for colorError
        colorError = resolveThemedColor(androidx.appcompat.R.attr.colorError) 
        
        defaultExpressionColorStateList = textExpression.textColors

        textExpression.showSoftInputOnFocus = false
        focusAnchor.requestFocus()

        // 5. Load State
        loadInitialStatePlaceholder()
        loadCalculationStateAsync()

        // 6. Setup Keypad and Listeners
        setupKeypad()
    }

    override fun onResume() {
        super.onResume()
        focusAnchor.requestFocus()
    }

    override fun onPause() {
        super.onPause()
        saveCalculationState()
    }

    /**
     * Helper function to resolve a theme attribute color.
     */
    private fun resolveThemedColor(attrId: Int): Int {
        val typedValue = TypedValue()
        // Use theme to resolve the attribute value
        if (theme.resolveAttribute(attrId, typedValue, true)) {
            return typedValue.data
        }
        // Fallback color if resolution fails
        return Color.RED 
    }

    /**
     * Sets a placeholder state instantly for visual stability during cold start.
     */
    private fun loadInitialStatePlaceholder() {
        textExpression.textSize = textSizingManager.getDefaultTextSizeSp()
        updateDisplay(shouldUpdateTextSize = false)
    }

    /**
     * COLD START OPTIMIZATION: Loads the last saved expression/cursor in a background thread.
     */
    private fun loadCalculationStateAsync() {
        Thread {
            val savedData = preferenceManager.loadState(textSizingManager.getDefaultTextSizeSp())

            // Post the update back to the main thread
            mainContentView.post {
                controller.loadSavedState(savedData)
                textExpression.textSize = savedData.textSizeSp // Apply saved size
                updateDisplay(shouldUpdateTextSize = false)
            }
        }.start()
    }

    /**
     * Saves the current application state via the PreferenceManager.
     */
    private fun saveCalculationState() {
        val state = controller.currentState
        val currentTextSizeSp = textExpression.textSize / resources.displayMetrics.scaledDensity

        preferenceManager.saveState(
            expression = state.expression,
            cursor = state.cursorPosition,
            textSizeSp = currentTextSizeSp
        )
    }

    /**
     * Maps button IDs to their functional value and drawable icon, then sets up listeners.
     */
    private fun setupKeypad() {
        val keypadLayout = findViewById<GridLayout>(R.id.keypad_grid)

        // Map R.id to the functional value and the drawable resource ID
        val buttonConfigs = mapOf(
            R.id.btn_clear to ButtonConfig("CLR", R.drawable.glyph_clr),
            R.id.btn_group to ButtonConfig(ExpressionParser.OPEN_BRACE + ExpressionParser.CLOSE_BRACE, R.drawable.glyph_parens),
            R.id.btn_percent to ButtonConfig(ExpressionParser.PERCENT, R.drawable.glyph_op_pct),
            R.id.btn_divide to ButtonConfig(ExpressionParser.DIVIDE, R.drawable.glyph_op_div),
            R.id.btn_seven to ButtonConfig("7", R.drawable.glyph_digit_7),
            R.id.btn_eight to ButtonConfig("8", R.drawable.glyph_digit_8),
            R.id.btn_nine to ButtonConfig("9", R.drawable.glyph_digit_9),
            R.id.btn_multiply to ButtonConfig(ExpressionParser.MULTIPLY, R.drawable.glyph_op_mul),
            R.id.btn_four to ButtonConfig("4", R.drawable.glyph_digit_4),
            R.id.btn_five to ButtonConfig("5", R.drawable.glyph_digit_5),
            R.id.btn_six to ButtonConfig("6", R.drawable.glyph_digit_6),
            R.id.btn_subtract to ButtonConfig(ExpressionParser.STD_MINUS, R.drawable.glyph_op_sub),
            R.id.btn_one to ButtonConfig("1", R.drawable.glyph_digit_1),
            R.id.btn_two to ButtonConfig("2", R.drawable.glyph_digit_2),
            R.id.btn_three to ButtonConfig("3", R.drawable.glyph_digit_3),
            R.id.btn_add to ButtonConfig(ExpressionParser.STD_PLUS, R.drawable.glyph_op_add),
            R.id.btn_zero to ButtonConfig("0", R.drawable.glyph_digit_0),
            R.id.btn_point to ButtonConfig(ExpressionParser.DECIMAL_POINT, R.drawable.glyph_point),
            R.id.btn_backspace to ButtonConfig("DEL", R.drawable.glyph_del),
            R.id.btn_equals to ButtonConfig("=", R.drawable.glyph_eq)
        )

        for ((buttonId, config) in buttonConfigs) {
            val button = keypadLayout.findViewById<MorphButton>(buttonId)

            button?.let {
                it.setDigit(config.drawableResId)
                it.contentDescription = config.functionalValue

                it.setOnClickListener { _ ->
                    handleInput(config.functionalValue)
                }
            }
        }

        // Listener for manual cursor changes on the EditText
        textExpression.setOnClickListener {
            controller.updateCursor(textExpression.selectionStart)
        }
    }

    /**
     * Directs the button input to the controller and triggers a UI update.
     */
    private fun handleInput(value: String) {
        hapticManager.triggerClickHaptic()

        textExpression.requestFocus()

        val selectionStart = textExpression.selectionStart
        val selectionEnd = textExpression.selectionEnd

        controller.handleInput(value, selectionStart, selectionEnd)
        updateDisplay()
    }

    /**
     * Reads the current state from the CalculationController and updates all UI elements.
     */
    private fun updateDisplay(shouldUpdateTextSize: Boolean = true) {
        val state = controller.currentState
        val newExpression = state.expression

        // 1. Handle Expression Text Coloring
        val expColor = if (state.isResultFinalized) colorTertiary else defaultExpressionColorStateList.defaultColor
        textExpression.setTextColor(expColor)

        // 2. Update Expression Text
        if (textExpression.text.toString() != newExpression) {
            textExpression.setText(newExpression)
        }

        // 3. Set Cursor Position
        if (state.cursorPosition in 0..newExpression.length) {
            textExpression.setSelection(state.cursorPosition)
        }

        // 4. Update Live Result and handle error coloring
        val liveResult = state.liveResult
        if (liveResult.startsWith(CalculatorEngine.ERROR_TAG)) {
            // Error case: Apply error color and remove the tag
            val errorMessage = liveResult.substring(CalculatorEngine.ERROR_TAG.length)
            textResult.text = errorMessage
            textResult.setTextColor(colorError)
        } else {
            // Success case: Restore tertiary color
            textResult.text = liveResult
            textResult.setTextColor(colorTertiary) 
        }

        // 5. Adjust text size
        if (shouldUpdateTextSize) {
            textSizingManager.adjustTextSize(textExpression, scrollExpression)
        }

        // 6. Scrolling logic to follow the cursor
        scrollExpression.post {
            val scrollX = textExpression.layout?.getPrimaryHorizontal(textExpression.selectionStart)?.toInt() ?: 0
            scrollExpression.smoothScrollTo(scrollX, 0)
        }
    }
}
