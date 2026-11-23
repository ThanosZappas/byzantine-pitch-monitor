package app.pitchmonitor.byz

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Colour picker: a saturation/brightness square, a hue slider, old/new preview, hex code,
 * recently used colours and presets.
 */
object ColorPicker {
    private val PRESETS = intArrayOf(
        0xFFFFEB3B.toInt(), 0xFFFFC107.toInt(), 0xFFFF9800.toInt(), 0xFFFF5722.toInt(), 0xFFF44336.toInt(),
        0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF673AB7.toInt(), 0xFF3F51B5.toInt(), 0xFF2196F3.toInt(),
        0xFF03A9F4.toInt(), 0xFF00BCD4.toInt(), 0xFF009688.toInt(), 0xFF4CAF50.toInt(), 0xFF8BC34A.toInt(),
        0xFFCDDC39.toInt(), 0xFF795548.toInt(), 0xFF000080.toInt(), 0xFFFFFFFF.toInt(), 0xFFC0C0C0.toInt(),
        0xFF808080.toInt(), 0xFF404040.toInt(), 0xFF202020.toInt(), 0xFF000000.toInt(),
    )

    /**
     * Shows the picker. [onDefault], when given, adds a "Default" button that restores the
     * theme's own colour.
     */
    fun show(
        context: ThemedActivity,
        title: String,
        initial: Int,
        recent: List<Int>,
        onDefault: (() -> Unit)? = null,
        onPick: (Int) -> Unit,
    ) {
        val p = context.palette
        val dp = context.resources.displayMetrics.density
        fun px(v: Float) = (v * dp).toInt()
        val hsv = FloatArray(3).also { Color.colorToHSV(initial, it) }
        val muted = p.onSurfaceVariant

        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20f), px(8f), px(20f), 0)
        }

        // Old | new preview.
        val preview = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val oldSwatch = View(context).apply { background = rounded(initial, px(10f).toFloat(), left = true) }
        val newSwatch = View(context)
        oldSwatch.setOnClickListener { setAll(initial) }
        preview.addView(oldSwatch, LinearLayout.LayoutParams(0, px(40f), 1f))
        preview.addView(newSwatch, LinearLayout.LayoutParams(0, px(40f), 1f))
        box.addView(preview)

        val sv = SatValView(context).apply { ring = p.onSurface }
        val hue = HueView(context)
        box.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(190f)).apply { topMargin = px(14f) })
        box.addView(hue, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(30f)).apply { topMargin = px(14f) })

        // Hex code.
        val hexRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        hexRow.addView(TextView(context).apply { text = "#"; textSize = 18f; setTextColor(muted) })
        val hex = EditText(context).apply {
            textSize = 18f
            setTextColor(p.onSurface)
            typeface = Typeface.MONOSPACE
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(6))
            minEms = 5
        }
        hexRow.addView(hex)
        box.addView(hexRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(6f) })

        fun swatchRow(label: Int, colors: List<Int>) {
            if (colors.isEmpty()) return
            box.addView(TextView(context).apply { setText(label); textSize = 13f; setTextColor(muted) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(8f) })
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in colors) {
                row.addView(View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL; setColor(c)
                        setStroke(px(1f), p.outline)
                    }
                    contentDescription = String.format(Locale.US, "#%06X", c and 0xFFFFFF)
                    setOnClickListener { setAll(c) }
                }, LinearLayout.LayoutParams(px(32f), px(32f)).apply { rightMargin = px(8f); topMargin = px(4f) })
            }
            box.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        }
        swatchRow(R.string.recent_colors, recent)
        swatchRow(R.string.preset_colors, PRESETS.toList())

        var current = initial
        var updatingHex = false
        fun refresh(fromHex: Boolean = false) {
            current = Color.HSVToColor(hsv)
            newSwatch.background = rounded(current, px(10f).toFloat(), left = false)
            sv.hue = hsv[0]; sv.sat = hsv[1]; sv.value = hsv[2]; sv.invalidate()
            hue.hue = hsv[0]; hue.invalidate()
            if (!fromHex) {
                updatingHex = true
                hex.setText(String.format(Locale.US, "%06X", current and 0xFFFFFF))
                updatingHex = false
            }
        }
        setAllRef = { c -> Color.colorToHSV(c, hsv); refresh() }
        sv.onChange = { s, v -> hsv[1] = s; hsv[2] = v; refresh() }
        hue.onChange = { h -> hsv[0] = h; refresh() }
        hex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updatingHex) return
                val t = s?.toString() ?: return
                if (t.length == 6) t.toLongOrNull(16)?.let { Color.colorToHSV((0xFF000000 or it).toInt(), hsv); refresh(fromHex = true) }
            }
        })
        refresh()

        val b = context.dialog()
            .setTitle(title)
            .setView(android.widget.ScrollView(context).apply { addView(box) })
            .setPositiveButton(android.R.string.ok) { _, _ -> onPick(current) }
            .setNegativeButton(android.R.string.cancel, null)
        if (onDefault != null) b.setNeutralButton(R.string.default_color) { _, _ -> onDefault() }
        val d = b.show()
        context.style(d)
        context.tintTree(hex)
    }

    private var setAllRef: ((Int) -> Unit)? = null
    private fun setAll(c: Int) { setAllRef?.invoke(c) }

    private fun rounded(c: Int, r: Float, left: Boolean) = GradientDrawable().apply {
        setColor(c)
        cornerRadii = if (left) floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r) else floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
    }

    /** The saturation (left→right) / brightness (bottom→top) square for one hue. */
    private class SatValView(context: Context) : View(context) {
        var hue = 0f; var sat = 0f; var value = 0f
        var onChange: ((Float, Float) -> Unit)? = null
        /** Outer ring of the thumb, from the theme. */
        var ring = Color.BLACK
        private val dp = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * dp }
        private val rect = RectF()
        private val clip = Path()

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            rect.set(0f, 0f, w, h)
            val pure = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
            val horizontal = LinearGradient(0f, 0f, w, 0f, Color.WHITE, pure, Shader.TileMode.CLAMP)
            val vertical = LinearGradient(0f, 0f, 0f, h, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
            paint.shader = ComposeShader(horizontal, vertical, PorterDuff.Mode.SRC_OVER)
            canvas.drawRoundRect(rect, 12 * dp, 12 * dp, paint)
            val x = sat * w; val y = (1 - value) * h
            // Two rings (theme outline + the picked colour's best contrast) keep the thumb visible on any colour.
            thumb.color = ring; canvas.drawCircle(x, y, 11 * dp, thumb)
            thumb.color = Colors.on(Color.HSVToColor(floatArrayOf(hue, sat, value))); canvas.drawCircle(x, y, 9 * dp, thumb)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            parent.requestDisallowInterceptTouchEvent(true)
            onChange?.invoke((e.x / width).coerceIn(0f, 1f), (1 - e.y / height).coerceIn(0f, 1f))
            return true
        }
    }

    /** The rainbow hue slider. */
    private class HueView(context: Context) : View(context) {
        var hue = 0f
        var onChange: ((Float) -> Unit)? = null
        private val dp = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * dp }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val colors = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val r = h / 2
            rect.set(0f, h * 0.2f, w, h * 0.8f)
            paint.shader = LinearGradient(r, 0f, w - r, 0f, colors, null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, r, r, paint)
            val x = r + hue / 360f * (w - 2 * r)
            fill.color = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
            canvas.drawCircle(x, h / 2, r - 2 * dp, fill)
            thumb.color = Colors.on(fill.color); canvas.drawCircle(x, h / 2, r - 2 * dp, thumb)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            parent.requestDisallowInterceptTouchEvent(true)
            val r = height / 2f
            onChange?.invoke(((e.x - r) / (width - 2 * r) * 360f).coerceIn(0f, 359.9f))
            return true
        }
    }
}
