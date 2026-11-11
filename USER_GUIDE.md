# AutoClicky — user guide

AutoClicky records what you do on your phone and replays it with one tap, from a small floating panel that stays above
every other app. This guide explains the two recording modes, how to save a macro and how to play it once or many times.

- [1. Before you start](#1-before-you-start)
- [2. The floating panel](#2-the-floating-panel)
- [3. Recording: taps or gestures?](#3-recording-taps-or-gestures)
- [4. Saving a recording](#4-saving-a-recording)
- [5. Playing a macro](#5-playing-a-macro)
- [6. Repeating a macro (×N)](#6-repeating-a-macro-n)
- [7. Your templates](#7-your-templates)
- [8. Tips and limits](#8-tips-and-limits)
- [9. Troubleshooting](#9-troubleshooting)
- [10. Support](#10-support)

## 1. Before you start

- **Android 16 (API 36) or newer.**
- Install the APK, open **AutoClicky** and tap **Open accessibility settings** → enable **AutoClicky**. Without that
  service the app can neither record nor click: it is also what makes the floating panel possible without any overlay
  permission.
- Tap **Show floating panel**. The small bar stays above every app until you press its **X**.

## 2. The floating panel

| Button | What it does |
|---|---|
| **REC** | **Tap** = record taps. **Hold ~0,5 s** = record gestures (see §3). |
| **STOP** | Stops the recording that is running, or the macro that is playing. |
| **PLAY** | Plays the template that was played last (the most recent one when nothing was played yet). |
| **×N** | Opens the **Repeat** card: how many times to play the template and how long to wait between two passes (§6). |
| **≡** | Opens the template list, to pick another template and play it. |
| **X** | Hides the panel. It comes back on its own while a macro plays. |

The panel can be **dragged** with any of its buttons: hold a button and move the finger. A short press on that same
button clicks it; holding it still triggers the alternative action of REC (gesture recording).

The panel hides itself while you record and while a card (save, repeat, template list) is open — that is normal: those
windows are full screen and need the taps.

## 3. Recording: taps or gestures?

Both modes store the **same kind of template**, and both replay the same way. They only differ in what they are able to
capture, because Android gives an app two very different views of the screen:

| | **Tap REC** — taps | **Hold REC** — gestures |
|---|---|---|
| The app you are in | stays **usable**: you tap normally | is **blocked**: the screen dims |
| What is recorded | the position of each tap and its order | the full path, duration and number of fingers |
| Typical use | menus, forms, buttons, "press 1+1=" | swipes, scrolling, long press, pinch/zoom, multi finger |
| Banner while recording | **● Recording TAPS** | **● Recording GESTURES** |

**Why the difference?** In tap mode AutoClicky does not take the touches: it lets them go to the app (so you can keep
using it) and only observes where they landed. Android then reports a position and a moment, nothing more — a swipe or a
long press cannot be reconstructed from that. In gesture mode AutoClicky takes the touches on a dimmed overlay, so it
gets every movement, but the app underneath cannot receive them anymore.

### Record taps

1. Open the app you want to automate.
2. Tap **REC** (short press). A red banner says **● Recording TAPS**, and a **Stop recording** button appears.
3. Tap what you want to replay, in the right order — the app works as usual.
4. Press **STOP** (panel) or **Stop recording**.
5. The **Save recording** card opens: press **SAVE**.

Taps are stored at the centre of the tapped button, which is more reliable than the raw finger position: it is what
makes the recording survive small finger placement differences.

### Record gestures

1. Tap **REC** and **keep the finger on the button for about half a second**. You feel a short vibration.
2. The screen dims and the banner says **● Recording GESTURES**.
3. Draw the gestures on the screen: swipe, hold, pinch… Each gesture is stored in order with its duration.
4. Press **STOP** (panel) or **Stop recording**.
5. Press **SAVE** on the card.

While gesture mode is active the app underneath is frozen: you are *describing* the gesture, not performing it.

## 4. Saving a recording

When a recording ends, the **Save recording** card opens above the app you are in:

- the subtitle tells you what was captured — **12 taps**, **1 gesture**, …;
- the proposed name is `Recording 2026-11-11 22:16`, and it can be changed later in the app (**Rename**);
- **SAVE** stores the template right away and **PLAY** will replay it, **DISCARD** throws the recording away.

A brand new template plays **once**. How many times it must play is chosen when you play it, with the **×N** button
(§6) — nothing to decide while saving.

## 5. Playing a macro

- **PLAY** on the panel: plays the template that was played last.
- **≡** on the panel: shows every template, tap one (or its ▶) to play it.
- While a macro plays, the panel stays visible so you can press **STOP** at any moment.

A macro replays **exactly** what was recorded: if the last action clears the screen (for instance `AC` on a calculator),
the screen looks unchanged after the replay — that is expected, the macro did run.

## 6. Repeating a macro (×N)

Tap **×N** on the panel to open the **Repeat** card of the last template:

| Setting | Values | Default |
|---|---|---|
| Repeat | 1× · 3× · 5× · 10× · 50× · **∞** | **1×** |
| Pause between passes | 0 s · 0,5 s · 1 s · 3 s · 10 s | 0 s |

Press **PLAY** in the card to start with those settings. The choice is **stored in the template**, so the next **PLAY**
(from the panel or from the app) uses it, even after a restart. The **×N** button always shows the current choice
(`1×`, `5×`, `∞`).

**∞ (endless)** plays the macro over and over until one of these happens:

- you press **STOP** on the panel, or
- you **touch the screen** — a one pixel watcher stops the macro at the first real finger touch. The clicks the macro
  injects itself are ignored, and so are taps on the panel itself.

> **Use a pause when you repeat.** Most apps ignore a second click that lands less than ~100 ms after the first one. With
> `0 s` a repeated macro can look like it does nothing in a calculator or a form: choose `0,5 s` or `1 s`.

## 7. Your templates

The app lists your templates **newest first**: the one you just recorded is on top. Each row shows its name, its number
of actions and its repeat setting (`1 action(s) · 5×`, `9 action(s) · ∞`) and offers:

- **Rename** — give it a meaningful name;
- **Delete** — remove it for good (not possible while a macro is playing).

Playing and repeating are done from the floating panel, so the app stays a simple library.

## 8. Tips and limits

- Macros replay at the **absolute screen coordinates** they were recorded at, so keep the app in the same orientation
  and layout, and bring it to the front before pressing PLAY. Recording on one device and playing on another will not
  work.
- Tap recording takes the **centre of the tapped view**, so a tap on a large list row replays in the middle of the row.
  Taps on a surface that no view handles (a canvas game) are not recorded.
- A recording that was made with AutoClicky **1.2.3 or older** may contain wrong tap positions: record it again.
- Gesture mode blocks the app underneath by design.
- The accessibility service reconnects after a reboot only once the phone has been unlocked for the first time.

## 9. Troubleshooting

| Symptom | What to do |
|---|---|
| The app says *Accessibility service: not connected* | Open the Android accessibility settings and enable AutoClicky again, then reopen the app. |
| No panel appears | Tap **Show floating panel** in the app; if it still does not, check the service is enabled. |
| PLAY does nothing | Make sure the target app is in the foreground and unchanged since the recording; check the template's repeat/pause (§6). |
| The macro taps the wrong place | The screen layout or the orientation changed: record it again. |
| A repeat only runs once, or too fast | Set a pause between passes (§6). |
| A recording captured only 1 action | You probably tapped very fast while the service was restarting; check the banner, then record again. |

## 10. Support

AutoClicky is free, without ads and without tracking. If it saves you some time, you can buy me a coffee — it pays for
the test phones and for the coffee that keeps the code flowing:

☕ <https://ko-fi.com/tartaruzz>

Licensed under the GNU GPL v3.0 or later — see [`LICENSE`](LICENSE). The clicking engine comes from Smart AutoClicker
(Klick'r) by Kevin Buzeau and contributors, also GPL-3.0-or-later; see [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
