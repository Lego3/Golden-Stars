package com.edvinlinge.hemma.mathstars2

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.edvinlinge.hemma.mathstars2.databinding.ActivityJuliaBinding

class JuliaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJuliaBinding
    private lateinit var preferences: AppPreferences

    // Owned here rather than by the view, because this activity owns the settings sheet and
    // preset slider that change them. The view keeps only its viewport across recreation.
    private var colorIndex = SettingsBottomSheet.DEFAULT_COLOR_INDEX
    private var presetIndex = JuliaMath.DEFAULT_PRESET_INDEX

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This screen is always dark, regardless of the system setting, so the system bar icons
        // have to be forced light rather than following the night-mode configuration.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        binding = ActivityJuliaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = AppPreferences.get(this)
        if (savedInstanceState != null) {
            colorIndex = savedInstanceState.getInt(SettingsBottomSheet.KEY_COLOR_INDEX)
            presetIndex = JuliaMath.coercedPresetIndex(
                savedInstanceState.getInt(STATE_PRESET_INDEX),
            )
        } else {
            colorIndex = preferences.loadJuliaColorIndex()
            presetIndex = preferences.loadJuliaPresetIndex()
        }

        binding.juliaView.setColorPalette(paletteFor(colorIndex))
        applyPreset()

        binding.juliaView.setOnZoomChangedListener { zoom ->
            val formatted = formatZoom(zoom)
            binding.zoomText.text = formatted
            binding.zoomText.contentDescription = getString(R.string.zoom_level_format, formatted)
        }

        binding.juliaView.setOnRenderingStateChangedListener { isRendering ->
            binding.renderProgress.isVisible = isRendering
        }

        doOnScreenInsets { insets ->
            binding.controlPanel.updateLayoutParams<MarginLayoutParams> {
                bottomMargin = insets.bottom + insets.edgeMargin
                marginStart = insets.start + insets.edgeMargin
                marginEnd = insets.end + insets.edgeMargin
            }
            binding.zoomText.updateLayoutParams<MarginLayoutParams> {
                topMargin = insets.top + insets.edgeMargin
                marginEnd = insets.end + insets.edgeMargin
            }
            binding.renderProgress.updateLayoutParams<MarginLayoutParams> {
                topMargin = insets.top + insets.edgeMargin
                marginStart = insets.start + insets.edgeMargin
            }
        }

        supportFragmentManager.setFragmentResultListener(
            SettingsBottomSheet.REQUEST_KEY,
            this,
        ) { _, result ->
            val newColorIndex = result.getInt(SettingsBottomSheet.KEY_COLOR_INDEX, colorIndex)
            if (newColorIndex != colorIndex) {
                colorIndex = newColorIndex
                binding.juliaView.setColorPalette(paletteFor(colorIndex))
                persistJuliaSettings()
            }
        }

        binding.presetSlider.valueFrom = 0f
        binding.presetSlider.valueTo = (JuliaMath.PRESETS.lastIndex).toFloat()
        binding.presetSlider.value = presetIndex.toFloat()
        binding.presetSlider.addOnChangeListener { _, value, _ ->
            val newIndex = JuliaMath.coercedPresetIndex(value.toInt())
            if (newIndex != presetIndex) {
                presetIndex = newIndex
                applyPreset()
                persistJuliaSettings()
            }
        }

        binding.resetButton.setOnClickListener { binding.juliaView.resetZoomAndPan() }

        binding.settingsButton.setOnClickListener {
            SettingsBottomSheet.newInstance(
                colorIndex = colorIndex,
                title = getString(R.string.customize_julia),
                showStarControls = false,
            ).show(supportFragmentManager, SettingsBottomSheet.TAG)
        }

        binding.helpButton.setOnClickListener {
            val preset = JuliaMath.presetAt(presetIndex)
            val connected = JuliaMath.isLikelyConnected(preset.real, preset.imag)
            val connectivity = getString(
                if (connected) R.string.julia_details_connected else R.string.julia_details_dust,
            )
            val helpText = getString(
                R.string.julia_help,
                JuliaMath.formatConstant(preset.real, preset.imag),
                presetName(presetIndex),
                connectivity,
            )
            InfoBottomSheet.newInstance(helpText, getString(R.string.help))
                .show(supportFragmentManager, InfoBottomSheet.TAG)
        }
    }

    override fun onPause() {
        super.onPause()
        persistJuliaSettings()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(SettingsBottomSheet.KEY_COLOR_INDEX, colorIndex)
        outState.putInt(STATE_PRESET_INDEX, presetIndex)
    }

    private fun persistJuliaSettings() {
        preferences.saveJuliaColorIndex(colorIndex)
        preferences.saveJuliaPresetIndex(presetIndex)
    }

    private fun applyPreset() {
        val preset = JuliaMath.presetAt(presetIndex)
        binding.juliaView.setConstant(preset.real, preset.imag)
        binding.cValueText.text = getString(
            R.string.julia_c_label,
            JuliaMath.formatConstant(preset.real, preset.imag),
        )
        binding.presetSlider.contentDescription = getString(
            R.string.julia_preset_a11y,
            presetName(presetIndex),
        )
    }

    private fun presetName(index: Int): String = getString(
        when (JuliaMath.coercedPresetIndex(index)) {
            0 -> R.string.julia_preset_rabbit
            1 -> R.string.julia_preset_san_marco
            2 -> R.string.julia_preset_dendrite
            3 -> R.string.julia_preset_siegel
            4 -> R.string.julia_preset_basilica
            5 -> R.string.julia_preset_airplane
            6 -> R.string.julia_preset_spiral
            else -> R.string.julia_preset_dust
        },
    )

    /** Swatch order matches the palette order, so the index maps straight across. */
    private fun paletteFor(index: Int): FractalPalette =
        FractalPalette.entries[index.coerceIn(FractalPalette.entries.indices)]

    companion object {
        private const val STATE_PRESET_INDEX = "preset_index"
    }
}
