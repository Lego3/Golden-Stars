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
}
