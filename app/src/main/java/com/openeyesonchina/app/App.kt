package com.openeyesonchina.app

import android.app.Application
import androidx.work.*
import java.time.Duration

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        schedulePrefetch()
    }

    private fun schedulePrefetch() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // Wi-Fi (user-friendly for data)
            .setRequiresBatteryNotLow(true)
            .setRequiresCharging(true) // only when charging for minimal impact
            .build()

        val request = PeriodicWorkRequestBuilder<PrefetchWorker>(Duration.ofHours(12))
            .setConstraints(constraints)
            .addTag(PREFETCH_TAG)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PREFETCH_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val PREFETCH_TAG = "offline-prefetch"
    }
}