package com.bos.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * Enabled only by the sender in Android Settings. Later, authenticated BOS viewer input
 * will call these methods after the local encrypted session validates it.
 */
class RemoteControlAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun tap(x: Float, y: Float): Boolean = gesture(pathOf(x, y), 60)

    fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long): Boolean {
        val path = Path().apply { moveTo(fromX, fromY); lineTo(toX, toY) }
        return gesture(path, durationMs.coerceIn(80, 1_500))
    }

    private fun pathOf(x: Float, y: Float) = Path().apply { moveTo(x, y); lineTo(x + 1f, y + 1f) }

    private fun gesture(path: Path, durationMs: Long): Boolean {
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    companion object {
        @Volatile private var instance: RemoteControlAccessibilityService? = null
        fun enabled(): Boolean = instance != null
        fun tap(x: Float, y: Float): Boolean = instance?.tap(x, y) ?: false
        fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long): Boolean =
            instance?.swipe(fromX, fromY, toX, toY, durationMs) ?: false
    }
}
