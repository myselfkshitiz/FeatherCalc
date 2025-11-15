package com.feather.calculator.custom_views

import android.animation.ArgbEvaluator
import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat

// Import R for custom attributes
import com.feather.calculator.R 
// Alias for theme attributes:
import androidx.appcompat.R as AppCompatR // For primary, primaryDark
import com.google.android.material.R as MaterialR // For colorSurface, colorAccent, colorButtonNormal, etc.

// Enum to define the button's role, matching the values in attrs.xml
enum class ButtonType {
    DIGIT, CLEAR, SECONDARY, TERTIARY
}

/**
 * Data class to hold the color scheme for a button state.
 */
private data class ButtonColors(
    val defaultContainerColorAttr: Int,
    val defaultContainerColorFallback: Int,
    val colorOnDefaultContainerAttr: Int,
    val colorOnDefaultContainerFallback: Int,
    val primaryColorAttr: Int, // Attribute for the Pressed/Active state background
    val colorOnPrimaryAttr: Int, // Attribute for the Pressed/Active state glyph
    val colorOnPrimaryFallback: Int // Fallback for the Pressed/Active state glyph
)

// CRITICAL FIX: The entire button logic is now self-contained in this one class.
class MorphButton @JvmOverloads constructor(
    context: Context, 
    attrs: AttributeSet? = null, 
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var cornerRadius = 0f

    protected var digitDrawable: Drawable? = null
    // DEFAULT: 0.70f - This value is now solely set here or overridden by XML.
    protected var drawableSizeRatio = 0.70f 

    private val cornerAnimator = ValueAnimator().apply {
        interpolator = DecelerateInterpolator()
        addUpdateListener { valueAnimator ->
            cornerRadius = valueAnimator.animatedValue as Float
            invalidate()
        }
    }

    private var colorAnimator: ValueAnimator? = null
    private var isLongPressing = false
    private var isColorAnimating = false 
    
    // --- Internal Color Configuration ---
    private var _defaultContainerColor: Int = 0
    private var _colorOnDefaultContainer: Int = 0
    // primaryColorAttr remains androidx.appcompat.R.attr.colorPrimary (aliased as AppCompatR)
    private var _primaryColorAttr: Int = AppCompatR.attr.colorPrimary 
    private var _colorOnPrimary: Int = 0
    private var buttonType: ButtonType = ButtonType.DIGIT
    
    // Accessors for colors used in drawing/animation
    private val defaultContainerColor: Int get() = _defaultContainerColor
    private val colorOnDefaultContainer: Int get() = _colorOnDefaultContainer
    private val resolvedPrimaryColor: Int get() = resolveColorAttribute(_primaryColorAttr, 0xFF6200EE.toInt())
    private val colorOnPrimary: Int get() = _colorOnPrimary
    
    // --- Theme Helpers ---
    private fun isDarkTheme(): Boolean {
        return context.resources.configuration.uiMode and
               Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    // Failsafe method to resolve theme attributes
    private fun resolveColorAttribute(attrId: Int, defaultColor: Int): Int {
        val typedValue = context.obtainStyledAttributes(intArrayOf(attrId))
        try {
            return typedValue.getColor(0, defaultColor)
        } finally {
            typedValue.recycle()
        }
    }
    
    // --- Color Loader based on ButtonType (Refactored) ---
    private fun loadColorsForType(type: ButtonType) {
        
        // Define common fallback colors (used across multiple types)
        // Fallback colors removed for brevity, as they are not used if attributes resolve

        // Use R.attr or AppCompatR.attr or MaterialR.attr for all attribute lookups
        val COLOR_CONFIGS = mapOf(
            ButtonType.CLEAR to ButtonColors(
                defaultContainerColorAttr = MaterialR.attr.colorPrimaryContainer,
                defaultContainerColorFallback = 0, // no fallback
                colorOnDefaultContainerAttr = MaterialR.attr.colorOnPrimaryContainer,
                colorOnDefaultContainerFallback = 0,
                primaryColorAttr = AppCompatR.attr.colorPrimary,
                colorOnPrimaryAttr = MaterialR.attr.colorOnPrimary,
                colorOnPrimaryFallback = 0
            ),

            ButtonType.SECONDARY to ButtonColors(
                defaultContainerColorAttr = MaterialR.attr.colorSecondaryContainer,
                defaultContainerColorFallback = 0,
                colorOnDefaultContainerAttr = MaterialR.attr.colorOnSecondaryContainer,
                colorOnDefaultContainerFallback = 0,
                primaryColorAttr = MaterialR.attr.colorSecondary,
                colorOnPrimaryAttr = MaterialR.attr.colorOnSecondary,
                colorOnPrimaryFallback = 0
            ),

            ButtonType.TERTIARY to ButtonColors(
                defaultContainerColorAttr = MaterialR.attr.colorTertiary,
                defaultContainerColorFallback = 0,
                colorOnDefaultContainerAttr = MaterialR.attr.colorOnTertiary,
                colorOnDefaultContainerFallback = 0,
                primaryColorAttr = MaterialR.attr.colorTertiary,
                colorOnPrimaryAttr = MaterialR.attr.colorOnTertiary,
                colorOnPrimaryFallback = 0
            ),

            ButtonType.DIGIT to ButtonColors(
                defaultContainerColorAttr = if (isDarkTheme())
                    MaterialR.attr.colorSurfaceContainerHigh
                else
                    MaterialR.attr.colorSurfaceContainerHighest,

                defaultContainerColorFallback = 0,
                colorOnDefaultContainerAttr = MaterialR.attr.colorOnSurface,
                colorOnDefaultContainerFallback = 0,
                primaryColorAttr = AppCompatR.attr.colorPrimary,
                colorOnPrimaryAttr = MaterialR.attr.colorOnPrimary,
                colorOnPrimaryFallback = 0
            )
        )
        
        val config = COLOR_CONFIGS[type] ?: COLOR_CONFIGS[ButtonType.DIGIT]!!

        // Apply resolved colors
        _defaultContainerColor = resolveColorAttribute(config.defaultContainerColorAttr, config.defaultContainerColorFallback)
        _colorOnDefaultContainer = resolveColorAttribute(config.colorOnDefaultContainerAttr, config.colorOnDefaultContainerFallback)
        _primaryColorAttr = config.primaryColorAttr
        _colorOnPrimary = resolveColorAttribute(config.colorOnPrimaryAttr, config.colorOnPrimaryFallback)
        
        // --- REMOVED: Type-based drawableSizeRatio assignment has been removed here. ---
    }

    init {
        // 1. Read the custom XML attributes using the assumed R.styleable
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.MorphButton, defStyleAttr, 0)
        
        val typeInt = typedArray.getInt(R.styleable.MorphButton_buttonType, 0) 
        buttonType = ButtonType.values().getOrElse(typeInt) { ButtonType.DIGIT }
        
        // 2. Load and apply the colors based on the type
        loadColorsForType(buttonType)
        
        // 3. Read the drawableSizeRatio, overriding the initial default (0.70f) if specified
        // The default value of getFloat is now the initial value (0.70f)
        val customRatio = typedArray.getFloat(R.styleable.MorphButton_drawableSizeRatio, drawableSizeRatio) 
        if (customRatio > 0.0f) {
            // Clamp the value to a sensible range
            drawableSizeRatio = customRatio.coerceIn(0.1f, 1.0f) 
        }

        typedArray.recycle()
        
        paint.color = defaultContainerColor
        isClickable = true 
    }

    fun setDigit(resId: Int) {
        digitDrawable = ContextCompat.getDrawable(context, resId)
        digitDrawable?.setTint(colorOnDefaultContainer) 
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        // 1. Draw the rounded background
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        // 2. Draw the Digit Drawable with Aspect Ratio Fix (Responsive Scaling)
        digitDrawable?.let { drawable ->
            val centerX = width / 2
            val centerY = height / 2
            // Use the now XML/default controlled ratio
            val maxDrawableSize = (width * drawableSizeRatio).toInt() 
            val intrinsicWidth = drawable.intrinsicWidth
            val intrinsicHeight = drawable.intrinsicHeight

            var finalWidth = maxDrawableSize
            var finalHeight = maxDrawableSize
            
            if (intrinsicWidth > 0 && intrinsicHeight > 0) {
                val drawableAspectRatio = intrinsicWidth.toFloat() / intrinsicHeight.toFloat()
                if (drawableAspectRatio > 1) { 
                    finalHeight = (finalWidth / drawableAspectRatio).toInt()
                } else if (drawableAspectRatio < 1) { 
                    finalWidth = (finalHeight * drawableAspectRatio).toInt()
                }
            }
            
            finalWidth = finalWidth.coerceAtMost(maxDrawableSize)
            finalHeight = finalHeight.coerceAtMost(maxDrawableSize)

            val left = centerX - finalWidth / 2
            val top = centerY - finalHeight / 2
            val right = centerX + finalWidth / 2
            val bottom = centerY + finalHeight / 2

            drawable.setBounds(left, top, right, bottom)
            drawable.draw(canvas)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = MeasureSpec.getSize(widthMeasureSpec).coerceAtMost(MeasureSpec.getSize(heightMeasureSpec))
        setMeasuredDimension(size, size)
        cornerRadius = size / 2f
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val circle = width / 2f
        val rounded = width * 0.25f
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isLongPressing = true
                colorAnimator?.cancel()
                cornerAnimator.cancel()
                animateCorner(rounded, 500)
                
                digitDrawable?.setTint(colorOnPrimary) 

                if (paint.color != resolvedPrimaryColor) {
                    animateColor(paint.color, resolvedPrimaryColor, 500, false) 
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isLongPressing) {
                    isLongPressing = false

                    if (event.eventTime - event.downTime < 200) {
                        
                        cornerAnimator.cancel()
                        cornerAnimator.setFloatValues(cornerRadius, rounded * 0.8f, circle) 
                        cornerAnimator.duration = 500
                        cornerAnimator.start()

                        colorAnimator?.cancel()
                        
                        val startColor = resolvedPrimaryColor 
                        val textStartColor = colorOnPrimary 
                        val textEndColor = colorOnDefaultContainer

                        colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), startColor, defaultContainerColor).apply {
                            this.duration = 500L
                            addUpdateListener { animator ->
                                paint.color = animator.animatedValue as Int
                                
                                val fraction = animator.animatedFraction
                                val animatedForegroundColor = ArgbEvaluator().evaluate(fraction, textStartColor, textEndColor) as Int
                                digitDrawable?.setTint(animatedForegroundColor) 

                                invalidate()
                            }
                            
                            addListener(object : Animator.AnimatorListener {
                                override fun onAnimationStart(animation: Animator) { isColorAnimating = true }
                                override fun onAnimationEnd(animation: Animator) { isColorAnimating = false }
                                override fun onAnimationCancel(animation: Animator) { isColorAnimating = false }
                                override fun onAnimationRepeat(animation: Animator) {}
                            })
                            start()
                        }
                        
                        performClick() 
                        
                    } else {
                        animateCorner(circle, 500)

                        colorAnimator?.cancel() 
                        if (paint.color != defaultContainerColor) {
                            animateColor(paint.color, defaultContainerColor, 500, true)
                        }
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                isLongPressing = false
                animateCorner(circle, 500)
                colorAnimator?.cancel() 
                animateColor(paint.color, defaultContainerColor, 500, true)
            }
        }
        return true
    }

    private fun animateCorner(target: Float, duration: Long) {
        cornerAnimator.cancel()
        cornerAnimator.setFloatValues(cornerRadius, target)
        cornerAnimator.duration = duration
        cornerAnimator.start()
    }

    private fun animateColor(from: Int, to: Int, duration: Long, shouldAnimateGlyph: Boolean = true) {
        colorAnimator?.cancel()
        isColorAnimating = true

        val textFromColor = if (shouldAnimateGlyph) {
            if (from == resolvedPrimaryColor) colorOnPrimary else colorOnDefaultContainer
        } else {
            colorOnPrimary 
        }
        
        val textToColor = if (to == resolvedPrimaryColor) colorOnPrimary else colorOnDefaultContainer 

        colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            this.duration = duration
            addUpdateListener { animator ->
                val animatedBackgroundColor = animator.animatedValue as Int
                paint.color = animatedBackgroundColor
                
                if (shouldAnimateGlyph) {
                    digitDrawable?.let { drawable ->
                        val fraction = animator.animatedFraction
                        val animatedForegroundColor = ArgbEvaluator().evaluate(fraction, textFromColor, textToColor) as Int
                        drawable.setTint(animatedForegroundColor) 
                    }
                }
                
                invalidate()
            }
            
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) {
                    isColorAnimating = false
                    if (shouldAnimateGlyph) {
                         digitDrawable?.setTint(textToColor)
                    }
                }
                override fun onAnimationCancel(animation: Animator) {
                    isColorAnimating = false
                }
                override fun onAnimationRepeat(animation: Animator) {}
            })
            start()
        }
    }
}
