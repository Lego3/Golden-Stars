# Changelog

All notable changes to Golden Stars are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions match
Git tags (`v1.1.0`, `v1.2.0`, `v1.3.0`, …).

## [Unreleased]

### Changed

- Golden Stars and Spirograph speed sliders retarget an in-progress drawing
  immediately from the current stroke, instead of applying the new rate only on
  the next replay.

### Fixed

- Dragging the speed slider no longer crawls an in-progress drawing backwards or
  forwards. Live seeks keep a stored revealed fraction and only convert it to
  whole milliseconds for the animator, so rounding error does not accumulate.

## [1.3.0] - 2026-08-25

Two new explorers join Golden Stars and Mandelbrot: a Spirograph that traces
hypotrochoids and epitrochoids, and a Julia set viewer for well-known values of
*c*. Mandelbrot pan and zoom is also much smoother, with cached tiles and instant
palette changes.

### Added

- Spirograph explorer for hypotrochoids and epitrochoids, with animated drawing,
  GCD-based details, and the same pan/zoom controls as Golden Stars.
- Julia set explorer with pinch-to-zoom, colour palettes, well-known *c* presets, and
  progressive rendering previews.
- Debug builds use applicationId `com.edvinlinge.hemma.mathstars2.debug`, the launcher
  title Golden Stars (D), and a green-banner icon so they can sit next to the Play Store
  app.

### Changed

- Mandelbrot pan and pinch stay smooth: tiles are cached in memory and on disk,
  nearby zoom levels stand in while a sharper image renders, and neighbours plus the
  next 2× zoom are pre-fetched. The loading circle shows a finished/queued count while
  the current view is still sharpening; silent prefetch does not.
- Mandelbrot palettes apply as a draw-time colour filter, so changing colour is instant
  and does not discard cached tiles. Julia uses the same approach.
- Hub cards keep a content inset so they no longer run to the screen edge.
- Details and Help sheets now use titles that match their contents (Help for how-to
  text, Details for figure math).
- CI and local debug APKs use a `-test` suffix in the filename so they stay distinct
  from tagged GitHub Releases.
- Instrumented CI retries up to three emulator runs when ADB or Espresso flakes.

### Fixed

- Debug and CI test APKs are signed with a committed debug keystore so a new sideload
  updates the installed debug app instead of reporting a package conflict.
- Hub scroll view reserves clearance above the floating version label so the last card
  does not sit underneath it.
- Mandelbrot no longer applies a late preview or full render after the viewport has
  already changed.
- Golden Stars reveal animation resumes after rotation instead of restarting from the
  beginning.

## [1.2.0] - 2026-08-09

### Added

- Mandelbrot set explorer with pinch-to-zoom, color palettes, and progressive rendering previews.
- Floating control panel plus settings and info bottom sheets on both drawing screens.
- Local settings persistence included in Android backup.
- GitHub Actions CI: unit tests, lint, debug/release assembly, and API 34 instrumented tests.
- Unit tests for star math, Mandelbrot escape iterations, viewport zoom math, zoom formatting, and skip-slider bounds.
- Instrumented tests covering configuration changes (rotation) and settings round-trip.
- Contributor documentation, security policy, and MIT license.

### Fixed

- Filled digons disappearing once the star reveal animation completes.
- DrawView reveal animation continuing after the view detaches.

### Changed

- Modernized Gradle/Kotlin build (JVM 17, compile/target SDK 37).

## [1.1.0] - 2026-07-27

First public release. Draw animated star figures by choosing dot and skip counts.
