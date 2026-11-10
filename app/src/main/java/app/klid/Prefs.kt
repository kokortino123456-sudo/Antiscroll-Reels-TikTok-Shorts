package app.klid

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/** All state lives in one local SharedPreferences file. Nothing leaves the device. */
object Prefs {
    const val YOUTUBE = "youtube"
    const val INSTAGRAM = "instagram"
    const val TIKTOK = "tiktok"
    private const val TIKTOK_LIMIT_MIN = "tiktok_limit_min"
    private const val TIKTOK_DAY = "tiktok_day"
    private const val TIKTOK_USED_SEC = "tiktok_used_sec"
    private const val KICKS_DAY = "kicks_day"
    private const val KICKS = "kicks"

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences("klid", Context.MODE_PRIVATE)

    private fun today(): String = LocalDate.now().toString()

    fun enabled(c: Context, key: String): Boolean = sp(c).getBoolean(key, true)

    fun setEnabled(c: Context, key: String, value: Boolean) =
        sp(c).edit().putBoolean(key, value).apply()

    fun tiktokLimitMin(c: Context): Int = sp(c).getInt(TIKTOK_LIMIT_MIN, 0)

    fun setTiktokLimitMin(c: Context, minutes: Int) =
        sp(c).edit().putInt(TIKTOK_LIMIT_MIN, minutes.coerceIn(0, 1440)).apply()

    fun tiktokUsedSec(c: Context): Int =
        if (sp(c).getString(TIKTOK_DAY, "") == today()) sp(c).getInt(TIKTOK_USED_SEC, 0) else 0

    fun addTiktokSec(c: Context, seconds: Int) {
        val used = tiktokUsedSec(c) + seconds
        sp(c).edit().putString(TIKTOK_DAY, today()).putInt(TIKTOK_USED_SEC, used).apply()
    }

    fun kicksToday(c: Context): Int =
        if (sp(c).getString(KICKS_DAY, "") == today()) sp(c).getInt(KICKS, 0) else 0

    fun addKick(c: Context) {
        val n = kicksToday(c) + 1
        sp(c).edit().putString(KICKS_DAY, today()).putInt(KICKS, n).apply()
    }
}
