# Golden Stars

Explore mathematics through calm, interactive visuals — draw star figures in a single
stroke, then zoom into the Mandelbrot set. Golden Stars runs entirely on your device:
no accounts, no ads, no data collection.

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.edvinlinge.hemma.mathstars2">
    <img alt="Get it on Google Play"
         src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"
         height="80">
  </a>
</p>

## What you can do

**Golden Stars** — Pick how many dots sit on a circle and how far the pen jumps each
step. Watch the shape draw itself, tweak colours and speed, and open *Details* to learn
which star figures are possible for a given number of dots.

**Mandelbrot Set** — Pinch to zoom and drag to pan through the classic fractal. A live
preview keeps gestures smooth while the full-resolution render catches up.

Both screens share a dark, edge-to-edge layout with floating controls, settings sheets,
and a short in-app help panel.

## Contents

- [Development setup](#development-setup)
- [Architecture](#architecture)
- [Continuous integration](#continuous-integration)
- [Releasing](#releasing)
- [Project layout](#project-layout)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [Privacy](#privacy)
- [License](#license)

## Development setup

### Android Studio (recommended)

1. Clone this repository.
2. Install [Android Studio](https://developer.android.com/studio) with Android SDK 37.
3. Open the project folder and let Gradle sync finish.
4. Run on an emulator or physical device (Android 10 / API 29 or newer).

### Command line

Requirements: **JDK 21** (matches CI) or **JDK 17+**, plus the Android SDK with
`ANDROID_SDK_ROOT` set.

```bash
chmod +x gradlew

# Unit tests (JVM — StarMath, ZoomFormat, MandelbrotMath)
./gradlew testDebugUnitTest

# Lint
./gradlew lintDebug

# Debug APK
./gradlew assembleDebug

# Instrumented tests (requires an emulator or connected device)
./gradlew connectedDebugAndroidTest
```

Unit-test and lint HTML reports live under `app/build/reports/`. Debug APK output:
`app/build/outputs/apk/debug/`.

### Cursor Cloud Agents

For headless environments without Android Studio:

- Config: `.cursor/environment.json`
- Bootstrap script: `.cursor/scripts/setup-android-sdk.sh`

The script installs command-line SDK tools, accepts licenses, and runs
`./gradlew testDebugUnitTest` to warm Gradle caches. Platform packages (for example
`platforms;android-37`) are fetched automatically on first build.

## Architecture

```
MainActivity (hub)
├── DrawActivity ── DrawView              star geometry + reveal animation
│                  SettingsBottomSheet / InfoBottomSheet
└── MandelbrotActivity ── MandelbrotView  async fractal rendering
                         SettingsBottomSheet / InfoBottomSheet

StarMath, MandelbrotMath, ZoomFormat      pure Kotlin helpers (unit-tested)
```

Activities own user settings (dots, skips, colour, speed). Custom views
(`DrawView`, `MandelbrotView`) own viewport state (zoom, pan) and restore it across
rotation via `onSaveInstanceState`.

`SettingsBottomSheet` publishes changes through the **Fragment Result API**
(`REQUEST_KEY`) so listeners registered in `onCreate` survive configuration changes.

### Golden Stars rendering

Star geometry is driven by two integers:

- **dots** — evenly spaced points on a circle
- **skips** — how many dots to advance on each straight-line step

The path closes after `dots / gcd(dots, skips)` steps. See `StarMath.starSkips()` for
valid skip counts. `DrawView` reveals the path with a `ValueAnimator`; slider drags
update geometry instantly and replay the animation when the finger lifts.

### Mandelbrot rendering

Rendering runs off the main thread with Kotlin coroutines. Downscaled previews keep
pinch and pan responsive; a full-resolution pass runs when the gesture ends. Iteration
count scales with zoom and is capped where double precision breaks down. Render jobs are
cancelled when the view detaches so rotation does not leave stale bitmaps.

## Continuous integration

GitHub Actions (`.github/workflows/android-ci.yml`) runs on every pull request to
`master`. Required checks always report; jobs fast-pass in seconds when a PR does not
touch Android-related paths. Pushes to `master` still use path filters to skip
unnecessary builds.

| Job | What it does |
|-----|----------------|
| Build, lint & unit test | `./gradlew testDebugUnitTest`, `lintDebug`, debug APK; uploads reports |
| Instrumented tests (emulator) | Smoke tests on API 34 via `reactivecircus/android-emulator-runner` |

A separate workflow (`.github/workflows/static.yml`) deploys `index.html` and
`privacy_policy.html` to GitHub Pages on `master`.

Every push to `master` also uploads a **debug APK** as a CI artifact (zipped download
from the Actions tab). Tagged releases are handled separately — see [Releasing](#releasing).

## Releasing

Version history and release notes live in [CHANGELOG.md](CHANGELOG.md). GitHub Releases
provide versioned download links; signed AAB uploads to Google Play stay a separate,
manual step.

1. Move items from `[Unreleased]` into a new `## [x.y.z]` section in `CHANGELOG.md` and
   bump `versionCode` / `versionName` in `app/build.gradle.kts`.
2. Commit and push to `master`, then tag:

   ```bash
   git commit -m "Release 1.2.0"
   git tag -a v1.2.0 -m "Optional short summary if CHANGELOG is skipped"
   git push origin master
   git push origin v1.2.0
   ```

3. The `.github/workflows/release.yml` workflow runs on the tag push, builds a debug
   APK, and creates a GitHub Release with notes from the matching `CHANGELOG.md`
   section (or the annotated tag message as a fallback) and a direct `.apk` attachment.

## Project layout

```
app/src/main/java/com/edvinlinge/hemma/mathstars2/
├── MainActivity.kt           Hub screen
├── DrawActivity.kt           Star screen controller
├── DrawView.kt               Star canvas + animation
├── MandelbrotActivity.kt     Fractal screen controller
├── MandelbrotView.kt         Fractal canvas + async render
├── StarMath.kt               GCD-based star arithmetic
├── MandelbrotMath.kt         Fractal math helpers
├── SettingsBottomSheet.kt    Shared settings UI
├── InfoBottomSheet.kt        Help / details panel
├── ZoomFormat.kt             Zoom label formatter
└── ScreenInsets.kt           Edge-to-edge inset helpers

app/src/test/                 JVM unit tests
app/src/androidTest/          Instrumented smoke tests
```

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `./gradlew` fails with missing SDK | `ANDROID_SDK_ROOT` unset or licenses not accepted | Run the setup script or Android Studio SDK Manager; `yes \| sdkmanager --licenses` |
| Star fill disappears for some dot/skip pairs | Digon geometry (≤2 distinct points) | Expected — `StarMath.canFill()` is false; use stroke or change skip |
| Mandelbrot blank after rotation | View detached mid-render | Reattach scope + `requestFullRender()` in `onAttachedToWindow` (already implemented) |
| Instrumented tests fail locally | No emulator/device | Start an AVD or connect a device; CI uses API 34 x86_64 |

Before pushing rendering changes, run `./gradlew testDebugUnitTest` — CI runs the same
command plus lint and emulator tests.

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for the workflow,
local checks, and review expectations.

Please follow our [Code of Conduct](CODE_OF_CONDUCT.md). Security issues must be
reported privately — see [SECURITY.md](SECURITY.md).

## Privacy

Golden Stars does not collect or transmit data to the developer or third parties. It
stores visualization preferences locally on your device (star geometry, colours,
thickness, fill, speed, and Mandelbrot palette). If Android backup is enabled, those
preferences may be included in your encrypted Google account backup or device transfer,
managed by Android — not by this app.

The published privacy policy is [privacy_policy.html](privacy_policy.html) (also
deployed to GitHub Pages on `master`).

## License

This project is licensed under the [MIT License](LICENSE).
