package com.edvinlinge.hemma.mathstars2

import android.app.Activity
import android.app.UiAutomation
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import java.io.OutputStream

/**
 * Launch an activity after closing keyguard / system dialogs so Espresso
 * can get window focus.
 */
internal inline fun <reified A : Activity> launchResumedActivity(
    noinline configure: Intent.() -> Unit = {},
): ActivityScenario<A> {
    closeSystemOverlays()
    val intent = Intent(ApplicationProvider.getApplicationContext(), A::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        configure()
    }
    val scenario = ActivityScenario.launch<A>(intent)
    waitForWindowFocus(scenario)
    return scenario
}

internal fun waitForWindowFocus(scenario: ActivityScenario<out Activity>, timeoutMs: Long = 8_000) {
    val instr = InstrumentationRegistry.getInstrumentation()
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < deadline) {
        var focused = false
        scenario.onActivity { focused = it.hasWindowFocus() }
        if (focused) {
            instr.waitForIdleSync()
            return
        }
        closeSystemOverlays()
        Thread.sleep(200)
    }
}

internal fun closeSystemOverlays() {
    val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
    runShell(ui, "am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
    runShell(ui, "wm dismiss-keyguard")
}

private fun runShell(ui: UiAutomation, command: String) {
    ui.executeShellCommand(command).use { pfd ->
        FileInputStream(pfd.fileDescriptor).copyTo(OutputStream.nullOutputStream())
    }
}
