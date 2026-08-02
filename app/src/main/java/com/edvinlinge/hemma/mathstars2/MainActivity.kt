package com.edvinlinge.hemma.mathstars2

import android.content.Intent
import android.os.Bundle
import android.content.pm.PackageManager
import android.os.Build
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawLayout)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        findViewById<MaterialCardView>(R.id.cardStars).setOnClickListener {
            val intent = Intent(this, DrawActivity::class.java).apply {
                putExtra("dots", 5)
                putExtra("skips", 2)
            }
            startActivity(intent)
        }

        findViewById<MaterialCardView>(R.id.cardMandelbrot).setOnClickListener {
            val intent = Intent(this, MandelbrotActivity::class.java)
            startActivity(intent)
        }

        val versionText = findViewById<TextView>(R.id.versionText)
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            versionText.text = getString(R.string.version_format, packageInfo.versionName)
        } catch (_: PackageManager.NameNotFoundException) {
            versionText.text = ""
        }
    }
}
