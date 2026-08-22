package com.edvinlinge.hemma.mathstars2

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.doubleClick
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MandelbrotActivityTest {

    @Test
    fun retainsZoomAfterRotation() {
        ActivityScenario.launch(MandelbrotActivity::class.java).use { scenario ->
            onView(withId(R.id.mandelbrotView)).perform(doubleClick())
            onView(withId(R.id.zoomText)).check(matches(withText("2.0x")))

            scenario.recreate()

            onView(withId(R.id.zoomText)).check(matches(withText("2.0x")))
        }
    }

    @Test
    fun opensHelpSheetWithHelpTitle() {
        ActivityScenario.launch(MandelbrotActivity::class.java).use {
            onView(withId(R.id.helpButton)).perform(click())
            onView(withId(R.id.infoTitle)).check(matches(withText(R.string.help)))
            onView(withId(R.id.infoMessage)).check(matches(isDisplayed()))
        }
    }
}
