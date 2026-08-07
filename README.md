# Golden Stars

Android app for exploring mathematics through interactive visualizations. Examples: single-stroke star polygons and the Mandelbrot set.

Package: `com.edvinlinge.hemma.mathstars2` · minSdk 29 · compile/targetSdk 37 · version 1.1.0

## Screens

| Screen | Activity | Purpose |
|--------|----------|---------|
| Hub | `MainActivity` | Launch stars or Mandelbrot; shows app version |
| Golden Stars | `DrawActivity` | Animated star drawing with zoom/pan and settings |
| Mandelbrot Set | `MandelbrotActivity` | Fractal explorer with pinch-to-zoom |

Both drawing screens use a floating control panel, zoom label, settings/info bottom sheets, and edge-to-edge dark UI.

## Architecture

```
MainActivity
├── DrawActivity ── DrawView          (star geometry + reveal animation)
│                  SettingsBottomSheet / InfoBottomSheet
└── MandelbrotActivity ── MandelbrotView  (async fractal rendering)
                         SettingsBottomSheet / InfoBottomSheet

StarMath (pure Kotlin, unit-tested)
ZoomFormat (shared zoom label formatting)
```

### State ownership

Activities own user settings (dots, skips, colour, speed). Custom views (`DrawView`, `MandelbrotView`) own viewport state (zoom, pan) and persist it across configuration changes via `onSaveInstanceState`.

`SettingsBottomSheet` publishes changes through the **Fragment Result API** (`REQUEST_KEY`) rather than holding callbacks. Callbacks assigned when a sheet is shown are lost on rotation; the result listener registered in `onCreate` survives recreation.

### Golden Stars (`StarMath`, `DrawView`)

Star geometry is driven by two integers:

- **dots** — evenly spaced points on a circle
- **skips** — how many dots to advance on each straight-line step

The figure closes after `dots / gcd(dots, skips)` steps. A genuine star requires `gcd(dots, skips) == 1` and `skips >= 2`. See `StarMath.starSkips()` for valid skip counts.

`DrawView` builds the full path, then reveals it with a `ValueAnimator`. During slider drags, `DrawActivity` calls `setGeometry(dots, skips, animate = false)` for instant preview and replays the reveal when the finger lifts.

**Fill constraint:** digons (e.g. 6 dots, skip 3) have only two distinct vertices. `StarMath.canFill()` returns false for these; filling would make the shape vanish because `Paint.Style.FILL` needs at least three distinct points.

### Mandelbrot Set (`MandelbrotView`)

Rendering runs off the main thread using Kotlin coroutines:

1. **Preview renders** (4× downscaled) run during pan/zoom gestures so interaction stays responsive.
2. **Full renders** run when the gesture ends or the viewport otherwise needs a high-resolution pass.
3. While a new render is in flight, `onDraw` scales/translates the last bitmap to approximate the current viewport.

Iteration count scales with zoom (`BASE_ITERATIONS` + log₁₀(zoom) × `ITERATIONS_PER_DECADE`, capped at `MAX_ITERATIONS`). Zoom is clamped at `MAX_ZOOM` (1e13) where double precision breaks down.

**Lifecycle:** `renderScope` is created in `onAttachedToWindow` and cancelled in `onDetachedFromWindow`. Cancelled renders share pixel buffers (`fullPixels`, `previewPixels`); `bufferJob` chains ensure a new render waits for the previous job to release buffers before writing (see `NonCancellable` join in `startRender`).

### Shared UI

- `formatZoom()` — compact zoom labels (`2.5x`, `1.4k x`, `3.0M x`)
- `ScreenInsets.kt` — edge-to-edge inset handling for control panels
- `SettingsBottomSheet` — star controls (dots, skips, thickness, fill, colour) or Mandelbrot colour-only mode via `showStarControls = false`

## Development setup

### Android Studio (recommended for UI work)

1. Clone the repository.
2. Install [Android Studio](https://developer.android.com/studio) with SDK 37.
3. Open the project folder.
4. Run on an emulator or device (API 29+).

### Command line

Requirements: **JDK 21** (CI) or **JDK 17+** (module target), Android SDK with `ANDROID_SDK_ROOT` set.

```bash
chmod +x gradlew

# Unit tests (JVM — StarMath, ZoomFormat)
./gradlew testDebugUnitTest

# Lint
./gradlew lintDebug

# Debug APK
./gradlew assembleDebug

# Instrumented tests (requires emulator or connected device)
./gradlew connectedDebugAndroidTest
```

Reports: `app/build/reports/tests/testDebugUnitTest`, `app/build/reports/lint-results-debug.html`.

APK output: `app/build/outputs/apk/debug/`.

### Cursor Cloud Agents

The repo includes a headless SDK bootstrap for agents without Android Studio:

- Config: `.cursor/environment.json`
- Script: `.cursor/scripts/setup-android-sdk.sh`

The script installs Android command-line tools, accepts licenses, and runs `./gradlew testDebugUnitTest` to warm Gradle/SDK caches. Platform packages (e.g. `platforms;android-37`) are downloaded automatically by the Android Gradle Plugin on first build.

## CI

GitHub Actions workflow `.github/workflows/android-ci.yml` runs on pushes and PRs to `master`:

| Job | Steps |
|-----|-------|
| `build-and-test` | unit tests, lint, debug APK; uploads reports and APK artifacts |
| `instrumented-test` | API 34 emulator via `reactivecircus/android-emulator-runner` |

A separate workflow (`.github/workflows/static.yml`) deploys `index.html` and `privacy_policy.html` to GitHub Pages on `master`.

## Project layout

```
app/src/main/java/com/edvinlinge/hemma/mathstars2/
├── MainActivity.kt           # Hub
├── DrawActivity.kt           # Star screen controller
├── DrawView.kt               # Star canvas + animation
├── MandelbrotActivity.kt     # Fractal screen controller
├── MandelbrotView.kt         # Fractal canvas + async render
├── StarMath.kt               # GCD-based star arithmetic
├── SettingsBottomSheet.kt    # Shared settings UI
├── InfoBottomSheet.kt        # HTML/text info panel
├── ZoomFormat.kt             # Zoom label formatter
└── ScreenInsets.kt           # Inset helpers

app/src/test/                 # JVM unit tests (StarMath, ZoomFormat)
app/src/androidTest/          # Instrumented smoke tests
```

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `./gradlew` fails with missing SDK | `ANDROID_SDK_ROOT` unset or licenses not accepted | Run setup script or Android Studio SDK Manager; `yes \| sdkmanager --licenses` |
| Star fill disappears for some dot/skip pairs | Digon geometry (≤2 distinct points) | Expected — `StarMath.canFill()` is false; use stroke or change skip |
| Mandelbrot blank after rotation | View detached mid-render | Fixed by reattaching scope + `requestFullRender()` in `onAttachedToWindow` |
| Settings stop working after rotation | Callback-based sheet wiring | Use Fragment Result API listener in activity `onCreate` (already implemented) |
| Instrumented tests fail locally | No emulator/device | Start an AVD or connect a device; CI uses API 34 x86_64 |

## Testing focus

Unit tests in `StarMathTest` cover GCD traversal, valid star skips, fill eligibility, and primality — the rules surfaced in the app's info panel. `ZoomFormatTest` covers label formatting at magnitude boundaries.

When changing star or Mandelbrot rendering logic, run `./gradlew testDebugUnitTest` before pushing; CI runs the same command plus lint and emulator tests.
