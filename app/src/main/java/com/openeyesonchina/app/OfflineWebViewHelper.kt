package com.openeyesonchina.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Helper to configure a WebView for better offline behavior.
 * - Enables app cache (legacy) fallback via setAppCachePath (below API 33 it's still honored for some vendors)
 * - Adjusts cacheMode based on connectivity
 */
object OfflineWebViewHelper {

    fun configure(webView: WebView, context: Context) {
        val settings = webView.settings
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportMultipleWindows(false)
        // Legacy app cache path (some devices ignore, but harmless)
        val cacheDir = context.cacheDir.absolutePath
        try {
            val method = settings.javaClass.getMethod("setAppCachePath", String::class.java)
            method.invoke(settings, cacheDir)
            val enableMethod = settings.javaClass.getMethod("setAppCacheEnabled", Boolean::class.javaPrimitiveType)
            enableMethod.invoke(settings, true)
        } catch (_: Exception) { /* ignore if removed */ }

        if (!hasNetwork(context)) {
            settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        }
    }

    private fun hasNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}