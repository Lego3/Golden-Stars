package com.edvinlinge.hemma.mathstars2

import android.os.Bundle
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

class DrawActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_draw)

        val dots = intent.getIntExtra("dots", 0)
        val skips = intent.getIntExtra("skips", 0)

        val drawView = findViewById<DrawView>(R.id.view)
        drawView.setDotsAndSkips(dots, skips)

        val button = findViewById<Button>(R.id.infoButton)
        val initialTopMargin = (button.layoutParams as MarginLayoutParams).topMargin
        val initialEndMargin = (button.layoutParams as MarginLayoutParams).marginEnd

        ViewCompat.setOnApplyWindowInsetsListener(button) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updateLayoutParams<MarginLayoutParams> {
                topMargin = bars.top + initialTopMargin
                marginEnd = bars.right + initialEndMargin
            }
            WindowInsetsCompat.CONSUMED
        }

        button.setOnClickListener {
            drawView.showDetails(this)
        }
    }
}
