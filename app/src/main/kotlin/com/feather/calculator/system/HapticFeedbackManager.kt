package com.feather.calculator.system

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Manages haptic feedback for button clicks.
 * Uses the modern VibratorManager API if available, falling back to the legacy Vibrator service.
 */
class HapticFeedbackManager(context: Context) {

    private val vibrator: Vibrator?

    init {
        // Initialize the vibrator service based on the Android version
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        if (vibrator == null) {
            // Log a warning if the service isn't found, though it's rare on modern phones.
            Log.w("Haptics", "Vibrator service not available on this device.")
        }
    }

    /**
     * Triggers a short, predefined haptic effect suitable for a button click.
     */
    fun triggerClickHaptic() {
        if (vibrator?.hasVibrator() == true) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Use a standard, predefined click effect (Android 10+)
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else {
                    // Fallback to a short, simple pulse for older devices
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50) // 50 milliseconds
                }
            } catch (e: Exception) {
                Log.e("Haptics", "Error triggering haptic feedback: ${e.message}")
            }
        }
    }
}