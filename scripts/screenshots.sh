#!/usr/bin/env bash
# Installs the APK on a running emulator, tests blocking against the emulator's own
# YouTube app, and captures the settings screen in each state.
# Usage: scripts/screenshots.sh path/to/app.apk output-dir
set -u
APK="$1"
OUT="$2"
PKG=app.klid
SERVICE="$PKG/$PKG.BlockerService"
YT=com.google.android.youtube
mkdir -p "$OUT"

adb install -r "$APK"
for s in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb shell settings put global "$s" 0
done

shot() { sleep 2; adb exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }
# Restarts the screen without force-stopping the app: Android disables an app's
# accessibility service when the app is force-stopped.
launch() { adb shell am start -W -f 0x10008000 -n "$PKG/.MainActivity" > /dev/null; sleep 2; }
service_on() {
  adb shell settings put secure enabled_accessibility_services "$SERVICE"
  adb shell settings put secure accessibility_enabled 1
  sleep 3
}
service_off() {
  adb shell settings delete secure enabled_accessibility_services > /dev/null
  adb shell settings put secure accessibility_enabled 0
  sleep 2
}
# The current screen as UI XML (fails on screens that never stop animating).
dump() { timeout 40 adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb exec-out cat /sdcard/ui.xml; }
# Taps the centre of the first element whose text or content description is exactly $1.
tap_on() {
  local bounds
  bounds=$(dump | tr '>' '\n' | grep -E "(text|content-desc)=\"$1\"" | head -1 \
    | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/')
  if [ -z "$bounds" ]; then echo "no element: $1"; return 1; fi
  set -- $bounds
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}
scroll_down() { adb shell input swipe 540 1800 540 500 300; sleep 1; }
# YouTube's Shorts tab: by its label, or by where it sits in the bottom bar.
tap_shorts() { tap_on "Shorts" || adb shell input tap 324 2270; }

# ---- Does blocking work? Try it on the emulator's YouTube app. ----
if adb shell pm path "$YT" > /dev/null 2>&1; then
  adb shell pm grant "$YT" android.permission.POST_NOTIFICATIONS > /dev/null 2>&1
  service_off
  adb shell monkey -p "$YT" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
  sleep 20
  shot yt-1-home
  dump > "$OUT/yt-home.xml"
  tap_shorts
  sleep 12
  shot yt-2-shorts-with-unreel-off
  dump > "$OUT/yt-shorts.xml"
  service_on
  sleep 8
  shot yt-3-after-turning-unreel-on
  tap_shorts
  sleep 8
  shot yt-4-shorts-again-with-unreel-on
  adb logcat -d -s Unreel:I > "$OUT/unreel-log.txt"
  adb shell input keyevent KEYCODE_HOME
else
  echo "YouTube is not installed on this emulator image"
fi

# ---- The settings screen ----
for mode in light dark; do
  if [ "$mode" = dark ]; then adb shell cmd uimode night yes; else adb shell cmd uimode night no; fi
  sleep 2

  service_off
  launch
  shot "$mode-01-off"
  if [ "$mode" = light ]; then
    tap_on "Turn on in Accessibility settings" && shot "light-02-before-turning-on"
    adb shell input keyevent KEYCODE_BACK
  fi

  service_on
  launch
  tap_on "YouTube"
  shot "$mode-03-youtube"
  scroll_down
  shot "$mode-04-youtube-chart"
  launch
  tap_on "Instagram" && shot "$mode-05-instagram"
  tap_on "TikTok" && shot "$mode-06-tiktok"
  scroll_down
  shot "$mode-07-tiktok-chart"

  launch
  tap_on "Pause for 5 minutes" && shot "$mode-08-paused"
  tap_on "End pause now"

  launch
  tap_on "YouTube"
  tap_on "Shorts" && shot "$mode-09-confession"
  adb shell input keyevent KEYCODE_BACK
  adb shell input keyevent KEYCODE_BACK
done

# Tablet width.
adb shell cmd uimode night no
adb shell wm size 1600x2560
adb shell wm density 320
launch
shot "light-10-tablet"
adb shell wm size reset
adb shell wm density reset
ls -la "$OUT"
