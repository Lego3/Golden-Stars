package com.edvinlinge.hemma.mathstars2

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.doubleClick
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.slider.Slider
import org.hamcrest.Matchers.containsString
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JuliaActivityTest {

    @Test
    fun retainsZoomAfterRotation() {
        launchResumedActivity<JuliaActivity>().use { scenario ->
            onView(withId(R.id.juliaView)).perform(doubleClick())
            onView(withId(R.id.zoomText)).check(matches(withText("2.0x")))

            scenario.recreate()

            onView(withId(R.id.zoomText)).check(matches(withText("2.0x")))
        }
    }

    @Test
    fun retainsPresetAfterRotation() {
        launchResumedActivity<JuliaActivity>().use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<Slider>(R.id.presetSlider).value = 1f
            }

            onView(withId(R.id.cValueText)).check(matches(withText(containsString("-0.75"))))

            scenario.recreate()

            onView(withId(R.id.cValueText)).check(matches(withText(containsString("-0.75"))))
        }
    }

    @Test
    fun opensSettingsSheet() {
        launchResumedActivity<JuliaActivity>().use {
            onView(withId(R.id.settingsButton)).perform(click())
            onView(withText(R.string.customize_julia)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun opensHelpSheetWithHelpTitle() {
        launchResumedActivity<JuliaActivity>().use {
            onView(withId(R.id.helpButton)).perform(click())
            onView(withId(R.id.infoTitle)).check(matches(withText(R.string.help)))
            onView(withId(R.id.infoMessage)).check(matches(isDisplayed()))
        }
    }
}
