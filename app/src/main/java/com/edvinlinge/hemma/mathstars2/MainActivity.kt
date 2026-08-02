package com.edvinlinge.hemma.mathstars2

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.edvinlinge.hemma.mathstars2.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.drawLayout) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        binding.cardStars.setOnClickListener {
            val intent = Intent(this, DrawActivity::class.java).apply {
                putExtra(DrawActivity.EXTRA_DOTS, SettingsBottomSheet.DEFAULT_DOTS)
                putExtra(DrawActivity.EXTRA_SKIPS, SettingsBottomSheet.DEFAULT_SKIPS)
            }
            startActivity(intent)
        }

        binding.cardMandelbrot.setOnClickListener {
            startActivity(Intent(this, MandelbrotActivity::class.java))
        }

        binding.versionText.text = try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            getString(R.string.version_format, packageInfo.versionName)
        } catch (_: PackageManager.NameNotFoundException) {
            ""
        }
    }
}
