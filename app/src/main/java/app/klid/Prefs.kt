package app.klid

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import java.time.LocalDate

/** All state lives in one local SharedPreferences file. Nothing leaves the device. */
object Prefs {
    const val YOUTUBE = "youtube"
    const val INSTAGRAM = "instagram"
    const val INSTAGRAM_HOME = "instagram_home"
    const val TIKTOK = "tiktok"

    // Things counted per day, for the stats in each tab.
    const val F_YT_SHORTS = "yt_shorts"
    const val F_IG_REELS = "ig_reels"
    const val F_IG_HOME = "ig_home"
    const val F_TT_HOME = "tt_home"
    const val F_TT_SECONDS = "tt_seconds"
    private val PULL_OUTS = listOf(F_YT_SHORTS, F_IG_REELS, F_IG_HOME, F_TT_HOME)

    const val PAUSE_MS = 5 * 60 * 1000L
    const val FREE_PAUSES = 5
    const val LIMIT_STEP_MIN = 5

    private const val TIKTOK_LIMIT_MIN = "tiktok_limit_min"
    private const val TIKTOK_DAY = "tiktok_day"
    private const val TIKTOK_USED_SEC = "tiktok_used_sec"
    private const val PAUSES_DAY = "pauses_day"
    private const val PAUSES = "pauses"
    private const val PAUSE_END = "pause_end_elapsed"
    private const val PAUSE_BOOT = "pause_boot"
    private const val TAB = "tab"
    private const val HISTORY = "h|"
    private const val HISTORY_PRUNED = "h_pruned"
    private const val HISTORY_KEEP_DAYS = 35L

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences("klid", Context.MODE_PRIVATE)

    private fun today(): String = LocalDate.now().toString()

    // Daily counters. A stored day that is today or later keeps its count, so
    // setting the phone's clock back does not hand out a fresh day.
    private fun dayCount(c: Context, dayKey: String, valueKey: String): Int {
        val day = sp(c).getString(dayKey, "") ?: ""
        return if (day >= today()) sp(c).getInt(valueKey, 0) else 0
    }

    private fun dayAdd(c: Context, dayKey: String, valueKey: String, amount: Int) {
        val stored = sp(c).getString(dayKey, "") ?: ""
        val day = if (stored > today()) stored else today()
        val value = dayCount(c, dayKey, valueKey) + amount
        sp(c).edit().putString(dayKey, day).putInt(valueKey, value).apply()
    }

    fun enabled(c: Context, key: String): Boolean = sp(c).getBoolean(key, true)

    fun setEnabled(c: Context, key: String, value: Boolean) =
        sp(c).edit().putBoolean(key, value).apply()

    fun tiktokLimitMin(c: Context): Int = sp(c).getInt(TIKTOK_LIMIT_MIN, 0)

    fun setTiktokLimitMin(c: Context, minutes: Int) =
        sp(c).edit().putInt(TIKTOK_LIMIT_MIN, minutes.coerceIn(0, 240)).apply()

    fun tiktokUsedSec(c: Context): Int = dayCount(c, TIKTOK_DAY, TIKTOK_USED_SEC)
    fun addTiktokSec(c: Context, seconds: Int) {
        dayAdd(c, TIKTOK_DAY, TIKTOK_USED_SEC, seconds)
        addCount(c, F_TT_SECONDS, seconds)
    }

    fun selectedTab(c: Context): Int = sp(c).getInt(TAB, 0)
    fun setSelectedTab(c: Context, index: Int) = sp(c).edit().putInt(TAB, index).apply()

    // ---- Daily history, for the charts. Kept for HISTORY_KEEP_DAYS days. ----

    private fun historyKey(feature: String, day: LocalDate): String = "$HISTORY$feature|$day"

    fun addCount(c: Context, feature: String, amount: Int = 1) {
        val key = historyKey(feature, LocalDate.now())
        sp(c).edit().putInt(key, sp(c).getInt(key, 0) + amount).apply()
        pruneHistory(c)
    }

    fun countToday(c: Context, feature: String): Int =
        sp(c).getInt(historyKey(feature, LocalDate.now()), 0)

    /** The last [days] days of [feature], oldest first and today last. */
    fun lastDays(c: Context, feature: String, days: Int = 7): IntArray {
        val now = LocalDate.now()
        return IntArray(days) { i -> sp(c).getInt(historyKey(feature, now.minusDays((days - 1 - i).toLong())), 0) }
    }

    /** How many times today Unreel pulled the user out of any feed. */
    fun pullOutsToday(c: Context): Int = PULL_OUTS.sumOf { countToday(c, it) }

    private fun pruneHistory(c: Context) {
        val prefs = sp(c)
        if (prefs.getString(HISTORY_PRUNED, "") == today()) return
        val cutoff = LocalDate.now().minusDays(HISTORY_KEEP_DAYS).toString()
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            if (key.startsWith(HISTORY) && key.substringAfterLast('|') < cutoff) editor.remove(key)
        }
        editor.putString(HISTORY_PRUNED, today()).apply()
    }

    fun pausesToday(c: Context): Int = dayCount(c, PAUSES_DAY, PAUSES)

    private fun bootCount(c: Context): Int =
        Settings.Global.getInt(c.contentResolver, Settings.Global.BOOT_COUNT, 0)

    // The pause end is kept on the time-since-boot clock, which the user cannot
    // change in Settings. That clock restarts on reboot, so a pause only counts
    // during the boot it was started in.
    fun pauseRemainingMs(c: Context): Long {
        if (sp(c).getInt(PAUSE_BOOT, -1) != bootCount(c)) return 0L
        val left = sp(c).getLong(PAUSE_END, 0L) - SystemClock.elapsedRealtime()
        return if (left in 1..PAUSE_MS) left else 0L
    }

    fun paused(c: Context): Boolean = pauseRemainingMs(c) > 0L

    fun startPause(c: Context) {
        sp(c).edit()
            .putLong(PAUSE_END, SystemClock.elapsedRealtime() + PAUSE_MS)
            .putInt(PAUSE_BOOT, bootCount(c))
            .apply()
        dayAdd(c, PAUSES_DAY, PAUSES, 1)
    }

    fun endPause(c: Context) = sp(c).edit().putLong(PAUSE_END, 0L).apply()
}
