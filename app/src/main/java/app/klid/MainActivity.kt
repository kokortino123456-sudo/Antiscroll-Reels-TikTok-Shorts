package app.klid

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private class Palette(
        val bg: Int, val card: Int, val text: Int, val sub: Int,
        val accent: Int, val warn: Int, val danger: Int, val onAccent: Int
    )

    private val pauseText =
        "I am pausing Klid again because I am addicted to scrolling. " +
            "I have already used all five of my free pauses today. " +
            "I spend a lot of my time on short videos and that time never comes back. " +
            "I know this and I am choosing to keep scrolling anyway."

    private val weakenText =
        "I am weakening Klid because I am addicted to scrolling. " +
            "I spend a lot of my time on short videos and that time never comes back. " +
            "I know this and I am choosing to make it easier to keep scrolling anyway."

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var p: Palette

    private lateinit var statusCard: LinearLayout
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var enableButton: Button
    private lateinit var pauseButton: Button
    private lateinit var pauseCaption: TextView
    private lateinit var limitValue: TextView
    private lateinit var statKicks: TextView
    private lateinit var statTiktok: TextView
    private var settingSwitch = false

    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000L)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        p = if (night) Palette(
            bg = Color.parseColor("#121413"), card = Color.parseColor("#1E2220"),
            text = Color.parseColor("#ECEAE4"), sub = Color.parseColor("#9A9C97"),
            accent = Color.parseColor("#4FB39A"), warn = Color.parseColor("#E0A84A"),
            danger = Color.parseColor("#E26D5C"), onAccent = Color.parseColor("#0E1513")
        ) else Palette(
            bg = Color.parseColor("#F4F1EA"), card = Color.parseColor("#FFFFFF"),
            text = Color.parseColor("#1C1B1A"), sub = Color.parseColor("#6B6862"),
            accent = Color.parseColor("#1F6F5C"), warn = Color.parseColor("#B7791F"),
            danger = Color.parseColor("#B03A2E"), onAccent = Color.parseColor("#FFFFFF")
        )
        window.statusBarColor = p.bg
        window.navigationBarColor = p.bg
        if (!night) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }

        column.addView(label("Klid", 36f, p.text, bold = true))
        column.addView(label("Less scrolling. Free for everyone.", 15f, p.sub))

        // Status
        statusTitle = label("", 22f, p.onAccent, bold = true)
        statusDetail = label("", 14f, p.onAccent).apply { setPadding(0, dp(4), 0, 0) }
        enableButton = pill("Turn on in Accessibility settings", p.onAccent, p.danger).apply {
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        statusCard = card(p.accent).apply {
            addView(statusTitle)
            addView(statusDetail)
            addView(enableButton, spaced(top = 14))
        }
        column.addView(statusCard, spaced(top = 22))

        // Pause
        pauseButton = pill("", p.accent, p.onAccent).apply { setOnClickListener { onPauseTapped() } }
        pauseCaption = label("", 13f, p.sub).apply { setPadding(0, dp(10), 0, 0) }
        column.addView(card(p.card).apply {
            addView(label("Need a break from the block?", 17f, p.text, bold = true))
            addView(pauseButton, spaced(top = 12))
            addView(pauseCaption)
        }, spaced(top = 14))

        // Blocks
        limitValue = label("", 17f, p.text, bold = true).apply {
            gravity = Gravity.CENTER
            minWidth = dp(72)
        }
        val stepper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label("TikTok minutes per day", 15f, p.sub),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(round("−") { changeLimit(-Prefs.LIMIT_STEP_MIN) })
            addView(limitValue)
            addView(round("+") { changeLimit(Prefs.LIMIT_STEP_MIN) })
        }
        column.addView(card(p.card).apply {
            addView(label("Blocked", 17f, p.text, bold = true))
            addView(toggle("YouTube Shorts", Prefs.YOUTUBE))
            addView(toggle("Instagram Reels", Prefs.INSTAGRAM))
            addView(toggle("TikTok", Prefs.TIKTOK))
            addView(stepper, spaced(top = 6))
            addView(label("Turning a block off or allowing more TikTok asks you to type a confession first.",
                13f, p.sub).apply { setPadding(0, dp(12), 0, 0) })
        }, spaced(top = 14))

        // Today
        statKicks = label("0", 30f, p.accent, bold = true)
        statTiktok = label("0", 30f, p.accent, bold = true)
        val tiles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tiles.addView(card(p.card).apply {
            addView(statKicks)
            addView(label("times pulled out today", 13f, p.sub))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(7)
        })
        tiles.addView(card(p.card).apply {
            addView(statTiktok)
            addView(label("TikTok minutes today", 13f, p.sub))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(7)
        })
        column.addView(tiles, spaced(top = 14))

        column.addView(label(
            "No ads, no account, no internet access. Everything stays on this phone.",
            13f, p.sub
        ).apply { gravity = Gravity.CENTER }, spaced(top = 22))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(p.bg)
            isFillViewport = true
            addView(column)
        })
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
        val color: Int
        if (!on) {
            color = p.danger
            statusTitle.text = "Klid is off"
            statusDetail.text = "Nothing is being blocked. If the switch is greyed out: App info, " +
                "three dots, Allow restricted settings."
        } else if (left > 0) {
            color = p.warn
            val sec = (left + 999) / 1000
            statusTitle.text = "Paused  %d:%02d".format(sec / 60, sec % 60)
            statusDetail.text = "Blocking comes back on its own."
        } else {
            color = p.accent
            statusTitle.text = "Klid is on"
            statusDetail.text = "Shorts and Reels close themselves."
        }
        (statusCard.background as GradientDrawable).setColor(color)
        enableButton.visibility = if (on) View.GONE else View.VISIBLE

        val used = Prefs.pausesToday(this)
        if (left > 0) {
            pauseButton.text = "End pause now"
        } else {
            pauseButton.text = "Pause for 5 minutes"
        }
        pauseCaption.text = if (used < Prefs.FREE_PAUSES) {
            "$used of ${Prefs.FREE_PAUSES} free pauses used today."
        } else {
            "$used pauses today. Every further pause has to be typed for."
        }

        val limit = Prefs.tiktokLimitMin(this)
        limitValue.text = if (limit == 0) "none" else "$limit min"
        statKicks.text = Prefs.kicksToday(this).toString()
        statTiktok.text = (Prefs.tiktokUsedSec(this) / 60).toString()
    }

    private fun onPauseTapped() {
        if (Prefs.paused(this)) {
            Prefs.endPause(this)
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
        val progress = label("0 / ${wanted.length}", 13f, p.sub)
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
            setPadding(dp(24), dp(12), dp(24), 0)
            addView(TextView(this@MainActivity).apply {
                text = target
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextIsSelectable(false)
            })
            addView(input, spaced(top = 12))
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

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun card(color: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(color, 22)
        setPadding(dp(20), dp(18), dp(20), dp(18))
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun pill(value: String, fill: Int, textColor: Int): Button = Button(this).apply {
        text = value
        textSize = 16f
        isAllCaps = false
        setTextColor(textColor)
        setTypeface(typeface, Typeface.BOLD)
        background = rounded(fill, 28)
        stateListAnimator = null
        minHeight = dp(52)
    }

    private fun round(value: String, onTap: () -> Unit): Button = Button(this).apply {
        text = value
        textSize = 20f
        setTextColor(p.text)
        background = rounded(p.bg, 22)
        stateListAnimator = null
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        setOnClickListener { onTap() }
    }

    private fun toggle(title: String, key: String): Switch = Switch(this).apply {
        text = title
        textSize = 17f
        setTextColor(p.text)
        setPadding(0, dp(14), 0, dp(14))
        isChecked = Prefs.enabled(this@MainActivity, key)
        setOnCheckedChangeListener { view, checked ->
            if (settingSwitch) return@setOnCheckedChangeListener
            if (checked) {
                Prefs.setEnabled(this@MainActivity, key, true)
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
