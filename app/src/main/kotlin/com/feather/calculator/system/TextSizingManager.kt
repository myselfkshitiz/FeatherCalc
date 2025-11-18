package com.feather.calculator.system

import android.content.Context
import android.util.TypedValue
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.TextView // Import TextView is needed for the new function

/**
 * Utility class dedicated to dynamically adjusting the size of the expression EditText
 * based on the content width to ensure the expression fits without breaking.
 */
class TextSizingManager(context: Context) {

    private val resources = context.resources

    private val MAX_TEXT_SIZE_SP = 64f
    private val MIN_TEXT_SIZE_SP = 54f
    
    // Define a size for when the result text is expanded/toggled.
    // This should be small and fixed to maximize content space.
    private val EXPANDED_RESULT_SIZE_SP = 24f 

    /**
     * Calculates and applies the appropriate text size to the EditText.
     *
     * @param editText The EditText containing the expression.
     * @param scrollView The HorizontalScrollView wrapping the EditText, used for available width.
     */
    fun adjustTextSize(editText: EditText, scrollView: HorizontalScrollView) {
        val expression = editText.text.toString()

        if (expression.isEmpty()) {
            editText.textSize = MAX_TEXT_SIZE_SP
            return
        }

        // Must wait until layout has been measured
        if (scrollView.width <= 0) {
            return
        }

        val availableWidthPx = scrollView.width - editText.paddingStart - editText.paddingEnd

        // Convert MAX and MIN SP values to Pixels for accurate measurement
        val maxTextSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            MAX_TEXT_SIZE_SP,
            resources.displayMetrics
        )

        val minTextSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            MIN_TEXT_SIZE_SP,
            resources.displayMetrics
        )

        // 2. Measure the text width if it were rendered at the MAX size
        val paint = editText.paint
        paint.textSize = maxTextSizePx
        val textWidthAtMaxPx = paint.measureText(expression)

        // 3. Determine if scaling is needed
        if (textWidthAtMaxPx <= availableWidthPx) {
            // Text fits comfortably at max size, use max size.
            editText.textSize = MAX_TEXT_SIZE_SP
            return
        }

        // 4. Calculate scaling
        // newSizePx = (maxTextSizePx * availableWidthPx) / textWidthAtMaxPx
        val newSizePx = (maxTextSizePx * availableWidthPx / textWidthAtMaxPx)
            .coerceAtLeast(minTextSizePx) // Constrain to minimum size

        // 5. Convert back to SP and apply
        // textExpression.textSize expects the value to be in SP units
        val newSizeSp = newSizePx / resources.displayMetrics.scaledDensity

        editText.textSize = newSizeSp
    }
    
    /**
     * Applies a fixed, smaller text size to the result TextView when the user
     * has toggled the result to be expanded/full-width.
     * * @param textView The TextView displaying the result.
     */
    fun adjustTextSizeForExpandedResult(textView: TextView) {
        // Force small font size to maximize space for expanded result text
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, EXPANDED_RESULT_SIZE_SP)
    }


    /**
     * Returns the default maximum text size in SP.
     */
    fun getDefaultTextSizeSp(): Float = MAX_TEXT_SIZE_SP
}
