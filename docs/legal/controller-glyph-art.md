# Controller glyph art

The button pictures in `core-ui/src/main/res/drawable-nodpi/ctl_*` come from **Xelu's Free
Controller & Keyboard Prompts**, released under **Creative Commons Zero (CC0)**.

- Source: <https://thoseawesomeguys.com/prompts/>
- Licence: CC0 — public domain, commercial use allowed, attribution not required
- Downloaded: 2026-09-24, pack dated 2024-08-13
- Processing: resized to 128×128 PNG to match what this module already shipped

## Which set each family uses, and why

| Family | Pack folder | Look |
|---|---|---|
| `XBOX` | `Xbox Series/XboxSeriesX_*` | Grey button, **coloured letter** |
| `PLAYSTATION` | `Others/PS4/PS4_*` | Dark button, **coloured symbol** |
| `NINTENDO` | `Switch/Switch_*` | Grey, uncoloured |

The PlayStation set is the DualShock 4 rather than the pack's PS5 art, **deliberately**: a
DualSense prints its four symbols in white, so accurate PS5 art has no colour in it at all. The
owner asked for PlayStation to be coloured, and the coloured PlayStation is the PS4.

Nintendo is uncoloured because a Switch pad is. Colouring it would be inventing a convention
rather than following one.

## What these are NOT

They are not first-party art. Microsoft, Sony and Nintendo's own button glyphs are trademarked and
are licensed to developers under their platform agreements; shipping them in a sideloaded launcher
is not something this project can claim a right to. Xelu's pack is the CC0 reconstruction that
games have used for years, and it is close enough that the shape and colour read correctly.

## `KEYBOARD` and `TOUCH` ship no art

They resolve through the printed-label fallback in `ControllerButtonGlyph`, drawn in the
launcher's own rounded-square badge — the same shape the context rail and the app drawer use.
That was a decision, not a gap: those two should look like this app rather than like a fourth
vendor.
