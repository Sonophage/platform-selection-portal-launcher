#!/usr/bin/env bash
# Boot one of the device-matched AVDs, in the orientation that device is actually held.
#
# No runtime rotation. The app's manifest is screenOrientation="user"
# (app/src/main/AndroidManifest.xml:79), so it follows the device — and `settings put system
# user_rotation 1` does NOT rotate a headless emulator (verified: the setting reads back as 1
# while dumpsys still reports rotation 0). So the landscape devices are given landscape PANELS
# instead: 1920x1080 rather than 1080x1920. Rotation 0 is then already the right way up, on
# every boot, headless or not, and in Android Studio too.
#
# Usage:  tools/emu/emu.sh konker-elite [--headless]
#         tools/emu/emu.sh list
set -euo pipefail
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
EMU="$ANDROID_HOME/emulator/emulator"

# name : port : what it reproduces (panel is already in the orientation the device is held)
devices() {
  cat <<'EOF'
konker-elite 5556 AYANEO Pocket FIT Elite - 1920x1080 @374dpi -> 821x462dp
titan-elite  5558 Unihertz Titan Elite    - 1436x1440 @360dpi -> 638x640dp
np05j-tablet 5560 NP05J 16:10 tablet      - 2400x1504 @360dpi -> 1067x668dp
EOF
}

if [ "${1:-}" = "list" ] || [ $# -eq 0 ]; then devices; exit 0; fi

NAME="$1"; shift
row=$(devices | awk -v n="$NAME" '$1==n')
[ -n "$row" ] || { echo "unknown avd: $NAME" >&2; devices >&2; exit 1; }
PORT=$(echo "$row" | awk '{print $2}')
SERIAL="emulator-$PORT"

WINDOW=()
GPU=(-gpu host)
for a in "$@"; do
  [ "$a" = "--headless" ] && { WINDOW=(-no-window); GPU=(-gpu swiftshader_indirect); }
done

if ANDROID_SERIAL=$SERIAL adb shell true >/dev/null 2>&1; then
  echo "$NAME already running on $SERIAL"
else
  echo "booting $NAME on $SERIAL ..."
  nohup "$EMU" -avd "$NAME" -port "$PORT" -no-audio -no-snapshot \
        "${GPU[@]}" "${WINDOW[@]}" >"/tmp/emu-$NAME.log" 2>&1 &
  for _ in $(seq 1 90); do
    [ "$(ANDROID_SERIAL=$SERIAL adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
    sleep 5
  done
fi

# Pin the display to the panel's NATURAL orientation, every boot.
#
# Not optional and not a no-op: user_rotation persists in the AVD's userdata, so one stray
# `settings put system user_rotation 1` leaves every later boot rotated 90 degrees off — which
# on a landscape panel means the launcher comes up PORTRAIT and reports sw462dp w462dp h821dp.
# That is the config the app would never see on the real handheld, and nothing about the
# emulator's own output says it is wrong. accelerometer_rotation goes off first, or the sensor
# puts it back.
ANDROID_SERIAL=$SERIAL adb shell settings put system accelerometer_rotation 0
ANDROID_SERIAL=$SERIAL adb shell settings put system user_rotation 0
sleep 2

# Report what the app will actually see.
#
# This reads the WindowManager configuration, not `wm size`. `wm size` prints the panel
# unrotated, so it says 1920x1080 just as happily when the launcher is coming up portrait.
# The `wNNNdp hNNNdp` line is the one the layout code is handed.
CONF=$(ANDROID_SERIAL=$SERIAL adb shell dumpsys window 2>/dev/null \
       | grep -oE "sw[0-9]+dp w[0-9]+dp h[0-9]+dp [0-9]+dpi" | head -1)
echo "serial   : $SERIAL"
echo "config   : ${CONF:-<unavailable — is the device booted?>}"
echo "expected : $(echo "$row" | cut -d' ' -f3-)"
