package com.doz.minihome

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
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

// Bond-themed glass home: rotating film-still wallpaper, big clock, frosted dock with apps,
// HDMI inputs and system tiles, cold-neon focus, camera-aperture intro.
class MainActivity : Activity() {

    private val ui by lazy { Ui(this) }
    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private lateinit var wallA: ImageView
    private lateinit var wallB: ImageView
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var dock: GlassDock
    private lateinit var label: TextView
    private lateinit var caption: TextView
    private lateinit var picker: FrameLayout
    private lateinit var pickerGrid: GridLayout
    private var showingA = true
    private var idx = 0
    private var labelPlaced = false

    private val rotateMs = 90_000L

    // Used only when no wallpapers have been pushed yet.
    private val skies = listOf(
        intArrayOf(0xFF0B1420.toInt(), 0xFF1E3550.toInt()),
        intArrayOf(0xFF101114.toInt(), 0xFF2A2D33.toInt())
    )

    // First-run dock. Only apps actually installed are used; edit later from "All Apps".
    private val defaults = listOf(
        "com.amazon.firetv.youtube", "com.netflix.ninja", "com.amazon.firebat",
        "in.startv.hotstar", "com.fancode.tv", "tv.airtel.xstream.tvapp", "com.amazon.venezia"
    )

    private val rot = object : Runnable {
        override fun run() { nextBackground(); main.postDelayed(this, rotateMs) }
    }
    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            clock.text = SimpleDateFormat("h:mm", Locale.getDefault()).format(now)
            date.text = SimpleDateFormat("EEEE  ·  d MMMM", Locale.ENGLISH).format(now).uppercase()
            main.postDelayed(this, 20_000)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("home", MODE_PRIVATE)
        val root = buildUi()
        refreshDock()
        if (b == null) {
            val iris = IrisView(this, ui)
            root.addView(iris, FrameLayout.LayoutParams(-1, -1))
            main.postDelayed({ iris.play { } }, 450)
        }
    }

    override fun onResume() {
        super.onResume()
        main.removeCallbacks(rot); main.post(rot)
        main.removeCallbacks(tick); main.post(tick)
        if (dock.focusedChild == null && picker.visibility != View.VISIBLE) focusFirst()
    }

    override fun onPause() {
        super.onPause()
        main.removeCallbacks(rot); main.removeCallbacks(tick)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (picker.visibility == View.VISIBLE) closePicker() else focusFirst()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (picker.visibility == View.VISIBLE) closePicker()
        // Home screen: Back does nothing.
    }

    private fun focusFirst() { dock.getChildAt(0)?.requestFocus() }

    // ---------- layout ----------

    private fun buildUi(): FrameLayout {
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF070708.toInt()); clipChildren = false }

        wallA = wall(); wallB = wall(); wallB.alpha = 0f
        root.addView(wallA); root.addView(wallB)
        root.addView(GradeView(this), FrameLayout.LayoutParams(-1, -1))
        for (g in listOf(Gravity.TOP, Gravity.BOTTOM))
            root.addView(View(this).apply { setBackgroundColor(Color.BLACK); alpha = .55f },
                FrameLayout.LayoutParams(-1, ui.px(18f), g))

        val clockBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clock = ui.text(TextView(this), 132f, 200, -2f).apply {
            setShadowLayer(ui.pxf(30f), 0f, ui.pxf(4f), 0x99000000.toInt())
        }
        date = ui.text(TextView(this), 24f, 300, 7f).apply {
            alpha = .85f
            setPadding(0, ui.px(18f), 0, 0)
            setShadowLayer(ui.pxf(12f), 0f, ui.pxf(2f), 0x99000000.toInt())
        }
        clockBox.addView(clock); clockBox.addView(date)
        root.addView(clockBox, FrameLayout.LayoutParams(-2, -2).apply {
            leftMargin = ui.px(88f); topMargin = ui.px(70f)
        })

        label = ui.text(TextView(this), 19f, 400, 3f).apply {
            setPadding(ui.px(22f), ui.px(11f), ui.px(22f), ui.px(9f))
            background = GradientDrawable().apply {
                cornerRadius = ui.pxf(999f); setColor(0x8C0A0A0C.toInt()); setStroke(1, 0x29FFFFFF)
            }
            alpha = 0f
        }
        root.addView(label, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START))

        dock = GlassDock(this, ui).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        root.addView(dock, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = ui.px(70f)
        })

        caption = ui.text(TextView(this), 13f, 400, 5f).apply { text = "INPUTS"; alpha = .75f }
        root.addView(caption, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply {
            bottomMargin = ui.px(34f)
        })

        picker = FrameLayout(this).apply {
            visibility = View.GONE
            isClickable = true
            setBackgroundColor(0xEB070708.toInt())
        }
        pickerGrid = GridLayout(this).apply { columnCount = 8; clipChildren = false; clipToPadding = false }
        val pickerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false; clipToPadding = false
            setPadding(0, ui.px(70f), 0, ui.px(80f))
            addView(ui.text(TextView(this@MainActivity), 40f, 200, 8f).apply { text = "ALL APPS" })
            addView(ui.text(TextView(this@MainActivity), 15f, 400, 4f).apply {
                text = "OK  ADD / REMOVE FROM DOCK        BACK  DONE"
                alpha = .6f
                setPadding(0, ui.px(16f), 0, ui.px(50f))
            })
            addView(pickerGrid)
        }
        picker.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false; clipChildren = false; clipToPadding = false
            addView(pickerCol, FrameLayout.LayoutParams(-1, -2))
        }, FrameLayout.LayoutParams(-1, -1))
        root.addView(picker, FrameLayout.LayoutParams(-1, -1))

        setContentView(root)
        return root
    }

    private fun wall() = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        layoutParams = FrameLayout.LayoutParams(-1, -1)
    }

    /** Cinematic grade: vignette plus top and bottom fades. */
    private class GradeView(ctx: Context) : View(ctx) {
        private val vignette = Paint()
        private val fade = Paint()
        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            vignette.shader = RadialGradient(w / 2f, h * 0.4f, maxOf(w, h) * 0.62f,
                intArrayOf(0x00000000, 0x00000000, 0x8C000000.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            fade.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                intArrayOf(0x73000000, 0x00000000, 0x00000000, 0xD1000000.toInt()),
                floatArrayOf(0f, 0.22f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
        override fun onDraw(c: Canvas) {
            c.drawPaint(vignette); c.drawPaint(fade)
        }
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
            dock.setBlur(null)
            return
        }
        val f = files[n % files.size]
        thread {
            val bmp = decode(f) ?: return@thread
            val blur = try { blurSmall(Bitmap.createScaledBitmap(bmp, 160, 90, true)) } catch (e: Throwable) { null }
            main.post { show(BitmapDrawable(resources, bmp)); dock.setBlur(blur) }
        }
    }

    private fun decode(f: File): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= 1920) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (e: Throwable) { null }

    private fun show(d: Drawable) {
        val inV = if (showingA) wallB else wallA
        val outV = if (showingA) wallA else wallB
        inV.setImageDrawable(d)
        inV.alpha = 0f
        inV.scaleX = 1.04f; inV.scaleY = 1.04f
        inV.animate().alpha(1f).setDuration(1600).start()
        inV.animate().scaleX(1.12f).scaleY(1.12f).setDuration(22_000).start()
        outV.animate().alpha(0f).setDuration(1600).start()
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

    // ---------- HDMI inputs ----------

    private class Hdmi(val id: String, val port: String, val name: String?)

    private fun hdmiInputs(): List<Hdmi> = try {
        val tim = getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
        tim.tvInputList
            .filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null && !it.isHidden(this) }
            .sortedBy { it.id }
            .mapIndexed { i, info ->
                val custom = info.loadCustomLabel(this)?.toString()?.takeIf { it.isNotBlank() }
                Hdmi(info.id, "HDMI ${i + 1}", custom)
            }
    } catch (e: Exception) { emptyList() }

    // ---------- tiles ----------

    private inner class Tile(size: Int, glassy: Boolean, fill: (FrameLayout) -> Unit) {
        val cell = FrameLayout(this@MainActivity).apply { clipChildren = false; clipToPadding = false }
        private val glow: NeonGlow
        private val face = FrameLayout(this@MainActivity)

        init {
            val corner = size * 26f / 104f
            val pad = ui.px(64f)
            glow = NeonGlow(this@MainActivity, ui, pad, corner)
            cell.addView(glow, FrameLayout.LayoutParams(size + 2 * pad, size + 2 * pad).apply {
                setMargins(-pad, -pad, -pad, -pad)
            })
            face.background = if (glassy) GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x29FFFFFF, 0x0DFFFFFF)).apply { cornerRadius = corner; setStroke(1, 0x33FFFFFF) }
            else GradientDrawable().apply { cornerRadius = corner; setColor(0xFF1B1D21.toInt()) }
            face.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, corner)
            }
            face.clipToOutline = true
            face.elevation = ui.pxf(8f)
            fill(face)
            face.foreground = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0x38FFFFFF, 0x00FFFFFF, 0x00FFFFFF)).apply { cornerRadius = corner }
            cell.addView(face, FrameLayout.LayoutParams(size, size))
        }

        fun focus(f: Boolean) {
            cell.animate().translationY(if (f) -ui.pxf(16f) else 0f)
                .scaleX(if (f) 1.14f else 1f).scaleY(if (f) 1.14f else 1f)
                .setInterpolator(OvershootInterpolator(1.2f)).setDuration(220).start()
            cell.translationZ = if (f) ui.pxf(20f) else 0f
            glow.animate().alpha(if (f) 1f else 0f).setDuration(200).start()
        }
    }

    private fun appTile(pkg: String, size: Int): Tile? {
        val icon = try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { return null }
        val iw = icon.intrinsicWidth; val ih = icon.intrinsicHeight
        val square = iw <= 0 || ih <= 0 || iw.toFloat() / ih in 0.85f..1.18f
        return Tile(size, false) { face ->
            // Wide/tall icons would be cropped; fit them on their own edge colour instead.
            if (!square) (face.background as? GradientDrawable)?.setColor(edgeColor(icon))
            face.addView(ImageView(this).apply {
                setImageDrawable(icon)
                scaleType = if (square) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            }, FrameLayout.LayoutParams(-1, -1))
        }
    }

    private fun edgeColor(d: Drawable): Int = try {
        val b = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        val old = d.copyBounds()
        d.setBounds(0, 0, 24, 24); d.draw(Canvas(b)); d.bounds = old
        val c = b.getPixel(1, 12); b.recycle()
        if (Color.alpha(c) < 200) 0xFF1B1D21.toInt() else c or 0xFF000000.toInt()
    } catch (e: Exception) { 0xFF1B1D21.toInt() }

    private fun glassTile(size: Int, kind: LineIcon.Kind, port: String?) = Tile(size, true) { face ->
        val s = size / 104f
        face.addView(ImageView(this).apply {
            setImageDrawable(LineIcon(kind, ui.pxf(1.6f) * 50f / 48f * s))
        }, FrameLayout.LayoutParams((ui.px(50f) * s).toInt(), (ui.px(50f) * s).toInt(), Gravity.CENTER).apply {
            if (port != null) bottomMargin = (ui.px(28f) * s).toInt()
        })
        if (port != null) face.addView(ui.text(TextView(this), 13f * s, 600, 2f * s).apply { text = port },
            FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = (ui.px(12f) * s).toInt()
            })
    }

    private fun separator(gap: Int) = View(this).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x00FFFFFF, 0x47FFFFFF, 0x00FFFFFF))
        layoutParams = LinearLayout.LayoutParams(maxOf(1, ui.px(1f)), ui.px(78f)).apply {
            setMargins(gap / 2 + ui.px(4f), 0, gap / 2 + ui.px(4f), 0)
        }
    }

    // ---------- dock ----------

    private fun refreshDock() {
        dock.removeAllViews()
        val apps = favs().filter { launchIntent(it) != null }
        val inputs = hdmiInputs()
        val gap = ui.px(20f)
        val count = apps.size + inputs.size + 2
        val seps = if (inputs.isEmpty()) 1 else 2
        val avail = ui.sw * 0.94f - 2 * ui.px(28f) - seps * (ui.px(9f) + gap)
        val size = minOf(ui.px(104f), (avail / count - gap).toInt())
        dock.setPadding(ui.px(28f) - gap / 2, ui.px(22f), ui.px(28f) - gap / 2, ui.px(22f))
        (label.layoutParams as FrameLayout.LayoutParams).bottomMargin = ui.px(70f) + size + 2 * ui.px(22f) + ui.px(18f)
        label.requestLayout()

        fun add(t: Tile, name: String, sub: String?, onClick: () -> Unit, onLong: (() -> Unit)? = null) {
            t.cell.isFocusable = true
            t.cell.setOnFocusChangeListener { v, f -> t.focus(f); if (f) showLabel(v, name, sub) }
            t.cell.setOnClickListener { onClick() }
            if (onLong != null) t.cell.setOnLongClickListener { onLong(); true }
            dock.addView(t.cell, LinearLayout.LayoutParams(size, size).apply { setMargins(gap / 2, 0, gap / 2, 0) })
        }

        for (p in apps) {
            val intent = launchIntent(p) ?: continue
            val t = appTile(p, size) ?: continue
            add(t, appLabel(p), null, { launch(intent) }, {
                val l = favs(); l.remove(p); saveFavs(l)
                Toast.makeText(this, "Removed ${appLabel(p)}", Toast.LENGTH_SHORT).show()
                refreshDock()
            })
        }
        val inputCells = ArrayList<View>()
        if (inputs.isNotEmpty()) {
            dock.addView(separator(gap))
            for (h in inputs) {
                val t = glassTile(size, LineIcon.Kind.HDMI, h.port)
                add(t, h.port, h.name, { openInput(h) })
                inputCells.add(t.cell)
            }
        }
        dock.addView(separator(gap))
        add(glassTile(size, LineIcon.Kind.GRID, null), "All Apps", null, { openPicker() })
        add(glassTile(size, LineIcon.Kind.GEAR, null), "Settings", null, { openSettings() })

        caption.visibility = if (inputCells.isEmpty()) View.GONE else View.VISIBLE
        dock.post {
            if (inputCells.isNotEmpty()) {
                val mid = (inputCells.first().left + inputCells.last().right) / 2f
                caption.translationX = dock.left + mid - caption.width / 2f
            }
            if (dock.focusedChild == null) focusFirst()
        }
    }

    private fun showLabel(cell: View, name: String, sub: String?) {
        label.text = if (sub != null) "${name.uppercase()}   ·   ${sub.uppercase()}" else name.uppercase()
        label.post {
            val x = dock.left + cell.left + cell.width / 2f - label.width / 2f
            if (!labelPlaced) { label.translationX = x; label.alpha = 1f; labelPlaced = true }
            else label.animate().translationX(x).alpha(1f).setDuration(220).start()
        }
    }

    private fun launch(i: Intent) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { Toast.makeText(this, "Can't open", Toast.LENGTH_SHORT).show() }
    }

    private fun openInput(h: Hdmi) {
        startActivity(Intent(this, InputActivity::class.java)
            .putExtra(InputActivity.EXTRA_ID, h.id)
            .putExtra(InputActivity.EXTRA_NAME, h.port))
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

        val size = ui.px(104f)
        val current = favs()
        for (r in list) {
            val pkg = r.activityInfo.packageName
            val name = r.loadLabel(packageManager).toString()
            val t = appTile(pkg, size) ?: continue
            fun title(on: Boolean) = if (on) "✓  $name" else name
            val name2 = ui.text(TextView(this), 15f, 400, 1f).apply {
                text = title(current.contains(pkg)); alpha = .8f
                gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setPadding(0, ui.px(14f), 0, 0)
            }
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                clipChildren = false; clipToPadding = false
                isFocusable = true
                addView(t.cell, LinearLayout.LayoutParams(size, size))
                addView(name2, LinearLayout.LayoutParams(size + ui.px(60f), -2))
                setOnFocusChangeListener { _, f -> t.focus(f); name2.alpha = if (f) 1f else .8f }
                setOnClickListener {
                    val l = favs()
                    if (l.contains(pkg)) l.remove(pkg) else l.add(pkg)
                    saveFavs(l)
                    name2.text = title(l.contains(pkg))
                }
            }
            pickerGrid.addView(col, GridLayout.LayoutParams().apply {
                width = size + ui.px(60f)
                setMargins(ui.px(12f), ui.px(18f), ui.px(12f), ui.px(18f))
            })
        }
        label.alpha = 0f
        picker.visibility = View.VISIBLE
        pickerGrid.post { pickerGrid.getChildAt(0)?.requestFocus() }
    }

    private fun closePicker() {
        picker.visibility = View.GONE
        refreshDock()
    }
}
