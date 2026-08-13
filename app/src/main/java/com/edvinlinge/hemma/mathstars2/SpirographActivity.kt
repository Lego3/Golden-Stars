package com.edvinlinge.hemma.mathstars2

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import com.edvinlinge.hemma.mathstars2.databinding.ActivitySpirographBinding

class SpirographActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpirographBinding
    private lateinit var preferences: AppPreferences

    // This activity owns the curve's geometry and styling because it owns the settings sheet.
    // SpirographView only keeps its own viewport and animation progress across recreation.
    private var fixedRadius = SpirographMath.DEFAULT_FIXED
    private var rollingRadius = SpirographMath.DEFAULT_ROLLING
    private var penOffset = SpirographMath.DEFAULT_PEN
    private var inside = SpirographMath.DEFAULT_INSIDE
    private var thickness = SettingsBottomSheet.DEFAULT_THICKNESS
    private var colorIndex = SettingsBottomSheet.DEFAULT_COLOR_INDEX
    private var speed = 1.0f

    /** False while a geometry slider is being dragged. See [onSettingsChanged]. */
    private var geometrySettled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        binding = ActivitySpirographBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = AppPreferences.get(this)
        restoreSettings(savedInstanceState)

        binding.spirographView.setOnZoomChangedListener { zoom ->
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

        supportFragmentManager.setFragmentResultListener(
            SettingsBottomSheet.REQUEST_KEY,
            this,
        ) { _, result -> onSettingsChanged(result) }

        speed = speed.coerceIn(binding.speedSlider.valueFrom, binding.speedSlider.valueTo)
        binding.speedSlider.value = speed
        binding.speedSlider.addOnChangeListener { _, value, _ ->
            speed = value
            applySpeed()
            persistSpirographSettings()
        }

        applySettings()

        binding.replayButton.setOnClickListener { binding.spirographView.replay() }
        binding.resetButton.setOnClickListener { binding.spirographView.resetZoomAndPan() }

        binding.settingsButton.setOnClickListener {
            SettingsBottomSheet.newInstance(
                thickness = thickness,
                colorIndex = colorIndex,
                title = getString(R.string.customize_spirograph),
                showStarControls = false,
                showSpirographControls = true,
                fixedRadius = fixedRadius,
                rollingRadius = rollingRadius,
                penOffset = penOffset,
                inside = inside,
            ).show(supportFragmentManager, SettingsBottomSheet.TAG)
        }

        binding.infoButton.setOnClickListener {
            val message = binding.spirographView.getDetailsHtml(this)
            InfoBottomSheet.newInstance(message).show(supportFragmentManager, InfoBottomSheet.TAG)
        }

        binding.helpButton.setOnClickListener {
            val helpText = getString(R.string.spirograph_help)
            InfoBottomSheet.newInstance(helpText).show(supportFragmentManager, InfoBottomSheet.TAG)
        }
    }

    private fun restoreSettings(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            fixedRadius = savedInstanceState.getInt(SettingsBottomSheet.KEY_FIXED_RADIUS, fixedRadius)
            rollingRadius = savedInstanceState.getInt(SettingsBottomSheet.KEY_ROLLING_RADIUS, rollingRadius)
            penOffset = savedInstanceState.getInt(SettingsBottomSheet.KEY_PEN_OFFSET, penOffset)
            inside = savedInstanceState.getBoolean(SettingsBottomSheet.KEY_INSIDE, inside)
            thickness = savedInstanceState.getFloat(SettingsBottomSheet.KEY_THICKNESS, thickness)
            colorIndex = savedInstanceState.getInt(SettingsBottomSheet.KEY_COLOR_INDEX, colorIndex)
            speed = savedInstanceState.getFloat(STATE_SPEED, speed)
            val restored = SpirographMath.normalized(fixedRadius, rollingRadius, penOffset, inside)
            fixedRadius = restored.fixedRadius
            rollingRadius = restored.rollingRadius
            penOffset = restored.penOffset
            return
        }

        val saved = preferences.loadSpirographSettings()
        fixedRadius = saved.fixedRadius
        rollingRadius = saved.rollingRadius
        penOffset = saved.penOffset
        inside = saved.inside
        thickness = saved.thickness
        colorIndex = saved.colorIndex
        speed = saved.speed
    }

    override fun onPause() {
        super.onPause()
        persistSpirographSettings()
    }

    private fun persistSpirographSettings() {
        preferences.saveSpirographSettings(
            AppPreferences.SpirographSettings(
                fixedRadius = fixedRadius,
                rollingRadius = rollingRadius,
                penOffset = penOffset,
                inside = inside,
                thickness = thickness,
                colorIndex = colorIndex,
                speed = speed,
            ),
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(SettingsBottomSheet.KEY_FIXED_RADIUS, fixedRadius)
        outState.putInt(SettingsBottomSheet.KEY_ROLLING_RADIUS, rollingRadius)
        outState.putInt(SettingsBottomSheet.KEY_PEN_OFFSET, penOffset)
        outState.putBoolean(SettingsBottomSheet.KEY_INSIDE, inside)
        outState.putFloat(SettingsBottomSheet.KEY_THICKNESS, thickness)
        outState.putInt(SettingsBottomSheet.KEY_COLOR_INDEX, colorIndex)
        outState.putFloat(STATE_SPEED, speed)
    }

    private fun applySettings() {
        binding.spirographView.setParams(fixedRadius, rollingRadius, penOffset, inside)
        binding.spirographView.setStrokeWidth(thickness)
        binding.spirographView.setDrawColor(SettingsBottomSheet.colorAt(this, colorIndex))
        applySpeed()
    }

    private fun applySpeed() {
        if (DrawViewMath.shouldRenderInstantly(speed)) {
            binding.spirographView.setInstant(true)
        } else {
            binding.spirographView.setInstant(false)
            binding.spirographView.setAnimationSpeed(speed)
        }
    }

    /** Applies only what actually changed, so a colour tap does not restart the animation. */
    private fun onSettingsChanged(result: Bundle) {
        val settled = result.getBoolean(SettingsBottomSheet.KEY_GEOMETRY_SETTLED, true)

        val newFixed = result.getInt(SettingsBottomSheet.KEY_FIXED_RADIUS, fixedRadius)
        val newRolling = result.getInt(SettingsBottomSheet.KEY_ROLLING_RADIUS, rollingRadius)
        val newPen = result.getInt(SettingsBottomSheet.KEY_PEN_OFFSET, penOffset)
        val newInside = result.getBoolean(SettingsBottomSheet.KEY_INSIDE, inside)
        val normalized = SpirographMath.normalized(newFixed, newRolling, newPen, newInside)

        if (normalized != SpirographMath.Params(fixedRadius, rollingRadius, penOffset, inside)) {
            fixedRadius = normalized.fixedRadius
            rollingRadius = normalized.rollingRadius
            penOffset = normalized.penOffset
            inside = normalized.inside
            binding.spirographView.setGeometry(
                fixedRadius, rollingRadius, penOffset, inside, animate = settled,
            )
        } else if (settled && !geometrySettled) {
            binding.spirographView.replay()
        }
        geometrySettled = settled

        val newThickness = result.getFloat(SettingsBottomSheet.KEY_THICKNESS, thickness)
        if (newThickness != thickness) {
            thickness = newThickness
            binding.spirographView.setStrokeWidth(thickness)
        }

        val newColorIndex = result.getInt(SettingsBottomSheet.KEY_COLOR_INDEX, colorIndex)
        if (newColorIndex != colorIndex) {
            colorIndex = newColorIndex
            binding.spirographView.setDrawColor(SettingsBottomSheet.colorAt(this, colorIndex))
        }

        persistSpirographSettings()
    }

    companion object {
        private const val STATE_SPEED = "speed"
    }
}
