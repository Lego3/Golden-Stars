package com.edvinlinge.hemma.mathstars2

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPreferencesTest {

    private lateinit var preferences: AppPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences(AppPreferences.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        preferences = AppPreferences.get(context)
    }

    @Test
    fun saveAndLoadStarSettings() {
        val settings = AppPreferences.StarSettings(
            dots = 9,
            skips = 4,
            thickness = 12f,
            filled = false,
            colorIndex = 2,
            speed = 2.5f,
        )

        preferences.saveStarSettings(settings)

        assertEquals(settings, preferences.loadStarSettings())
    }

    @Test
    fun saveAndLoadMandelbrotColorIndex() {
        preferences.saveMandelbrotColorIndex(3)
        assertEquals(3, preferences.loadMandelbrotColorIndex())
    }

    @Test
    fun saveAndLoadSpirographSettings() {
        val settings = AppPreferences.SpirographSettings(
            fixedRadius = 80,
            rollingRadius = 24,
            penOffset = 18,
            inside = false,
            thickness = 10f,
            colorIndex = 1,
            speed = 2.0f,
        )

        preferences.saveSpirographSettings(settings)

        assertEquals(settings, preferences.loadSpirographSettings())
    }

    @Test
    fun spirographInsideRollingRadiusIsClampedBelowTheRing() {
        preferences.saveSpirographSettings(
            AppPreferences.SpirographSettings(
                fixedRadius = 40,
                rollingRadius = 90,
                penOffset = 12,
                inside = true,
                thickness = 8f,
                colorIndex = 0,
                speed = 1.0f,
            ),
        )

        val loaded = preferences.loadSpirographSettings()
        assertEquals(40, loaded.fixedRadius)
        assertEquals(39, loaded.rollingRadius)
        assertEquals(true, loaded.inside)
    }
}
