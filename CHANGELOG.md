# Changelog

All notable changes to Golden Stars are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions match
Git tags (`v1.1.0`, `v1.2.0`, …).

## [Unreleased]

## [1.1.0] - 2026-08-09

### Added

- Unit tests for Mandelbrot viewport zoom math and skip-slider bounds.
- Instrumented tests for rotation and settings persistence.

### Fixed

- Mandelbrot viewport re-renders correctly after rotation.
- DrawView reveal animator is cancelled when the view detaches.
- Pinch-to-zoom no longer triggers duplicate pan drift.
- Plain-text info sheets preserve paragraph breaks.
- Settings color swatches show a selected state.
- Hub version label respects navigation bar insets.

### Changed

- CI validates release builds with R8 minification.
- Settings are persisted locally and included in Android backup.
