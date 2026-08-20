package com.bos.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * Enabled only by the sender in Android Settings. BOS performs input only for a
 * viewer that has already authenticated against the local session password.
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

    private fun dispatch(path: Path, durationMs: Long): Boolean = try {
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    } catch (_: Throwable) {
        false
    }

    companion object {
        @Volatile private var instance: RemoteControlAccessibilityService? = null

        fun enabled(): Boolean = instance != null

        fun tap(x: Float, y: Float): Boolean {
            val service = instance ?: return false
            val path = Path().apply { moveTo(x, y); lineTo(x + 1f, y + 1f) }
            return service.dispatch(path, 60)
        }

        fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long): Boolean {
            val service = instance ?: return false
            val path = Path().apply { moveTo(fromX, fromY); lineTo(toX, toY) }
            return service.dispatch(path, durationMs.coerceIn(60, 2_000))
        }

        fun longPress(x: Float, y: Float): Boolean {
            val service = instance ?: return false
            val path = Path().apply { moveTo(x, y); lineTo(x + 1f, y + 1f) }
            return service.dispatch(path, 650)
        }

        fun back(): Boolean = global(GLOBAL_ACTION_BACK)
        fun home(): Boolean = global(GLOBAL_ACTION_HOME)
        fun recents(): Boolean = global(GLOBAL_ACTION_RECENTS)
        fun notifications(): Boolean = global(GLOBAL_ACTION_NOTIFICATIONS)
        fun powerDialog(): Boolean = global(GLOBAL_ACTION_POWER_DIALOG)

        fun lockScreen(): Boolean =
            if (Build.VERSION.SDK_INT >= 28) global(GLOBAL_ACTION_LOCK_SCREEN) else false

        private fun global(action: Int): Boolean = try {
            instance?.performGlobalAction(action) ?: false
        } catch (_: Throwable) {
            false
        }
    }
}
