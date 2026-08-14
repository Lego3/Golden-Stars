# Contributing to Golden Stars

Thank you for your interest in contributing. This is a hobby project, so reviews
are best effort, but clear, focused pull requests are always welcome.

## Before you start

- Read the [README](README.md) for development setup, CI, and releasing.
- Skim [Architecture](#architecture) below before touching activities, views, or math.
- Security issues must **not** be reported via public issues or pull requests.
  See [SECURITY.md](SECURITY.md).
- By participating, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md).

## Architecture

Golden Stars is a single-module Android app (`app/`) with four interactive explorers
launched from a hub screen. Each explorer follows the same shape: an **Activity** wires
UI chrome and persistence, a custom **View** handles drawing and gestures, and a pure
Kotlin **Math** object holds the geometry or iteration logic.

```
MainActivity (hub)
├── DrawActivity      → DrawView        + StarMath / DrawViewMath
├── MandelbrotActivity → MandelbrotView + MandelbrotMath
├── SpirographActivity → SpirographView + SpirographMath
└── JuliaActivity     → JuliaView       + JuliaMath
```

Shared across explorers:

| Component | Role |
|-----------|------|
| `SettingsBottomSheet` | Sliders and toggles for geometry, colour, speed, and style |
| `InfoBottomSheet` | Short help text or HTML details (star / Spirograph GCD facts, Julia *c* membership) |
| `AppPreferences` | `SharedPreferences` wrapper; one settings blob per explorer |
| `ScreenInsets` | Edge-to-edge layout helper for floating overlays and system bars |
| `ZoomFormat` | Human-readable zoom labels on Mandelbrot and Julia screens |

Package: `com.edvinlinge.hemma.mathstars2`. View binding is enabled; layouts live under
`app/src/main/res/layout/`.

### Math layer

Each `*Math` object is `internal`, has no Android imports, and is covered by JVM unit
tests under `app/src/test/`. Keep new geometry or iteration logic here rather than in
views so it stays fast to test.

- **`StarMath`** — GCD-based star polygons: visited dot count, single-stroke detection,
  fill safety (`canFill` rejects digons that would vanish when filled), and vertex order.
- **`DrawViewMath`** — Pan/zoom focus math and reveal-animation timing for `DrawView`.
- **`SpirographMath`** — Hypotrochoid / epitrochoid sampling, period and lobe counts
  (reuses `StarMath.gcd`), and view fitting.
- **`MandelbrotMath`** — Viewport sizing, zoom-clamped iteration counts, escape-time
  iteration, and the stale-bitmap transform used while renders catch up.
- **`JuliaMath`** — Preset constants of *c*, escape iteration from arbitrary *z₀*, and
  connected-set hints via the critical orbit (*z₀* = 0).

`MandelbrotMath` and `JuliaMath` share escape-radius and max-iteration constants.

### Rendering patterns

**Path reveal (Golden Stars, Spirograph).** `DrawView` and `SpirographView` build a
closed `Path`, then animate reveal with `PathMeasure` and `ValueAnimator`. Phase `1` is
fully hidden, `0` is complete. Speed maps to duration via `DrawViewMath`; at high speed
the figure draws instantly. Both views save `currentPhase` across configuration changes
and resume the remaining reveal time (`DrawViewMath.remainingAnimationDurationMs`).

**Bitmap coroutines (Mandelbrot, Julia).** `MandelbrotView` and `JuliaView` share the
same pipeline:

1. Gestures update a logical viewport (`zoom`, `offsetX`, `offsetY`; Julia also holds
   `cReal` / `cImag`).
2. While interacting, a **preview** pass renders at reduced resolution
   (`requestPreviewRender`).
3. When idle, a **full** pass renders at view size (`requestFullRender`).
4. Until the new bitmap arrives, the previous one is scaled and shifted using
   `MandelbrotMath.staleBitmapDrawTransform` so pan and pinch stay responsive.
5. Pixel buffers are written on a background dispatcher; `bufferJob` serializes access
   so a detached view never races with a new attach.

Iteration depth scales with zoom (`MandelbrotMath.iterationsFor`) and is capped to keep
frames bounded.

### Settings and configuration changes

`SettingsBottomSheet` publishes every change through the **Fragment Result API**
(`setFragmentResult`), not through host callbacks. Callbacks assigned when the sheet opens
are lost after a rotation, which previously made controls silently stop working.

Each host activity:

1. Restores explorer state from `AppPreferences` and any `savedInstanceState`.
2. Shows the sheet with arguments describing which control groups are visible.
3. Listens for result keys and applies the snapshot to its view.

`AppPreferences` normalizes Spirograph radii through `SpirographMath.normalized` on load
and save so stored values always match slider bounds.

### Testing map

| Layer | Location | Examples |
|-------|----------|----------|
| Unit | `app/src/test/` | `StarMathTest`, `SpirographMathTest`, `JuliaMathTest`, `MandelbrotMathTest`, `DrawViewMathTest` |
| Instrumented | `app/src/androidTest/` | Settings round-trip, rotation survival, smoke launch per activity |

When adding behaviour, extend the matching `*Math` unit tests first. Reserve
instrumented tests for Android integration (preferences, fragments, configuration
changes).

### Common pitfalls

- **Bottom sheets after rotation** — never wire settings through fragment → activity
  callbacks; use fragment results.
- **Filled digons** — check `StarMath.shouldFill` before switching to
  `Paint.Style.FILL`; a two-point figure disappears once the reveal finishes.
- **Spirograph inside mode** — `SpirographMath.coercedRolling` caps the rolling radius
  below the fixed ring; epitrochoid mode allows the full range.
- **Fractal buffer lifetime** — await or chain on `bufferJob` before recycling bitmap
  pixel arrays; both fractal views keep the last buffer job across detach.
- **Preview vs full** — changing only the colour palette should call `requestFullRender`,
  not preview; pan/zoom during interaction should prefer preview.

## Development workflow

1. Fork the repository and create a branch from `master`.
2. Make your changes. Keep pull requests focused on one topic when possible.
3. Run checks locally before opening a pull request:

   ```bash
   ./gradlew testDebugUnitTest lintDebug assembleDebug
   ```

4. Open a pull request against `master` and fill in the PR template.

CI runs the same unit tests, lint, and instrumented tests on every pull
request. External contributions require maintainer approval before CI starts.

## What to contribute

Good first contributions include:

- Bug fixes with a clear reproduction case
- Unit tests for `StarMath`, `SpirographMath`, `JuliaMath`, or `ZoomFormat`
- Documentation improvements
- UI polish that matches the existing Material dark theme

Build-system or CI changes get extra scrutiny because they execute on GitHub
Actions runners. Put those in separate, clearly described pull requests.

## Code style

- Match the existing Kotlin style in the file you are editing.
- Prefer the patterns already used in activities and views (Fragment Result
  API for bottom sheets, path animation for stars and Spirograph, coroutines
  for Mandelbrot and Julia rendering).
- Do not reformat unrelated code.

## Questions

Open a [GitHub issue](https://github.com/Lego3/Golden-Stars/issues) for bugs or
feature ideas. For private matters, email edvin.linge@gmail.com.
