# AutoClicky

A small Android auto-clicker: record the taps (or full gestures) you want to repeat, save them as a template, and let
the app replay them through an accessibility service.

The clicking engine, gesture dispatching and storage come from the GPL-3.0 **Smart AutoClicker (Klick'r)** project,
vendored into this repository (see [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)). The app UI, the recorder and the
template library are specific to AutoClicky.

> AutoClicky is licensed under the **GNU GPL v3.0 or later** — see [`LICENSE`](LICENSE).

> **New here?** Read the **[user guide](USER_GUIDE.md)**: the difference between the two REC modes, saving a macro and
> making it run 1×, 5× or endlessly.

## Features

- **Record taps while you keep using your apps** — a pass-through watcher window captures the taps you make on other
  apps, in order, at their real screen coordinates. When the app does not report them (a game, a video, a device that
  reports no touch position) the capture switches on its own to a relay overlay that receives the touches and sends them
  back to the app, so recording works everywhere instead of silently doing nothing.
- **Floating panel above every app** — a small draggable **REC / STOP / PLAY / ×N / LIST / X** bar
  (`TYPE_ACCESSIBILITY_OVERLAY`) stays on top of the app you are using: record a macro, save it automatically, and replay
  any stored template — all without going back to AutoClicky. It appears on its own while a template is playing.
- **Save where you are** — a recording is saved as soon as you tap the ✕ under the red dot, with a **Recording saved**
  toast (or **No taps recorded** when nothing was captured). No card, no naming: **PLAY** replays it right away. A new
  template plays once; set the repeat with **×N** when you play it.
- **Repeat** — play a macro once, 3×, 5×, 10×, 50× or endlessly, with a pause between the passes. The choice is stored in
  the template. A green frame outlines the whole screen while it plays, and **touching the screen stops it immediately**.
- **Templates** — every recording is stored in a Room database and listed in the app, where it can be pinned, renamed or
  deleted. At most five **unpinned** recordings are kept: saving a sixth removes the oldest unpinned one, pinned
  recordings are never removed automatically. The template list is also one tap away on the floating panel.
- **About & license** — the app shows its GPL-3.0 text and the upstream credits (the license file is bundled in the APK).
- **No root, no media projection, no overlay permission** — everything runs through a single accessibility service.

Made by **Notoriouzz** — <https://github.com/notoriouzz/AutoClicky>.

## Support

AutoClicky is free — no ads, no subscription, no tracking. If it saves you a few taps a day, treat me to a coffee:
donations start at **€1**, and every cup is 0% code but 100% motivation. ☕

<https://ko-fi.com/tartaruzz>

## Relationship with Smart AutoClicker (is this a fork?)

Short answer: **it is not a GitHub fork**. AutoClicky is a *derivative work*: the source code of
[Smart AutoClicker (Klick'r)](https://github.com/Nain57/Smart-AutoClicker) is vendored (copied) into `core/`, and this
repository has its own, independent Git history — so GitHub shows no "forked from …" banner.

GitHub's *Fork* button is only a platform feature (it duplicates the repository and links it to its parent); it is not
something the GPL requires. The GPL-3.0 obligations are met by:

- the upstream copyright header in every vendored file,
- `THIRD_PARTY_NOTICES.md` with the upstream project, revision (`699bff8`), module mapping and the exact changes made,
- the credit in this README and in the in-app **About & license** dialog,
- publishing the whole work under the GPL-3.0-or-later.

If you ever want the GitHub "fork" association (for example to open pull requests upstream), create a real fork of
`Nain57/Smart-AutoClicker` and put AutoClicky on a branch there. For this repository, the attribution above is correct
and sufficient.

## Requirements

| Tool | Version |
|---|---|
| Android Studio | latest (AGP 9 project) |
| JDK | 21+ (Gradle daemon JVM is pinned by `gradle/gradle-daemon-jvm.properties`) |
| Gradle | 9.6.0 (wrapper included) |
| Android Gradle Plugin | 9.5.1 (built-in Kotlin 2.2.10) |
| Android SDK | platform 37 (compile/target), device or emulator with **API 36+** |

The app targets `minSdk 36`, `targetSdk 37` and uses only APIs available on those versions (no desugaring needed).

## Build and test

The SDK location comes from `local.properties` (`sdk.dir=...`).

```bash
# Debug APK
./gradlew :app:assembleDebug          # Windows: gradlew.bat :app:assembleDebug

# Unit tests (recorder logic: stroke collection, tap/gesture classification)
./gradlew :app:testDebugUnitTest

# Release APK (R8 + resource shrinking)
./gradlew :app:assembleRelease
```

Outputs:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk` — signed and installable (see below)
- `app/build/outputs/apk/release/app-release-unsigned.apk` — only when no signing credentials are available

### Release signing

The release APK is signed with a keystore that is **not** part of this repository. Credentials are read from the
git-ignored `keystore.properties` in the repository root:

```properties
storeFile=C:/path/to/autoclicky-release.jks
storePassword=…
keyAlias=autoclicky
keyPassword=…
```

or from the `AUTOCLICKY_KEYSTORE`, `AUTOCLICKY_KEYSTORE_PASSWORD`, `AUTOCLICKY_KEY_ALIAS` and
`AUTOCLICKY_KEY_PASSWORD` environment variables (handy for CI). Without them the release build stays unsigned and cannot
be installed on a device. Keystores, `keystore.properties` and `*.jks` are listed in `.gitignore`: never commit them,
and keep a safe backup — Android refuses to update an app whose APK is signed with a different key.

## Install the app (APK)

Each published version is available as a signed APK on the **Releases** page of this repository:

1. Download `AutoClicky-<version>.apk` from the latest release.
2. Allow "install unknown apps" for the browser or file manager when Android asks.
3. Open the downloaded file and install it.

Requirements: **Android 16 (API 36) or newer**, because the app is built with `minSdk 36`. After installing, open
AutoClicky and enable its accessibility service (**Open accessibility settings → AutoClicky**), otherwise nothing can
be recorded or replayed.

## Usage

1. Install and open AutoClicky.
2. Tap **Open accessibility settings**, find **AutoClicky** and enable its accessibility service. Without it no gesture
   can be executed and no recording can be captured.
3. Tap **Show floating panel**: recording and playing are done from there, above whatever app you are using.
4. Record: press **REC**, tap the targets in the order they must be replayed, then tap the **✕** under the red dot.
5. The recording is saved automatically (**Recording saved**) and becomes the one **PLAY** starts.
6. Press **PLAY** (or **×N** to change the repeat) to replay it. The templates are listed in the app, where they can be
   pinned, renamed or deleted.

### Recording and replaying from the floating panel

Tap **Show floating panel** in the main screen (the panel also appears on its own while a template runs). A small
draggable bar with **REC / STOP / PLAY / ×N / LIST / X** then stays above every other app:

1. **REC** — starts a **tap** recording: open any app, tap what you want to replay, then tap the **✕** under the blinking
   red dot. The recording is saved on the spot and **PLAY** will replay it.
2. **Hold REC** (~0,5 s) — starts a **gesture** recording instead: the screen dims and you draw the gestures (swipes,
   long press, multi finger) the template must replay, then tap the **✕**. The red dot blinks here too, and the app
   underneath cannot be used while this mode is active (the overlay swallows the touches).
3. A new template plays **once**; set the repeat with **×N** when you play it.
4. **PLAY** — replays the template that was played last (the first one when none was played yet) above the app you are
   in. A **green frame** outlines the whole screen while it runs.
5. **LIST** — opens the template list above the current app: tap a row (or its ▶) to play that template.
6. **×N** — opens the **Repeat** card for the last template: choose how many times it must be played
   (1× / 3× / 5× / 10× / 50× / **∞**) and how long the engine waits between two passes (0 s / 0.5 s / 1 s / 3 s / 10 s),
   then press **PLAY**. The choice is stored in the template itself, so the next **PLAY** (from the panel or from the
   app) uses the same repeat. The button label always shows the current choice (`5×`, `∞`).

While a template plays, **touching the screen stops it immediately** (a one pixel pass-through watcher detects the
finger). The gestures the engine injects itself are ignored (they carry no tool type, unlike a real touch), and so are
the touches on the AutoClicky panel and cards. The screen turning off also stops the playback.

A repeat needs a pause between the passes for most apps: two taps 50 ms apart are usually collapsed by the target app —
set at least `0.5 s`/`1 s` when the template ends on a button of a calculator or of a form.

**STOP** stops a recording or a running template, **X** hides the panel for the current run. While a template runs, the
panel stays visible (with the running template's name in the list) so it can be stopped from any app.

The panel is hidden while a capture overlay is active (both recording modes already show their own **Stop**
button) and comes back as soon as the capture ends.

## How the recording works

```
TapRecordingOverlay   (1 px, FLAG_WATCH_OUTSIDE_TOUCH)  ─┐
RelayRecordingOverlay (full screen, touchable)          ─┼─> RecordingController ──> ScenarioRecorder
GestureRecordingOverlay (full screen MotionEvent capture)┘                              │
                                                                                        v
                                                                        DumbScenarioBuilder (DumbScenario)
                                                                                         │
                                                                                         v
                                                        IDumbRepository  ──>  Room "dumb_database"
                                                                                         │
                                                                                         v
                                                                     DumbEngine + AndroidActionExecutor
                                                                        (AccessibilityService.dispatchGesture)
```

- Android delivers exactly one `ACTION_OUTSIDE` per gesture made outside the watcher window (and the underlying app
  still receives the touch), which is why the pass-through mode can only record taps.
- The position of a tap comes from the accessibility click event of the view that received it: `ACTION_OUTSIDE` reports
  no coordinates at all on several devices (Samsung reports `0,0` for every touch). Both sources are paired when the
  capture ends, one action per gesture, so a fast sequence keeps every tap instead of collapsing into one. The pairing
  also gives the press duration (down from the watcher, release from the click), so a long press stays a long press.
- When neither source can place a tap — no coordinates and a click that never comes, which is what a game, a canvas or a
  video player does — `RecordingController` switches to `RelayRecordingOverlay` for the rest of the capture. It is the
  only technique left without root: the overlay receives the touches and `dispatchGesture` sends them back.
- The service declares `android:notificationTimeout="0"`: with a non zero value, the system aggregates the click events
  of the same window during that window and only the last one of a burst is delivered — a fast recording then produced a
  single action.
- Full gestures are captured from a touchable accessibility overlay, then converted into the engine model:
  `DumbAction.DumbClick` for taps, `DumbAction.DumbGesture` with `DumbGestureFinger`s (`Press`/`Move`/`Wait`/`Release`)
  for everything else. Actions keep their order through `priority`, and the idle time between two actions is stored as
  the previous action's `repeatDelayMs`.
- A template stores its own repeat in the `dumb_scenario` table (`repeatCount`, `isRepeatInfinite`); the pause between
  two passes is the `repeatDelayMs` of the last action, because the engine waits that delay after each action it
  executes. Templates recorded before 1.2.4 keep playing a single pass.

## Project structure

```
AutoClicky/
├── app/                                  AutoClicky application (Compose UI, service, recorder)
│   └── src/main/java/nl/tartaruzz/autoclicky/
│       ├── engine/                       EngineController, RepeatSetting
│       ├── overlay/                      floating panel and template picker (accessibility overlays)
│       ├── recording/                    recorder overlays + pure recording logic
│       ├── service/AutoClickService.kt   accessibility service (gesture executor host)
│       └── ui/                           Compose screens
├── core/                                 vendored Smart AutoClicker (Klick'r) modules
│   ├── common/base                       logging/dump, data store, identifiers, workarounds
│   ├── common/settings                   user settings repository (DataStore)
│   ├── common/actions                    gesture building & dispatching (AndroidActionExecutor)
│   ├── common/bitmaps                    bitmap cache
│   ├── common/display                    display config + media projection recording
│   ├── common/ui                         shared views (view binding)
│   ├── common/overlays                   overlay windows, menus, dialogs
│   ├── common/permissions                permission models and dialogs
│   └── dumb                              dumb (non "smart") scenario engine, Room database
├── THIRD_PARTY_NOTICES.md                provenance and modifications of the vendored code
└── LICENSE                               GNU GPL v3.0
```

Dependency flow of the vendored code (`A ──▶ B` means *A depends on B*):

```
:dumb ──▶ :core:common:actions ──▶ :core:common:base
                             └──▶ :core:common:permissions ──▶ :core:common:overlays ──▶ :core:common:ui ──▶ :core:common:display ──▶ :core:common:bitmaps ──▶ :core:common:base
                                                                              └──▶ :core:common:settings ──▶ :core:common:base
```

## Notes for contributors

- The vendored modules keep their upstream namespaces (`com.buzbuz.smartautoclicker.core.*`, and
  `com.smartautoclicker.core.dumb` for `:core:dumb`) so upstream fixes stay easy to apply.
- Their build scripts were rewritten for the AGP 9 DSL: the upstream `build-logic` convention plugins and the
  `playStore`/`fDroid` flavours are gone, kapt-free KSP is used for Hilt and Room.
- `android.nonTransitiveRClass=false` is required: the vendored modules reference resources through their own `R` class.
- Two upstream files were adjusted for the Kotlin 2.2 compiler (`return` inside expression-body functions):
  `core/common/actions/.../text/TextExecutor.kt` and `core/common/ui/.../gesture/edit/GestureEditorOverlayView.kt`.
- Unit tests only cover the pure recording logic (`app/src/test/.../recording`); the vendored modules' tests were not
  copied.

## Known limitations

- The pass-through tap recording captures taps only (no duration, movement or extra fingers), a platform limitation:
  `ACTION_OUTSIDE` is delivered once per gesture. A long press is still recognised from the click event of the view, and
  when the app reports neither, the capture switches to the relay overlay, which records everything.
- The relay overlay forwards each gesture **after** it was released (Android cancels any gesture in progress when one is
  injected, see `AccessibilityService.dispatchGesture`): the app reacts a moment later, and a touch made while the
  gesture is being sent back (about a tenth of a second, measured on a Galaxy S22 / Android 16) goes straight to the app
  without being recorded. Without root this is the only way to both see and forward a touch, and it is what makes
  recording work in games and in any app that draws its own interface.
- The floating panel only records **taps** (a platform limitation of the pass-through watcher) unless **REC** is held, in
  which case the gestures drawn on the dimmed overlay are recorded instead — the app underneath is blocked during that
  mode.
- Idle time between actions is clamped to the engine's maximum delay (1 hour); a single gesture is limited to ~59 s.
- After a reboot the accessibility service reconnects only once the device has been unlocked for the first time: Android
  ignores accessibility services that are not `directBootAware` while the credential-encrypted storage is locked, and
  AutoClicky cannot opt in because its templates live in the credential-encrypted database.
- A recording is saved automatically under a generated name (**Recording saved**) when the ✕ is tapped; rename it in the
  app if you want a meaningful name. There is no naming step anymore: an editable field in an accessibility overlay would
  open the keyboard and, on several devices, leave the card blocking the screen.
- Recordings that are not pinned are capped at **five**: saving a sixth removes the oldest unpinned one. Pinned
  recordings are never removed automatically, and neither is the one currently selected for playback.
- Playback can be stopped by a finger touch and by the screen turning off. The volume keys and a power press that keeps
  the screen on are **not** visible to a normal accessibility service and are therefore not detected.
- Templates replay at the absolute screen coordinates they were recorded at: keep the target app in the same orientation
  and layout, and bring it to the foreground before pressing PLAY.
- The pass-through capture stores the **centre of the clicked view** (from the accessibility click event), not the raw
  touch point: `ACTION_OUTSIDE` reports no coordinates at all on several devices (Samsung reports `0,0`, which made every
  recorded tap land in the top left corner). An app that exposes no clickable view is captured by the relay overlay.
- A template replays **all** its actions, including the last one. Ending a recording with a "clear" action (for instance
  `AC` on a calculator, or "back" in an app) means the screen looks unchanged after the playback: choose a last action
  that leaves something visible if you want to check the result.
- The vendored overlay **menu** modules (`OverlayManager`, `ItemsBriefMenu`, …) compile and are available, but AutoClicky
  does not show Klick'r's menu: the recorder and the floating panel use their own lightweight windows.

## License

AutoClicky is licensed under the **GNU General Public License v3.0 or later** (`GPL-3.0-or-later`), because it embeds
code from Smart AutoClicker (Klick'r).

| Where | What |
|---|---|
| `LICENSE` | Full text of the GPL v3.0 |
| `app/src/main/res/raw/gpl_3_0.txt` | The same text, bundled in the APK and shown in the app (**About & license**) |
| `THIRD_PARTY_NOTICES.md` | Upstream project, revision, vendored modules and the list of modifications |
| `/* SPDX-License-Identifier: GPL-3.0-or-later */` | Header on every AutoClicky source file; vendored files keep their own upstream headers |

Third-party libraries are **not** GPL: AndroidX, Compose, Kotlin, kotlinx, Hilt/Dagger, KSP and Material Components are
Apache-2.0 (shipped in the APK), and JUnit 4 is EPL-1.0 but only used by the JVM unit tests, so it never reaches a built
artifact. Apache-2.0 is compatible with the GPL, EPL-1.0 is not — do not move JUnit to an `implementation` dependency.

### Distributing the app

If you hand an APK to anyone (Play Store, GitHub release, a friend's phone) the GPL requires you to:

1. make the **complete corresponding source code** of that exact build available (this repository + any local change),
   under the GPL-3.0-or-later;
2. keep the copyright notices, `LICENSE`, `THIRD_PARTY_NOTICES.md` and the in-app license (About & license) intact;
3. not add restrictions on top of the GPL — the app cannot be relicensed as proprietary, MIT or Apache.

The repository itself may be private: the GPL does not require public hosting, only that the people who receive the
APK can obtain the corresponding source (for example by being given access to this repository or a source archive).
