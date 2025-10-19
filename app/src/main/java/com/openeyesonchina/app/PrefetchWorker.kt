package com.openeyesonchina.app

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import android.util.Log

class PrefetchWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    private data class PrefetchPayload(val urls: List<String>, val version: String?, val checksum: String?)
    private val prefs by lazy { applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override fun doWork(): Result {
        // Fallback static strategic pages (homepage + key taxonomy roots + policy pages)
        val fallback = listOf(
            "https://openeyesonchina.com/",
            "https://openeyesonchina.com/about/",
            "https://openeyesonchina.com/archives/",
            "https://openeyesonchina.com/contact/",
            "https://openeyesonchina.com/privacy-policy/",
            // Categories (ensure trailing slash for canonical URLs)
            "https://openeyesonchina.com/categories/geopolitics/",
            "https://openeyesonchina.com/categories/international-relations/",
            "https://openeyesonchina.com/categories/history/",
            "https://openeyesonchina.com/categories/culture-and-society/"
        )

        val payload = fetchDynamicPayload("https://openeyesonchina.com/offline/prefetch.json")
    val lastVersion = prefs.getString(KEY_VERSION, null)
    val lastChecksum = prefs.getString(KEY_CHECKSUM, null)
    val lastTs = prefs.getLong(KEY_LAST_TS, 0L)

        // If version & checksum unchanged, skip dynamic URLs to save resources
        val unchanged = payload.version != null && payload.version == lastVersion &&
                payload.checksum != null && payload.checksum == lastChecksum
        val dynamicUrls = if (unchanged) {
            Log.d(TAG, "Prefetch skip: version/checksum unchanged (version=$lastVersion)")
            emptyList<String>()
        } else {
            if (payload.urls.isNotEmpty()) {
                Log.d(TAG, "Prefetch refresh: ${payload.urls.size} dynamic URLs (version=${payload.version})")
            } else {
                Log.d(TAG, "Prefetch refresh: dynamic list empty, using fallback only")
            }
            prefs.edit().apply {
                putString(KEY_VERSION, payload.version)
                putString(KEY_CHECKSUM, payload.checksum)
                putLong(KEY_LAST_TS, System.currentTimeMillis())
                apply()
            }
            payload.urls
        }

        // Merge and cap to top 20 recent/popular dynamic URLs + all fallback essentials (fallback kept fully)
        val merged = (dynamicUrls.take(20) + fallback).distinct()
        if (merged.isNotEmpty()) {
            Log.d(TAG, "Prefetch scheduling ${merged.size} URLs (dynamic=${dynamicUrls.size}, fallback=${fallback.size})")
            OfflineWebViewClient.prefetch(applicationContext, merged)
        } else {
            Log.d(TAG, "Prefetch no-op: merged list empty")
        }
        Log.d(TAG, "Last dynamic version=$lastVersion checksum=$lastChecksum ts=$lastTs")
        return Result.success()
    }

    private fun fetchDynamicPayload(url: String): PrefetchPayload {
        return try {
            val body = fetchWithRetry(url, maxAttempts = 3)
            if (body != null) {
                // Two supported formats:
                // 1. Object: { "version": "2025-10-19", "checksum": "sha256-...", "urls": ["https://.../"] }
                // 2. Legacy array: ["https://.../", ...]
                if (body.trim().startsWith("{")) {
                    val obj = JSONObject(body)
                    val arr = obj.optJSONArray("urls") ?: JSONArray()
                    val list = (0 until arr.length()).mapNotNull { i -> arr.optString(i) }.filter { it.startsWith("http") }
                    val version = obj.optString("version", null)
                    val checksum = obj.optString("checksum", null).ifBlank { null } ?: computeChecksum(list)
                    PrefetchPayload(list, version, checksum)
                } else {
                    val arr = JSONArray(body)
                    val list = (0 until arr.length()).mapNotNull { i -> arr.optString(i) }.filter { it.startsWith("http") }
                    PrefetchPayload(list, null, computeChecksum(list))
                }
            } else {
                PrefetchPayload(emptyList(), null, null)
            }
        } catch (_: Exception) {
            PrefetchPayload(emptyList(), null, null)
        }
    }

    private fun fetchWithRetry(url: String, maxAttempts: Int): String? {
        var attempt = 0
        while (attempt < maxAttempts) {
            attempt++
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                val code = conn.responseCode
                if (code in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    return body
                } else {
                    Log.d(TAG, "Prefetch attempt $attempt failed HTTP $code")
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Prefetch attempt $attempt exception: ${e.message}")
            }
            // Exponential backoff (base 750ms)
            val backoff = 750L * (1 shl (attempt - 1))
            try { Thread.sleep(backoff) } catch (_: InterruptedException) {}
        }
        Log.d(TAG, "Prefetch giving up after $maxAttempts attempts")
        return null
    }

    private fun computeChecksum(urls: List<String>): String {
        val md = MessageDigest.getInstance("SHA-256")
        val joined = urls.sorted().joinToString("\n")
        val bytes = md.digest(joined.toByteArray())
        return bytes.joinToString("") { String.format("%02x", it) }
    }

    companion object {
        private const val PREFS_NAME = "prefetch_meta"
        private const val KEY_VERSION = "version"
        private const val KEY_CHECKSUM = "checksum"
        private const val KEY_LAST_TS = "last_ts"
        private const val TAG = "PrefetchWorker"
    }
}