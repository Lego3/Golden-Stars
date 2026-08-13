# Contributing to Golden Stars

Thank you for your interest in contributing. This is a hobby project, so reviews
are best effort, but clear, focused pull requests are always welcome.

## Before you start

- Read the [README](README.md) for architecture and development setup.
- Security issues must **not** be reported via public issues or pull requests.
  See [SECURITY.md](SECURITY.md).
- By participating, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md).

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
