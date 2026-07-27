package com.edvinlinge.hemma.mathstars2

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class DrawActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_draw)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val dots = intent.getIntExtra("dots", 0)
        val skips = intent.getIntExtra("skips", 0)

        val drawView = findViewById<DrawView>(R.id.view)
        drawView.setDotsAndSkips(dots, skips)

        val button = findViewById<Button>(R.id.infoButton)

        button.setOnClickListener {
            drawView.showDetails(this)
        }
    }
}