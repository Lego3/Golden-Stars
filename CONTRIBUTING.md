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

### Hub screen

`MainActivity` is a scrollable card list with a floating version label pinned to the
bottom of the window. Cards launch each explorer; the label reads `versionName` from the
package manager.

Edge-to-edge insets differ from the explorers: the scroll content gets system-bar and
cutout padding (`hub_content_padding` plus bar insets), while the version label sits in a
`CoordinatorLayout` overlay with its own bottom margin. `updateHubScrollBottomPadding`
adds bottom padding to the `NestedScrollView` via `hubScrollBottomPadding` (label height,
margin, and `hub_version_clearance`) so the last card can scroll fully above the label.
`HubLayoutTest` covers that formula; `MainActivityTest` asserts the padding is applied and
that the Julia card does not overlap the label when scrolled to the end.

### Shared UI and persistence

| Component | Role |
|-----------|------|
| `SettingsBottomSheet` | Sliders and toggles for geometry, colour, speed, and style |
| `InfoBottomSheet` | Short help text or HTML details (star / Spirograph GCD facts, Julia *c* membership) |
| `AppPreferences` | `SharedPreferences` wrapper; one settings blob per explorer |
| `ScreenInsets` / `doOnScreenInsets` | Maps system bars and cutouts to layout-direction-aware start/end insets for floating overlays |
| `ZoomFormat` | Human-readable zoom labels on Mandelbrot and Julia screens |

Package: `com.edvinlinge.hemma.mathstars2`. Debug builds append `.debug` to the
installed applicationId and override the launcher name/icon from `app/src/debug/`.
View binding is enabled; layouts live under `app/src/main/res/layout/`.

### Math layer

Each `*Math` object is `internal`, has no Android imports, and is covered by JVM unit
tests under `app/src/test/`. Keep new geometry or iteration logic here rather than in
views so it stays fast to test.

- **`StarMath`** — GCD-based star polygons: visited dot count, single-stroke detection,
  fill safety (`canFill` rejects digons that would vanish when filled), and vertex order.
- **`DrawViewMath`** — Pan/zoom focus math, zoom clamping after configuration changes
  (`coercedZoom`), reveal-animation timing, and `revealRestoreAction` for resuming or
  skipping the path reveal after rotation.
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
the figure draws instantly. Both views save `currentPhase` and viewport across
configuration changes. Because `onSizeChanged` can start a fresh reveal before
`onRestoreInstanceState` runs, `DrawViewMath.revealRestoreAction` decides whether to
show the completed figure, skip (empty path), or resume with
`remainingAnimationDurationMs`.

**Bitmap coroutines (Mandelbrot, Julia).** Gestures update a logical viewport
(`zoom`, `offsetX`, `offsetY`; Julia also holds `cReal` / `cImag`). Iteration depth
scales with zoom (`MandelbrotMath.iterationsFor`) and is capped to keep frames bounded.

`JuliaView` still uses a single view-sized bitmap:

1. While interacting, a **preview** pass renders at reduced resolution
   (`requestPreviewRender`).
2. When idle, a **full** pass renders at view size (`requestFullRender`).
3. Until the new bitmap arrives, the previous one is scaled and shifted using
   `MandelbrotMath.staleBitmapDrawTransform` so pan and pinch stay responsive.
4. In-flight results are dropped when the viewport or Julia constant no
   longer match (`shouldApplyRenderResult`), so a late preview cannot overwrite a
   correct full-resolution frame. Palette is a draw-time colour filter over a
   greyscale escape map, so changing colour does not rerender.
5. Pixel buffers are written on a background dispatcher; `bufferJob` serializes access
   so a detached view never races with a new attach.

`MandelbrotView` composites **tiles** instead of one full-view bitmap. Cached tiles live
on power-of-two zoom steps (`MandelbrotTiles`), in an in-memory LRU plus an on-disk
store (`MandelbrotTileCache`). Nearby zoom steps and parent tiles stand in, scaled,
while missing tiles render in the background. The loading circle appears while visible
full-resolution tiles are still missing, including when a scaled parent or preview is
already on screen, and shows how many of those tiles have finished. Prefetch of
off-screen neighbours stays silent. Visible missing tiles render as one row-parallel
pass (with cardioid/bulb interior early-out). Cached tiles store an 8-bit escape-time
alpha map; `ColorMatrixColorFilter` applies the current `FractalPalette` at draw time,
so a colour change does not invalidate the cache. Prefetch fills neighbours and the
next 2× zoom after a gesture settles, a few tiles at a time. LRU eviction keeps the
tiles the current frame still blits, including scaled parents. Disk eviction follows
last *use*, including memory hits, so the default 1× view is not deleted just because
it was written first.

### Settings and configuration changes

`SettingsBottomSheet` publishes every change through the **Fragment Result API**
(`setFragmentResult`), not through host callbacks. Callbacks assigned when the sheet opens
are lost after a rotation, which previously made controls silently stop working.

Each host activity:

1. Restores explorer state from `AppPreferences` and any `savedInstanceState`.
2. Shows the sheet with arguments describing which control groups are visible.
3. Listens for result keys and applies the snapshot to its view.

Fractal and pan/zoom views also save viewport fields in `onSaveInstanceState` and clamp
restored zoom through `MandelbrotMath.coercedZoom` (Mandelbrot, Julia) or
`DrawViewMath.coercedZoom` (Golden Stars, Spirograph) so an out-of-range value from an
older build cannot break gestures after rotation.

`AppPreferences` normalizes Spirograph radii through `SpirographMath.normalized` on load
and save so stored values always match slider bounds.

### Testing map

| Layer | Location | Examples |
|-------|----------|----------|
| Unit | `app/src/test/` | `StarMathTest`, `SpirographMathTest`, `JuliaMathTest`, `MandelbrotMathTest`, `FractalColoringTest`, `MandelbrotTilesTest`, `MandelbrotTileCacheTest`, `DrawViewMathTest`, `ScreenInsetsTest`, `HubLayoutTest` |
| Instrumented | `app/src/androidTest/` | Settings round-trip, rotation survival, smoke launch per activity, hub scroll/version layout (`MainActivityTest`), debug applicationId and launcher title (`DebugBuildIdentityTest`) |

When adding behaviour, extend the matching `*Math` unit tests first. Reserve
instrumented tests for Android integration (preferences, fragments, configuration
changes).

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
  for Mandelbrot tile rendering and Julia bitmap rendering).
- Do not reformat unrelated code.

## Questions

Open a [GitHub issue](https://github.com/Lego3/Golden-Stars/issues) for bugs or
feature ideas. For private matters, email edvin.linge@gmail.com.
