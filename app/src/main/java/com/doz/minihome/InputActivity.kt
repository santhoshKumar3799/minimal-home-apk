package com.doz.minihome

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.tv.TvContract
import android.media.tv.TvView
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Full-screen HDMI passthrough using the standard TvView. Back returns home.
 *
 * Like Amazon's own HDMI player, it keeps the display awake while the input has a picture
 * (a console is played with its own controller, so the TV sees no remote activity and would
 * otherwise sleep after screen_off_timeout and CEC-standby the console). With no signal the
 * flag is dropped so the TV can still sleep normally. If the TV sleeps anyway, the input is
 * remembered and reopened on wake.
 */
class InputActivity : Activity() {
    private var tv: TvView? = null
    private lateinit var msg: TextView
    private lateinit var id: String
    private lateinit var name: String

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val ui = Ui(this)
        id = intent.getStringExtra(EXTRA_ID) ?: return finish()
        name = intent.getStringExtra(EXTRA_NAME) ?: "HDMI"
        clearResume(this)

        msg = ui.text(TextView(this), 30f, 300, 6f).apply { gravity = Gravity.CENTER }
        val view = TvView(this).apply { isFocusable = true; isFocusableInTouchMode = true }
        tv = view
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(view, FrameLayout.LayoutParams(-1, -1))
            addView(msg, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        })

        view.setCallback(object : TvView.TvInputCallback() {
            override fun onVideoAvailable(inputId: String?) { msg.alpha = 0f; keepAwake(true) }
            override fun onVideoUnavailable(inputId: String?, reason: Int) { status("NO SIGNAL"); keepAwake(false) }
            override fun onConnectionFailed(inputId: String?) { status("CAN'T OPEN THIS INPUT"); keepAwake(false) }
            override fun onDisconnected(inputId: String?) { status("DISCONNECTED"); keepAwake(false) }
        })
    }

    override fun onStart() {
        super.onStart()
        clearResume(this)
        status("CONNECTING")
        tv?.tune(id, TvContract.buildChannelUriForPassthroughInput(id))
        tv?.requestFocus()
    }

    override fun onStop() {
        super.onStop()
        keepAwake(false)
        tv?.reset()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) {
            // TV went to sleep while on this input: come back here when it wakes.
            rememberForWake(this, id, name)
        } else if (!isChangingConfigurations) {
            finish()   // user left (Home/Back/another app)
        }
    }

    private fun status(s: String) { msg.alpha = 1f; msg.text = "${name.uppercase()}  ·  $s" }

    private fun keepAwake(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_BACK) {
            if (e.action == KeyEvent.ACTION_UP) { clearResume(this); finish() }
            return true
        }
        return super.dispatchKeyEvent(e)
    }

    companion object {
        const val EXTRA_ID = "inputId"
        const val EXTRA_NAME = "inputName"
        private const val PREFS = "input"
        private const val RESUME_WINDOW_MS = 6 * 60 * 60 * 1000L   // forget after 6 h asleep

        fun intent(ctx: Context, id: String, name: String) = Intent(ctx, InputActivity::class.java)
            .putExtra(EXTRA_ID, id).putExtra(EXTRA_NAME, name)

        private fun rememberForWake(ctx: Context, id: String, name: String) {
            ctx.getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("id", id).putString("name", name)
                .putLong("at", SystemClock.elapsedRealtime()).apply()
        }

        fun clearResume(ctx: Context) = ctx.getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply()

        /** If the TV slept while showing an input, reopen it. Returns true if it did. */
        fun resumeAfterWake(ctx: Context): Boolean {
            val p = ctx.getSharedPreferences(PREFS, MODE_PRIVATE)
            val id = p.getString("id", null) ?: return false
            val name = p.getString("name", "HDMI") ?: "HDMI"
            val fresh = SystemClock.elapsedRealtime() - p.getLong("at", 0) < RESUME_WINDOW_MS
            clearResume(ctx)
            if (!fresh) return false
            ctx.startActivity(intent(ctx, id, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        }
    }
}
