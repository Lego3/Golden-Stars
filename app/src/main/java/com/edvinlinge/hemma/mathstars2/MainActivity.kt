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
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import com.edvinlinge.hemma.mathstars2.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            applyHubInsets(insets)
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
        binding.versionText.doOnLayout { updateHubScrollBottomPadding() }
    }

    private fun applyHubInsets(insets: WindowInsetsCompat) {
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val contentPadding = resources.getDimensionPixelSize(R.dimen.hub_content_padding)
        val edgeMargin = resources.getDimensionPixelSize(R.dimen.overlay_edge_margin)

        binding.drawLayout.setPadding(
            bars.left + contentPadding,
            bars.top + contentPadding,
            bars.right + contentPadding,
            bars.bottom + contentPadding,
        )

        binding.versionText.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            bottomMargin = bars.bottom + edgeMargin
        }

        updateHubScrollBottomPadding()
    }

    private fun updateHubScrollBottomPadding() {
        val version = binding.versionText
        if (version.height == 0) return

        val versionMargin = (version.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin
        val extraGap = resources.getDimensionPixelSize(R.dimen.hub_version_clearance)
        binding.hubScrollView.setPadding(
            0,
            0,
            0,
            hubScrollBottomPadding(version.height, versionMargin, extraGap),
        )
    }
}
