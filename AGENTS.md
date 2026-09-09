# Agent notes

## Cursor Cloud specific instructions

Cloud Agents boot an API 34 Android emulator (`golden_stars_api34`) from
`.cursor/scripts/start-android-emulator.sh` after each environment start.
The emulator uses TCG, not KVM: nested virtualization currently panics on
Cloud Agent kernels. First boot can take several minutes.

To run the app and capture UI after a change:

1. Confirm the emulator: `adb devices` should list `emulator-5554` as `device`. If it does not, run `bash .cursor/scripts/start-android-emulator.sh`.
2. Install and launch: `bash .cursor/scripts/run-android-app.sh`.
3. Prefer `bash .cursor/scripts/android-screenshot.sh` (adb screencap at 1080×1920) over a desktop screenshot. For motion, `bash .cursor/scripts/android-screenrecord.sh 10` or record the emulator window on `DISPLAY=:1`.

The emulator window is Qt-scaled to fit the 1920×1200 desktop. Unit tests and lint do not need the emulator: `./gradlew testDebugUnitTest lintDebug` is enough for non-UI changes.

## Test coverage automation — GitHub stacked PRs

The scheduled **Add test coverage** automation
(https://cursor.com/automations/52d208f5-9268-11f1-ba66-0e7d0216e441)
must add **one new layer to the existing GitHub stack**, not a new
independent pull request against `master`. Independent PRs all targeting
`master` conflict with each other and cannot be merged as a train.

### Each run

1. `git fetch origin master` and list open stacks:
   `gh api repos/Lego3/Golden-Stars/stacks`.
2. Pick the base:
   - If an **open GitHub stack** targeting `master` has an unmerged top PR,
     branch from that PR's **head** (not `master`). Create the new PR with
     `base_branch` set to that head branch.
   - Otherwise, if a coverage PR is already open against `master` (for
     example a leftover top car after a partial stack merge), branch from
     that head and stack on it.
   - Only if there is **no** open coverage stack and **no** open coverage
     PR, start a new bottom layer against `master`.
3. Ignore leftover duplicate coverage PRs that still target `master` while a
   stacked restack of the same work exists. Do not stack on
   `cursor/test-coverage-automation-*` branches that were superseded by a
   stacked train.
4. Implement **one** focused coverage gap (tests plus the smallest production
   helper needed). Do not bundle unrelated gaps into one layer.
5. Open the PR with `ManagePullRequest` (`create_pr`), passing `base_branch`
   as the previous layer's branch. Then attach it to the GitHub stack:
   - Prefer `update_pr` with `stack_on` set to the previous PR's URL when
     that parameter is available.
   - Otherwise, after the PR exists, `POST /repos/Lego3/Golden-Stars/stacks`
     with every open PR in the chain (bottom to top) or
     `POST /repos/Lego3/Golden-Stars/stacks/{number}/add` with the new PR
     number. Cloud Agent `gh` is read-only for writes; use
     `ManagePullRequest` for create/update, not `gh pr create`.
6. Keep **linear history**. Each car's first parent must be the previous
   car's tip. Do **not** cherry-pick the same commit onto every layer. If you
   change a lower layer, rebase cars above it onto the new tip and
   `git push --force-with-lease` only those rewritten branches.
7. Title the layer as a single concern (`test: …`). Mention the stack
   position and parent PR in the body.

Android CI already runs on stacked cars (no `master`-only
`pull_request` filter). Required check is **Build, lint & unit test**.
A GitHub stack will not merge until history is linear and that check
has reported on every unmerged layer.
