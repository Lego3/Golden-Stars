package com.edvinlinge.hemma.mathstars2

import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

class MandelbrotActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_mandelbrot)

        val mandelbrotView = findViewById<MandelbrotView>(R.id.mandelbrotView)

        val controlPanel = findViewById<View>(R.id.controlPanel)
        ViewCompat.setOnApplyWindowInsetsListener(controlPanel) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<MarginLayoutParams> {
                bottomMargin = bars.bottom + 16
            }
            insets
        }

        findViewById<View>(R.id.settingsButton).setOnClickListener {
            val settings = SettingsBottomSheet.newInstance(
                0, 0, 0f, false,
                getString(R.string.customize_mandelbrot),
                false
            )
            settings.onColorChanged = { color ->
                // Map color back to palette
                val palette = when (color) {
                    "#FFD700".toColorInt() -> MandelbrotView.Palette.GOLDEN
                    "#C0C0C0".toColorInt() -> MandelbrotView.Palette.SILVER
                    "#00BFFF".toColorInt() -> MandelbrotView.Palette.BLUE
                    "#32CD32".toColorInt() -> MandelbrotView.Palette.GREEN
                    else -> MandelbrotView.Palette.GOLDEN
                }
                mandelbrotView.setColorPalette(palette)
            }
            settings.show(supportFragmentManager, SettingsBottomSheet.TAG)
        }

        findViewById<View>(R.id.infoButton).setOnClickListener {
            val helpText = getString(R.string.mandelbrot_help)
            InfoBottomSheet.newInstance(helpText).show(supportFragmentManager, InfoBottomSheet.TAG)
        }
    }
}
