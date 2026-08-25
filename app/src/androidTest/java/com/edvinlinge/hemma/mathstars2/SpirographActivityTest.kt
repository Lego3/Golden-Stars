package com.edvinlinge.hemma.mathstars2

import android.os.Bundle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.containsString
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpirographActivityTest {

    @Test
    fun retainsGeometryAfterRotation() {
        launchResumedActivity<SpirographActivity>().use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.setFragmentResult(
                    SettingsBottomSheet.REQUEST_KEY,
                    settingsSnapshot(fixedRadius = 80, rollingRadius = 24, penOffset = 18),
                )
            }

            onView(withId(R.id.spirographView)).check(
                matches(withContentDescription(containsString("fixed ring 80"))),
            )

            scenario.recreate()

            onView(withId(R.id.spirographView)).check(
                matches(withContentDescription(containsString("fixed ring 80"))),
            )
        }
    }

    @Test
    fun opensSettingsSheet() {
        launchResumedActivity<SpirographActivity>().use {
            onView(withId(R.id.settingsButton)).perform(click())
            onView(withText(R.string.customize_spirograph)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun opensDetailsSheetWithDetailsTitle() {
        launchResumedActivity<SpirographActivity>().use {
            onView(withId(R.id.infoButton)).perform(click())
            onView(withId(R.id.infoTitle)).check(matches(withText(R.string.more_info_button)))
            onView(withId(R.id.infoMessage)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun opensHelpSheetWithHelpTitle() {
        launchResumedActivity<SpirographActivity>().use {
            onView(withId(R.id.helpButton)).perform(click())
            onView(withId(R.id.infoTitle)).check(matches(withText(R.string.help)))
            onView(withId(R.id.infoMessage)).check(matches(isDisplayed()))
        }
    }

    private fun settingsSnapshot(
        fixedRadius: Int = SpirographMath.DEFAULT_FIXED,
        rollingRadius: Int = SpirographMath.DEFAULT_ROLLING,
        penOffset: Int = SpirographMath.DEFAULT_PEN,
        inside: Boolean = SpirographMath.DEFAULT_INSIDE,
        thickness: Float = SettingsBottomSheet.DEFAULT_THICKNESS,
        colorIndex: Int = SettingsBottomSheet.DEFAULT_COLOR_INDEX,
        geometrySettled: Boolean = true,
    ): Bundle = Bundle().apply {
        putInt(SettingsBottomSheet.KEY_FIXED_RADIUS, fixedRadius)
        putInt(SettingsBottomSheet.KEY_ROLLING_RADIUS, rollingRadius)
        putInt(SettingsBottomSheet.KEY_PEN_OFFSET, penOffset)
        putBoolean(SettingsBottomSheet.KEY_INSIDE, inside)
        putFloat(SettingsBottomSheet.KEY_THICKNESS, thickness)
        putInt(SettingsBottomSheet.KEY_COLOR_INDEX, colorIndex)
        putBoolean(SettingsBottomSheet.KEY_GEOMETRY_SETTLED, geometrySettled)
    }
}
