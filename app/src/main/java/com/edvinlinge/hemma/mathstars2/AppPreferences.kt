package com.edvinlinge.hemma.mathstars2

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Locally persisted user preferences for the visualization screens. */
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

    data class SpirographSettings(
        val fixedRadius: Int,
        val rollingRadius: Int,
        val penOffset: Int,
        val inside: Boolean,
        val thickness: Float,
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

    fun loadSpirographSettings(): SpirographSettings = normalizedSpirographSettings(
        fixedRadius = prefs.getInt(KEY_SPIRO_FIXED, SpirographMath.DEFAULT_FIXED),
        rollingRadius = prefs.getInt(KEY_SPIRO_ROLLING, SpirographMath.DEFAULT_ROLLING),
        penOffset = prefs.getInt(KEY_SPIRO_PEN, SpirographMath.DEFAULT_PEN),
        inside = prefs.getBoolean(KEY_SPIRO_INSIDE, SpirographMath.DEFAULT_INSIDE),
        thickness = prefs.getFloat(KEY_SPIRO_THICKNESS, SettingsBottomSheet.DEFAULT_THICKNESS),
        colorIndex = prefs.getInt(KEY_SPIRO_COLOR_INDEX, SettingsBottomSheet.DEFAULT_COLOR_INDEX),
        speed = prefs.getFloat(KEY_SPIRO_SPEED, DEFAULT_STAR_SPEED),
    )

    fun saveSpirographSettings(settings: SpirographSettings) {
        val normalized = normalizedSpirographSettings(
            fixedRadius = settings.fixedRadius,
            rollingRadius = settings.rollingRadius,
            penOffset = settings.penOffset,
            inside = settings.inside,
            thickness = settings.thickness,
            colorIndex = settings.colorIndex,
            speed = settings.speed,
        )
        prefs.edit {
            putInt(KEY_SPIRO_FIXED, normalized.fixedRadius)
            putInt(KEY_SPIRO_ROLLING, normalized.rollingRadius)
            putInt(KEY_SPIRO_PEN, normalized.penOffset)
            putBoolean(KEY_SPIRO_INSIDE, normalized.inside)
            putFloat(KEY_SPIRO_THICKNESS, normalized.thickness)
            putInt(KEY_SPIRO_COLOR_INDEX, normalized.colorIndex)
            putFloat(KEY_SPIRO_SPEED, normalized.speed)
        }
    }

    private fun normalizedSpirographSettings(
        fixedRadius: Int,
        rollingRadius: Int,
        penOffset: Int,
        inside: Boolean,
        thickness: Float,
        colorIndex: Int,
        speed: Float,
    ): SpirographSettings {
        val geometry = SpirographMath.normalized(fixedRadius, rollingRadius, penOffset, inside)
        return SpirographSettings(
            fixedRadius = geometry.fixedRadius,
            rollingRadius = geometry.rollingRadius,
            penOffset = geometry.penOffset,
            inside = geometry.inside,
            thickness = thickness.coerceIn(MIN_LINE_THICKNESS, MAX_LINE_THICKNESS),
            colorIndex = colorIndex.coerceIn(0, MAX_COLOR_INDEX),
            speed = speed.coerceIn(MIN_SPEED, MAX_SPEED),
        )
    }

    fun loadJuliaColorIndex(): Int =
        prefs.getInt(KEY_JULIA_COLOR_INDEX, SettingsBottomSheet.DEFAULT_COLOR_INDEX)

    fun saveJuliaColorIndex(colorIndex: Int) {
        prefs.edit { putInt(KEY_JULIA_COLOR_INDEX, colorIndex) }
    }

    fun loadJuliaPresetIndex(): Int =
        JuliaMath.coercedPresetIndex(
            prefs.getInt(KEY_JULIA_PRESET_INDEX, JuliaMath.DEFAULT_PRESET_INDEX),
        )

    fun saveJuliaPresetIndex(presetIndex: Int) {
        prefs.edit { putInt(KEY_JULIA_PRESET_INDEX, JuliaMath.coercedPresetIndex(presetIndex)) }
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
        private const val KEY_SPIRO_FIXED = "spiro_fixed"
        private const val KEY_SPIRO_ROLLING = "spiro_rolling"
        private const val KEY_SPIRO_PEN = "spiro_pen"
        private const val KEY_SPIRO_INSIDE = "spiro_inside"
        private const val KEY_SPIRO_THICKNESS = "spiro_thickness"
        private const val KEY_SPIRO_COLOR_INDEX = "spiro_color_index"
        private const val KEY_SPIRO_SPEED = "spiro_speed"
        private const val KEY_JULIA_COLOR_INDEX = "julia_color_index"
        private const val KEY_JULIA_PRESET_INDEX = "julia_preset_index"

        private const val DEFAULT_STAR_SPEED = 1.0f

        /** Matches the line-thickness slider in [SettingsBottomSheet]. */
        private const val MIN_LINE_THICKNESS = 2f
        private const val MAX_LINE_THICKNESS = 24f

        /** Matches the speed slider on the star and Spirograph screens. */
        private const val MIN_SPEED = 0.5f
        private const val MAX_SPEED = 4.0f

        private const val MAX_COLOR_INDEX = 3

        fun get(context: Context): AppPreferences =
            AppPreferences(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    }
}
