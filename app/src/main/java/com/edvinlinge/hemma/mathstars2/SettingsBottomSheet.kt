package com.edvinlinge.hemma.mathstars2

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.edvinlinge.hemma.mathstars2.databinding.LayoutSettingsBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider

/**
 * Settings for the star, Mandelbrot, and Spirograph screens.
 *
 * Every change publishes a full snapshot of the settings through the fragment result API instead
 * of invoking callbacks held by the host. Callbacks assigned when the sheet is shown are lost when
 * the activity is recreated, which silently turned every control into a no-op after a rotation.
 */
class SettingsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: LayoutSettingsBottomSheetBinding? = null
    private val binding get() = _binding!!

    private var dots = DEFAULT_DOTS
    private var skips = DEFAULT_SKIPS
    private var thickness = DEFAULT_THICKNESS
    private var filled = true
    private var colorIndex = DEFAULT_COLOR_INDEX
    private var fixedRadius = SpirographMath.DEFAULT_FIXED
    private var rollingRadius = SpirographMath.DEFAULT_ROLLING
    private var penOffset = SpirographMath.DEFAULT_PEN
    private var inside = SpirographMath.DEFAULT_INSIDE

    /** True while the user is dragging one of the geometry sliders. */
    private var draggingGeometry = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = LayoutSettingsBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val arguments = requireArguments()
        // After a recreation the user's in-progress edits live in savedInstanceState; the
        // arguments only carry the values the host started the sheet with.
        val initial = savedInstanceState ?: arguments
        dots = initial.getInt(KEY_DOTS, DEFAULT_DOTS)
        skips = initial.getInt(KEY_SKIPS, DEFAULT_SKIPS)
        thickness = initial.getFloat(KEY_THICKNESS, DEFAULT_THICKNESS)
        filled = initial.getBoolean(KEY_FILLED, true)
        colorIndex = initial.getInt(KEY_COLOR_INDEX, DEFAULT_COLOR_INDEX)
        fixedRadius = initial.getInt(KEY_FIXED_RADIUS, SpirographMath.DEFAULT_FIXED)
        rollingRadius = initial.getInt(KEY_ROLLING_RADIUS, SpirographMath.DEFAULT_ROLLING)
        penOffset = initial.getInt(KEY_PEN_OFFSET, SpirographMath.DEFAULT_PEN)
        inside = initial.getBoolean(KEY_INSIDE, SpirographMath.DEFAULT_INSIDE)

        binding.settingsTitle.text =
            arguments.getString(ARG_TITLE) ?: getString(R.string.customize_star)

        val showStarControls = arguments.getBoolean(ARG_SHOW_STAR_CONTROLS, true)
        val showSpirographControls = arguments.getBoolean(ARG_SHOW_SPIROGRAPH_CONTROLS, false)
        binding.geometryControls.isVisible = showStarControls
        binding.spirographControls.isVisible = showSpirographControls
        binding.starStyleControls.isVisible = showStarControls || showSpirographControls
        binding.switchFilled.isVisible = showStarControls
        if (showStarControls) {
            bindStarControls()
        }
        if (showSpirographControls) {
            bindSpirographControls()
            if (!showStarControls) {
                bindThicknessSlider()
            }
        }

        bindColorSwatches()

        binding.doneButton.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun swatches(): List<View> =
        listOf(binding.colorGold, binding.colorSilver, binding.colorBlue, binding.colorGreen)

    private fun bindColorSwatches() {
        val selectedForeground = ContextCompat.getDrawable(requireContext(), R.drawable.fg_color_swatch_selected)
        swatches().forEachIndexed { index, swatch ->
            val selected = index == colorIndex
            swatch.isSelected = selected
            swatch.foreground = if (selected) selectedForeground else null
            swatch.contentDescription = if (selected) {
                getString(R.string.color_selected_format, colorNameAt(index))
            } else {
                colorNameAt(index)
            }
            swatch.setOnClickListener {
                if (colorIndex == index) return@setOnClickListener
                colorIndex = index
                bindColorSwatches()
                publish()
            }
        }
    }

    private fun colorNameAt(index: Int): String = getString(
        when (index) {
            0 -> R.string.color_gold
            1 -> R.string.color_silver
            2 -> R.string.color_blue
            else -> R.string.color_green
        },
    )

    private fun bindStarControls() {
        val dotsSlider = binding.dotsSlider
        val skipsSlider = binding.skipsSlider

        // Bounds before values, so a value is never briefly outside the slider's range.
        skipsSlider.valueTo = StarMath.maxSkipsFor(dots).toFloat()
        skipsSlider.value = StarMath.coercedSkips(dots, skips, skipsSlider.valueFrom.toInt()).toFloat()
        dotsSlider.value = dots.toFloat().coerceIn(dotsSlider.valueFrom, dotsSlider.valueTo)
        skips = skipsSlider.value.toInt()
        dots = dotsSlider.value.toInt()

        dotsSlider.addOnChangeListener { _, value, _ ->
            dots = value.toInt()
            val maxSkips = StarMath.maxSkipsFor(dots).toFloat()
            if (skipsSlider.value > maxSkips) {
                skipsSlider.value = maxSkips
            }
            skipsSlider.valueTo = maxSkips
            skips = skipsSlider.value.toInt()
            publish()
        }

        skipsSlider.addOnChangeListener { _, value, _ ->
            skips = value.toInt()
            publish()
        }

        // Rebuilding the star is cheap, but restarting its several-second reveal on every step of
        // a drag is not. The host is told when the drag ends so it can animate exactly once.
        val dragListener = object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                draggingGeometry = true
            }

            override fun onStopTrackingTouch(slider: Slider) {
                draggingGeometry = false
                publish()
            }
        }
        dotsSlider.addOnSliderTouchListener(dragListener)
        skipsSlider.addOnSliderTouchListener(dragListener)

        binding.switchFilled.apply {
            isChecked = filled
            setOnCheckedChangeListener { _, checked ->
                filled = checked
                publish()
            }
        }

        binding.thicknessSlider.apply {
            value = thickness.coerceIn(valueFrom, valueTo)
            thickness = value
            addOnChangeListener { _, newValue, _ ->
                thickness = newValue
                publish()
            }
        }
    }

    private fun bindThicknessSlider() {
        binding.thicknessSlider.apply {
            value = thickness.coerceIn(valueFrom, valueTo)
            thickness = value
            addOnChangeListener { _, newValue, _ ->
                thickness = newValue
                publish()
            }
        }
    }

    private fun bindSpirographControls() {
        val fixedSlider = binding.fixedRadiusSlider
        val rollingSlider = binding.rollingRadiusSlider
        val penSlider = binding.penOffsetSlider

        val normalized = SpirographMath.normalized(fixedRadius, rollingRadius, penOffset, inside)
        fixedRadius = normalized.fixedRadius
        rollingRadius = normalized.rollingRadius
        penOffset = normalized.penOffset
        inside = normalized.inside

        fixedSlider.valueFrom = SpirographMath.MIN_FIXED.toFloat()
        fixedSlider.valueTo = SpirographMath.MAX_FIXED.toFloat()
        penSlider.valueFrom = SpirographMath.MIN_PEN.toFloat()
        penSlider.valueTo = SpirographMath.MAX_PEN.toFloat()
        applyRollingBounds()

        fixedSlider.value = fixedRadius.toFloat()
        rollingSlider.value = rollingRadius.toFloat()
        penSlider.value = penOffset.toFloat()
        binding.switchInside.isChecked = inside

        fixedSlider.addOnChangeListener { _, value, _ ->
            fixedRadius = value.toInt()
            applyRollingBounds()
            rollingRadius = rollingSlider.value.toInt()
            publish()
        }
        rollingSlider.addOnChangeListener { _, value, _ ->
            rollingRadius = value.toInt()
            publish()
        }
        penSlider.addOnChangeListener { _, value, _ ->
            penOffset = value.toInt()
            publish()
        }

        val dragListener = object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                draggingGeometry = true
            }

            override fun onStopTrackingTouch(slider: Slider) {
                draggingGeometry = false
                publish()
            }
        }
        fixedSlider.addOnSliderTouchListener(dragListener)
        rollingSlider.addOnSliderTouchListener(dragListener)
        penSlider.addOnSliderTouchListener(dragListener)

        binding.switchInside.setOnCheckedChangeListener { _, checked ->
            inside = checked
            applyRollingBounds()
            rollingRadius = rollingSlider.value.toInt()
            publish()
        }
    }

    private fun applyRollingBounds() {
        val rollingSlider = binding.rollingRadiusSlider
        val maxRolling = SpirographMath.coercedRolling(fixedRadius, SpirographMath.MAX_ROLLING, inside)
        rollingSlider.valueFrom = SpirographMath.MIN_ROLLING.toFloat()
        if (rollingSlider.value > maxRolling) {
            rollingSlider.value = maxRolling.toFloat()
        }
        rollingSlider.valueTo = maxRolling.toFloat()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putAll(snapshot())
    }

    private fun snapshot(): Bundle = Bundle().apply {
        putInt(KEY_DOTS, dots)
        putInt(KEY_SKIPS, skips)
        putFloat(KEY_THICKNESS, thickness)
        putBoolean(KEY_FILLED, filled)
        putInt(KEY_COLOR_INDEX, colorIndex)
        putBoolean(KEY_GEOMETRY_SETTLED, !draggingGeometry)
        putInt(KEY_FIXED_RADIUS, fixedRadius)
        putInt(KEY_ROLLING_RADIUS, rollingRadius)
        putInt(KEY_PEN_OFFSET, penOffset)
        putBoolean(KEY_INSIDE, inside)
    }

    private fun publish() {
        parentFragmentManager.setFragmentResult(REQUEST_KEY, snapshot())
    }

    companion object {
        const val TAG = "SettingsBottomSheet"
        const val REQUEST_KEY = "settings_request"

        const val KEY_DOTS = "dots"
        const val KEY_SKIPS = "skips"
        const val KEY_THICKNESS = "thickness"
        const val KEY_FILLED = "filled"
        const val KEY_COLOR_INDEX = "color_index"
        const val KEY_GEOMETRY_SETTLED = "geometry_settled"
        const val KEY_FIXED_RADIUS = "fixed_radius"
        const val KEY_ROLLING_RADIUS = "rolling_radius"
        const val KEY_PEN_OFFSET = "pen_offset"
        const val KEY_INSIDE = "inside"

        private const val ARG_TITLE = "title"
        private const val ARG_SHOW_STAR_CONTROLS = "show_star_controls"
        private const val ARG_SHOW_SPIROGRAPH_CONTROLS = "show_spirograph_controls"

        const val DEFAULT_DOTS = 5
        const val DEFAULT_SKIPS = 2
        const val DEFAULT_THICKNESS = 8f
        const val DEFAULT_COLOR_INDEX = 0

        /** Selectable colours, in swatch order. Matches [MandelbrotView.Palette]. */
        private val SWATCH_COLORS = intArrayOf(
            R.color.swatch_gold,
            R.color.swatch_silver,
            R.color.swatch_blue,
            R.color.swatch_green,
        )

        fun colorAt(context: Context, index: Int): Int =
            ContextCompat.getColor(context, SWATCH_COLORS[index.coerceIn(SWATCH_COLORS.indices)])

        fun newInstance(
            dots: Int = DEFAULT_DOTS,
            skips: Int = DEFAULT_SKIPS,
            thickness: Float = DEFAULT_THICKNESS,
            filled: Boolean = true,
            colorIndex: Int = DEFAULT_COLOR_INDEX,
            title: String? = null,
            showStarControls: Boolean = true,
            showSpirographControls: Boolean = false,
            fixedRadius: Int = SpirographMath.DEFAULT_FIXED,
            rollingRadius: Int = SpirographMath.DEFAULT_ROLLING,
            penOffset: Int = SpirographMath.DEFAULT_PEN,
            inside: Boolean = SpirographMath.DEFAULT_INSIDE,
        ): SettingsBottomSheet {
            val args = Bundle().apply {
                putInt(KEY_DOTS, dots)
                putInt(KEY_SKIPS, skips)
                putFloat(KEY_THICKNESS, thickness)
                putBoolean(KEY_FILLED, filled)
                putInt(KEY_COLOR_INDEX, colorIndex)
                putString(ARG_TITLE, title)
                putBoolean(ARG_SHOW_STAR_CONTROLS, showStarControls)
                putBoolean(ARG_SHOW_SPIROGRAPH_CONTROLS, showSpirographControls)
                putInt(KEY_FIXED_RADIUS, fixedRadius)
                putInt(KEY_ROLLING_RADIUS, rollingRadius)
                putInt(KEY_PEN_OFFSET, penOffset)
                putBoolean(KEY_INSIDE, inside)
            }
            return SettingsBottomSheet().apply { arguments = args }
        }
    }
}
