package com.doz.minihome

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Fire OS won't let another app be Home and won't let its launcher be disabled.
 *
 * Fast path: catch the remote's Home key before Fire OS acts on it and open Minimal Home
 * directly (no Amazon home flash, no wait). A long press still opens Amazon's quick menu.
 * Fallback: if Amazon's home screen comes to the front anyway (boot, apps that call Home),
 * bring Minimal Home forward.
 *
 * Enable:  settings put secure enabled_accessibility_services com.doz.minihome/com.doz.minihome.HomeWatcher
 */
class HomeWatcher : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private var homeDown = false
    private var longFired = false
    private val longPress = Runnable { longFired = true; openQuickMenu() }

    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode != KeyEvent.KEYCODE_HOME) return false
        when (e.action) {
            KeyEvent.ACTION_DOWN -> if (e.repeatCount == 0) {
                homeDown = true; longFired = false
                main.postDelayed(longPress, LONG_PRESS_MS)
            }
            KeyEvent.ACTION_UP -> {
                main.removeCallbacks(longPress)
                if (homeDown && !longFired) openHome()
                homeDown = false
            }
        }
        return true   // Fire OS never sees Home, so its launcher never starts
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (e.packageName?.toString() != AMAZON_LAUNCHER) return
        if (SystemClock.uptimeMillis() < pauseUntil) return
        if (e.className?.toString()?.contains("HomeActivity") != true) return
        openHome()
    }

    private fun openHome() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }

    private fun openQuickMenu() {
        try {
            sendBroadcast(Intent("com.amazon.tv.action.HOME_LONGPRESSED").setPackage("com.amazon.tv.quicksettings.ui"))
        } catch (e: Exception) { }
    }

    override fun onInterrupt() {}

    companion object {
        const val AMAZON_LAUNCHER = "com.amazon.tv.launcher"
        private const val LONG_PRESS_MS = 600L

        // Fire OS Settings lives in the Amazon launcher and passes through its HomeActivity on
        // the way in, so stand down briefly when we open it ourselves.
        @Volatile private var pauseUntil = 0L
        fun pause(ms: Long) { pauseUntil = SystemClock.uptimeMillis() + ms }
    }
}
