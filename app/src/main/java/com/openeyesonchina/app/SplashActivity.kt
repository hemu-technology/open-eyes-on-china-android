package com.openeyesonchina.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.ProgressBar
import java.util.Calendar
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/**
 * Launcher activity: shows splash, initializes MobileAds & loads interstitial.
 * Transitions to [EnhancedMainActivity] when ready or after timeout.
 */
class SplashActivity : ComponentActivity() {
    // Minimum and maximum splash duration windows.
    private val minSplashMs = 1200L
    private val maxSplashMs = 4000L
    private var proceedCalled = false
    private var startUptime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        startUptime = android.os.SystemClock.uptimeMillis()

        // Keep system splash until we set our custom layout
    // Keep system splash only during very early init (we immediately set content view).
    splash.setKeepOnScreenCondition { false }

        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.splashLogo)
        val title = findViewById<TextView>(R.id.splashTitle)
        val tagline = findViewById<TextView>(R.id.splashTagline)
    val progress = findViewById<ProgressBar>(R.id.splashProgress)
    val copyright = findViewById<TextView>(R.id.splashCopyright)

    // Dynamic year
    val year = Calendar.getInstance().get(Calendar.YEAR)
    copyright.text = getString(R.string.splash_copyright_template, year)

        // Intro animations
        logo.alpha = 0f
        title.alpha = 0f
        tagline.alpha = 0f
        progress.alpha = 0f

        logo.animate().alpha(1f).setDuration(400).setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
            title.animate().alpha(1f).setDuration(300).start()
            tagline.animate().alpha(1f).setStartDelay(200).setDuration(400).start()
            progress.animate().alpha(1f).setStartDelay(400).setDuration(300).start()
        }.start()


        // Initialize and load ad. Proceed decision considers min and max wait.
        AdManager.initializeMobileAds(this) {
            AdManager.loadLaunchInterstitial(this) {
                // Ad finished loading OR failed. Respect min wait threshold.
                val elapsed = android.os.SystemClock.uptimeMillis() - startUptime
                if (elapsed >= minSplashMs) {
                    proceed()
                } else {
                    // Ensure we still meet minimum splash duration before proceeding.
                    val remaining = minSplashMs - elapsed
                    Handler(Looper.getMainLooper()).postDelayed({ proceed() }, remaining)
                }
            }
        }

        // Hard max timeout: if ad still not resolved (callback not fired) by maxSplashMs, proceed anyway.
        Handler(Looper.getMainLooper()).postDelayed({ proceed() }, maxSplashMs)
    }

    private fun proceed() {
        if (proceedCalled) return
        proceedCalled = true
        // Attempt to show interstitial if ready; navigate afterwards.
        AdManager.showIfReady(this) {
            startActivity(Intent(this, EnhancedMainActivity::class.java))
            finish()
        }
    }

}