#!/usr/bin/env bash
# Installs the APK on a running emulator and captures the settings screen in each state.
# (Blocking itself can't be tested here: the emulator's built-in YouTube is too old for
# YouTube's servers, and Instagram and TikTok aren't installed.)
# Usage: scripts/screenshots.sh path/to/app.apk output-dir
set -u
APK="$1"
OUT="$2"
PKG=app.klid
SERVICE="$PKG/$PKG.BlockerService"
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

# ---- The settings screen ----
for mode in dark; do
  if [ "$mode" = dark ]; then adb shell cmd uimode night yes; else adb shell cmd uimode night no; fi
  sleep 2

  service_off
  launch
  shot "$mode-01-off"
  if true; then
    tap_on "Turn on in Accessibility settings" && shot "$mode-02-before-turning-on"
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
shot "tablet"
adb shell wm size reset
adb shell wm density reset
ls -la "$OUT"
