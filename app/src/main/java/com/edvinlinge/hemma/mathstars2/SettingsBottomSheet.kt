package com.edvinlinge.hemma.mathstars2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class SettingsBottomSheet : BottomSheetDialogFragment() {

    var onGeometryChanged: ((Int, Int) -> Unit)? = null
    var onThicknessChanged: ((Float) -> Unit)? = null
    var onColorChanged: ((Int) -> Unit)? = null
    var onFilledChanged: ((Boolean) -> Unit)? = null

    private var currentDots = 5
    private var currentSkips = 2
    private var currentThickness = 8f
    private var currentFilled = true
    private var showStarControls = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.layout_settings_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentDots = arguments?.getInt(ARG_DOTS) ?: 5
        currentSkips = arguments?.getInt(ARG_SKIPS) ?: 2
        currentThickness = arguments?.getFloat(ARG_THICKNESS) ?: 8f
        currentFilled = arguments?.getBoolean(ARG_FILLED) ?: true
        showStarControls = arguments?.getBoolean(ARG_SHOW_STAR_CONTROLS) ?: true

        val title = arguments?.getString(ARG_TITLE) ?: getString(R.string.customize_star)
        view.findViewById<TextView>(R.id.settingsTitle).text = title

        view.findViewById<View>(R.id.geometryControls).visibility = if (showStarControls) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.starStyleControls).visibility = if (showStarControls) View.VISIBLE else View.GONE

        val skipsSlider = view.findViewById<Slider>(R.id.skipsSlider)

        view.findViewById<Slider>(R.id.dotsSlider).apply {
            value = currentDots.toFloat()
            addOnChangeListener { _, value, _ ->
                currentDots = value.toInt()
                val maxSkips = (currentDots / 2).toFloat()
                if (skipsSlider.value > maxSkips) {
                    skipsSlider.value = maxSkips
                    currentSkips = maxSkips.toInt()
                }
                skipsSlider.valueTo = maxSkips
                onGeometryChanged?.invoke(currentDots, currentSkips)
            }
        }

        skipsSlider.apply {
            valueTo = (currentDots / 2).toFloat()
            value = currentSkips.toFloat()
            addOnChangeListener { _, value, _ ->
                currentSkips = value.toInt()
                onGeometryChanged?.invoke(currentDots, currentSkips)
            }
        }

        view.findViewById<MaterialSwitch>(R.id.switchFilled).apply {
            isChecked = currentFilled
            setOnCheckedChangeListener { _, isChecked ->
                onFilledChanged?.invoke(isChecked)
            }
        }

        view.findViewById<Slider>(R.id.thicknessSlider).apply {
            value = currentThickness
            addOnChangeListener { _, value, _ ->
                onThicknessChanged?.invoke(value)
            }
        }

        view.findViewById<View>(R.id.colorGold).setOnClickListener { onColorChanged?.invoke("#FFD700".toColorInt()) }
        view.findViewById<View>(R.id.colorSilver).setOnClickListener { onColorChanged?.invoke("#C0C0C0".toColorInt()) }
        view.findViewById<View>(R.id.colorBlue).setOnClickListener { onColorChanged?.invoke("#00BFFF".toColorInt()) }
        view.findViewById<View>(R.id.colorGreen).setOnClickListener { onColorChanged?.invoke("#32CD32".toColorInt()) }

        view.findViewById<Button>(R.id.doneButton).setOnClickListener {
            dismiss()
        }
    }

    companion object {
        const val TAG = "SettingsBottomSheet"
        private const val ARG_DOTS = "arg_dots"
        private const val ARG_SKIPS = "arg_skips"
        private const val ARG_THICKNESS = "arg_thickness"
        private const val ARG_FILLED = "arg_filled"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_SHOW_STAR_CONTROLS = "arg_show_star_controls"

        fun newInstance(dots: Int, skips: Int, thickness: Float, filled: Boolean, title: String? = null, showStarControls: Boolean = true): SettingsBottomSheet {
            return SettingsBottomSheet().apply {
                arguments = Bundle().apply {
                    putInt(ARG_DOTS, dots)
                    putInt(ARG_SKIPS, skips)
                    putFloat(ARG_THICKNESS, thickness)
                    putBoolean(ARG_FILLED, filled)
                    putString(ARG_TITLE, title)
                    putBoolean(ARG_SHOW_STAR_CONTROLS, showStarControls)
                }
            }
        }
    }
}
