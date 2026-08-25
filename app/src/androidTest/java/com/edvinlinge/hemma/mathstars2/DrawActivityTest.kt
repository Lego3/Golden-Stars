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
class DrawActivityTest {

    @Test
    fun retainsLaunchGeometryAfterRotation() {
        launchResumedActivity<DrawActivity> {
            putExtra(DrawActivity.EXTRA_DOTS, 7)
            putExtra(DrawActivity.EXTRA_SKIPS, 3)
        }.use { scenario ->
            onView(withId(R.id.drawView)).check(
                matches(withContentDescription(containsString("7 dots"))),
            )
            scenario.recreate()
            onView(withId(R.id.drawView)).check(
                matches(withContentDescription(containsString("7 dots"))),
            )
        }
    }

    @Test
    fun retainsSettingsAfterRotation() {
        launchResumedActivity<DrawActivity>().use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.setFragmentResult(
                    SettingsBottomSheet.REQUEST_KEY,
                    settingsSnapshot(dots = 8, skips = 3),
                )
            }

            onView(withId(R.id.drawView)).check(
                matches(withContentDescription(containsString("8 dots"))),
            )

            scenario.recreate()

            onView(withId(R.id.drawView)).check(
                matches(withContentDescription(containsString("8 dots"))),
            )
        }
    }

    @Test
    fun opensSettingsSheet() {
        launchResumedActivity<DrawActivity>().use {
            onView(withId(R.id.settingsButton)).perform(click())
            onView(withText(R.string.customize_star)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun opensDetailsSheetWithDetailsTitle() {
        launchResumedActivity<DrawActivity>().use {
            onView(withId(R.id.infoButton)).perform(click())
            onView(withId(R.id.infoTitle)).check(matches(withText(R.string.more_info_button)))
            onView(withId(R.id.infoMessage)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun opensHelpSheetWithHelpTitle() {
        launchResumedActivity<DrawActivity>().use {
            onView(withId(R.id.helpButton)).perform(click())
            onView(withId(R.id.infoTitle)).check(matches(withText(R.string.help)))
            onView(withId(R.id.infoMessage)).check(matches(isDisplayed()))
        }
    }

    private fun settingsSnapshot(
        dots: Int = SettingsBottomSheet.DEFAULT_DOTS,
        skips: Int = SettingsBottomSheet.DEFAULT_SKIPS,
        thickness: Float = SettingsBottomSheet.DEFAULT_THICKNESS,
        filled: Boolean = true,
        colorIndex: Int = SettingsBottomSheet.DEFAULT_COLOR_INDEX,
        geometrySettled: Boolean = true,
    ): Bundle = Bundle().apply {
        putInt(SettingsBottomSheet.KEY_DOTS, dots)
        putInt(SettingsBottomSheet.KEY_SKIPS, skips)
        putFloat(SettingsBottomSheet.KEY_THICKNESS, thickness)
        putBoolean(SettingsBottomSheet.KEY_FILLED, filled)
        putInt(SettingsBottomSheet.KEY_COLOR_INDEX, colorIndex)
        putBoolean(SettingsBottomSheet.KEY_GEOMETRY_SETTLED, geometrySettled)
    }
}
