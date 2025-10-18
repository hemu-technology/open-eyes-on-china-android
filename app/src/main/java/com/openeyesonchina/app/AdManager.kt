package com.openeyesonchina.app

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Simple wrapper for loading & showing a single InterstitialAd at launch.
 * Replace [TEST_INTERSTITIAL_ID] with your production unit ID when ready.
 */
object AdManager {
    private const val TAG = "AdManager"
    // Official Google sample interstitial test ad unit IDs (standard + video)
    // Source: https://developers.google.com/admob/android/test-ads#sample_ad_units
    private val TEST_INTERSTITIAL_IDS = listOf(
        "ca-app-pub-3940256099942544/1033173712", // Standard interstitial
        "ca-app-pub-3940256099942544/8691691433"  // Interstitial video
    )
    private var currentIndex = 0

    private var interstitial: InterstitialAd? = null
    private var loading = false

    fun initializeMobileAds(context: Context, onInit: () -> Unit = {}) {
        Log.d(TAG, "Initializing MobileAds...")
        MobileAds.initialize(context) {
            Log.d(TAG, "MobileAds initialized")
            // Configure test device IDs (emulator) for consistency (not required for test ad unit but useful if mixing prod/dev)
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                    .build()
            )
            onInit()
        }
    }

    fun loadLaunchInterstitial(context: Context, onLoadedOrFailed: () -> Unit) {
        if (interstitial != null || loading) {
            onLoadedOrFailed()
            return
        }
        loading = true
        val unitId = TEST_INTERSTITIAL_IDS[currentIndex]
        Log.d(TAG, "Starting interstitial load (unit=$unitId, attemptIndex=$currentIndex)...")
        val request = AdRequest.Builder().build()
        InterstitialAd.load(context, unitId, request, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitial = ad
                loading = false
                Log.d(TAG, "Interstitial loaded (unit=$unitId)")
                onLoadedOrFailed()
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.w(TAG, "Interstitial failed (code=${error.code}) unit=$unitId msg='${error.message}'")
                // If format mismatch, try next test ID once.
                val messageLower = error.message.lowercase()
                val formatMismatch = messageLower.contains("doesn't match format") || messageLower.contains("ad unit doesn't match format")
                if (formatMismatch && currentIndex + 1 < TEST_INTERSTITIALIDS_SIZE) {
                    currentIndex += 1
                    Log.d(TAG, "Retrying with alternate test interstitial unit index=$currentIndex")
                    // Reset state and re-attempt immediately.
                    interstitial = null
                    loading = false
                    loadLaunchInterstitial(context, onLoadedOrFailed)
                } else {
                    loading = false
                    interstitial = null
                    onLoadedOrFailed()
                }
            }
        })
    }

    fun showIfReady(activity: Activity, onDismiss: () -> Unit) {
        val ad = interstitial
        if (ad == null) {
            Log.d(TAG, "Interstitial not ready at show time; proceeding without ad")
            onDismiss()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                Log.d(TAG, "Interstitial dismissed")
                onDismiss()
            }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Interstitial failed to show: ${adError.message}")
                interstitial = null
                onDismiss()
            }
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial shown")
            }
        }
        ad.show(activity)
    }
}

// Size constant to avoid recomputing list.size repeatedly in the hot path.
private const val TEST_INTERSTITIALIDS_SIZE = 2