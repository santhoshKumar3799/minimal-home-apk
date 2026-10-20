package com.doz.minihome

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Fire OS won't let another app be Home and won't let its launcher be disabled.
 * This service sees Amazon's home screen come to the front (Home button, boot, app exit)
 * and immediately brings Minimal Home forward instead.
 * Enable:  settings put secure enabled_accessibility_services com.doz.minihome/com.doz.minihome.HomeWatcher
 */
class HomeWatcher : AccessibilityService() {
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (e.packageName?.toString() != AMAZON_LAUNCHER) return
        if (e.className?.toString()?.contains("HomeActivity") != true) return
        startActivity(Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }

    override fun onInterrupt() {}

    companion object { const val AMAZON_LAUNCHER = "com.amazon.tv.launcher" }
}
