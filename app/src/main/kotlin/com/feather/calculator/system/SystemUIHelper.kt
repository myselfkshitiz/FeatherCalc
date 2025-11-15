package com.feather.calculator.system

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.content.res.Configuration
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * A highly reusable helper for managing system UI (Status Bar and Navigation Bar).
 * It enables immersive, edge-to-edge mode, automatically handles icon colors based on
 * system Night Mode, and includes the universal OEM navigation bar scrim workaround.
 */
class SystemUIHelper(private val activity: Activity) {

    /**
     * Applies the complete immersive, edge-to-edge mode setup.
     */
    fun applyImmersiveMode() {
        val window = activity.window

        // 1. Enable Edge-to-Edge: Content extends under system bars.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 2. Set Soft Input Mode: Content resizes when the keyboard appears.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // 3. Set Status Bar color to fully transparent.
        window.statusBarColor = Color.TRANSPARENT

        // 4. Set Navigation Bar color using the universal OEM scrim workaround (near-transparent black).
        window.navigationBarColor = Color.argb(1, 0, 0, 0)

        // 5. Disable contrast enforcement on API 31+ for a clean look.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.isNavigationBarContrastEnforced = false
        }

        // 6. Update icon appearance based on system mode.
        val windowController = WindowCompat.getInsetsController(window, window.decorView)
        applySystemBarIconAppearance(windowController)
    }

    /**
     * Public method to manually set the system bar icon appearance.
     */
    fun applySystemBarIconAppearanceManual(useLightIcons: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val windowController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)

        // isAppearanceLight... = true means icons are dark (light appearance).
        windowController.isAppearanceLightStatusBars = !useLightIcons
        windowController.isAppearanceLightNavigationBars = !useLightIcons
    }

    /**
     * Internal function to set status/navigation bar icon appearance based on Night Mode.
     */
    private fun applySystemBarIconAppearance(windowController: WindowInsetsControllerCompat) {
        val isNight = isNightMode()

        // If it's NOT night mode (Light Mode), we need dark icons (isAppearanceLight... = true)
        windowController.isAppearanceLightStatusBars = !isNight
        windowController.isAppearanceLightNavigationBars = !isNight
    }

    /**
     * Checks if the device is currently in Night Mode (Dark Theme).
     */
    private fun isNightMode(): Boolean {
        val currentNightMode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Applies padding to the content view equal to the system bar heights (status and nav bars).
     */
    fun applyPaddingToContent(contentView: View) {
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { view, windowInsets ->

            // Get the insets for the System Bars (Status Bar + Navigation Bar)
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.setPadding(
                insets.left,
                insets.top,
                insets.right,
                insets.bottom
            )

            // Consume the insets so they are not propagated further down the view hierarchy
            WindowInsetsCompat.CONSUMED
        }
    }
}