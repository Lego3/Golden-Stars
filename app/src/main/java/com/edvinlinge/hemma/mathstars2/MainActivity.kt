package com.edvinlinge.hemma.mathstars2

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
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
            val extra = resources.getDimensionPixelSize(R.dimen.hub_content_padding)
            val bottomClearance = resources.getDimensionPixelSize(R.dimen.hub_version_clearance)
            view.setPadding(
                bars.left + extra,
                bars.top + extra,
                bars.right + extra,
                bars.bottom + extra + bottomClearance,
            )
            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.versionText) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                bottomMargin = bars.bottom + resources.getDimensionPixelSize(R.dimen.overlay_edge_margin)
            }
            insets
        }

        binding.cardStars.setOnClickListener {
            startActivity(Intent(this, DrawActivity::class.java))
        }

        binding.cardMandelbrot.setOnClickListener {
            startActivity(Intent(this, MandelbrotActivity::class.java))
        }

        binding.cardSpirograph.setOnClickListener {
            startActivity(Intent(this, SpirographActivity::class.java))
        }

        binding.cardJulia.setOnClickListener {
            startActivity(Intent(this, JuliaActivity::class.java))
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
