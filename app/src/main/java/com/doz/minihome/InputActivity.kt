package com.doz.minihome

import android.app.Activity
import android.graphics.Color
import android.media.tv.TvContract
import android.media.tv.TvView
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.TextView

/** Full-screen HDMI passthrough using the standard TvView. Back returns home. */
class InputActivity : Activity() {
    private lateinit var tv: TvView
    private lateinit var msg: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val ui = Ui(this)
        val id = intent.getStringExtra(EXTRA_ID) ?: return finish()
        val name = intent.getStringExtra(EXTRA_NAME) ?: "HDMI"

        tv = TvView(this).apply { isFocusable = true; isFocusableInTouchMode = true }
        msg = ui.text(TextView(this), 30f, 300, 6f).apply {
            text = "${name.uppercase()}  ·  CONNECTING"
            gravity = Gravity.CENTER
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(tv, FrameLayout.LayoutParams(-1, -1))
            addView(msg, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        })

        tv.setCallback(object : TvView.TvInputCallback() {
            override fun onVideoAvailable(inputId: String?) { msg.alpha = 0f }
            override fun onVideoUnavailable(inputId: String?, reason: Int) {
                msg.alpha = 1f; msg.text = "${name.uppercase()}  ·  NO SIGNAL"
            }
            override fun onConnectionFailed(inputId: String?) {
                msg.alpha = 1f; msg.text = "${name.uppercase()}  ·  CAN'T OPEN THIS INPUT"
            }
            override fun onDisconnected(inputId: String?) {
                msg.alpha = 1f; msg.text = "${name.uppercase()}  ·  DISCONNECTED"
            }
        })
        tv.tune(id, TvContract.buildChannelUriForPassthroughInput(id))
        tv.requestFocus()
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_BACK) {
            if (e.action == KeyEvent.ACTION_UP) finish()
            return true
        }
        return super.dispatchKeyEvent(e)
    }

    override fun onStop() {
        super.onStop()
        if (::tv.isInitialized) tv.reset()
        finish()
    }

    companion object {
        const val EXTRA_ID = "inputId"
        const val EXTRA_NAME = "inputName"
    }
}
