package com.feather.calculator.utils

import android.content.Context
import android.util.DisplayMetrics
import android.util.TypedValue // Added to support dpToPx conversion

/**
 * Utility class for calculating dynamic dimensions that scale proportionally
 * based on the device's screen width relative to a standard base size.
 */
object DynamicDimensionHelper {

    // The reference screen width (in dp) used for the scaling base.
    // 360dp is a common base for mobile devices.
    private const val BASE_WIDTH_DP = 360f

    // --- NEW CONSTANTS REQUIRED BY MainActivity ---

    /**
     * Default text size for the result TextView, used when the result is not expanded.
     * This is set to a base of 40sp for a 360dp screen.
     */
    const val RESULT_DEFAULT_TEXT_SIZE = 40f

    // --- END NEW CONSTANTS ---


    /**
     * Calculates a proportional dimension in pixels (px) based on the device's
     * screen width (in dp) relative to the BASE_WIDTH_DP.
     *
     * Example: If the base dimension is 2dp (for a 360dp screen) and the current
     * screen is 720dp, the returned value will correspond to 4dp.
     *
     * @param context The application context.
     * @param baseDimensionDp The design dimension (e.g., 2f for a 2dp spacing) on the base screen.
     * @return The calculated proportional dimension in pixels (Int).
     */
    fun getProportionalDimensionPx(context: Context, baseDimensionDp: Float): Int {
        val displayMetrics: DisplayMetrics = context.resources.displayMetrics
        val screenWidthPx = displayMetrics.widthPixels
        val density = displayMetrics.density

        // 1. Get the current screen width in DP
        val currentScreenWidthDp = screenWidthPx / density

        // 2. Calculate the scaling factor: (Current Width / Base Width)
        val scaleFactor = currentScreenWidthDp / BASE_WIDTH_DP

        // 3. Apply the scale factor to the base dimension to get the dynamic dimension in DP
        val dynamicDimensionDp = baseDimensionDp * scaleFactor

        // 4. Convert the final dynamic DP value back to Pixels (px)
        // Adding 0.5f ensures correct rounding when converting to Int
        val dynamicDimensionPx = (dynamicDimensionDp * density + 0.5f).toInt()

        return dynamicDimensionPx
    }

    /**
     * Converts density-independent pixels (dp) value to an actual pixel (px) value.
     * This is the function required by MainActivity to size the scientific keys.
     */
    fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    /**
     * Convenience function specifically for the button spacing you requested (2dp base).
     */
    fun getButtonSpacingPx(context: Context): Int {
        // Base spacing is 2dp, as requested for a 360dp screen.
        val BASE_SPACING_DP = 2f
        return getProportionalDimensionPx(context, BASE_SPACING_DP)
    }
}
