package app.klid

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var enableButton: Button
    private lateinit var stats: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(24))
        }

        column.addView(text("Klid", 34f, bold = true))
        column.addView(text("Free, no ads, no account, no internet access.", 15f, Color.DKGRAY))

        status = text("", 16f, bold = true).apply { setPadding(0, dp(28), 0, dp(8)) }
        column.addView(status)

        enableButton = Button(this).apply {
            text = "Turn on in Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        column.addView(enableButton)
        column.addView(
            text(
                "If Android says the setting is restricted: open Klid's App info, tap the " +
                    "three dots in the corner and choose \"Allow restricted settings\".",
                13f, Color.GRAY
            )
        )

        column.addView(text("Block", 20f, bold = true).apply { setPadding(0, dp(28), 0, dp(4)) })
        column.addView(toggle("YouTube Shorts", Prefs.YOUTUBE))
        column.addView(toggle("Instagram Reels", Prefs.INSTAGRAM))
        column.addView(toggle("TikTok", Prefs.TIKTOK))

        column.addView(text("TikTok minutes allowed per day (0 = none)", 15f).apply {
            setPadding(0, dp(16), 0, 0)
        })
        column.addView(EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(Prefs.tiktokLimitMin(this@MainActivity).toString())
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    Prefs.setTiktokLimitMin(this@MainActivity, s?.toString()?.toIntOrNull() ?: 0)
                }
            })
        })

        stats = text("", 15f, Color.DKGRAY).apply { setPadding(0, dp(24), 0, 0) }
        column.addView(stats)

        setContentView(ScrollView(this).apply { addView(column) })
    }

    override fun onResume() {
        super.onResume()
        val on = serviceEnabled()
        status.text = if (on) "Klid is on" else "Klid is off"
        status.setTextColor(if (on) Color.rgb(31, 111, 92) else Color.rgb(176, 58, 46))
        enableButton.visibility = if (on) View.GONE else View.VISIBLE
        val kicks = Prefs.kicksToday(this)
        val tiktokMin = Prefs.tiktokUsedSec(this) / 60
        stats.text = "Today: pulled you out $kicks times, TikTok used $tiktokMin min."
    }

    private fun serviceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.startsWith("$packageName/") }
    }

    private fun text(
        value: String, size: Float, color: Int = Color.BLACK, bold: Boolean = false
    ): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun toggle(label: String, key: String): Switch = Switch(this).apply {
        text = label
        textSize = 17f
        setPadding(0, dp(12), 0, dp(12))
        isChecked = Prefs.enabled(this@MainActivity, key)
        setOnCheckedChangeListener { _, checked -> Prefs.setEnabled(this@MainActivity, key, checked) }
    }
}
