package com.edvinlinge.hemma.mathstars2

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.edvinlinge.hemma.mathstars2.databinding.ActivityMandelbrotBinding

class MandelbrotActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMandelbrotBinding
    private lateinit var preferences: AppPreferences

    // Owned here rather than by the view, because this activity owns the settings sheet that
    // changes it. The view keeps only its viewport across recreation.
    private var colorIndex = SettingsBottomSheet.DEFAULT_COLOR_INDEX

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This screen is always dark, regardless of the system setting, so the system bar icons
        // have to be forced light rather than following the night-mode configuration.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        binding = ActivityMandelbrotBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = AppPreferences.get(this)
        colorIndex = savedInstanceState?.getInt(SettingsBottomSheet.KEY_COLOR_INDEX)
            ?: preferences.loadMandelbrotColorIndex()

        binding.mandelbrotView.setColorPalette(paletteFor(colorIndex))

        binding.mandelbrotView.setOnZoomChangedListener { zoom ->
            val formatted = formatZoom(zoom)
            binding.zoomText.text = formatted
            binding.zoomText.contentDescription = getString(R.string.zoom_level_format, formatted)
        }

        binding.mandelbrotView.setOnRenderingStateChangedListener { isRendering ->
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
                binding.mandelbrotView.setColorPalette(paletteFor(colorIndex))
                preferences.saveMandelbrotColorIndex(colorIndex)
            }
        }

        binding.resetButton.setOnClickListener { binding.mandelbrotView.resetZoomAndPan() }

        binding.settingsButton.setOnClickListener {
            SettingsBottomSheet.newInstance(
                colorIndex = colorIndex,
                title = getString(R.string.customize_mandelbrot),
                showStarControls = false,
            ).show(supportFragmentManager, SettingsBottomSheet.TAG)
        }

        binding.helpButton.setOnClickListener {
            val helpText = getString(R.string.mandelbrot_help)
            InfoBottomSheet.newInstance(helpText, getString(R.string.help))
                .show(supportFragmentManager, InfoBottomSheet.TAG)
        }
    }

    override fun onPause() {
        super.onPause()
        preferences.saveMandelbrotColorIndex(colorIndex)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(SettingsBottomSheet.KEY_COLOR_INDEX, colorIndex)
    }

    /** Swatch order matches the palette order, so the index maps straight across. */
    private fun paletteFor(index: Int): FractalPalette =
        FractalPalette.entries[index.coerceIn(FractalPalette.entries.indices)]
}
