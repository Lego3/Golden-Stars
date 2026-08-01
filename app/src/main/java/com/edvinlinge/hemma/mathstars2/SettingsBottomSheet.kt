package com.edvinlinge.hemma.mathstars2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.graphics.toColorInt
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider

class SettingsBottomSheet : BottomSheetDialogFragment() {

    var onThicknessChanged: ((Float) -> Unit)? = null
    var onColorChanged: ((Int) -> Unit)? = null

    private var currentThickness = 8f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.layout_settings_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentThickness = arguments?.getFloat(ARG_THICKNESS) ?: 8f

        val thicknessSlider = view.findViewById<Slider>(R.id.thicknessSlider)
        thicknessSlider.value = currentThickness
        thicknessSlider.addOnChangeListener { _, value, _ ->
            onThicknessChanged?.invoke(value)
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
        private const val ARG_THICKNESS = "arg_thickness"

        fun newInstance(thickness: Float): SettingsBottomSheet {
            return SettingsBottomSheet().apply {
                arguments = Bundle().apply {
                    putFloat(ARG_THICKNESS, thickness)
                }
            }
        }
    }
}
