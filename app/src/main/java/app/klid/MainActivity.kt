package app.klid

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

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
}

private object Type {
    const val TITLE = 34f
    const val STATUS = 22f
    const val SECTION = 17f
    const val BODY = 16f
    const val SMALL = 14f
    const val CAPTION = 13f
    const val STAT = 30f
}

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

    private val handler = Handler(Looper.getMainLooper())

    private var bg = 0
    private var surface = 0
    private var border = 0
    private var ink = 0
    private var sub = 0
    private var accent = 0
    private var onStatus = 0

    private lateinit var statusCard: LinearLayout
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var statusButton: Button
    private lateinit var statusCaption: TextView
    private lateinit var minusButton: Button
    private lateinit var limitValue: TextView
    private lateinit var statKicks: TextView
    private lateinit var statTiktok: TextView
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
        border = getColor(R.color.border)
        ink = getColor(R.color.text)
        sub = getColor(R.color.text_secondary)
        accent = getColor(R.color.accent)
        onStatus = getColor(R.color.on_status)

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
        column.addView(label("Less scrolling. Free for everyone.", Type.BODY, sub))

        // Status, with the one action that matters right now.
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
        column.addView(statusCard, spaced(top = Space.XL))

        // What gets blocked
        minusButton = stepButton("−", "Fewer TikTok minutes") { changeLimit(-Prefs.LIMIT_STEP_MIN) }
        limitValue = label("", Type.SECTION, ink, bold = true).apply {
            gravity = Gravity.CENTER
            minWidth = dp(72)
            tabular()
        }
        val stepper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label("TikTok minutes per day", Type.BODY, ink),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(minusButton)
            addView(limitValue)
            addView(stepButton("+", "More TikTok minutes") { changeLimit(Prefs.LIMIT_STEP_MIN) })
        }
        column.addView(card(surface).apply {
            addView(label("Blocked", Type.SECTION, ink, bold = true).apply { heading() })
            addView(toggle("YouTube Shorts", Prefs.YOUTUBE))
            addView(toggle("Instagram Reels", Prefs.INSTAGRAM))
            addView(toggle("Instagram home feed", Prefs.INSTAGRAM_HOME, freeToTurnOff = true))
            addView(toggle("TikTok", Prefs.TIKTOK))
            addView(stepper, spaced(top = Space.S))
            addView(label(
                "Turning off Shorts, Reels or TikTok, or allowing more TikTok, asks you to type a confession first.",
                Type.CAPTION, sub
            ).apply { setPadding(0, dp(Space.M), 0, 0) })
        }, spaced(top = Space.L))

        // Today, as one card with two numbers.
        statKicks = label("0", Type.STAT, accent, bold = true).apply { tabular() }
        statTiktok = label("0", Type.STAT, accent, bold = true).apply { tabular() }
        fun stat(number: TextView, caption: String) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(number)
            addView(label(caption, Type.CAPTION, sub))
        }
        column.addView(card(surface).apply {
            addView(label("Today", Type.SECTION, ink, bold = true).apply { heading() })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(stat(statKicks, "times pulled out"),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(stat(statTiktok, "TikTok minutes"),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }, spaced(top = Space.S))
        }, spaced(top = Space.L))

        column.addView(label(
            "No ads, no account, no internet access. Everything stays on this phone.",
            Type.CAPTION, sub
        ).apply { gravity = Gravity.CENTER }, spaced(top = Space.XL))

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
                statusDetail.text = "Shorts, Reels and the Instagram feed close themselves."
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

        val limit = Prefs.tiktokLimitMin(this)
        limitValue.text = if (limit == 0) "none" else "$limit min"
        minusButton.isEnabled = limit > 0
        minusButton.alpha = if (limit > 0) 1f else 0.38f
        statKicks.text = Prefs.kicksToday(this).toString()
        statTiktok.text = (Prefs.tiktokUsedSec(this) / 60).toString()
    }

    private fun onStatusButton() {
        if (!serviceEnabled()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } else {
            onPauseTapped()
        }
    }

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
        dialog.setOnShowListener {
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
    private fun pressable(fill: Int, radius: Int, rippleBase: Int, bordered: Boolean): Drawable {
        val ripple = Color.argb(51, Color.red(rippleBase), Color.green(rippleBase), Color.blue(rippleBase))
        return RippleDrawable(
            ColorStateList.valueOf(ripple),
            rounded(fill, radius, bordered),
            rounded(Color.WHITE, radius, false)
        )
    }

    private fun card(color: Int, bordered: Boolean = true): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(color, Radius.CARD, bordered)
        val pad = dp(Radius.CARD_PAD)
        setPadding(pad, pad, pad, pad)
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

    private fun toggle(title: String, key: String, freeToTurnOff: Boolean = false): Switch = Switch(this).apply {
        text = title
        textSize = Type.BODY
        setTextColor(ink)
        setPadding(0, dp(Space.M), 0, dp(Space.M))
        minHeight = dp(48)
        isChecked = Prefs.enabled(this@MainActivity, key)
        setOnCheckedChangeListener { view, checked ->
            if (settingSwitch) return@setOnCheckedChangeListener
            if (checked || freeToTurnOff) {
                Prefs.setEnabled(this@MainActivity, key, checked)
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
                }
            }
        }
    }
}
