package com.edvinlinge.hemma.mathstars2

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Locally persisted user preferences for both visualization screens. */
class AppPreferences private constructor(
    private val prefs: SharedPreferences,
) {

    data class StarSettings(
        val dots: Int,
        val skips: Int,
        val thickness: Float,
        val filled: Boolean,
        val colorIndex: Int,
        val speed: Float,
    )

    fun loadStarSettings(): StarSettings = StarSettings(
        dots = prefs.getInt(KEY_STAR_DOTS, SettingsBottomSheet.DEFAULT_DOTS),
        skips = prefs.getInt(KEY_STAR_SKIPS, SettingsBottomSheet.DEFAULT_SKIPS),
        thickness = prefs.getFloat(KEY_STAR_THICKNESS, SettingsBottomSheet.DEFAULT_THICKNESS),
        filled = prefs.getBoolean(KEY_STAR_FILLED, true),
        colorIndex = prefs.getInt(KEY_STAR_COLOR_INDEX, SettingsBottomSheet.DEFAULT_COLOR_INDEX),
        speed = prefs.getFloat(KEY_STAR_SPEED, DEFAULT_STAR_SPEED),
    )

    fun saveStarSettings(settings: StarSettings) {
        prefs.edit {
            putInt(KEY_STAR_DOTS, settings.dots)
            putInt(KEY_STAR_SKIPS, settings.skips)
            putFloat(KEY_STAR_THICKNESS, settings.thickness)
            putBoolean(KEY_STAR_FILLED, settings.filled)
            putInt(KEY_STAR_COLOR_INDEX, settings.colorIndex)
            putFloat(KEY_STAR_SPEED, settings.speed)
        }
    }

    fun loadMandelbrotColorIndex(): Int =
        prefs.getInt(KEY_MANDELBROT_COLOR_INDEX, SettingsBottomSheet.DEFAULT_COLOR_INDEX)

    fun saveMandelbrotColorIndex(colorIndex: Int) {
        prefs.edit { putInt(KEY_MANDELBROT_COLOR_INDEX, colorIndex) }
    }

    companion object {
        /** SharedPreferences file included in Android backup rules. */
        const val PREFS_NAME = "app_settings"

        private const val KEY_STAR_DOTS = "star_dots"
        private const val KEY_STAR_SKIPS = "star_skips"
        private const val KEY_STAR_THICKNESS = "star_thickness"
        private const val KEY_STAR_FILLED = "star_filled"
        private const val KEY_STAR_COLOR_INDEX = "star_color_index"
        private const val KEY_STAR_SPEED = "star_speed"
        private const val KEY_MANDELBROT_COLOR_INDEX = "mandelbrot_color_index"

        private const val DEFAULT_STAR_SPEED = 1.0f

        fun get(context: Context): AppPreferences =
            AppPreferences(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    }
}
