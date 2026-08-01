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
import com.google.android.material.slider.Slider

class DrawActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_draw)

        val dots = intent.getIntExtra("dots", 0)
        val skips = intent.getIntExtra("skips", 0)

        val drawView = findViewById<DrawView>(R.id.view)
        drawView.setDotsAndSkips(dots, skips)
        drawView.setDrawColor("#FFD700".toColorInt()) // Golden

        val controlPanel = findViewById<View>(R.id.controlPanel)
        ViewCompat.setOnApplyWindowInsetsListener(controlPanel) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<MarginLayoutParams> {
                bottomMargin = bars.bottom + 16 // 16dp margin
            }
            insets
        }

        findViewById<View>(R.id.replayButton).setOnClickListener {
            drawView.replay()
        }

        findViewById<Slider>(R.id.speedSlider).addOnChangeListener { _, value, _ ->
            drawView.setAnimationSpeed(value)
        }

        findViewById<View>(R.id.settingsButton).setOnClickListener {
            val settings = SettingsBottomSheet.newInstance(drawView.getStrokeWidth())
            settings.onThicknessChanged = { drawView.setStrokeWidth(it) }
            settings.onColorChanged = { drawView.setDrawColor(it) }
            settings.show(supportFragmentManager, SettingsBottomSheet.TAG)
        }

        findViewById<View>(R.id.infoButton).setOnClickListener {
            val message = drawView.getDetailsHtml(this)
            InfoBottomSheet.newInstance(message).show(supportFragmentManager, InfoBottomSheet.TAG)
        }
    }
}
