package com.edvinlinge.hemma.mathstars2

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import com.edvinlinge.hemma.mathstars2.databinding.ActivityDrawBinding

class DrawActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDrawBinding

    // This activity owns the star's geometry and styling because it owns the settings sheet.
    // DrawView only keeps its own viewport and animation progress across recreation.
    private var dots = SettingsBottomSheet.DEFAULT_DOTS
    private var skips = SettingsBottomSheet.DEFAULT_SKIPS
    private var thickness = SettingsBottomSheet.DEFAULT_THICKNESS
    private var colorIndex = SettingsBottomSheet.DEFAULT_COLOR_INDEX
    private var filled = true
    private var speed = DEFAULT_SPEED

    /** False while a geometry slider is being dragged. See [onSettingsChanged]. */
    private var geometrySettled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This screen is always dark, regardless of the system setting, so the system bar icons
        // have to be forced light rather than following the night-mode configuration.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        binding = ActivityDrawBinding.inflate(layoutInflater)
        setContentView(binding.root)

        restoreSettings(savedInstanceState)

        binding.drawView.setOnZoomChangedListener { zoom ->
            val formatted = formatZoom(zoom.toDouble())
            binding.zoomText.text = formatted
            binding.zoomText.contentDescription = getString(R.string.zoom_level_format, formatted)
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
        }

        // Registered here rather than when the sheet is shown, so it is restored along with the
        // activity and keeps receiving changes after a configuration change.
        supportFragmentManager.setFragmentResultListener(
            SettingsBottomSheet.REQUEST_KEY,
            this,
        ) { _, result -> onSettingsChanged(result) }

        speed = speed.coerceIn(binding.speedSlider.valueFrom, binding.speedSlider.valueTo)
        binding.speedSlider.value = speed
        binding.speedSlider.addOnChangeListener { _, value, _ ->
            speed = value
            applySpeed()
        }

        applySettings()

        binding.replayButton.setOnClickListener { binding.drawView.replay() }
        binding.resetButton.setOnClickListener { binding.drawView.resetZoomAndPan() }

        binding.settingsButton.setOnClickListener {
            SettingsBottomSheet.newInstance(
                dots = dots,
                skips = skips,
                thickness = thickness,
                filled = filled,
                colorIndex = colorIndex,
            ).show(supportFragmentManager, SettingsBottomSheet.TAG)
        }

        binding.infoButton.setOnClickListener {
            val message = binding.drawView.getDetailsHtml(this)
            InfoBottomSheet.newInstance(message).show(supportFragmentManager, InfoBottomSheet.TAG)
        }

        binding.helpButton.setOnClickListener {
            val helpText = getString(R.string.help_details)
            InfoBottomSheet.newInstance(helpText).show(supportFragmentManager, InfoBottomSheet.TAG)
        }
    }

    private fun restoreSettings(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            dots = intent.getIntExtra(EXTRA_DOTS, dots)
            skips = intent.getIntExtra(EXTRA_SKIPS, skips)
            return
        }
        dots = savedInstanceState.getInt(SettingsBottomSheet.KEY_DOTS, dots)
        skips = savedInstanceState.getInt(SettingsBottomSheet.KEY_SKIPS, skips)
        thickness = savedInstanceState.getFloat(SettingsBottomSheet.KEY_THICKNESS, thickness)
        filled = savedInstanceState.getBoolean(SettingsBottomSheet.KEY_FILLED, filled)
        colorIndex = savedInstanceState.getInt(SettingsBottomSheet.KEY_COLOR_INDEX, colorIndex)
        speed = savedInstanceState.getFloat(STATE_SPEED, speed)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(SettingsBottomSheet.KEY_DOTS, dots)
        outState.putInt(SettingsBottomSheet.KEY_SKIPS, skips)
        outState.putFloat(SettingsBottomSheet.KEY_THICKNESS, thickness)
        outState.putBoolean(SettingsBottomSheet.KEY_FILLED, filled)
        outState.putInt(SettingsBottomSheet.KEY_COLOR_INDEX, colorIndex)
        outState.putFloat(STATE_SPEED, speed)
    }

    private fun applySettings() {
        binding.drawView.setDotsAndSkips(dots, skips)
        binding.drawView.setStrokeWidth(thickness)
        binding.drawView.setFilled(filled)
        binding.drawView.setDrawColor(SettingsBottomSheet.colorAt(this, colorIndex))
        applySpeed()
    }

    private fun applySpeed() {
        if (speed >= INSTANT_SPEED_THRESHOLD) {
            binding.drawView.setInstant(true)
        } else {
            binding.drawView.setInstant(false)
            binding.drawView.setAnimationSpeed(speed)
        }
    }

    /** Applies only what actually changed, so a colour tap does not restart the animation. */
    private fun onSettingsChanged(result: Bundle) {
        val settled = result.getBoolean(SettingsBottomSheet.KEY_GEOMETRY_SETTLED, true)

        val newDots = result.getInt(SettingsBottomSheet.KEY_DOTS, dots)
        val newSkips = result.getInt(SettingsBottomSheet.KEY_SKIPS, skips)
        if (newDots != dots || newSkips != skips) {
            dots = newDots
            skips = newSkips
            // Dragging a slider shows each shape immediately; the reveal animation is saved for
            // when the finger lifts, instead of restarting on every step of the drag.
            binding.drawView.setGeometry(dots, skips, animate = settled)
        } else if (settled && !geometrySettled) {
            binding.drawView.replay()
        }
        geometrySettled = settled

        val newThickness = result.getFloat(SettingsBottomSheet.KEY_THICKNESS, thickness)
        if (newThickness != thickness) {
            thickness = newThickness
            binding.drawView.setStrokeWidth(thickness)
        }

        val newFilled = result.getBoolean(SettingsBottomSheet.KEY_FILLED, filled)
        if (newFilled != filled) {
            filled = newFilled
            binding.drawView.setFilled(filled)
        }

        val newColorIndex = result.getInt(SettingsBottomSheet.KEY_COLOR_INDEX, colorIndex)
        if (newColorIndex != colorIndex) {
            colorIndex = newColorIndex
            binding.drawView.setDrawColor(SettingsBottomSheet.colorAt(this, colorIndex))
        }
    }

    companion object {
        const val EXTRA_DOTS = "dots"
        const val EXTRA_SKIPS = "skips"

        private const val STATE_SPEED = "speed"
        private const val DEFAULT_SPEED = 1.0f

        /** At and above this slider value the star is drawn instantly instead of animated. */
        private const val INSTANT_SPEED_THRESHOLD = 4.0f
    }
}
