# Changelog

All notable changes to Golden Stars are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions match
Git tags (`v1.1.0`, `v1.2.0`, …).

## [Unreleased]

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
