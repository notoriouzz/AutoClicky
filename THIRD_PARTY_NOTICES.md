# Third party notices

## Smart AutoClicker (Klick'r)

AutoClicky embeds source code copied from **Smart AutoClicker (Klick'r)**:

- Upstream project: <https://github.com/Nain57/Smart-AutoClicker>
- Author: Kevin Buzeau and contributors
- Upstream revision: `699bff818519bc418addb7f0bdad5fbfb6c3f720` (2026-10-23, application version 4.1.2)
- License: GNU General Public License v3.0 or later (see `LICENSE`)

The following Gradle projects are vendored as-is (Kotlin/Java sources, manifests, resources and Room schemas), and are
therefore distributed under the same GPL-3.0-or-later terms. Every file keeps its original copyright header.

| Module in AutoClicky       | Upstream module                          |
|----------------------------|------------------------------------------|
| `:core:common:base`        | `core/common/base`                       |
| `:core:common:settings`    | `core/common/settings`                   |
| `:core:common:actions`     | `core/common/actions`                    |
| `:core:common:bitmaps`     | `core/common/bitmaps`                    |
| `:core:common:display`     | `core/common/display`                    |
| `:core:common:ui`          | `core/common/ui`                         |
| `:core:common:overlays`    | `core/common/overlays`                   |
| `:core:common:permissions` | `core/common/permissions`                |
| `:core:dumb`               | `core/dumb`                              |

### Modifications made while vendoring

1. **Build scripts rewritten** (`build.gradle.kts` of every vendored module): the upstream convention plugins
   (`com.buzbuz.gradle.*` from `build-logic`) and the `playStore`/`fDroid` product flavours were dropped. Each module now
   uses the plain AGP 9 DSL (`com.android.library`), KSP, the Hilt Gradle plugin, Kotlin serialization and Room where
   upstream did, with `compileSdk 37` and a `minSdk 24` (the AutoClicky app itself targets `minSdk 36`).
2. **Product flavours flattened**: `core/common/base/src/fDroid/**` was merged into `src/main/**` and the `playStore`
   sources (Firebase Crashlytics implementation of `setCrashKey`, `logCrashBreadcrumb`, `throwWithKeys`,
   `recordNonFatal`) were not vendored. The flavour-agnostic, `Log`-based implementations are used instead.
3. **Unit tests of the vendored modules were not copied** (they rely on the upstream convention plugin test
   dependencies). AutoClicky has its own unit tests for the recorder.
4. **`core/common/actions`**: fixed two Kotlin 2.2 compilation errors caused by newer language rules
   (`return` inside expression-body functions), in `text/TextExecutor.kt` and
   `core/common/ui/.../gesture/edit/GestureEditorOverlayView.kt` (the latter is in `:core:common:ui`).
5. `android.nonTransitiveRClass=false` is set in `gradle.properties` because the vendored modules reference resources
   through their module `R` class, exactly as upstream does.

Everything else (the engine, the gesture dispatching, the overlay/menu implementation, the Room schema) is upstream code
and behaviour.

## License of AutoClicky

Because it embeds GPL-3.0-or-later code, **AutoClicky is distributed under the GNU General Public License v3.0 or
later**. See `LICENSE` for the full text. The same text is bundled in the application
(`app/src/main/res/raw/gpl_3_0.txt`) and displayed by the **About & license** entry of the main screen, so the notice
travels with every APK. Every AutoClicky-owned source file carries a
`SPDX-License-Identifier: GPL-3.0-or-later` header.
