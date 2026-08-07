# Security Policy

## Reporting a Vulnerability

Please do **not** open a public issue or pull request for security problems.

Report privately through GitHub's
[private vulnerability reporting](https://github.com/Lego3/Golden-Stars/security/advisories/new)
(Security tab → Report a vulnerability). If that is unavailable, email
edvin.linge@gmail.com.

Please include the affected version or commit, what an attacker could achieve,
and the steps needed to reproduce it.

This is a hobby project maintained in spare time, so responses are best effort.

## Scope

Golden Stars is an offline Android app. It requests no permissions, opens no
network connections, and stores no user data, so the most likely findings are
in the build and release pipeline rather than the app itself:

- The GitHub Actions workflows in `.github/workflows`
- The Gradle build (`build.gradle.kts`, `settings.gradle.kts`,
  `gradle/libs.versions.toml`, and the Gradle wrapper)
- The static site published to GitHub Pages (`index.html`,
  `privacy_policy.html`)

## Note for Contributors

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full contribution workflow.

CI builds pull requests, which means a pull request that changes build logic
executes on our runners. Such changes get extra scrutiny, and CI runs for
external contributions require maintainer approval before they start. Keep
build-system changes in separate, clearly described pull requests so they are
easy to review.
