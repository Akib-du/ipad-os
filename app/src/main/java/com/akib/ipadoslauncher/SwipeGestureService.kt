package com.akib.ipadoslauncher

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Provides an optional iPad-like bottom gesture handle.
 * Swiping the handle up opens Android's own Recents screen, where the user
 * can swipe an app card away. Android does not permit ordinary launchers to
 * force-stop other apps directly.
 */
class SwipeGestureService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var gestureHandle: View? = null
    private var startY = 0f
    private var recentsOpened = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addGestureHandle()
    }

    private fun addGestureHandle() {
        if (gestureHandle != null) return

        val density = resources.displayMetrics.density
        val handle = View(this).apply {
            contentDescription = "Swipe up to open Recent Apps; tap to go Home"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f * density
                setColor(0xFFF4F5FA.toInt())
            }
            alpha = 0.88f
            setOnClickListener {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = event.rawY
                        recentsOpened = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val swipeDistance = startY - event.rawY
                        if (!recentsOpened && swipeDistance > 34f * density) {
                            recentsOpened = true
                            performGlobalAction(GLOBAL_ACTION_RECENTS)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!recentsOpened) view.performClick()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> true
                    else -> true
                }
            }
        }

        val params = WindowManager.LayoutParams(
            (72f * density).toInt(),
            (5f * density).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (10f * density).toInt()
        }

        runCatching { windowManager?.addView(handle, params) }
            .onSuccess { gestureHandle = handle }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        gestureHandle?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        gestureHandle = null
        windowManager = null
        super.onDestroy()
    }
}
