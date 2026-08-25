package com.edvinlinge.hemma.mathstars2

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugBuildIdentityTest {

    @Test
    fun debugApplicationIdIsSuffixed() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("com.edvinlinge.hemma.mathstars2.debug", context.packageName)
    }

    @Test
    fun debugLauncherTitleIsMarked() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(
            "Expected debug app name to include (D), was: ${context.getString(R.string.app_name)}",
            context.getString(R.string.app_name).contains("(D)"),
        )
    }
}
