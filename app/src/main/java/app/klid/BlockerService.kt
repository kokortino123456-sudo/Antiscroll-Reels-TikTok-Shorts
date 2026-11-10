package app.klid

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Presses Back when a short-video feed is on screen in YouTube or Instagram,
 * moves you from Instagram's home feed to your messages, and sends you Home
 * once TikTok's daily allowance is used up.
 *
 * Feeds are recognised by the view ids of their players. Those ids belong to
 * the other apps and can change when they update; fix them in the lists below.
 */
class BlockerService : AccessibilityService() {

    companion object {
        const val YOUTUBE = "com.google.android.youtube"
        const val INSTAGRAM = "com.instagram.android"
        val TIKTOK = setOf(
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically.go", "com.ss.android.ugc.trill.go", // TikTok Lite
        )

        val YOUTUBE_IDS = listOf(
            "$YOUTUBE:id/reel_recycler",
            "$YOUTUBE:id/reel_player_page_container",
            "$YOUTUBE:id/reel_watch_player",
        )
        val INSTAGRAM_IDS = listOf(
            "$INSTAGRAM:id/clips_viewer_view_pager",
            "$INSTAGRAM:id/clips_video_container",
            "$INSTAGRAM:id/clips_viewer_container",
        )

        // Instagram's bottom bar. The home feed is "Home tab selected, no Back arrow":
        // posts and profiles opened from a link carry a Back arrow and are left alone.
        const val INSTAGRAM_HOME_TAB = "$INSTAGRAM:id/feed_tab"
        const val INSTAGRAM_BACK_ARROW = "$INSTAGRAM:id/action_bar_button_back"
        /** Where to send the user instead of the home feed, first match wins. */
        val INSTAGRAM_ESCAPES = listOf(
            "$INSTAGRAM:id/direct_tab",
            "$INSTAGRAM:id/action_bar_inbox_button",
            "$INSTAGRAM:id/profile_tab",
        )

        private const val CHECK_DELAY_MS = 200L
        private const val KICK_COOLDOWN_MS = 800L
        private const val TICK_SEC = 5
        private const val STREAK_WINDOW_MS = 6000L
        private const val STREAK_BEFORE_HOME = 3

        /** The running service, so the settings screen can tell it a pause started. */
        @Volatile
        var instance: BlockerService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var checkPending = false
    private var ticking = false
    private var lastKick = 0L
    private var streakStart = 0L
    private var streak = 0

    private val check = Runnable {
        checkPending = false
        checkFeed()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!tiktokInFront()) {
                ticking = false
                return
            }
            if (!Prefs.paused(this@BlockerService)) Prefs.addTiktokSec(this@BlockerService, TICK_SEC)
            if (!enforceTiktokLimit()) handler.postDelayed(this, TICK_SEC * 1000L)
            else ticking = false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg in TIKTOK) {
            if (!Prefs.enabled(this, Prefs.TIKTOK)) return
            if (enforceTiktokLimit()) return
            if (!ticking) {
                ticking = true
                handler.postDelayed(tick, TICK_SEC * 1000L)
            }
        } else if (pkg == YOUTUBE || pkg == INSTAGRAM) {
            // Events arrive in bursts; look at the screen at most once per CHECK_DELAY_MS.
            if (!checkPending) {
                checkPending = true
                handler.postDelayed(check, CHECK_DELAY_MS)
            }
        }
    }

    private fun checkFeed() {
        val now = SystemClock.uptimeMillis()
        if (now - lastKick < KICK_COOLDOWN_MS) return
        if (Prefs.paused(this)) return
        val root = try { rootInActiveWindow } catch (e: Exception) { null } ?: return
        val pkg = root.packageName?.toString()
        val feedIds = when (pkg) {
            YOUTUBE -> if (Prefs.enabled(this, Prefs.YOUTUBE)) YOUTUBE_IDS else emptyList()
            INSTAGRAM -> if (Prefs.enabled(this, Prefs.INSTAGRAM)) INSTAGRAM_IDS else emptyList()
            else -> return
        }
        if (feedIds.any { visible(root, it).isNotEmpty() }) {
            kick(now) { performGlobalAction(GLOBAL_ACTION_BACK) }
            return
        }
        if (pkg == INSTAGRAM && Prefs.enabled(this, Prefs.INSTAGRAM_HOME) && onInstagramHome(root)) {
            val escape = INSTAGRAM_ESCAPES.firstNotNullOfOrNull { visible(root, it).firstOrNull() }
            // No known tab to escape to means these ids no longer match Instagram; do nothing
            // rather than throw the user out of the app every time it opens.
            if (escape != null) kick(now) { click(escape) }
        }
    }

    private fun visible(root: AccessibilityNodeInfo, id: String): List<AccessibilityNodeInfo> =
        root.findAccessibilityNodeInfosByViewId(id).filter { it.isVisibleToUser }

    private fun onInstagramHome(root: AccessibilityNodeInfo): Boolean {
        val tab = visible(root, INSTAGRAM_HOME_TAB).firstOrNull() ?: return false
        val selected = tab.isSelected ||
            (0 until tab.childCount).any { tab.getChild(it)?.isSelected == true }
        return selected && visible(root, INSTAGRAM_BACK_ARROW).isEmpty()
    }

    /** Clicks the node, or the nearest clickable ancestor. */
    private fun click(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        for (i in 0 until 4) {
            val t = target ?: return false
            if (t.isClickable) return t.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            target = t.parent
        }
        return false
    }

    /**
     * Runs [action] to get the user out of a feed. If the feed is still there after a
     * few tries in a row (the action did nothing, or they went straight back), leaves
     * the app instead.
     */
    private fun kick(now: Long, action: () -> Boolean) {
        lastKick = now
        if (now - streakStart > STREAK_WINDOW_MS) {
            streakStart = now
            streak = 0
        }
        streak++
        if (streak == 1) Prefs.addKick(this)
        if (streak >= STREAK_BEFORE_HOME || !action()) performGlobalAction(GLOBAL_ACTION_HOME)
        // Look again once the cooldown is over, even if no new event arrives.
        handler.removeCallbacks(check)
        checkPending = true
        handler.postDelayed(check, KICK_COOLDOWN_MS + 100L)
    }

    private fun tiktokInFront(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return false
        val pkg = try { rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null }
        return pkg != null && pkg in TIKTOK
    }

    /** Sends the user Home and returns true when today's TikTok allowance is spent. */
    private fun enforceTiktokLimit(): Boolean {
        if (!Prefs.enabled(this, Prefs.TIKTOK)) return false
        if (Prefs.paused(this)) return false
        val limitSec = Prefs.tiktokLimitMin(this) * 60
        if (Prefs.tiktokUsedSec(this) < limitSec) return false
        val now = SystemClock.uptimeMillis()
        if (now - lastKick >= KICK_COOLDOWN_MS) {
            lastKick = now
            Prefs.addKick(this)
            performGlobalAction(GLOBAL_ACTION_HOME)
            Toast.makeText(this, "Unreel: TikTok is done for today", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    /** Look at the screen again once the pause is over (or now, if it was ended early). */
    fun recheckAfterPause() {
        handler.postDelayed({
            checkFeed()
            if (tiktokInFront()) enforceTiktokLimit()
        }, Prefs.pauseRemainingMs(this) + 500L)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
