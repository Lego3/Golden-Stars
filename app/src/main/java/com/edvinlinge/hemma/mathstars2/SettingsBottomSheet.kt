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
 * Settings for the star and Mandelbrot screens.
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

        binding.settingsTitle.text =
            arguments.getString(ARG_TITLE) ?: getString(R.string.customize_star)

        val showStarControls = arguments.getBoolean(ARG_SHOW_STAR_CONTROLS, true)
        binding.geometryControls.isVisible = showStarControls
        binding.starStyleControls.isVisible = showStarControls
        if (showStarControls) {
            bindStarControls()
        }

        swatches().forEachIndexed { index, swatch ->
            swatch.setOnClickListener {
                colorIndex = index
                publish()
            }
        }

        binding.doneButton.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun swatches(): List<View> =
        listOf(binding.colorGold, binding.colorSilver, binding.colorBlue, binding.colorGreen)

    private fun bindStarControls() {
        val dotsSlider = binding.dotsSlider
        val skipsSlider = binding.skipsSlider

        // Bounds before values, so a value is never briefly outside the slider's range.
        skipsSlider.valueTo = maxSkipsFor(dots)
        skipsSlider.value = skips.toFloat().coerceIn(skipsSlider.valueFrom, skipsSlider.valueTo)
        dotsSlider.value = dots.toFloat().coerceIn(dotsSlider.valueFrom, dotsSlider.valueTo)
        skips = skipsSlider.value.toInt()
        dots = dotsSlider.value.toInt()

        dotsSlider.addOnChangeListener { _, value, _ ->
            dots = value.toInt()
            val maxSkips = maxSkipsFor(dots)
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

        private const val ARG_TITLE = "title"
        private const val ARG_SHOW_STAR_CONTROLS = "show_star_controls"

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

        /**
         * Highest usable skip count for [dots]. Kept at two or more so it can never collide with
         * the skips slider's lower bound, which Material rejects with an exception.
         */
        private fun maxSkipsFor(dots: Int): Float = (dots / 2).toFloat().coerceAtLeast(2f)

        fun newInstance(
            dots: Int = DEFAULT_DOTS,
            skips: Int = DEFAULT_SKIPS,
            thickness: Float = DEFAULT_THICKNESS,
            filled: Boolean = true,
            colorIndex: Int = DEFAULT_COLOR_INDEX,
            title: String? = null,
            showStarControls: Boolean = true,
        ): SettingsBottomSheet {
            val args = Bundle().apply {
                putInt(KEY_DOTS, dots)
                putInt(KEY_SKIPS, skips)
                putFloat(KEY_THICKNESS, thickness)
                putBoolean(KEY_FILLED, filled)
                putInt(KEY_COLOR_INDEX, colorIndex)
                putString(ARG_TITLE, title)
                putBoolean(ARG_SHOW_STAR_CONTROLS, showStarControls)
            }
            return SettingsBottomSheet().apply { arguments = args }
        }
    }
}
