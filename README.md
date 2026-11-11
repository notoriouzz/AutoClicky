# AutoClicky

A small Android auto-clicker: record the taps (or full gestures) you want to repeat, save them as a template, and let
the app replay them through an accessibility service.

The clicking engine, gesture dispatching and storage come from the GPL-3.0 **Smart AutoClicker (Klick'r)** project,
vendored into this repository (see [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)). The app UI, the recorder and the
template library are specific to AutoClicky.

> AutoClicky is licensed under the **GNU GPL v3.0 or later** — see [`LICENSE`](LICENSE).

## Features

- **Record taps while you keep using your apps** — a pass-through watcher window captures the taps you make on other
  apps, in order, at their real screen coordinates.
- **Record full gestures** — a dimmed overlay captures taps, long presses, swipes and multi-finger gestures with their
  real timings.
- **Floating panel above every app** — a small draggable **REC / STOP / PLAY / LIST / X** bar
  (`TYPE_ACCESSIBILITY_OVERLAY`) stays on top of the app you are using: start a recording, stop it, save it on the spot,
  and pick any stored template to replay — all without going back to AutoClicky. It appears on its own while a template
  is playing.
- **Save where you are** — as soon as a capture started from the panel ends, a **Save recording** card with the name
  field opens above the current app; one tap stores it as a template.
- **Templates** — every recording is stored in a Room database and listed in the app: play / stop, duplicate, rename and
  delete.
- **Quick test** — a one-click "click at the screen center" scenario to check that the accessibility service works.
- **About & license** — the app shows its GPL-3.0 text and the upstream credits (the license file is bundled in the APK).
- **No root, no media projection, no overlay permission** — everything runs through a single accessibility service.

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
3. Record:
   - **Record taps (keep using your apps)** — switch to the app you want to automate, tap the targets in the order you
     want them replayed, then press the floating **Stop recording** button. Only taps are captured in this mode.
   - **Record gestures (swipes, long press, multi finger)** — a dimmed overlay covers the screen: draw the gestures on
     it, then press **Stop recording**. The app underneath cannot be used while this mode is active.
4. Give the recording a name. It is now a template in the list.
5. Press **Play** on a template to replay it once (stop it any time with **Stop**).

### Recording and replaying from the floating panel

Tap **Show floating panel** in the main screen (the panel also appears on its own while a template runs). A small
draggable bar with **REC / STOP / PLAY / LIST / X** then stays above every other app:

1. **REC** — starts a tap recording, exactly like *Record taps*: open any app, tap what you want to replay, then press
   **STOP** on the panel (or the floating **Stop recording** button).
2. The **Save recording** card opens immediately, above the app you are in: change the name if you want and tap **Save**
   — one tap is enough, the proposed name is already filled in. The template is stored right away, without opening
   AutoClicky. **Cancel** keeps the recording pending, so the main screen still offers to save it.
3. **PLAY** — replays the template that was played last (the first one when none was played yet) above the app you are
   in.
4. **LIST** — opens the template list above the current app: tap a row (or its ▶) to play that template.

**STOP** stops a recording or a running template, **X** hides the panel for the current run. While a template runs, the
panel stays visible (with the running template's name in the list) so it can be stopped from any app.

The panel is hidden while a capture overlay is active (both recording modes already show their own **Stop recording**
button) and comes back as soon as the capture ends.

## How the recording works

```
TapRecordingOverlay   (1 px, FLAG_WATCH_OUTSIDE_TOUCH)  ─┐
                                                          ├─> RecordingController ──> ScenarioRecorder
GestureRecordingOverlay (full screen MotionEvent capture)─┘                              │
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
  still receives the touch), which is why the live mode can only record taps.
- Full gestures are captured from a touchable accessibility overlay, then converted into the engine model:
  `DumbAction.DumbClick` for taps, `DumbAction.DumbGesture` with `DumbGestureFinger`s (`Press`/`Move`/`Wait`/`Release`)
  for everything else. Actions keep their order through `priority`, and the idle time between two actions is stored as
  the previous action's `repeatDelayMs`.
- Recorded templates play a single pass (`repeatCount = 1`, finite), unlike the quick-test scenario which clicks until
  stopped.

## Project structure

```
AutoClicky/
├── app/                                  AutoClicky application (Compose UI, service, recorder)
│   └── src/main/java/nl/tartaruzz/autoclicky/
│       ├── engine/                       EngineController, DefaultScenarioFactory
│       ├── overlay/                      floating panel, save dialog and template picker (accessibility overlays)
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

- Live tap recording captures taps only (no duration, movement or extra fingers), a platform limitation.
- Gesture recording blocks the app underneath while it is active.
- Idle time between actions is clamped to the engine's maximum delay (1 hour); a single gesture is limited to ~59 s.
- After a reboot the accessibility service reconnects only once the device has been unlocked for the first time: Android
  ignores accessibility services that are not `directBootAware` while the credential-encrypted storage is locked, and
  AutoClicky cannot opt in because its templates live in the credential-encrypted database.
- The floating panel only records **taps** (the same platform limitation as *Record taps*); for swipes and multi-finger
  gestures use *Record gestures* in the activity.
- Changing the name in the floating **Save recording** card needs the soft keyboard inside an accessibility overlay, which
  some manufacturers refuse. **Save** always works with the proposed name in that case; rename the template afterwards in
  the app.
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
