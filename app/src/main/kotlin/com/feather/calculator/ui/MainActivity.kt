package com.feather.calculator.ui

import android.animation.ValueAnimator
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
import com.feather.calculator.logic.CalculationController
import com.feather.calculator.logic.CalculatorEngine
import com.feather.calculator.custom_views.MorphButton
import com.feather.calculator.system.TextSizingManager
import com.feather.calculator.system.SystemUIHelper
import com.feather.calculator.system.PreferenceManager
import com.feather.calculator.system.HapticFeedbackManager
import com.feather.calculator.utils.DynamicDimensionHelper
import android.widget.ImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Guideline
import android.util.Log
import android.os.Handler
import android.os.Looper
import com.feather.calculator.logic.SavedState 

class MainActivity : AppCompatActivity() {

    private data class ButtonConfig(val functionalValue: String, val drawableResId: Int)

    private lateinit var textExpression: EditText
    private lateinit var scrollExpression: HorizontalScrollView
    private lateinit var textResult: TextView
    private lateinit var focusAnchor: View
    private lateinit var mainContentView: View
    private lateinit var btnExpandCollapse: ImageButton
    private lateinit var guidelineSplit: Guideline

    private lateinit var keypadContainer: ConstraintLayout
    private lateinit var scientificKeypadWrapper: ConstraintLayout
    private lateinit var scientificKeypadGrid: GridLayout
    private lateinit var standardKeypadGrid: GridLayout

    private val controller = CalculationController()
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var textSizingManager: TextSizingManager
    private val uiHelper: SystemUIHelper by lazy { SystemUIHelper(this) }
    private val hapticManager: HapticFeedbackManager by lazy { HapticFeedbackManager(this) }
    private val mainHandler = Handler(Looper.getMainLooper())

    private var colorTertiary: Int = Color.BLACK
    private var colorError: Int = Color.RED
    private lateinit var defaultExpressionColorStateList: ColorStateList

    private var isKeypadExpanded = false
    private var keypadAnimator: ValueAnimator? = null 

    private var scientificKeypadHeight = 0
    private var singleButtonRowHeightPx = 0
    private val ANIMATION_DURATION: Long = 250L

    private val TOTAL_KEYPAD_ROWS = 8
    private val SCIENTIFIC_ROWS = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        uiHelper.applyImmersiveMode()
        setContentView(R.layout.activity_main)

        preferenceManager = PreferenceManager(this)
        textSizingManager = TextSizingManager(this)

        mainContentView = findViewById(R.id.main_content)
        textExpression = findViewById(R.id.text_expression)
        scrollExpression = findViewById(R.id.scroll_expression)
        textResult = findViewById(R.id.text_result)
        focusAnchor = findViewById(R.id.focus_anchor)
        btnExpandCollapse = findViewById(R.id.btn_expand_collapse)
        guidelineSplit = findViewById(R.id.guideline_split)

        keypadContainer = findViewById(R.id.keypad_container)
        scientificKeypadWrapper = findViewById(R.id.scientific_keypad_wrapper)
        scientificKeypadGrid = findViewById(R.id.scientific_keypad_grid)
        standardKeypadGrid = findViewById(R.id.standard_keypad_grid)

        uiHelper.applyPaddingToContent(mainContentView)

        colorTertiary = resolveThemedColor(MaterialR.attr.colorTertiary)
        colorError = resolveThemedColor(androidx.appcompat.R.attr.colorError)

        defaultExpressionColorStateList = textExpression.textColors

        textExpression.showSoftInputOnFocus = false
        focusAnchor.requestFocus()

        loadInitialStatePlaceholder()
        loadCalculationStateAsync()
        
        setupKeypad()
        initializeKeypadState() 
        setupArrowButton()
    }

    override fun onResume() {
        super.onResume()
        focusAnchor.requestFocus()
    }

    override fun onPause() {
        super.onPause()
        saveCalculationState()
    }

    private fun isKeypadAlwaysVisible(): Boolean {
        return try {
            resources.getBoolean(R.bool.is_landscape_or_sw600dp)
        } catch (e: Exception) {
            Log.e("MainActivity", "Missing R.bool.is_landscape_or_sw600dp. Assuming compact layout.")
            false
        }
    }

    private fun resolveThemedColor(attrId: Int): Int {
        val typedValue = TypedValue()
        if (theme.resolveAttribute(attrId, typedValue, true)) {
            return typedValue.data
        }
        return Color.RED
    }

    private fun loadInitialStatePlaceholder() {
        textExpression.textSize = textSizingManager.getDefaultTextSizeSp()
        updateDisplay(shouldUpdateTextSize = false)
    }

    private fun loadCalculationStateAsync() {
        Thread {
            val savedData: SavedState = preferenceManager.loadState(
                defaultTextSizeSp = textSizingManager.getDefaultTextSizeSp(),
                defaultIsDegreesMode = false 
            )

            mainContentView.post {
                controller.loadSavedState(savedData)
                textExpression.textSize = savedData.textSizeSp
                updateDisplay(shouldUpdateTextSize = false)
            }
        }.start()
    }


    private fun saveCalculationState() {
        val state = controller.currentState
        val currentTextSizeSp = textExpression.textSize / resources.displayMetrics.scaledDensity

        preferenceManager.saveState(
            expression = state.expression,
            cursor = state.cursorPosition,
            textSizeSp = currentTextSizeSp,
            isDegreesMode = controller.isDegreesMode 
        )
    }


    private fun initializeKeypadState() {
        val keypadAlwaysVisible = isKeypadAlwaysVisible()

        keypadContainer.post {
            
            if (scientificKeypadHeight == 0) {
                // Calculate necessary dimensions only once
                val totalKeypadAreaHeight = keypadContainer.height
                val totalSpacingPx = DynamicDimensionHelper.getButtonSpacingPx(this)

                // Calculate single row height based on all 8 potential rows 
                val totalInternalGapHeight = (TOTAL_KEYPAD_ROWS - 1) * totalSpacingPx 
                val spaceForButtonsContent = totalKeypadAreaHeight - totalInternalGapHeight
                singleButtonRowHeightPx = spaceForButtonsContent / TOTAL_KEYPAD_ROWS 

                // Calculate the total height for the 3 scientific rows + spacing (2 internal gaps)
                val internalGapsScientific = SCIENTIFIC_ROWS - 1 
                scientificKeypadHeight = (singleButtonRowHeightPx * SCIENTIFIC_ROWS) + (internalGapsScientific * totalSpacingPx)
                
                // CRITICAL STEP: Apply the calculated button heights to ensure correct measurement
                applyScientificKeypadDimensions() 
            }


            // Set initial state based on visibility
            isKeypadExpanded = keypadAlwaysVisible
            btnExpandCollapse.visibility = if (keypadAlwaysVisible) View.GONE else View.VISIBLE
            btnExpandCollapse.rotation = if (keypadAlwaysVisible) 0f else 180f

            // Apply the final required height state immediately (collapsed or expanded).
            val finalHeight = if (keypadAlwaysVisible) scientificKeypadHeight else 0
            val params = scientificKeypadWrapper.layoutParams
            params.height = finalHeight
            scientificKeypadWrapper.layoutParams = params
            scientificKeypadWrapper.requestLayout()
        }
    }


    // FINAL AGGRESSIVE ANIMATION FIX - Robust Height Management
    private fun toggleScientificKeypad(shouldExpand: Boolean) {
        if (isKeypadAlwaysVisible()) return

        if (scientificKeypadHeight == 0) {
            Log.e("MainActivity", "Scientific Keypad height is 0. Cannot animate.")
            return
        }

        // Cancel any ongoing animation to prevent jumping/stuttering
        keypadAnimator?.cancel() 

        // CRITICAL: Get the current height to ensure the animation starts from the current visual state
        val currentHeight = scientificKeypadWrapper.layoutParams.height
        val endHeight = if (shouldExpand) scientificKeypadHeight else 0
        
        // If the current height is already the target height, do nothing.
        if (currentHeight == endHeight) {
            return
        }

        // Create and store the new animator. This runs from the current height to the target height.
        keypadAnimator = ValueAnimator.ofInt(currentHeight, endHeight).apply {
            duration = ANIMATION_DURATION
            addUpdateListener { animator ->
                val value = animator.animatedValue as Int
                val params = scientificKeypadWrapper.layoutParams
                params.height = value
                scientificKeypadWrapper.layoutParams = params
                
                // CRITICAL AGGRESSIVE FIX: Force the parent container to re-layout on every frame
                // This is what makes the standard keypad adjust smoothly and relatively.
                keypadContainer.requestLayout() 
            }
            start()
        }
    }


    private fun setupKeypad() {
        val allButtonConfigs = mapOf(
            // --- Scientific Keypad ---
            R.id.btn_op_sqrt     to ButtonConfig("√(", R.drawable.glyph_op_sqrt),
            R.id.btn_const_pi    to ButtonConfig("π", R.drawable.glyph_const_pi),
            R.id.btn_op_pow      to ButtonConfig("^", R.drawable.glyph_op_pow),
            R.id.btn_op_fact     to ButtonConfig("!", R.drawable.glyph_op_fact),
            R.id.btn_mode_rad    to ButtonConfig("RAD", R.drawable.glyph_mode_rad),
            R.id.btn_fun_sin     to ButtonConfig("sin(", R.drawable.glyph_fun_sin),
            R.id.btn_fun_cos     to ButtonConfig("cos(", R.drawable.glyph_fun_cos),
            R.id.btn_fun_tan     to ButtonConfig("tan(", R.drawable.glyph_fun_tan),
            R.id.btn_inv         to ButtonConfig("inv", R.drawable.glyph_inv),
            R.id.btn_const_e     to ButtonConfig("e", R.drawable.glyph_const_e),
            R.id.btn_fun_ln      to ButtonConfig("ln(", R.drawable.glyph_fun_ln),
            R.id.btn_fun_log     to ButtonConfig("log(", R.drawable.glyph_fun_log),

            // --- Standard Keypad ---
            R.id.btn_clear       to ButtonConfig("CLR", R.drawable.glyph_clr),
            R.id.btn_group       to ButtonConfig("()", R.drawable.glyph_parens),
            R.id.btn_percent     to ButtonConfig("%", R.drawable.glyph_op_pct),
            R.id.btn_divide      to ButtonConfig("÷", R.drawable.glyph_op_div),
            R.id.btn_seven       to ButtonConfig("7", R.drawable.glyph_digit_7),
            R.id.btn_eight       to ButtonConfig("8", R.drawable.glyph_digit_8),
            R.id.btn_nine        to ButtonConfig("9", R.drawable.glyph_digit_9),
            R.id.btn_multiply    to ButtonConfig("×", R.drawable.glyph_op_mul),
            R.id.btn_four        to ButtonConfig("4", R.drawable.glyph_digit_4),
            R.id.btn_five        to ButtonConfig("5", R.drawable.glyph_digit_5),
            R.id.btn_six         to ButtonConfig("6", R.drawable.glyph_digit_6),
            R.id.btn_subtract    to ButtonConfig("-", R.drawable.glyph_op_sub),
            R.id.btn_one         to ButtonConfig("1", R.drawable.glyph_digit_1),
            R.id.btn_two         to ButtonConfig("2", R.drawable.glyph_digit_2),
            R.id.btn_three       to ButtonConfig("3", R.drawable.glyph_digit_3),
            R.id.btn_add         to ButtonConfig("+", R.drawable.glyph_op_add),
            R.id.btn_zero        to ButtonConfig("0", R.drawable.glyph_digit_0),
            R.id.btn_point       to ButtonConfig(".", R.drawable.glyph_point),
            R.id.btn_backspace   to ButtonConfig("DEL", R.drawable.glyph_del),
            R.id.btn_equals      to ButtonConfig("=", R.drawable.glyph_eq)
        )
        
        val totalSpacingPx = DynamicDimensionHelper.getButtonSpacingPx(this)
        val halfSpacingPx = totalSpacingPx / 2

        val columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        val weightedRowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) 

        for ((buttonId, config) in allButtonConfigs) {
            var button = scientificKeypadGrid.findViewById<MorphButton>(buttonId)
            val isScientificKey = (button != null)

            if (button == null) {
                button = standardKeypadGrid.findViewById(buttonId)
            }

            button?.let {
                it.setDigit(config.drawableResId)
                it.contentDescription = config.functionalValue

                try {
                    // Only apply weighted layout params to STANDARD keys
                    if (!isScientificKey) { 
                        val params = GridLayout.LayoutParams(weightedRowSpec, columnSpec).apply {
                            width = 0 
                            height = 0 
                            setMargins(halfSpacingPx, halfSpacingPx, halfSpacingPx, halfSpacingPx)
                        }
                        it.layoutParams = params
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error setting layout params for button $buttonId: ${e.message}")
                }

                it.setOnClickListener { _ ->
                    handleInput(config.functionalValue)
                }
            }
        }

        textExpression.setOnClickListener {
            controller.updateCursor(textExpression.selectionStart)
            updateDisplay(shouldUpdateTextSize = false)
        }
    }


    private fun setupArrowButton() {
        if (isKeypadAlwaysVisible()) {
            return
        }

        btnExpandCollapse.setOnClickListener {
            hapticManager.triggerClickHaptic()

            isKeypadExpanded = !isKeypadExpanded

            val targetRotation = if (isKeypadExpanded) 0f else 180f
            btnExpandCollapse.animate().rotation(targetRotation).setDuration(ANIMATION_DURATION).start()

            toggleScientificKeypad(isKeypadExpanded)
        }
    }

    private fun handleInput(value: String) {
        hapticManager.triggerClickHaptic()

        textExpression.requestFocus()

        val selectionStart = textExpression.selectionStart
        val selectionEnd = textExpression.selectionEnd

        when (value) {
            "=" -> {
                controller.finalizeCalculation()
                updateDisplay(shouldUpdateTextSize = true) 
            }
            "RAD", "DEG" -> {
                controller.setDegreesMode(!controller.isDegreesMode)
                updateDisplay(shouldUpdateTextSize = false)
            }
            else -> {
                controller.handleInput(value, selectionStart, selectionEnd)
                updateDisplay()
            }
        }
    }
    
    // Function implementing the aggressive fix for 3-row layout
    private fun applyScientificKeypadDimensions() {
        if (singleButtonRowHeightPx <= 0) {
            Log.e("MainActivity", "singleButtonRowHeightPx is not set. Cannot apply dimensions.")
            return
        }

        val halfSpacingPx = DynamicDimensionHelper.getButtonSpacingPx(this) / 2
        val columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        
        for (i in 0 until scientificKeypadGrid.childCount) {
            val child = scientificKeypadGrid.getChildAt(i)
            if (child is MorphButton) {
                try {
                    val params = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), columnSpec).apply {
                        width = 0 
                        height = singleButtonRowHeightPx 
                        setMargins(halfSpacingPx, halfSpacingPx, halfSpacingPx, halfSpacingPx)
                    }
                    child.layoutParams = params
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error applying scientific button dimensions: ${e.message}")
                }
            }
        }
        scientificKeypadGrid.requestLayout() 
    }


    private fun updateDisplay(shouldUpdateTextSize: Boolean = true) {
        val state = controller.currentState
        val newExpression = state.expression

        val expColor = if (state.isResultFinalized) colorTertiary else defaultExpressionColorStateList.defaultColor
        textExpression.setTextColor(expColor)

        if (textExpression.text.toString() != newExpression) {
            textExpression.setText(newExpression)
        }

        if (state.cursorPosition in 0..newExpression.length) {
            textExpression.setSelection(state.cursorPosition)
        }

        val liveResult = state.liveResult
        if (liveResult.startsWith(CalculatorEngine.ERROR_TAG)) {
            val errorMessage = liveResult.substring(CalculatorEngine.ERROR_TAG.length)
            textResult.text = errorMessage
            textResult.setTextColor(colorError)
            textResult.setTextSize(TypedValue.COMPLEX_UNIT_SP, DynamicDimensionHelper.RESULT_DEFAULT_TEXT_SIZE)
        } else {
            textResult.text = liveResult
            textResult.setTextColor(colorTertiary)
            textResult.setTextSize(TypedValue.COMPLEX_UNIT_SP, DynamicDimensionHelper.RESULT_DEFAULT_TEXT_SIZE)
        }

        val btnModeRad = scientificKeypadGrid.findViewById<MorphButton>(R.id.btn_mode_rad)
        if (btnModeRad != null) {
            if (controller.isDegreesMode) {
                btnModeRad.setDigit(R.drawable.glyph_mode_deg) 
                btnModeRad.contentDescription = "DEG"
            } else {
                btnModeRad.setDigit(R.drawable.glyph_mode_rad) 
                btnModeRad.contentDescription = "RAD"
            }
        }


        if (shouldUpdateTextSize) { 
            textSizingManager.adjustTextSize(textExpression, scrollExpression)
        }

        scrollExpression.post {
            val scrollX = textExpression.layout?.getPrimaryHorizontal(textExpression.selectionStart)?.toInt() ?: 0
            scrollExpression.smoothScrollTo(scrollX, 0)
        }
    }
}
