package app.pitchmonitor.byz

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

/**
 * Builds the card-based settings screens. All colours come from [p] (theme roles), all spacing
 * from a 4 dp grid: 16 dp inside cards, 12 dp between cards, 24 dp above section headers.
 */
class Kit(private val a: ThemedActivity, private val p: Palette) {
    private val dp = a.resources.displayMetrics.density
    fun px(v: Float) = (v * dp).toInt()

    private val checked = intArrayOf(android.R.attr.state_checked)
    private val none = intArrayOf()
    private fun states(on: Int, off: Int) = ColorStateList(arrayOf(checked, none), intArrayOf(on, off))

    // --- text styles ------------------------------------------------------------------------

    fun titleText(text: CharSequence) = TextView(a).apply {
        this.text = text; textSize = 16f; setTextColor(p.onSurface)
    }

    fun bodyText(text: CharSequence) = TextView(a).apply {
        this.text = text; textSize = 13.5f; setTextColor(p.onSurfaceVariant); setLineSpacing(0f, 1.1f)
    }

    /** Section header above a card. */
    fun section(parent: LinearLayout, text: CharSequence) {
        parent.addView(TextView(a).apply {
            this.text = text
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.02f
            setTextColor(p.primaryText)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(px(20f), px(24f), px(20f), px(8f))
        })
    }

    /** A rounded card; rows added to the returned layout get dividers between them. */
    fun card(parent: LinearLayout): LinearLayout {
        val inner = object : LinearLayout(a) {
            override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
                if (childCount > 0 && child.tag != DIVIDER) super.addView(divider(), -1, dividerParams())
                super.addView(child, index, params)
            }
        }.apply { orientation = LinearLayout.VERTICAL }
        val card = MaterialCardView(a).apply {
            radius = 20 * dp
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(p.surfaceContainer)
            addView(inner)
        }
        parent.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(px(12f), 0, px(12f), px(4f))
        })
        return inner
    }

    private fun divider() = View(a).apply { setBackgroundColor(p.outlineVariant); tag = DIVIDER }
    private fun dividerParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
        setMargins(px(16f), 0, px(16f), 0)
    }

    private fun rowBox(vertical: Boolean = false) = LinearLayout(a).apply {
        orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        gravity = if (vertical) Gravity.NO_GRAVITY else Gravity.CENTER_VERTICAL
        setPadding(px(16f), px(14f), px(16f), px(14f))
        minimumHeight = px(56f)
    }

    private fun texts(title: CharSequence, desc: CharSequence?) = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        addView(titleText(title))
        if (!desc.isNullOrEmpty()) addView(bodyText(desc), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(2f) })
    }

    // --- rows -------------------------------------------------------------------------------

    /** Title + description + switch; tapping anywhere on the row toggles it. */
    fun switchRow(card: LinearLayout, title: CharSequence, desc: CharSequence?, value: Boolean, onChange: (Boolean) -> Unit): MaterialSwitch {
        val row = rowBox()
        row.addView(texts(title, desc), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = px(16f) })
        val sw = MaterialSwitch(a).apply {
            isChecked = value
            trackTintList = states(p.primary, p.surfaceContainerHigh)
            thumbTintList = states(p.onPrimary, p.outline)
            trackDecorationTintList = states(Colors.alpha(p.primary, 0f), p.outline)
            contentDescription = title
            setOnCheckedChangeListener { _, b -> onChange(b) }
        }
        row.addView(sw)
        row.background = p.ripple()
        row.setOnClickListener { sw.toggle() }
        card.addView(row)
        return sw
    }

    /** Title, value on the right, description, then a slider. */
    fun sliderRow(
        card: LinearLayout, title: CharSequence, desc: CharSequence?,
        from: Float, to: Float, step: Float, value: Float,
        format: (Float) -> String, onChange: (Float) -> Unit,
    ): Slider {
        val box = rowBox(vertical = true).apply { setPadding(px(16f), px(14f), px(16f), px(6f)) }
        val head = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val valueText = TextView(a).apply {
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(p.onSecondaryContainer)
            background = p.rounded(p.secondaryContainer, 8f, dp)
            setPadding(px(8f), px(2f), px(8f), px(2f))
        }
        head.addView(titleText(title), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(valueText)
        box.addView(head)
        if (!desc.isNullOrEmpty()) box.addView(bodyText(desc), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(2f) })
        val slider = Slider(a).apply {
            valueFrom = from; valueTo = to; stepSize = step
            // Material sliders need the value exactly on the step grid; older saved values may not be.
            val steps = Math.round((value.coerceIn(from, to) - from) / step)
            this.value = (Math.round((from + steps * step) * 10000f) / 10000f).coerceIn(from, to)
            labelBehavior = com.google.android.material.slider.LabelFormatter.LABEL_GONE
            trackActiveTintList = ColorStateList.valueOf(p.primary)
            trackInactiveTintList = ColorStateList.valueOf(p.secondaryContainer)
            thumbTintList = ColorStateList.valueOf(p.primary)
            haloTintList = ColorStateList.valueOf(Colors.alpha(p.primary, 0.2f))
            tickActiveTintList = ColorStateList.valueOf(Colors.alpha(p.onPrimary, 0.5f))
            tickInactiveTintList = ColorStateList.valueOf(Colors.alpha(p.onSecondaryContainer, 0.4f))
            isTickVisible = (to - from) / step <= 12
            contentDescription = title
            addOnChangeListener { _, v, _ -> valueText.text = format(v); onChange(v) }
        }
        valueText.text = format(slider.value)
        box.addView(slider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(-px(8f), px(4f), -px(8f), 0)
        })
        card.addView(box)
        return slider
    }

    /** Title + description + a row of joined buttons (one selected). Best for 2–4 short options. */
    fun segmentedRow(card: LinearLayout, title: CharSequence, desc: CharSequence?, options: List<String>, selected: Int, onSelect: (Int) -> Unit): MaterialButtonToggleGroup {
        val box = rowBox(vertical = true)
        if (title.isNotEmpty()) box.addView(texts(title, desc)) else box.setPadding(px(16f), px(4f), px(16f), px(8f))
        val group = MaterialButtonToggleGroup(a).apply { isSingleSelection = true; isSelectionRequired = true }
        val ids = options.map { text ->
            MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = View.generateViewId()
                this.text = text
                isAllCaps = false
                textSize = 14f
                insetTop = 0; insetBottom = 0
                minHeight = px(44f)
                setTextColor(states(p.onSecondaryContainer, p.onSurface))
                backgroundTintList = states(p.secondaryContainer, p.surfaceContainer)
                strokeColor = ColorStateList.valueOf(p.outline)
                rippleColor = ColorStateList.valueOf(Colors.alpha(p.onSurface, 0.12f))
                isCheckable = true
                // Whole words on one line: long labels shrink a little instead of being cut.
                maxLines = 1
                setPadding(px(4f), paddingTop, px(4f), paddingBottom)
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 10, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
                group.addView(this, LinearLayout.LayoutParams(0, px(44f), 1f))
            }.id
        }
        if (selected in ids.indices) group.check(ids[selected])
        group.addOnButtonCheckedListener { _, id, isChecked -> if (isChecked) ids.indexOf(id).takeIf { it >= 0 }?.let(onSelect) }
        box.addView(group, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = if (title.isNotEmpty()) px(10f) else 0 })
        card.addView(box)
        return group
    }

    /** Title + description + wrapping choice chips (one selected); [selected] -1 = none. */
    fun chipsRow(card: LinearLayout, title: CharSequence, desc: CharSequence?, options: List<String>, selected: Int, onSelect: (Int) -> Unit): ChipGroup {
        val box = rowBox(vertical = true)
        box.addView(texts(title, desc))
        val group = ChipGroup(a).apply { isSingleSelection = true; chipSpacingHorizontal = px(8f) }
        val ids = options.map { text -> chip(text).also { group.addView(it) }.id }
        if (selected in ids.indices) group.check(ids[selected])
        group.setOnCheckedStateChangeListener { _, checkedIds ->
            checkedIds.firstOrNull()?.let { id -> ids.indexOf(id).takeIf { it >= 0 }?.let(onSelect) }
        }
        box.addView(group, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(8f) })
        card.addView(box)
        return group
    }

    fun chip(text: String) = Chip(a).apply {
        id = View.generateViewId()
        this.text = text
        isCheckable = true
        isCheckedIconVisible = true
        checkedIconTint = ColorStateList.valueOf(p.onSecondaryContainer)
        chipBackgroundColor = states(p.secondaryContainer, p.surfaceContainer)
        setTextColor(states(p.onSecondaryContainer, p.onSurface))
        chipStrokeColor = states(p.secondaryContainer, p.outline)
        chipStrokeWidth = dp
        rippleColor = ColorStateList.valueOf(Colors.alpha(p.onSurface, 0.12f))
        textSize = 14f
    }

    /** Icon + title + description; the whole row is a button. */
    fun actionRow(card: LinearLayout, @DrawableRes icon: Int, title: CharSequence, desc: CharSequence?, onClick: () -> Unit): View {
        val row = rowBox()
        row.addView(icon(icon, p.onSurfaceVariant), LinearLayout.LayoutParams(px(24f), px(24f)).apply { marginEnd = px(16f) })
        row.addView(texts(title, desc), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.background = p.ripple()
        row.setOnClickListener { onClick() }
        card.addView(row)
        return row
    }

    fun icon(@DrawableRes res: Int, tint: Int) = ImageView(a).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(tint)
    }

    /** A filled or tonal button. */
    fun button(text: CharSequence, @DrawableRes icon: Int = 0, filled: Boolean = true, onClick: () -> Unit) =
        MaterialButton(a).apply {
            this.text = text
            isAllCaps = false
            textSize = 14f
            cornerRadius = px(20f)
            backgroundTintList = ColorStateList.valueOf(if (filled) p.primary else p.secondaryContainer)
            setTextColor(if (filled) p.onPrimary else p.onSecondaryContainer)
            rippleColor = ColorStateList.valueOf(Colors.alpha(if (filled) p.onPrimary else p.onSecondaryContainer, 0.16f))
            if (icon != 0) {
                setIconResource(icon)
                iconTint = ColorStateList.valueOf(if (filled) p.onPrimary else p.onSecondaryContainer)
                iconPadding = px(8f)
            }
            setOnClickListener { onClick() }
        }

    companion object {
        private const val DIVIDER = "divider"
    }
}
