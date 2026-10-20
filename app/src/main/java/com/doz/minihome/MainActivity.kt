package com.doz.minihome

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private lateinit var bgA: ImageView
    private lateinit var bgB: ImageView
    private lateinit var clock: TextView
    private lateinit var dateTv: TextView
    private lateinit var focusLabel: TextView
    private lateinit var dock: LinearLayout
    private lateinit var picker: FrameLayout
    private lateinit var grid: GridLayout
    private var showingA = true
    private var idx = 0

    private val rotateMs = 120_000L

    // Used only when you have not pushed any wallpapers yet.
    private val skies = listOf(
        intArrayOf(0xFF0B3D2E.toInt(), 0xFF2E7D5B.toInt()),
        intArrayOf(0xFF0F2027.toInt(), 0xFF2C5364.toInt()),
        intArrayOf(0xFF3E2C41.toInt(), 0xFFE08E45.toInt()),
        intArrayOf(0xFF1B2A49.toInt(), 0xFF5C7AEA.toInt())
    )

    // First-run dock. Only apps actually installed are used; edit later from "All apps".
    private val defaults = listOf(
        "com.amazon.firetv.youtube", "com.netflix.ninja", "com.amazon.firebat",
        "in.startv.hotstar", "com.fancode.tv", "tv.airtel.xstream.tvapp", "com.amazon.venezia"
    )

    private val rot = object : Runnable {
        override fun run() { nextBackground(); ui.postDelayed(this, rotateMs) }
    }
    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            clock.text = SimpleDateFormat("h:mm", Locale.getDefault()).format(now)
            dateTv.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
            ui.postDelayed(this, 20_000)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("home", MODE_PRIVATE)
        buildUi()
        refreshDock()
    }

    override fun onResume() {
        super.onResume()
        ui.removeCallbacks(rot); ui.post(rot)
        ui.removeCallbacks(tick); ui.post(tick)
        if (dock.focusedChild == null && picker.visibility != View.VISIBLE) dock.getChildAt(0)?.requestFocus()
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(rot); ui.removeCallbacks(tick)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (picker.visibility == View.VISIBLE) closePicker() else dock.getChildAt(0)?.requestFocus()
    }

    override fun onBackPressed() {
        if (picker.visibility == View.VISIBLE) closePicker()
        // Home screen: Back does nothing.
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(size: Float, light: Boolean = false) = TextView(this).apply {
        setTextColor(Color.WHITE)
        textSize = size
        setShadowLayer(8f, 0f, 2f, 0x99000000.toInt())
        if (light) typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    private fun bg() = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        layoutParams = FrameLayout.LayoutParams(-1, -1)
    }

    private fun ring() = GradientDrawable().apply {
        cornerRadius = dp(22).toFloat()
        setStroke(dp(3), Color.WHITE)
        setColor(0x33FFFFFF)
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK); clipChildren = false }
        bgA = bg(); bgB = bg(); bgB.alpha = 0f
        root.addView(bgA); root.addView(bgB)

        root.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xCC000000.toInt(), 0x00000000)
            )
        }, FrameLayout.LayoutParams(-1, dp(420), Gravity.BOTTOM))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(64), dp(40), 0, 0)
        }
        clock = tv(72f, true); dateTv = tv(20f)
        top.addView(clock); top.addView(dateTv)
        root.addView(top, FrameLayout.LayoutParams(-2, -2))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
        }
        focusLabel = tv(22f)
        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            setPadding(dp(16), dp(20), dp(16), dp(20))
            background = GradientDrawable().apply { cornerRadius = dp(36).toFloat(); setColor(0x66000000) }
        }
        bottom.addView(focusLabel)
        bottom.addView(dock)
        root.addView(bottom, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(48)
        })

        picker = FrameLayout(this).apply {
            visibility = View.GONE
            isClickable = true
            setBackgroundColor(0xEE000000.toInt())
        }
        grid = GridLayout(this).apply {
            columnCount = 6
            setPadding(dp(48), dp(96), dp(48), dp(48))
        }
        picker.addView(ScrollView(this).apply { addView(grid) }, FrameLayout.LayoutParams(-1, -1))
        picker.addView(tv(18f).apply { text = "OK = add / remove from dock     Back = done" },
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(48), dp(40), 0, 0) })
        root.addView(picker, FrameLayout.LayoutParams(-1, -1))

        setContentView(root)
    }

    // ---------- wallpapers ----------

    private fun wallDir(): File = File(getExternalFilesDir(null), "wallpapers").apply { mkdirs() }

    private fun nextBackground() {
        val files = wallDir().listFiles { f ->
            f.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp")
        }?.sortedBy { it.name } ?: emptyList()
        val n = idx++
        if (files.isEmpty()) {
            show(GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, skies[n % skies.size]))
            return
        }
        val f = files[n % files.size]
        thread {
            val bmp = decode(f)
            if (bmp != null) ui.post { show(BitmapDrawable(resources, bmp)) }
        }
    }

    private fun decode(f: File): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= 1920) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply {
            inSampleSize = s
            inPreferredConfig = Bitmap.Config.RGB_565
        })
    } catch (e: Throwable) { null }

    private fun show(d: Drawable) {
        val inV = if (showingA) bgB else bgA
        val outV = if (showingA) bgA else bgB
        inV.setImageDrawable(d)
        inV.alpha = 0f
        inV.animate().alpha(1f).setDuration(1500).start()
        outV.animate().alpha(0f).setDuration(1500).start()
        showingA = !showingA
    }

    // ---------- favourites ----------

    private fun launchIntent(pkg: String): Intent? =
        packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)

    private fun saveFavs(l: List<String>) = prefs.edit().putString("favs", l.joinToString(",")).apply()

    private fun favs(): MutableList<String> {
        val s = prefs.getString("favs", null)
        if (s == null) {
            val d = defaults.filter { launchIntent(it) != null }
            saveFavs(d)
            return d.toMutableList()
        }
        return s.split(",").filter { it.isNotEmpty() }.toMutableList()
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    // ---------- dock ----------

    private fun glyph(text: String): Drawable {
        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0x44FFFFFF
        c.drawRoundRect(0f, 0f, 256f, 256f, 56f, 56f, p)
        p.color = Color.WHITE
        p.textSize = 110f
        p.textAlign = Paint.Align.CENTER
        c.drawText(text, 128f, 128f - (p.descent() + p.ascent()) / 2, p)
        return BitmapDrawable(resources, bmp)
    }

    private fun tile(icon: Drawable, label: String, onClick: () -> Unit, onLong: (() -> Unit)?): View =
        ImageView(this).apply {
            setImageDrawable(icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(96), dp(96)).apply { setMargins(dp(10), 0, dp(10), 0) }
            isFocusable = true
            setOnClickListener { onClick() }
            if (onLong != null) setOnLongClickListener { onLong(); true }
            setOnFocusChangeListener { v, f ->
                val s = if (f) 1.25f else 1f
                v.animate().scaleX(s).scaleY(s).setDuration(120).start()
                v.background = if (f) ring() else null
                if (f) focusLabel.text = label
            }
        }

    private fun refreshDock() {
        dock.removeAllViews()
        for (p in favs()) {
            val intent = launchIntent(p) ?: continue
            val icon = try { packageManager.getApplicationIcon(p) } catch (e: Exception) { continue }
            dock.addView(tile(icon, appLabel(p), { launch(intent) }, {
                val l = favs(); l.remove(p); saveFavs(l)
                Toast.makeText(this, "Removed ${appLabel(p)}", Toast.LENGTH_SHORT).show()
                refreshDock()
            }))
        }
        dock.addView(tile(glyph("+"), "All apps", { openPicker() }, null))
        dock.addView(tile(glyph("..."), "Settings", { openSettings() }, null))
        dock.getChildAt(0)?.requestFocus()
    }

    private fun launch(i: Intent) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { Toast.makeText(this, "Can't open", Toast.LENGTH_SHORT).show() }
    }

    private fun openSettings() {
        val options = listOf(
            Intent(Settings.ACTION_SETTINGS),
            packageManager.getLeanbackLaunchIntentForPackage("com.amazon.tv.settings.v2"),
            packageManager.getLaunchIntentForPackage("com.amazon.tv.settings.v2")
        )
        for (i in options) {
            if (i == null) continue
            try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (e: Exception) { }
        }
        Toast.makeText(this, "Settings not found", Toast.LENGTH_SHORT).show()
    }

    // ---------- app picker ----------

    private fun openPicker() {
        grid.removeAllViews()
        val seen = HashSet<String>()
        val list = ArrayList<ResolveInfo>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (r in packageManager.queryIntentActivities(q, 0)) {
                val pkg = r.activityInfo.packageName
                if (pkg != packageName && seen.add(pkg)) list.add(r)
            }
        }
        list.sortBy { it.loadLabel(packageManager).toString().lowercase() }

        for (r in list) {
            val pkg = r.activityInfo.packageName
            val name = r.loadLabel(packageManager).toString()
            val icon = ImageView(this).apply {
                setImageDrawable(r.loadIcon(packageManager))
                layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
            }
            val label = TextView(this).apply {
                setTextColor(Color.WHITE); textSize = 14f; maxLines = 1
                ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
                text = if (favs().contains(pkg)) "* $name" else name
            }
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                setPadding(dp(8), dp(8), dp(8), dp(8))
                addView(icon); addView(label)
                setOnFocusChangeListener { v, f -> v.background = if (f) ring() else null }
                setOnClickListener {
                    val l = favs()
                    if (l.contains(pkg)) l.remove(pkg) else l.add(pkg)
                    saveFavs(l)
                    label.text = if (l.contains(pkg)) "* $name" else name
                }
            }
            grid.addView(cell, GridLayout.LayoutParams().apply {
                width = dp(170); height = dp(130); setMargins(dp(6), dp(6), dp(6), dp(6))
            })
        }
        picker.visibility = View.VISIBLE
        grid.post { grid.getChildAt(0)?.requestFocus() }
    }

    private fun closePicker() {
        picker.visibility = View.GONE
        refreshDock()
    }
}
