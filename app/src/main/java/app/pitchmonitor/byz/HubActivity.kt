package app.pitchmonitor.byz

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import com.google.android.material.navigationrail.NavigationRailView
import java.io.File
import java.util.Date
import java.util.Locale

/**
 * Settings, Appearance and Recordings, switched with a navigation rail on the right.
 *
 * Navigation: the three destinations are top-level, so switching between them does not add to
 * the back stack; Back (or the arrow) returns to the pitch monitor. Back first leaves the
 * recordings selection if one is active. The current destination survives rotation.
 */
class HubActivity : ThemedActivity() {
    private lateinit var kit: Kit
    private lateinit var rail: NavigationRailView
    private lateinit var pages: FrameLayout
    private lateinit var topTitle: TextView
    private lateinit var topAction: com.google.android.material.button.MaterialButton
    private lateinit var topBar: LinearLayout
    private lateinit var selectionBar: LinearLayout
    private var current = PAGE_SETTINGS
    private var selectingProgrammatically = false
    private val built = HashMap<String, View>()
    private val dp by lazy { resources.displayMetrics.density }
    private fun px(v: Float) = (v * dp).toInt()

    // Recordings
    private lateinit var store: RecordingStore
    private var files: List<File> = emptyList()
    private val selected = LinkedHashSet<File>()
    private var listAdapter: BaseAdapter? = null
    private var selectionCount: TextView? = null
    private var renameAction: View? = null
    private var emptyView: View? = null

    private val backFromSelection = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = clearSelection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kit = Kit(this, palette)
        store = RecordingStore(this)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.nav_enter, R.anim.nav_hold)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.nav_hold, R.anim.nav_exit)
        }
        onBackPressedDispatcher.addCallback(this, backFromSelection)
        current = savedInstanceState?.getString(STATE_PAGE) ?: intent.getStringExtra(EXTRA_PAGE) ?: PAGE_SETTINGS
        setContentView(buildFrame())
        show(current, animate = false)
        debugExtras()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PAGE, current)
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT < 34) overridePendingTransition(R.anim.nav_hold, R.anim.nav_exit)
    }

    // ------------------------------------------------------------------------------------
    // Frame: top bar, page, footer | rail
    // ------------------------------------------------------------------------------------

    private fun buildFrame(): View {
        val p = palette
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(p.surface)
            fitsSystemWindows = true
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Top bar (normal) and selection bar (recordings).
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(4f), 0, px(4f), 0)
        }
        topBar.addView(iconButton(R.drawable.ic_arrow_back, R.string.back) { finish() })
        topTitle = TextView(this).apply {
            textSize = 22f
            setTextColor(p.onSurface)
            setPadding(px(8f), 0, 0, 0)
            isSingleLine = true
        }
        topBar.addView(topTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        topAction = kit.button("", filled = false) {}.apply { minHeight = px(40f); insetTop = 0; insetBottom = 0 }
        topBar.addView(topAction, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(40f)).apply { marginEnd = px(8f) })
        selectionBar = buildSelectionBar()
        val bars = FrameLayout(this)
        bars.addView(topBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(64f)))
        bars.addView(selectionBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(64f)))
        column.addView(bars)

        pages = FrameLayout(this)
        column.addView(pages, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        column.addView(buildFooter())
        root.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        // Rail on the right, with a hairline separating it from the content.
        root.addView(View(this).apply { setBackgroundColor(p.outlineVariant) }, LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT))
        rail = NavigationRailView(this).apply {
            inflateMenu(R.menu.nav)
            labelVisibilityMode = NavigationRailView.LABEL_VISIBILITY_LABELED
            menuGravity = Gravity.CENTER
            setBackgroundColor(p.surface)
            styleRail(this, p)
            setOnItemSelectedListener { item ->
                if (!selectingProgrammatically) show(pageOf(item.itemId), animate = true)
                true
            }
        }
        root.addView(rail, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return root
    }

    private fun iconButton(@DrawableRes icon: Int, @StringRes desc: Int, onClick: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(palette.onSurfaceVariant)
        background = palette.ripple(android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()))
        contentDescription = getString(desc)
        setPadding(px(12f), px(12f), px(12f), px(12f))
        layoutParams = LinearLayout.LayoutParams(px(48f), px(48f))
        setOnClickListener { onClick() }
    }

    /** Name, version and an email link, always visible at the bottom. */
    private fun buildFooter(): View {
        val p = palette
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(20f), px(8f), px(12f), px(8f))
            setBackgroundColor(p.surface)
        }
        box.addView(TextView(this).apply {
            text = getString(R.string.version_line, version, DEVELOPER)
            textSize = 12f
            setTextColor(p.onSurfaceVariant)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(8f), px(6f), px(8f), px(6f))
            background = p.ripple(p.mask(16f, dp))
            contentDescription = getString(R.string.feedback)
            addView(kit.icon(R.drawable.ic_mail, p.primaryText), LinearLayout.LayoutParams(px(16f), px(16f)).apply { marginEnd = px(6f) })
            addView(TextView(this@HubActivity).apply { setText(R.string.email_short); textSize = 12f; setTextColor(p.primaryText) })
            setOnClickListener { sendFeedback(this@HubActivity, version) }
        })
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(View(this).apply { setBackgroundColor(p.outlineVariant) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        wrap.addView(box)
        return wrap
    }

    private fun pageOf(itemId: Int) = when (itemId) {
        R.id.nav_appearance -> PAGE_APPEARANCE
        R.id.nav_recordings -> PAGE_RECORDINGS
        else -> PAGE_SETTINGS
    }

    private fun itemOf(page: String) = when (page) {
        PAGE_APPEARANCE -> R.id.nav_appearance
        PAGE_RECORDINGS -> R.id.nav_recordings
        else -> R.id.nav_settings
    }

    /** Shows a destination: the old page fades out, the new one fades and rises in. */
    private fun show(page: String, animate: Boolean) {
        val old = built[current]?.takeIf { it.parent != null }
        current = page
        if (rail.selectedItemId != itemOf(page)) {
            selectingProgrammatically = true
            rail.selectedItemId = itemOf(page)
            selectingProgrammatically = false
        }
        val view = built.getOrPut(page) {
            when (page) {
                PAGE_APPEARANCE -> buildAppearance()
                PAGE_RECORDINGS -> buildRecordings()
                else -> buildSettings()
            }
        }
        if (page == PAGE_RECORDINGS) refreshRecordings()
        clearSelection()
        topTitle.setText(when (page) {
            PAGE_APPEARANCE -> R.string.appearance
            PAGE_RECORDINGS -> R.string.recordings
            else -> R.string.settings
        })
        configureTopAction(page)
        if (view.parent == null) pages.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (old === view) return
        val ease = PathInterpolator(0.2f, 0f, 0f, 1f)
        if (animate && old != null) {
            old.animate().alpha(0f).setDuration(90).withEndAction { pages.removeView(old); old.alpha = 1f }.start()
            view.alpha = 0f
            view.translationY = 24 * dp
            view.animate().alpha(1f).translationY(0f).setStartDelay(60).setDuration(240).setInterpolator(ease).start()
        } else {
            old?.let { pages.removeView(it) }
        }
    }

    /** The text button at the top right: "Default" on Settings, "Import" on Recordings. */
    private fun configureTopAction(page: String) {
        when (page) {
            PAGE_SETTINGS -> {
                topAction.visibility = View.VISIBLE
                topAction.setText(R.string.default_)
                topAction.setOnClickListener {
                    style(dialog().setTitle(R.string.reset_title).setMessage(R.string.reset_confirm)
                        .setPositiveButton(R.string.reset_yes) { _, _ -> prefs.resetToDefaults(); recreate() }
                        .setNegativeButton(android.R.string.cancel, null).show())
                }
            }
            PAGE_RECORDINGS -> {
                topAction.visibility = View.VISIBLE
                topAction.setText(R.string.import_)
                topAction.setOnClickListener { finishWith(ACTION_IMPORT) }
            }
            else -> topAction.visibility = View.GONE
        }
    }

    private fun finishWith(action: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_ACTION, action))
        finish()
    }

    /** A scrolling page whose content is at most 720 dp wide (tablets, landscape). */
    private fun scrollingPage(fill: (LinearLayout) -> Unit): View {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, px(24f)) }
        fill(content)
        val frame = FrameLayout(this)
        val maxWidth = px(720f)
        frame.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL))
        frame.addOnLayoutChangeListener { v, l, _, r, _, _, _, _, _ ->
            val w = r - l
            val lp = content.layoutParams as FrameLayout.LayoutParams
            val want = if (w > maxWidth) maxWidth else ViewGroup.LayoutParams.MATCH_PARENT
            if (lp.width != want) { lp.width = want; v.post { content.layoutParams = lp } }
        }
        return ScrollView(this).apply { addView(frame); isFillViewport = true; tag = "scroll" }
    }

    // ------------------------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------------------------

    private fun buildSettings() = scrollingPage { c ->
        val k = kit
        k.section(c, getString(R.string.presets))
        k.card(c).also { card -> fillPresets(card) }
        k.section(c, getString(R.string.sec_detection))
        k.card(c).also { card ->
            k.sliderRow(card, getString(R.string.threshold), getString(R.string.threshold_desc), 0f, 50f, 0.25f, prefs.threshold,
                { String.format(Locale.US, "%.2f", it) }) { prefs.threshold = it }
        }

        k.section(c, getString(R.string.sec_view))
        k.card(c).also { card ->
            k.sliderRow(card, getString(R.string.h_zoom), getString(R.string.h_zoom_desc), 1f, 4f, 0.05f, prefs.hZoom, ::zoom) { prefs.hZoom = it }
            k.sliderRow(card, getString(R.string.v_zoom), getString(R.string.v_zoom_desc), 0.5f, 2f, 0.05f, prefs.vZoom, ::zoom) { prefs.vZoom = it }
            k.switchRow(card, getString(R.string.enable_auto_scroll), getString(R.string.auto_scroll_desc), prefs.autoScroll) { prefs.autoScroll = it }
            k.sliderRow(card, getString(R.string.scroll_speed), null, 1f, 10f, 1f, prefs.scrollSpeed.toFloat(), { it.toInt().toString() }) {
                prefs.scrollSpeed = it.toInt()
            }
        }

        k.section(c, getString(R.string.tuner))
        k.card(c).also { card ->
            k.switchRow(card, getString(R.string.show_tuner), getString(R.string.show_tuner_desc), prefs.showTuner) { prefs.showTuner = it }
            k.sliderRow(card, getString(R.string.smoothing), getString(R.string.smoothing_desc), 1f, 5f, 1f, prefs.smoothing.toFloat(), { it.toInt().toString() }) {
                prefs.smoothing = it.toInt()
            }
            k.switchRow(card, getString(R.string.show_hz), getString(R.string.show_hz_desc), prefs.showHz) { prefs.showHz = it }
        }

        k.section(c, getString(R.string.tempo))
        k.card(c).also { card ->
            k.switchRow(card, getString(R.string.beat_lines), getString(R.string.beat_lines_desc), prefs.beatLines) { prefs.beatLines = it }
            k.switchRow(card, getString(R.string.blink), getString(R.string.blink_desc), prefs.blink) { prefs.blink = it }
            var syncing = false
            lateinit var bpm: com.google.android.material.slider.Slider
            val presets = Prefs.TEMPO_PRESETS
            val chips = k.chipsRow(card, getString(R.string.byz_tempo), getString(R.string.byz_tempo_desc),
                presets.map { (res, b) -> getString(res) + " · " + b }, presets.indexOfFirst { it.second == prefs.bpm }) { i ->
                if (!syncing) bpm.value = presets[i].second.toFloat()
            }
            bpm = k.sliderRow(card, getString(R.string.bpm), getString(R.string.bpm_desc), 20f, 260f, 1f, prefs.bpm.toFloat(), { it.toInt().toString() }) {
                prefs.bpm = it.toInt()
                syncing = true
                val i = presets.indexOfFirst { p -> p.second == prefs.bpm }
                if (i >= 0) chips.check(chips.getChildAt(i).id) else chips.clearCheck()
                syncing = false
            }
            k.segmentedRow(card, getString(R.string.meter), null, listOf("4/4", "3/4", getString(R.string.meter_none)), prefs.meter) { prefs.meter = it }
        }

        k.section(c, getString(R.string.button_display))
        k.card(c).also { card ->
            k.switchRow(card, getString(R.string.btn_hold), getString(R.string.btn_hold_desc), prefs.showHold) { prefs.showHold = it }
            k.switchRow(card, getString(R.string.btn_scale), getString(R.string.btn_scale_desc), prefs.showScale) { prefs.showScale = it }
            k.switchRow(card, getString(R.string.btn_transport), getString(R.string.btn_transport_desc), !prefs.hideTransport) { prefs.hideTransport = !it }
        }

        k.section(c, getString(R.string.sec_notes))
        k.card(c).also { card ->
            k.chipsRow(card, getString(R.string.note_name), getString(R.string.note_name_desc),
                listOf(getString(R.string.names_english), getString(R.string.names_solfege), "ΝΗ, ΠΑ, ΒΟΥ…"), prefs.noteNames) { prefs.noteNames = it }
            val modes = listOf(Catalog.EU_ALL, Catalog.EU_C_MAJOR, Catalog.EU_NONE)
            k.chipsRow(card, getString(R.string.european_scales), getString(R.string.european_desc),
                listOf(getString(R.string.eu_all_short), getString(R.string.eu_cmajor_short), getString(R.string.eu_none_short)),
                modes.indexOf(prefs.european).coerceAtLeast(0)) {
                prefs.european = modes[it]
                if (!Catalog.visible(prefs.scale, prefs.european)) prefs.scale = Catalog.visibleList(prefs.european).first()
            }
        }

        k.section(c, getString(R.string.lines_tuning))
        k.card(c).also { card ->
            k.switchRow(card, getString(R.string.semitone_lines), getString(R.string.semitone_lines_desc), prefs.semitoneLines) { prefs.semitoneLines = it }
            k.switchRow(card, getString(R.string.semitone_labels), getString(R.string.semitone_labels_desc), prefs.semitoneLabels) { prefs.semitoneLabels = it }
            k.segmentedRow(card, getString(R.string.octave_number), getString(R.string.octave_desc),
                listOf("440 Hz = A4", "440 Hz = A3"), if (prefs.octaveA3) 1 else 0) { prefs.octaveA3 = it == 1 }
            k.sliderRow(card, getString(R.string.calibration), getString(R.string.calibration_desc), 415f, 465f, 1f, prefs.a4.toFloat(),
                { "A4 = ${it.toInt()} Hz" }) { prefs.a4 = it.toInt() }
            k.segmentedRow(card, getString(R.string.transpose), getString(R.string.transpose_desc),
                listOf("C", "B♭", "E♭", "F"), prefs.instrument) { prefs.instrument = it }
            k.sliderRow(card, getString(R.string.transpose_shift), null, -12f, 12f, 1f, prefs.transposeShift.toFloat(),
                { val v = it.toInt(); if (v > 0) "+$v" else v.toString() }) { prefs.transposeShift = it.toInt() }
        }

    }

    /** Saved setting presets: tap to load, the bin to delete, the last row saves the current settings. */
    private fun fillPresets(card: LinearLayout) {
        val p = palette
        for (name in prefs.presetNames()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(px(16f), px(6f), px(4f), px(6f))
                minimumHeight = px(56f)
                background = p.ripple()
                contentDescription = getString(R.string.preset_load, name)
                setOnClickListener {
                    style(dialog().setTitle(name).setMessage(R.string.preset_load_confirm)
                        .setPositiveButton(R.string.preset_load_yes) { _, _ -> prefs.loadPreset(name); recreate() }
                        .setNegativeButton(android.R.string.cancel, null).show())
                }
            }
            row.addView(kit.icon(R.drawable.ic_settings_tune, p.onSurfaceVariant), LinearLayout.LayoutParams(px(24f), px(24f)).apply { marginEnd = px(16f) })
            row.addView(kit.titleText(name), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(iconButton(R.drawable.ic_delete, R.string.delete) {
                style(dialog().setMessage(getString(R.string.delete_one, name))
                    .setPositiveButton(R.string.delete) { _, _ -> prefs.deletePreset(name); rebuild(PAGE_SETTINGS) }
                    .setNegativeButton(android.R.string.cancel, null).show())
            })
            card.addView(row)
        }
        kit.actionRow(card, R.drawable.ic_check, getString(R.string.preset_save), getString(R.string.preset_save_desc)) { askPresetName() }
    }

    private fun askPresetName() {
        val input = EditText(this).apply { isSingleLine = true; hint = getString(R.string.preset_name_hint) }
        val box = FrameLayout(this).apply { setPadding(px(24f), px(8f), px(24f), 0); addView(input) }
        val d = dialog().setTitle(R.string.preset_save).setView(box)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    prefs.savePreset(name)
                    Toast.makeText(this, getString(R.string.preset_saved, name), Toast.LENGTH_SHORT).show()
                    rebuild(PAGE_SETTINGS)
                }
            }
            .setNegativeButton(android.R.string.cancel, null).show()
        style(d); tintTree(box)
    }

    /** Rebuilds one page in place (keeps the scroll position). */
    private fun rebuild(page: String) {
        val old = built.remove(page) ?: return
        val y = (old as? ScrollView)?.scrollY ?: 0
        val index = pages.indexOfChild(old)
        pages.removeView(old)
        val fresh = when (page) { PAGE_APPEARANCE -> buildAppearance(); PAGE_RECORDINGS -> buildRecordings(); else -> buildSettings() }
        built[page] = fresh
        if (index >= 0) pages.addView(fresh, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        (fresh as? ScrollView)?.post { fresh.scrollTo(0, y) }
    }

    private fun zoom(v: Float): String {
        val s = String.format(Locale.US, "%.2f", v).trimEnd('0')
        return if (s.endsWith('.')) s + "0" else s
    }

    // ------------------------------------------------------------------------------------
    // Appearance
    // ------------------------------------------------------------------------------------

    private fun buildAppearance() = scrollingPage { c ->
        val k = kit
        val p = palette
        k.section(c, getString(R.string.theme))
        k.card(c).also { card ->
            val names = listOf(R.string.theme_system to R.string.theme_system_desc, R.string.theme_dark to R.string.theme_dark_desc,
                R.string.theme_black to R.string.theme_black_desc, R.string.theme_light to R.string.theme_light_desc,
                R.string.theme_parchment to R.string.theme_parchment_desc)
            Palette.THEMES.forEachIndexed { i, t ->
                val preview = Palette.of(this, t, prefs.accent)
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(px(16f), px(12f), px(16f), px(12f))
                    background = p.ripple()
                }
                // Mini preview: bar, graph and the app colour.
                val mini = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = p.rounded(preview.graphBg, 8f, dp, stroke = p.outline)
                    clipToOutline = true
                    addView(View(this@HubActivity).apply { setBackgroundColor(preview.bar) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(10f)))
                    addView(View(this@HubActivity).apply { setBackgroundColor(preview.accentText) }, LinearLayout.LayoutParams(px(22f), px(3f)).apply { setMargins(px(6f), px(8f), 0, 0) })
                }
                row.addView(mini, LinearLayout.LayoutParams(px(48f), px(36f)).apply { marginEnd = px(16f) })
                val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                texts.addView(k.titleText(getString(names[i].first)))
                texts.addView(k.bodyText(getString(names[i].second)))
                row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val selectedTheme = prefs.theme == t
                row.addView(k.icon(R.drawable.ic_check, p.primaryText).apply { visibility = if (selectedTheme) View.VISIBLE else View.INVISIBLE },
                    LinearLayout.LayoutParams(px(24f), px(24f)))
                row.isSelected = selectedTheme
                row.contentDescription = getString(names[i].first) + if (selectedTheme) " ✓" else ""
                row.setOnClickListener { if (prefs.theme != t) { prefs.theme = t; recreate() } }
                card.addView(row)
            }
        }

        k.section(c, getString(R.string.app_colour))
        k.card(c).also { card ->
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(16f), px(14f), px(16f), px(16f)) }
            box.addView(k.bodyText(getString(R.string.app_colour_desc)))
            val row = com.google.android.material.chip.ChipGroup(this).apply { chipSpacingHorizontal = px(12f); chipSpacingVertical = px(12f) }
            Prefs.ACCENTS.forEach { color ->
                val on = color == prefs.accent
                val swatch = FrameLayout(this).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(color)
                        setStroke(if (on) px(3f) else px(1f), if (on) p.onSurface else p.outlineVariant)
                    }
                    contentDescription = getString(R.string.app_colour) + String.format(Locale.US, " #%06X", color and 0xFFFFFF) + if (on) " ✓" else ""
                    if (on) addView(k.icon(R.drawable.ic_check, Colors.on(color)), FrameLayout.LayoutParams(px(22f), px(22f), Gravity.CENTER))
                    setOnClickListener { if (prefs.accent != color) { prefs.changeAccent(color); recreate() } }
                }
                row.addView(swatch, ViewGroup.LayoutParams(px(44f), px(44f)))
            }
            box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(12f) })
            card.addView(box)
        }

        k.section(c, getString(R.string.graph_colours))
        k.card(c).also { card ->
            val schemes = Palette.GRAPH_SCHEMES
            k.chipsRow(card, getString(R.string.graph_scheme), getString(R.string.graph_scheme_desc),
                listOf(getString(R.string.scheme_app), getString(R.string.scheme_classic), getString(R.string.scheme_contrast), getString(R.string.scheme_warm)),
                schemes.indexOf(prefs.graphScheme).coerceAtLeast(0)) {
                if (prefs.graphScheme != schemes[it]) { prefs.resetColors(); prefs.graphScheme = schemes[it]; rebuild(PAGE_APPEARANCE) }
            }
            k.switchRow(card, getString(R.string.graph_lock), getString(R.string.graph_lock_desc), prefs.graphLock) { prefs.graphLock = it }
            colorRow(card, getString(R.string.color_pitch), "color_pitch", { prefs.colorPitch }) { prefs.colorPitch = it }
            colorRow(card, getString(R.string.color_beats), "color_beats", { prefs.colorBeats }) { prefs.colorBeats = it }
            colorRow(card, getString(R.string.color_metronome), "color_metronome", { prefs.colorMetronome }) { prefs.colorMetronome = it }
            val degrees = listOf(R.string.deg_1, R.string.deg_2, R.string.deg_3, R.string.deg_4, R.string.deg_5, R.string.deg_6, R.string.deg_7, R.string.deg_semitone)
            expandable(card, getString(R.string.scale_colours), getString(R.string.scale_colours_desc), { prefs.scaleColorsOpen }, { prefs.scaleColorsOpen = it }) { box ->
                degrees.forEachIndexed { i, res -> colorRow(box, getString(res), "color_scale_$i", { prefs.scaleColor(i) }) { prefs.setScaleColor(i, it) } }
            }
            expandable(card, getString(R.string.chromatic), getString(R.string.chromatic_desc), { prefs.chromaticColorsOpen }, { prefs.chromaticColorsOpen = it }) { box ->
                listOf("C", "C♯ (D♭)", "D", "D♯ (E♭)", "E", "F", "F♯ (G♭)", "G", "G♯ (A♭)", "A", "A♯ (B♭)", "B").forEachIndexed { i, name ->
                    colorRow(box, name, "color_chromatic_$i", { prefs.chromaticColor(i) }) { prefs.setChromaticColor(i, it) }
                }
            }
            k.actionRow(card, R.drawable.ic_restore, getString(R.string.reset_colours), getString(R.string.reset_colours_desc)) {
                prefs.resetColors(); recreate()
            }
        }

        k.section(c, getString(R.string.language))
        k.card(c).also { card ->
            val langs = listOf(Lang.EN, Lang.EL)
            k.segmentedRow(card, getString(R.string.language), getString(R.string.language_desc), listOf("English", "Ελληνικά"),
                langs.indexOf(prefs.language).coerceAtLeast(0)) {
                if (prefs.language != langs[it]) { prefs.language = langs[it]; recreate() }
            }
        }
    }

    private fun colorRow(card: LinearLayout, title: String, key: String, get: () -> Int, set: (Int) -> Unit) {
        val p = palette
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(16f), px(10f), px(16f), px(10f))
            minimumHeight = px(52f)
            background = p.ripple()
        }
        row.addView(kit.titleText(title), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val swatch = View(this)
        fun paint() { swatch.background = p.rounded(get(), 8f, dp, stroke = p.outline) }
        paint()
        row.addView(swatch, LinearLayout.LayoutParams(px(56f), px(28f)))
        row.contentDescription = title
        row.setOnClickListener {
            ColorPicker.show(this, title, get(), prefs.recentColors, onDefault = { prefs.clearColor(key); paint() }) { c -> set(c); paint() }
        }
        card.addView(row)
    }

    private fun expandable(card: LinearLayout, title: String, desc: String, isOpen: () -> Boolean, setOpen: (Boolean) -> Unit, fill: (LinearLayout) -> Unit) {
        val p = palette
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(16f), px(14f), px(16f), px(14f))
            background = p.ripple()
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(kit.titleText(title)); texts.addView(kit.bodyText(desc))
        head.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val chevron = kit.icon(R.drawable.ic_chevron, p.onSurfaceVariant)
        head.addView(chevron, LinearLayout.LayoutParams(px(24f), px(24f)))
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Colors.blend(p.surfaceContainer, p.surface, 0.5f)) }
        fill(box)
        fun apply(open: Boolean, animate: Boolean) {
            box.visibility = if (open) View.VISIBLE else View.GONE
            val r = if (open) 0f else 180f
            if (animate) chevron.animate().rotation(r).setDuration(200).start() else chevron.rotation = r
            head.contentDescription = title + if (open) " ▲" else " ▼"
        }
        apply(isOpen(), false)
        head.setOnClickListener { val o = !isOpen(); setOpen(o); apply(o, true) }
        card.addView(head)
        card.addView(box)
    }

    // ------------------------------------------------------------------------------------
    // Recordings
    // ------------------------------------------------------------------------------------

    private fun buildRecordings(): View {
        val p = palette
        val frame = FrameLayout(this)
        val list = ListView(this).apply {
            divider = null
            selector = android.graphics.drawable.ColorDrawable(0)
            clipToPadding = false
            setPadding(px(12f), px(4f), px(12f), px(16f))
        }
        val adapter = object : BaseAdapter() {
            override fun getCount() = files.size
            override fun getItem(position: Int) = files[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = recordingRow(files[position])
        }
        listAdapter = adapter
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val f = files[pos]
            if (selected.isNotEmpty()) toggle(f) else {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_FILE, f.absolutePath))
                finish()
            }
        }
        list.setOnItemLongClickListener { _, _, pos, _ -> toggle(files[pos]); true }
        frame.addView(list)

        // Empty state.
        val empty = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(px(32f), px(32f), px(32f), px(32f))
            addView(FrameLayout(this@HubActivity).apply {
                background = p.rounded(p.secondaryContainer, 32f, dp)
                addView(kit.icon(R.drawable.ic_library_music, p.onSecondaryContainer), FrameLayout.LayoutParams(px(32f), px(32f), Gravity.CENTER))
            }, LinearLayout.LayoutParams(px(64f), px(64f)))
            addView(kit.titleText(getString(R.string.no_recordings)).apply { gravity = Gravity.CENTER; setPadding(0, px(16f), 0, px(4f)) })
            addView(kit.bodyText(getString(R.string.no_recordings_desc)).apply { gravity = Gravity.CENTER })
            addView(kit.button(getString(R.string.import_), R.drawable.ic_import, filled = false) { finishWith(ACTION_IMPORT) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(20f) })
        }
        emptyView = empty
        frame.addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return frame
    }

    private fun recordingRow(f: File): View {
        val p = palette
        val on = f in selected
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(12f), px(10f), px(16f), px(10f))
            background = android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(Colors.alpha(p.onSurface, 0.12f)),
                p.rounded(if (on) p.secondaryContainer else p.surfaceContainer, 16f, dp), null)
            layoutParams = android.widget.AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val lead = FrameLayout(this).apply {
            background = p.rounded(if (on) p.primary else p.secondaryContainer, 20f, dp)
            addView(kit.icon(if (on) R.drawable.ic_check else R.drawable.ic_mic, if (on) p.onPrimary else p.onSecondaryContainer),
                FrameLayout.LayoutParams(px(20f), px(20f), Gravity.CENTER))
        }
        row.addView(lead, LinearLayout.LayoutParams(px(40f), px(40f)).apply { marginEnd = px(14f) })
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(kit.titleText(f.nameWithoutExtension).apply { isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END })
        val seconds = (f.length() - 44).coerceAtLeast(0) / 2 / SAMPLE_RATE
        val date = Date(f.lastModified())
        texts.addView(kit.bodyText(DateFormat.getMediumDateFormat(this).format(date) + " " + DateFormat.getTimeFormat(this).format(date) +
            String.format(Locale.US, "  ·  %d:%02d", seconds / 60, seconds % 60)))
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.contentDescription = f.nameWithoutExtension + if (on) ", " + getString(R.string.selected) else ""
        // Space between rows.
        return FrameLayout(this).apply {
            setPadding(0, px(4f), 0, px(4f))
            addView(row)
            layoutParams = android.widget.AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun refreshRecordings() {
        files = store.list()
        selected.retainAll(files.toSet())
        listAdapter?.notifyDataSetChanged()
        emptyView?.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toggle(f: File) {
        if (!selected.remove(f)) selected += f
        updateSelection()
    }

    private fun clearSelection() {
        if (selected.isEmpty() && selectionBar.visibility != View.VISIBLE) return
        selected.clear()
        updateSelection()
    }

    private fun updateSelection() {
        val active = selected.isNotEmpty()
        backFromSelection.isEnabled = active
        listAdapter?.notifyDataSetChanged()
        selectionCount?.text = getString(R.string.n_selected, selected.size)
        renameAction?.visibility = if (selected.size == 1) View.VISIBLE else View.GONE
        if (active != (selectionBar.visibility == View.VISIBLE)) {
            val show = if (active) selectionBar else topBar
            val hide = if (active) topBar else selectionBar
            show.alpha = 0f; show.visibility = View.VISIBLE
            show.animate().alpha(1f).setDuration(150).start()
            hide.animate().alpha(0f).setDuration(120).withEndAction { hide.visibility = View.INVISIBLE; hide.alpha = 1f }.start()
        }
    }

    private fun buildSelectionBar(): LinearLayout {
        val p = palette
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(4f), 0, px(4f), 0)
            setBackgroundColor(p.secondaryContainer)
            visibility = View.INVISIBLE
            addView(iconButton(R.drawable.ic_close, R.string.clear_selection) { clearSelection() }.apply { imageTintList = ColorStateList.valueOf(p.onSecondaryContainer) })
            selectionCount = TextView(this@HubActivity).apply { textSize = 18f; setTextColor(p.onSecondaryContainer); setPadding(px(8f), 0, 0, 0) }
            addView(selectionCount, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            fun action(icon: Int, desc: Int, onClick: () -> Unit) = iconButton(icon, desc, onClick).also {
                it.imageTintList = ColorStateList.valueOf(p.onSecondaryContainer); addView(it)
            }
            action(R.drawable.ic_select_all, R.string.select_all) { selected.addAll(files); updateSelection() }
            renameAction = action(R.drawable.ic_edit, R.string.rename) { selected.firstOrNull()?.let { rename(it) } }
            action(R.drawable.ic_share, R.string.send) { share(selected.toList()) }
            action(R.drawable.ic_delete, R.string.delete) { confirmDelete(selected.toList()) }
        }
    }

    private fun share(chosen: List<File>) {
        if (chosen.isEmpty()) return
        val uris = ArrayList<Uri>(chosen.map { FileProvider.getUriForFile(this, "$packageName.files", it) })
        val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        send.setType("audio/wav").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, getString(R.string.send)))
    }

    private fun confirmDelete(chosen: List<File>) {
        if (chosen.isEmpty()) return
        val msg = if (chosen.size == 1) getString(R.string.delete_one, chosen[0].nameWithoutExtension) else getString(R.string.delete_many, chosen.size)
        style(dialog().setMessage(msg)
            .setPositiveButton(R.string.delete) { _, _ -> chosen.forEach { it.delete() }; selected.clear(); updateSelection(); refreshRecordings() }
            .setNegativeButton(android.R.string.cancel, null).show())
    }

    private fun rename(file: File) {
        val input = EditText(this).apply { setText(file.nameWithoutExtension); selectAll(); isSingleLine = true }
        val box = FrameLayout(this).apply { setPadding(px(24f), px(8f), px(24f), 0); addView(input) }
        val d = dialog().setTitle(R.string.rename).setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (store.rename(file, input.text.toString()) == null) Toast.makeText(this, R.string.name_taken, Toast.LENGTH_SHORT).show()
                selected.clear(); updateSelection(); refreshRecordings()
            }
            .setNegativeButton(android.R.string.cancel, null).show()
        style(d); tintTree(box)
    }

    // ------------------------------------------------------------------------------------

    private fun debugExtras() {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val y = intent.getIntExtra("scrollTo", 0)
        if (y > 0) pages.post { (built[current] as? ScrollView)?.scrollTo(0, px(y.toFloat())) }
        if (intent.getBooleanExtra("picker", false)) pages.post {
            ColorPicker.show(this, getString(R.string.color_pitch), prefs.colorPitch, prefs.recentColors, onDefault = {}) {}
        }
        if (intent.getBooleanExtra("selectAll", false)) pages.post { selected.addAll(files); updateSelection() }
    }

    companion object {
        const val EXTRA_PAGE = "page"
        const val EXTRA_ACTION = "action"
        const val EXTRA_FILE = "file"
        const val ACTION_IMPORT = "import"
        const val PAGE_SETTINGS = "settings"
        const val PAGE_APPEARANCE = "appearance"
        const val PAGE_RECORDINGS = "recordings"
        private const val STATE_PAGE = "page"
        const val FEEDBACK_EMAIL = "thanoszapp@gmail.com"
        const val DEVELOPER = "Athanasios Zappas"

        /** Opens an email to the developer; falls back to Gmail in the browser. */
        fun sendFeedback(a: ThemedActivity, version: String) {
            val subject = "Byzantine Pitch Monitor $version"
            val body = "\n\n—\nVersion $version · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}"
            val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            try {
                a.startActivity(mail)
            } catch (e: ActivityNotFoundException) {
                val web = Uri.parse("https://mail.google.com/mail/?view=cm&fs=1&to=$FEEDBACK_EMAIL&su=" + Uri.encode(subject) + "&body=" + Uri.encode(body))
                runCatching { a.startActivity(Intent(Intent.ACTION_VIEW, web)) }
            }
        }

        /** Rail colours: active indicator, icons and labels from the palette. */
        fun styleRail(rail: NavigationRailView, p: Palette, checkable: Boolean = true) {
            val checked = intArrayOf(android.R.attr.state_checked)
            fun states(on: Int, off: Int) = ColorStateList(arrayOf(checked, intArrayOf()), intArrayOf(on, off))
            rail.itemIconTintList = states(p.onSecondaryContainer, p.onSurfaceVariant)
            rail.itemTextColor = states(p.onSurface, p.onSurfaceVariant)
            rail.itemActiveIndicatorColor = ColorStateList.valueOf(p.secondaryContainer)
            rail.itemRippleColor = ColorStateList.valueOf(Colors.alpha(p.onSurface, 0.12f))
            rail.isItemActiveIndicatorEnabled = checkable
            if (!checkable) rail.menu.setGroupCheckable(0, false, true)
        }
    }
}
