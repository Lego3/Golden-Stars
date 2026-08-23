# Changelog

All notable changes to Golden Stars are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions match
Git tags (`v1.1.0`, `v1.2.0`, …).

## [Unreleased]

### Added

- Spirograph explorer for hypotrochoids and epitrochoids, with animated drawing,
  GCD-based details, and the same pan/zoom controls as Golden Stars.
- Julia set explorer with pinch-to-zoom, colour palettes, well-known *c* presets, and
  progressive rendering previews.

### Changed

- Mandelbrot and Julia palettes are draw-time colour filters over a cached greyscale
  escape map, so changing colour no longer discards tiles or rerenders.
- Mandelbrot explorer keeps a memory and disk tile cache, reuses nearby zoom steps, and
  pre-renders neighbouring tiles plus the next 2× zoom so pan and pinch stay smooth.
  The loading circle shows while on-screen tiles are still missing or only covered by a
  scaled/preview stand-in, with a finished/queued count for those tiles. Prefetch of
  neighbours stays silent. Visible tiles render as one parallel pass, with cardioid/bulb
  interior skipping. Covering parent tiles stay in memory while a sharper zoom renders.
  Disk eviction drops the least recently *used* tiles, including ones that stayed in RAM.
- Hub cards keep a content inset so they no longer run to the screen edge.
- Instrumented CI retries up to three emulator runs when ADB or Espresso flakes.

### Fixed

- Debug and CI test APKs are signed with a committed debug keystore so a new sideload
  updates the installed app instead of reporting a package conflict. The Google Play
  build still uses a different key, so it cannot be overwritten by these APKs.
- Hub scroll view reserves clearance above the floating version label so the last card
  does not sit underneath it.
- Mandelbrot and Julia explorers discard in-flight preview or full renders when the
  viewport or Julia constant no longer matches, so late results cannot overwrite a
  correct frame. Palette is a draw-time filter, so a colour change does not discard
  or rerender.
- Golden Stars, Spirograph, Mandelbrot, and Julia restore pan/zoom and reveal animation
  correctly after configuration changes (rotation).

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
