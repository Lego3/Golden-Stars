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

    private var dots = 5
    private var skips = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_draw)

        dots = intent.getIntExtra("dots", 5)
        skips = intent.getIntExtra("skips", 2)

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
            if (value >= 4.0f) {
                drawView.setInstant(true)
            } else {
                drawView.setInstant(false)
                drawView.setAnimationSpeed(value)
            }
        }

        findViewById<View>(R.id.settingsButton).setOnClickListener {
            val settings = SettingsBottomSheet.newInstance(
                dots,
                skips,
                drawView.getStrokeWidth(),
                drawView.isFilled()
            )
            settings.onGeometryChanged = { d, s ->
                dots = d
                skips = s
                drawView.updatePointsAndPath(dots, skips)
            }
            settings.onThicknessChanged = { drawView.setStrokeWidth(it) }
            settings.onColorChanged = { drawView.setDrawColor(it) }
            settings.onFilledChanged = { drawView.setFilled(it) }
            settings.show(supportFragmentManager, SettingsBottomSheet.TAG)
        }

        findViewById<View>(R.id.infoButton).setOnClickListener {
            val message = drawView.getDetailsHtml(this)
            InfoBottomSheet.newInstance(message).show(supportFragmentManager, InfoBottomSheet.TAG)
        }

        findViewById<View>(R.id.helpButton).setOnClickListener {
            val helpText = getString(R.string.help_details)
            InfoBottomSheet.newInstance(helpText).show(supportFragmentManager, InfoBottomSheet.TAG)
        }
    }
}
