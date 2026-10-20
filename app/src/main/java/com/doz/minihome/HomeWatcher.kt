package com.doz.minihome

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Fire OS won't let another app be Home and won't let its launcher be disabled, and it handles
 * the Home key at system level (accessibility key filtering never sees it).
 *
 * Fast path: the system broadcasts CLOSE_SYSTEM_DIALOGS(reason=homekey) the moment Home is
 * pressed, before Amazon's home has loaded (~3.5 s). Open Minimal Home right then.
 * Fallback: if Amazon's home screen comes to the front anyway (boot, apps that call Home),
 * bring Minimal Home forward.
 *
 * Enable:  settings put secure enabled_accessibility_services com.doz.minihome/com.doz.minihome.HomeWatcher
 */
class HomeWatcher : AccessibilityService() {
    private val homeKey = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.getStringExtra("reason") == "homekey" && SystemClock.uptimeMillis() >= pauseUntil) openHome()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerReceiver(homeKey, IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
    }

    override fun onDestroy() {
        try { unregisterReceiver(homeKey) } catch (e: Exception) { }
        super.onDestroy()
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

    override fun onInterrupt() {}

    companion object {
        const val AMAZON_LAUNCHER = "com.amazon.tv.launcher"

        // Fire OS Settings lives in the Amazon launcher and passes through its HomeActivity on
        // the way in, so stand down briefly when we open it ourselves.
        @Volatile private var pauseUntil = 0L
        fun pause(ms: Long) { pauseUntil = SystemClock.uptimeMillis() + ms }
    }
}
