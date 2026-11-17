package app.klid

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Spacing, radius and type tokens. Colours live in the colors.xml resources (light and night). */
private object Space {
    const val XS = 4
    const val S = 8
    const val M = 12
    const val L = 16
    const val XL = 24
    const val XXL = 32
}

private object Radius {
    const val CARD = 28
    const val CARD_PAD = 16
    /** Controls inside a card: card radius minus card padding, so the corners are concentric. */
    const val CONTROL = CARD - CARD_PAD
    const val TRACK_PAD = 4
    /** The tab bar: its segments use CONTROL, so the track around them adds its padding. */
    const val TRACK = CONTROL + TRACK_PAD
}

private object Type {
    const val TITLE = 34f
    const val STATUS = 22f
    const val SECTION = 17f
    const val BODY = 16f
    const val SMALL = 14f
    const val CAPTION = 13f
}

private enum class Tab(val title: String, val packages: List<String>) {
    YOUTUBE("YouTube", listOf(BlockerService.YOUTUBE)),
    INSTAGRAM("Instagram", listOf(BlockerService.INSTAGRAM)),
    TIKTOK("TikTok", BlockerService.TIKTOK.toList()),
}

private class TabViews(val tab: Tab, val button: Button, val page: View, val update: () -> Unit)

private class SwitchRow(val view: View, val detail: TextView)

private class ChartCard(val view: View, val chart: BarChart, val caption: TextView)

class MainActivity : Activity() {

    private val pauseText =
        "I am pausing Unreel again because I am addicted to scrolling. " +
            "I have already used all five of my free pauses today. " +
            "I spend a lot of my time on short videos and that time never comes back. " +
            "I know this and I am choosing to keep scrolling anyway."

    private val weakenText =
        "I am weakening Unreel because I am addicted to scrolling. " +
            "I spend a lot of my time on short videos and that time never comes back. " +
            "I know this and I am choosing to make it easier to keep scrolling anyway."

    private val sourceUrl = "https://github.com/kokortino123456-sudo/Antiscroll-Reels-TikTok-Shorts"

    private val handler = Handler(Looper.getMainLooper())

    private var bg = 0
    private var surface = 0
    private var track = 0
    private var border = 0
    private var ink = 0
    private var sub = 0
    private var accent = 0
    private var onStatus = 0
    private var alarm = 0

    private lateinit var todayLine: TextView
    private lateinit var statusCard: LinearLayout
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var statusButton: Button
    private lateinit var statusCaption: TextView
    private val tabs = mutableListOf<TabViews>()
    private var settingSwitch = false
    private var shownStatusColor = 0

    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000L)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bg = getColor(R.color.background)
        surface = getColor(R.color.surface)
        track = getColor(R.color.track)
        border = getColor(R.color.border)
        ink = getColor(R.color.text)
        sub = getColor(R.color.text_secondary)
        accent = getColor(R.color.accent)
        onStatus = getColor(R.color.on_status)
        alarm = getColor(R.color.status_off)

        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (!night) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(Space.L), dp(Space.XL), dp(Space.L), dp(Space.XXL))
        }
        column.addView(label("Unreel", Type.TITLE, ink, bold = true).apply {
            letterSpacing = -0.02f
            heading()
        })
        todayLine = label("", Type.BODY, sub).apply { tabular() }
        column.addView(todayLine)
        column.addView(statusCardView(), spaced(top = Space.XL))

        // One tab per app, as a segmented control above that app's page.
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(track, Radius.TRACK, bordered = false)
            val pad = dp(Radius.TRACK_PAD)
            setPadding(pad, pad, pad, pad)
        }
        val pages = FrameLayout(this)
        for (tab in Tab.values()) {
            val (page, update) = when (tab) {
                Tab.YOUTUBE -> youtubePage()
                Tab.INSTAGRAM -> instagramPage()
                Tab.TIKTOK -> tiktokPage()
            }
            val button = tabButton(tab.title)
            button.setOnClickListener { select(tab, animate = true) }
            tabBar.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f))
            pages.addView(page, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ))
            tabs.add(TabViews(tab, button, page, update))
        }
        column.addView(tabBar, spaced(top = Space.XL))
        column.addView(pages, spaced(top = Space.L))
        column.addView(footer(), spaced(top = Space.XL))

        // Keep a readable width on tablets and in landscape.
        val maxWidth = dp(560)
        val width = if (resources.displayMetrics.widthPixels > maxWidth) maxWidth
        else FrameLayout.LayoutParams.MATCH_PARENT
        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            addView(FrameLayout(this@MainActivity).apply {
                addView(column, FrameLayout.LayoutParams(
                    width, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL
                ))
            })
        })
        select(Tab.values().getOrElse(Prefs.selectedTab(this)) { Tab.YOUTUBE }, animate = false)
        render()
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    // ---- Status, shared by all tabs ----

    private fun statusCardView(): View {
        statusTitle = label("", Type.STATUS, onStatus, bold = true).apply { tabular() }
        statusDetail = label("", Type.SMALL, onStatus).apply { setPadding(0, dp(Space.XS), 0, 0) }
        statusButton = button("").apply { setOnClickListener { onStatusButton() } }
        statusCaption = label("", Type.CAPTION, onStatus).apply {
            setPadding(0, dp(Space.S), 0, 0)
            tabular()
        }
        statusCard = card(0, bordered = false).apply {
            addView(statusTitle)
            addView(statusDetail)
            addView(statusButton, spaced(top = Space.L))
            addView(statusCaption)
        }
        return statusCard
    }

    private fun render() {
        val on = serviceEnabled()
        val left = Prefs.pauseRemainingMs(this)
        val used = Prefs.pausesToday(this)
        val color: Int
        when {
            !on -> {
                color = getColor(R.color.status_off)
                statusTitle.text = "Unreel is off"
                statusDetail.text = "Nothing is being blocked. If the switch is greyed out: " +
                    "App info, three dots, Allow restricted settings."
                statusButton.text = "Turn on in Accessibility settings"
                statusCaption.visibility = View.GONE
            }
            left > 0 -> {
                color = getColor(R.color.status_paused)
                val sec = (left + 999) / 1000
                statusTitle.text = "Paused for %d:%02d".format(sec / 60, sec % 60)
                statusDetail.text = "Blocking comes back on its own."
                statusButton.text = "End pause now"
                statusCaption.visibility = View.VISIBLE
            }
            else -> {
                color = getColor(R.color.status_on)
                statusTitle.text = "Unreel is on"
                statusDetail.text = activeSummary()
                statusButton.text = "Pause for 5 minutes"
                statusCaption.visibility = View.VISIBLE
            }
        }
        statusCaption.text = if (used < Prefs.FREE_PAUSES) {
            "$used of ${Prefs.FREE_PAUSES} free pauses used today"
        } else {
            "$used pauses today. Each further pause has to be typed for."
        }
        if (color != shownStatusColor) {
            shownStatusColor = color
            (statusCard.background as GradientDrawable).setColor(color)
            statusButton.background = pressable(onStatus, Radius.CONTROL, color, bordered = false)
            statusButton.setTextColor(color)
        }

        val total = Prefs.pullOutsToday(this)
        todayLine.text = if (total == 0) "Nothing to pull you out of yet today."
        else "Pulled you out ${howOften(total)} today."
        for (t in tabs) t.update()
    }

    /** What is switched on, in one or two plain sentences. */
    private fun activeSummary(): String {
        val feeds = mutableListOf<String>()
        if (Prefs.enabled(this, Prefs.YOUTUBE)) feeds.add("Shorts")
        if (Prefs.enabled(this, Prefs.INSTAGRAM)) feeds.add("Reels")
        if (Prefs.enabled(this, Prefs.INSTAGRAM_HOME)) feeds.add("the Instagram feed")
        val parts = mutableListOf<String>()
        if (feeds.isNotEmpty()) parts.add("Pulls you out of ${naturalJoin(feeds)}.")
        if (Prefs.enabled(this, Prefs.TIKTOK)) {
            val limit = Prefs.tiktokLimitMin(this)
            parts.add(if (limit == 0) "TikTok is blocked." else "TikTok stops after $limit min a day.")
        }
        return if (parts.isEmpty()) "Every block is switched off. Turn some on below."
        else parts.joinToString(" ")
    }

    private fun onStatusButton() {
        if (!serviceEnabled()) showDisclosure() else onPauseTapped()
    }

    /** Says plainly what the accessibility permission is used for before sending the user there. */
    private fun showDisclosure() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Before you turn it on")
            .setMessage(
                "Unreel uses Android's accessibility service to notice when YouTube, Instagram " +
                    "or TikTok shows something you've blocked, such as Shorts or Reels, and then " +
                    "presses Back or Home for you.\n\n" +
                    "It only looks for those screens in those three apps. It doesn't save anything " +
                    "you see or type, and it has no internet access, so nothing can leave your phone."
            )
            .setNegativeButton("Not now", null)
            .setPositiveButton("Open settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .create()
        dialogLook(dialog)
        dialog.setOnShowListener { plainButtons(dialog) }
        dialog.show()
    }

    // ---- Tabs ----

    private fun select(tab: Tab, animate: Boolean) {
        Prefs.setSelectedTab(this, tab.ordinal)
        for (t in tabs) {
            val selected = t.tab == tab
            t.button.isSelected = selected
            t.button.setTextColor(if (selected) ink else sub)
            t.button.setTypeface(Typeface.DEFAULT, if (selected) Typeface.BOLD else Typeface.NORMAL)
            t.button.background = if (selected) rounded(surface, Radius.CONTROL, bordered = true)
            else pressable(Color.TRANSPARENT, Radius.CONTROL, ink, bordered = false)
            if (selected) {
                val wasHidden = t.page.visibility != View.VISIBLE
                t.page.visibility = View.VISIBLE
                if (animate && wasHidden) {
                    t.page.alpha = 0f
                    t.page.animate().alpha(1f).setDuration(150L).start()
                }
            } else {
                t.page.visibility = View.GONE
            }
        }
    }

    private fun youtubePage(): Pair<View, () -> Unit> {
        val shorts = switchRow("Shorts", Prefs.YOUTUBE)
        val stats = chartCard("Shorts closed", "Nothing closed in the last 7 days")
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(card(surface).apply {
                addView(appHeader(Tab.YOUTUBE,
                    "Closes Shorts the moment they open, from the Shorts tab, search or a link."))
                addView(shorts.view, spaced(top = Space.S))
                addView(note("Turning this off asks you to type a confession first."))
            })
            addView(stats.view, spaced(top = Space.L))
        }
        val update = {
            shorts.detail.text = todayCount("Closed", Prefs.countToday(this, Prefs.F_YT_SHORTS))
            setChart(stats, Prefs.lastDays(this, Prefs.F_YT_SHORTS), "")
        }
        return page to update
    }

    private fun instagramPage(): Pair<View, () -> Unit> {
        val reels = switchRow("Reels", Prefs.INSTAGRAM)
        val home = switchRow("Home feed", Prefs.INSTAGRAM_HOME, freeToTurnOff = true)
        val stats = chartCard("Times pulled out", "Nothing in the last 7 days")
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(card(surface).apply {
                addView(appHeader(Tab.INSTAGRAM,
                    "Closes Reels, and opens your messages instead of the home feed."))
                addView(reels.view, spaced(top = Space.S))
                addView(home.view)
                addView(note("Turning off Reels asks you to type a confession first. " +
                    "The home feed switch turns off with one tap."))
            })
            addView(stats.view, spaced(top = Space.L))
        }
        val update = {
            reels.detail.text = todayCount("Closed", Prefs.countToday(this, Prefs.F_IG_REELS))
            home.detail.text = todayCount("Sent to messages", Prefs.countToday(this, Prefs.F_IG_HOME))
            val a = Prefs.lastDays(this, Prefs.F_IG_REELS)
            val b = Prefs.lastDays(this, Prefs.F_IG_HOME)
            setChart(stats, IntArray(a.size) { a[it] + b[it] }, "")
        }
        return page to update
    }

    private fun tiktokPage(): Pair<View, () -> Unit> {
        val limit = switchRow("Daily limit", Prefs.TIKTOK)
        val minus = stepButton("−", "Fewer minutes") { changeLimit(-Prefs.LIMIT_STEP_MIN) }
        val plus = stepButton("+", "More minutes") { changeLimit(Prefs.LIMIT_STEP_MIN) }
        val value = label("", Type.SECTION, ink, bold = true).apply {
            gravity = Gravity.CENTER
            minWidth = dp(88)
            tabular()
        }
        val stepperLabel = label("Minutes per day", Type.BODY, ink)
        val stepper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(stepperLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(minus)
            addView(value)
            addView(plus)
        }
        val usageText = label("", Type.SMALL, ink).apply { tabular() }
        val usageBar = UsageBar(this, track, accent, alarm).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val stats = chartCard("Minutes on TikTok", "No TikTok time in the last 7 days")
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(card(surface).apply {
                addView(appHeader(Tab.TIKTOK,
                    "When today's minutes run out, TikTok closes and you land on the home screen."))
                addView(limit.view, spaced(top = Space.S))
                addView(stepper, spaced(top = Space.XS))
                addView(usageText, spaced(top = Space.L))
                addView(usageBar, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(8)
                ).apply { topMargin = dp(Space.S) })
                addView(note("Turning the limit off or adding minutes asks you to type a confession first."))
            })
            addView(stats.view, spaced(top = Space.L))
        }
        val update = {
            val on = Prefs.enabled(this, Prefs.TIKTOK)
            val limitMin = Prefs.tiktokLimitMin(this)
            val usedMin = Prefs.tiktokUsedSec(this) / 60
            limit.detail.text = todayCount("Sent you home", Prefs.countToday(this, Prefs.F_TT_HOME))
            value.text = if (limitMin == 0) "Blocked" else "$limitMin min"
            setEnabledLook(stepperLabel, on)
            setEnabledLook(value, on)
            setEnabledLook(minus, on && limitMin > 0)
            setEnabledLook(plus, on)
            // With the limit at zero the stepper already says "Blocked", so say nothing more.
            usageText.visibility = if (on && limitMin == 0) View.GONE else View.VISIBLE
            usageText.text = if (!on) "No limit. Used $usedMin min today." else "Used $usedMin of $limitMin min today"
            usageBar.visibility = if (on && limitMin > 0) View.VISIBLE else View.GONE
            usageBar.fraction = if (limitMin > 0) usedMin.toFloat() / limitMin else 0f
            setChart(stats, Prefs.lastDays(this, Prefs.F_TT_SECONDS).map { it / 60 }.toIntArray(), " min")
        }
        return page to update
    }

    /** The app's own icon and name, or a note when it isn't installed. */
    private fun appHeader(tab: Tab, description: String): View {
        val icon = appIcon(tab)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (icon != null) {
                addView(ImageView(this@MainActivity).apply {
                    setImageDrawable(icon)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(Space.M) })
            }
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(tab.title, Type.SECTION, ink, bold = true).apply { heading() })
                addView(label(
                    if (icon != null) description
                    else "Not installed on this phone. These settings apply once it is.",
                    Type.CAPTION, sub
                ))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun appIcon(tab: Tab): Drawable? {
        for (pkg in tab.packages) {
            try {
                return packageManager.getApplicationIcon(pkg)
            } catch (e: PackageManager.NameNotFoundException) {
                // Not installed under this name; try the next one.
            }
        }
        return null
    }

    private fun chartCard(title: String, emptyText: String): ChartCard {
        val caption = label("", Type.CAPTION, sub).apply { tabular() }
        val chart = BarChart(this, accent, withAlpha(accent, 0.45f), withAlpha(sub, 0.25f), sub, ink, emptyText)
        val view = card(surface).apply {
            addView(label(title, Type.SECTION, ink, bold = true).apply { heading() })
            addView(caption)
            addView(chart, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(140)
            ).apply { topMargin = dp(Space.M) })
        }
        return ChartCard(view, chart, caption)
    }

    private fun setChart(target: ChartCard, values: IntArray, unit: String) {
        target.chart.setData(values, dayLabels())
        val total = values.sum()
        target.caption.text = if (total == 0) "Last 7 days" else "Last 7 days, $total$unit in total"
    }

    private fun dayLabels(): Array<String> {
        val today = LocalDate.now()
        return Array(7) { i ->
            if (i == 6) "Today"
            else today.minusDays((6 - i).toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        }
    }

    private fun footer(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(label(
            "No ads, no account, no internet access. Everything stays on this phone.",
            Type.CAPTION, sub
        ).apply { gravity = Gravity.CENTER })
        addView(label("Version ${versionName()}", Type.CAPTION, sub).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(Space.S), 0, 0)
        })
        addView(Button(this@MainActivity).apply {
            text = "Source code on GitHub"
            textSize = Type.SMALL
            isAllCaps = false
            setTextColor(accent)
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            background = pressable(Color.TRANSPARENT, Radius.CONTROL, accent, bordered = false)
            stateListAnimator = null
            minHeight = dp(48)
            setPadding(dp(Space.M), 0, dp(Space.M), 0)
            setOnClickListener { openSource() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    @Suppress("DEPRECATION")
    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (e: PackageManager.NameNotFoundException) {
        "?"
    }

    private fun openSource() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl)))
        } catch (e: ActivityNotFoundException) {
            // No browser on this device; nothing else to do.
        }
    }

    // ---- Actions ----

    private fun onPauseTapped() {
        if (Prefs.paused(this)) {
            Prefs.endPause(this)
            BlockerService.instance?.recheckAfterPause()
            render()
            return
        }
        if (Prefs.pausesToday(this) < Prefs.FREE_PAUSES) startPause()
        else challenge(pauseText) { startPause() }
    }

    private fun startPause() {
        Prefs.startPause(this)
        BlockerService.instance?.recheckAfterPause()
        render()
    }

    private fun changeLimit(delta: Int) {
        val now = Prefs.tiktokLimitMin(this)
        if (delta < 0) {
            Prefs.setTiktokLimitMin(this, now + delta)
            render()
        } else {
            challenge(weakenText) {
                Prefs.setTiktokLimitMin(this, now + delta)
                render()
            }
        }
    }

    private fun serviceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.startsWith("$packageName/") }
    }

    // ---- The typed confession ----

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9\\s]"), "").trim().replace(Regex("\\s+"), " ")

    /** Shows [target] and runs [onPass] only after it has been typed out by hand. */
    private fun challenge(target: String, onPass: () -> Unit) {
        val wanted = normalize(target)
        val progress = label("0 / ${wanted.length}", Type.CAPTION, sub).apply { tabular() }
        val input = EditText(this).apply {
            // Visible-password mode switches off suggestions, autocorrect and swipe typing.
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            typeface = Typeface.DEFAULT
            minLines = 4
            gravity = Gravity.TOP
            hint = "Type it here, word for word"
            isLongClickable = false
            setTextIsSelectable(false)
            val noMenu = object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = false
                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false
                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false
                override fun onDestroyActionMode(mode: ActionMode?) {}
            }
            customSelectionActionModeCallback = noMenu
            customInsertionActionModeCallback = noMenu
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(Space.XL), dp(Space.M), dp(Space.XL), 0)
            addView(TextView(this@MainActivity).apply {
                text = target
                textSize = Type.BODY
                setTypeface(typeface, Typeface.BOLD)
                setTextIsSelectable(false)
            })
            addView(input, spaced(top = Space.M))
            addView(progress)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Type this to continue")
            .setView(ScrollView(this).apply { addView(box) })
            .setNegativeButton("Never mind", null)
            .setPositiveButton("Continue", null)
            .create()
        dialogLook(dialog)
        dialog.setOnShowListener {
            plainButtons(dialog)
            val ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ok.isEnabled = false
            ok.setOnClickListener {
                if (normalize(input.text.toString()) == wanted) {
                    dialog.dismiss()
                    onPass()
                }
            }
            input.addTextChangedListener(object : TextWatcher {
                private var previous = ""
                private var reverting = false
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s == null || reverting) return
                    // More than two characters appearing at once is a paste or dictation: undo it.
                    if (s.length - previous.length > 2) {
                        reverting = true
                        s.replace(0, s.length, previous)
                        reverting = false
                        return
                    }
                    previous = s.toString()
                    val typed = normalize(previous)
                    progress.text = "${typed.length} / ${wanted.length}"
                    ok.isEnabled = typed == wanted
                }
            })
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
    }

    // ---- Dialog look ----

    /** Match the cards: same surface colour, radius and border instead of the platform default. */
    private fun dialogLook(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawable(
            InsetDrawable(rounded(surface, Radius.CARD, bordered = true), dp(Space.L))
        )
    }

    private fun plainButtons(dialog: AlertDialog) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isAllCaps = false
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isAllCaps = false
    }

    // ---- Words ----

    private fun howOften(n: Int): String = if (n == 1) "once" else "$n times"

    /** "Closed 3 times today", or a plain line instead of "0 times". */
    private fun todayCount(done: String, n: Int): String =
        if (n == 0) "Not needed yet today" else "$done ${howOften(n)} today"

    private fun naturalJoin(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    // ---- Small view helpers ----

    private fun spaced(top: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(top) }

    private fun rounded(color: Int, radius: Int, bordered: Boolean): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (bordered) setStroke(dp(1).coerceAtLeast(1), border)
        }

    /** A rounded fill that shows a ripple when pressed or focused. */
    private fun pressable(fill: Int, radius: Int, rippleBase: Int, bordered: Boolean): Drawable =
        RippleDrawable(
            ColorStateList.valueOf(withAlpha(rippleBase, 0.2f)),
            rounded(fill, radius, bordered),
            rounded(Color.WHITE, radius, false)
        )

    private fun withAlpha(color: Int, fraction: Float): Int =
        Color.argb((fraction * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    private fun setEnabledLook(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.38f
    }

    private fun card(color: Int, bordered: Boolean = true): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(color, Radius.CARD, bordered)
        val pad = dp(Radius.CARD_PAD)
        setPadding(pad, pad, pad, pad)
    }

    private fun note(value: String): TextView = label(value, Type.CAPTION, sub).apply {
        setPadding(0, dp(Space.M), 0, 0)
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun TextView.tabular() {
        fontFeatureSettings = "tnum"
    }

    private fun View.heading() {
        if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
    }

    private fun button(value: String): Button = Button(this).apply {
        text = value
        textSize = Type.BODY
        isAllCaps = false
        setTypeface(typeface, Typeface.BOLD)
        stateListAnimator = null
        minHeight = dp(52)
    }

    private fun tabButton(title: String): Button = Button(this).apply {
        text = title
        textSize = Type.BODY
        isAllCaps = false
        stateListAnimator = null
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
    }

    private fun stepButton(value: String, description: String, onTap: () -> Unit): Button =
        Button(this).apply {
            text = value
            textSize = 20f
            contentDescription = description
            setTextColor(ink)
            background = pressable(bg, Radius.CONTROL, ink, bordered = true)
            stateListAnimator = null
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setOnClickListener { onTap() }
        }

    private fun switchRow(title: String, key: String, freeToTurnOff: Boolean = false): SwitchRow {
        val detail = label("", Type.CAPTION, sub).apply {
            tabular()
            setPadding(0, 0, dp(64), dp(Space.S))
        }
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toggle(title, key, freeToTurnOff))
            addView(detail)
        }
        return SwitchRow(view, detail)
    }

    private fun toggle(title: String, key: String, freeToTurnOff: Boolean): Switch = Switch(this).apply {
        text = title
        textSize = Type.BODY
        setTextColor(ink)
        setPadding(0, dp(Space.M), 0, dp(Space.XS))
        minHeight = dp(48)
        isChecked = Prefs.enabled(this@MainActivity, key)
        setOnCheckedChangeListener { view, checked ->
            if (settingSwitch) return@setOnCheckedChangeListener
            if (checked || freeToTurnOff) {
                Prefs.setEnabled(this@MainActivity, key, checked)
                render()
            } else {
                // Switching a block off has to be earned: put the switch back, then ask.
                settingSwitch = true
                view.isChecked = true
                settingSwitch = false
                challenge(weakenText) {
                    Prefs.setEnabled(this@MainActivity, key, false)
                    settingSwitch = true
                    view.isChecked = false
                    settingSwitch = false
                    render()
                }
            }
        }
    }
}
