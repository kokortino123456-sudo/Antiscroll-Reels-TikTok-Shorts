#!/usr/bin/env bash
# Installs the APK on a running emulator and captures the settings screen in each state.
# Usage: scripts/screenshots.sh path/to/app.apk output-dir
set -u
APK="$1"
OUT="$2"
PKG=app.klid
SERVICE="$PKG/$PKG.BlockerService"
mkdir -p "$OUT"

adb install -r "$APK"
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

shot() { sleep 2; adb exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }
launch() { adb shell am force-stop "$PKG"; adb shell am start -W -n "$PKG/.MainActivity" > /dev/null; sleep 2; }
service_on() {
  adb shell settings put secure enabled_accessibility_services "$SERVICE"
  adb shell settings put secure accessibility_enabled 1
}
service_off() {
  adb shell settings delete secure enabled_accessibility_services > /dev/null
  adb shell settings put secure accessibility_enabled 0
}
# Taps the centre of the first element whose text is exactly $1.
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null
  local bounds
  bounds=$(adb exec-out cat /sdcard/ui.xml | tr '>' '\n' | grep -F "text=\"$1\"" | head -1 \
    | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/')
  if [ -z "$bounds" ]; then echo "no element with text: $1"; return 1; fi
  set -- $bounds
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}
scroll_down() { adb shell input swipe 540 1800 540 600 300; sleep 1; }

for mode in light dark; do
  if [ "$mode" = dark ]; then adb shell cmd uimode night yes; else adb shell cmd uimode night no; fi
  sleep 2

  service_off
  launch
  shot "$mode-1-off"

  service_on
  launch
  shot "$mode-2-on"
  scroll_down
  shot "$mode-3-on-bottom"

  launch
  tap_text "Pause for 5 minutes" && shot "$mode-4-paused"
  tap_text "End pause now"

  launch
  tap_text "TikTok" && shot "$mode-5-confession"
  adb shell input keyevent KEYCODE_BACK
  adb shell input keyevent KEYCODE_BACK
done

# A tablet-width check: same screen at a wide resolution.
adb shell cmd uimode night no
adb shell wm size 1600x2560
adb shell wm density 320
launch
shot "light-6-wide"
adb shell wm size reset
adb shell wm density reset
ls -la "$OUT"
