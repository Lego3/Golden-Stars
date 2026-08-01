package com.edvinlinge.hemma.mathstars2

import android.content.Intent
import android.os.Bundle
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

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

        val buttonRender = findViewById<Button>(R.id.buttonRender)

        val dots = findViewById<EditText>(R.id.editNumberDots)
        val skips = findViewById<EditText>(R.id.editNumberSkips)

        buttonRender.setOnClickListener {
            val dotNumber = dots.text.toString().toIntOrNull() ?: 5
            val skipNumber = skips.text.toString().toIntOrNull() ?: 2
            val intent = Intent(this, DrawActivity::class.java).apply {
                putExtra("dots", dotNumber)
                putExtra("skips", skipNumber)
            }
            startActivity(intent)
        }

        val buttonHelp = findViewById<Button>(R.id.buttonHelp)

        buttonHelp.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.help)
                .setMessage(getString(R.string.help_details))
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
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