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
