# Device-matched emulators

Three AVDs whose panels match the hardware this app is actually used on, so a layout bug can be
reproduced without the device in hand.

```sh
tools/emu/emu.sh list                  # what exists
tools/emu/emu.sh konker-elite          # boot it, with a window
tools/emu/emu.sh np05j-tablet --headless
```

The script prints the WindowManager configuration after boot. That is the check — if the line it
prints does not match the `expected` line under it, the AVD is not reproducing the device and
anything measured on it is worthless.

| AVD | panel | app config | reproduces |
|---|---|---|---|
| `konker-elite` | 1920×1080 @374dpi | `sw462dp w821dp h462dp` | AYANEO Pocket FIT Elite — real device reports 822×462dp (1dp of rounding) |
| `titan-elite` | 1436×1440 @360dpi | `sw638dp w638dp h640dp` | Unihertz Titan Elite — exact |
| `np05j-tablet` | 2400×1504 @360dpi | `sw668dp w1067dp h668dp` | NP05J 16:10 tablet — exact, measured off the device 2026-09-25 |

Ports are fixed (5556 / 5558 / 5560) so all three can run at once and `ANDROID_SERIAL` is
predictable.

## Two things that were got wrong building these, so they do not get got wrong again

**The landscape devices have landscape PANELS.** `hw.initialOrientation=landscape` does nothing
under `-no-window`, and the app's manifest is `screenOrientation="user"`
(`app/src/main/AndroidManifest.xml:79`) so it follows the device rather than forcing itself
round. Setting `hw.lcd.width` to the long edge is what makes rotation 0 already correct.

**`user_rotation` persists in the AVD's userdata.** One stray `settings put system user_rotation 1`
leaves every later boot rotated 90° off — on a landscape panel that means the launcher comes up
portrait reporting `w462dp h821dp`, which is a configuration the real handheld never produces.
Nothing in the emulator's own output flags this. `emu.sh` pins rotation to natural on every boot
for that reason; it is not a no-op.

**Do not trust `wm size` for this.** It prints the panel unrotated, so it says `1920x1080` just as
happily while the launcher is drawing portrait. `emu.sh` reads the `wNNNdp hNNNdp` line out of
`dumpsys window` instead, because that is the one the layout code is handed.

## What these do and do not reproduce

They reproduce **geometry** — dp size, density, aspect — which is what the chrome-band, dead-space
and responsive work is about. All three run **`system-images;android-36;google_apis;x86_64`**,
matching the real NP05J tablet (Android 16 / API 36) and one above the app's `targetSdk` of 35.

For dummy content to put on them, see [CONTENT.md](CONTENT.md).

They do **not** reproduce: the Konker's attached gamepad, the Titan's physical or capacitive
keyboard, arm64 native code paths, or real GPU shader behaviour (`--headless` uses swiftshader).
Anything about input hardware or the wave's performance still needs the device.
