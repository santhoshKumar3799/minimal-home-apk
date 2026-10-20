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
import android.view.ViewGroup
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

// Apple-TV-style home: full-screen "top shelf" wallpaper, 5-column grid of 16:9 app cards.
class MainActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private lateinit var bgA: ImageView
    private lateinit var bgB: ImageView
    private lateinit var clock: TextView
    private lateinit var home: GridLayout
    private lateinit var picker: FrameLayout
    private lateinit var pickerGrid: GridLayout
    private var showingA = true
    private var idx = 0

    private val rotateMs = 120_000L
    private val cols = 5
    private var cardW = 0
    private var cardH = 0

    // Used only when you have not pushed any wallpapers yet.
    private val skies = listOf(
        intArrayOf(0xFF0B3D2E.toInt(), 0xFF2E7D5B.toInt()),
        intArrayOf(0xFF0F2027.toInt(), 0xFF2C5364.toInt()),
        intArrayOf(0xFF3E2C41.toInt(), 0xFFE08E45.toInt()),
        intArrayOf(0xFF1B2A49.toInt(), 0xFF5C7AEA.toInt())
    )

    // First-run dock. Only apps actually installed are used; edit later from "All Apps".
    private val defaults = listOf(
        "com.amazon.firetv.youtube", "com.netflix.ninja", "com.amazon.firebat",
        "in.startv.hotstar", "com.fancode.tv", "tv.airtel.xstream.tvapp", "com.amazon.venezia"
    )

    private val rot = object : Runnable {
        override fun run() { nextBackground(); ui.postDelayed(this, rotateMs) }
    }
    private val tick = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
            ui.postDelayed(this, 20_000)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("home", MODE_PRIVATE)
        val sw = resources.displayMetrics.widthPixels
        val side = dp(64); val gap = dp(28)
        cardW = (sw - 2 * side - (cols - 1) * gap) / cols
        cardH = cardW * 9 / 16
        buildUi()
        refreshHome()
    }

    override fun onResume() {
        super.onResume()
        ui.removeCallbacks(rot); ui.post(rot)
        ui.removeCallbacks(tick); ui.post(tick)
        if (home.focusedChild == null && picker.visibility != View.VISIBLE) home.getChildAt(0)?.requestFocus()
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(rot); ui.removeCallbacks(tick)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (picker.visibility == View.VISIBLE) closePicker() else home.getChildAt(0)?.requestFocus()
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

    private fun grid() = GridLayout(this).apply {
        columnCount = cols
        clipChildren = false
        clipToPadding = false
    }

    // Vertical scroller that never clips the lifted (scaled) focused card.
    private fun scroller(content: View) = ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        clipChildren = false
        clipToPadding = false
        addView(content)
    }

    private fun buildUi() {
        val sh = resources.displayMetrics.heightPixels
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK); clipChildren = false }
        bgA = bg(); bgB = bg(); bgB.alpha = 0f
        root.addView(bgA); root.addView(bgB)

        // Top shelf stays clear; the app area below fades to dark.
        root.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x00000000, 0x33000000, 0xE6000000.toInt())
            )
        }, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x66000000, 0x00000000)
            )
        }, FrameLayout.LayoutParams(-1, dp(140), Gravity.TOP))

        clock = tv(26f, true)
        root.addView(clock, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(32), dp(64), 0)
        })

        home = grid()
        val homeWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, (sh * 0.50).toInt(), 0, dp(48))
            addView(home)
        }
        root.addView(scroller(homeWrap), FrameLayout.LayoutParams(-1, -1))

        picker = FrameLayout(this).apply {
            visibility = View.GONE
            isClickable = true
            setBackgroundColor(0xF2101012.toInt())
        }
        pickerGrid = grid()
        val pickerWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(40), 0, dp(48))
            addView(tv(34f).apply { text = "All Apps" })
            addView(tv(16f).apply {
                text = "OK  add / remove from Home      Back  done"
                alpha = 0.6f
                setPadding(0, dp(4), 0, dp(28))
            })
            addView(pickerGrid)
        }
        picker.addView(scroller(pickerWrap), FrameLayout.LayoutParams(-1, -1))
        root.addView(picker, FrameLayout.LayoutParams(-1, -1))

        setContentView(root)
    }

    // ---------- wallpapers ----------

    // Internal dir is fillable over adb with `run-as` (Android 11 blocks adb from Android/data).
    private fun wallDirs(): List<File> = listOfNotNull(filesDir, getExternalFilesDir(null))
        .map { File(it, "wallpapers").apply { mkdirs() } }

    private fun nextBackground() {
        val files = wallDirs().flatMap { d ->
            d.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp") }?.toList() ?: emptyList()
        }.sortedBy { it.name }
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

    // ---------- cards ----------

    private class Art(val drawable: Drawable, val isBanner: Boolean)

    // Prefer the app's 16:9 TV banner (what Fire TV shows); fall back to its icon.
    private fun art(pkg: String): Art? {
        val pm = packageManager
        val banner = try {
            launchIntent(pkg)?.component?.let { pm.getActivityBanner(it) } ?: pm.getApplicationBanner(pkg)
        } catch (e: Exception) { null }
        if (banner != null) return Art(banner, true)
        return try { Art(pm.getApplicationIcon(pkg), false) } catch (e: Exception) { null }
    }

    private fun glyph(text: String, size: Float): Drawable {
        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        c.drawText(text, 128f, 128f - (p.descent() + p.ascent()) / 2, p)
        return BitmapDrawable(resources, bmp)
    }

    private fun card(
        art: Art, name: String, alwaysLabel: Boolean,
        onClick: (TextView) -> Unit, onLong: (() -> Unit)?
    ): View {
        val face = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (art.isBanner) 0xFF202024.toInt() else 0x40FFFFFF)
            }
            clipToOutline = true
            elevation = dp(4).toFloat()
            addView(ImageView(this@MainActivity).apply {
                setImageDrawable(art.drawable)
                if (art.isBanner) {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                } else {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(cardH / 5, cardH / 5, cardH / 5, cardH / 5)
                }
            }, FrameLayout.LayoutParams(-1, -1))
        }
        val label = tv(15f).apply {
            text = name
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(10), 0, 0)
            alpha = if (alwaysLabel) 0.75f else 0f
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            isFocusable = true
            addView(face, LinearLayout.LayoutParams(cardW, cardH))
            addView(label, LinearLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT))
            setOnClickListener { onClick(label) }
            if (onLong != null) setOnLongClickListener { onLong(); true }
            setOnFocusChangeListener { _, f ->
                val s = if (f) 1.12f else 1f
                face.animate().scaleX(s).scaleY(s).translationZ(if (f) dp(18).toFloat() else 0f)
                    .setDuration(150).start()
                label.animate().alpha(if (f) 1f else if (alwaysLabel) 0.75f else 0f)
                    .translationY(if (f) dp(8).toFloat() else 0f).setDuration(150).start()
            }
        }
    }

    private fun addCell(g: GridLayout, v: View) {
        g.addView(v, GridLayout.LayoutParams().apply {
            width = cardW
            setMargins(dp(14), dp(10), dp(14), dp(22))
        })
    }

    // ---------- home ----------

    private fun refreshHome() {
        home.removeAllViews()
        for (p in favs()) {
            val intent = launchIntent(p) ?: continue
            val a = art(p) ?: continue
            addCell(home, card(a, appLabel(p), false, { launch(intent) }, {
                val l = favs(); l.remove(p); saveFavs(l)
                Toast.makeText(this, "Removed ${appLabel(p)}", Toast.LENGTH_SHORT).show()
                refreshHome()
            }))
        }
        addCell(home, card(Art(glyph("+", 150f), false), "All Apps", false, { openPicker() }, null))
        addCell(home, card(Art(glyph("⚙", 130f), false), "Settings", false, { openSettings() }, null))
        home.getChildAt(0)?.requestFocus()
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

    // ---------- all apps ----------

    private fun openPicker() {
        pickerGrid.removeAllViews()
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

        val current = favs()
        for (r in list) {
            val pkg = r.activityInfo.packageName
            val name = r.loadLabel(packageManager).toString()
            val a = art(pkg) ?: continue
            fun title(on: Boolean) = if (on) "✓  $name" else name
            addCell(pickerGrid, card(a, title(current.contains(pkg)), true, { label ->
                val l = favs()
                if (l.contains(pkg)) l.remove(pkg) else l.add(pkg)
                saveFavs(l)
                label.text = title(l.contains(pkg))
            }, null))
        }
        picker.visibility = View.VISIBLE
        pickerGrid.post { pickerGrid.getChildAt(0)?.requestFocus() }
    }

    private fun closePicker() {
        picker.visibility = View.GONE
        refreshHome()
    }
}
