package com.openeyesonchina.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.LruCache
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedHashMap
import java.util.Collections
import java.util.zip.GZIPInputStream
import kotlin.concurrent.thread

/**
 * Intercepts network requests when offline and serves local fallback (offline/index.html).
 * Future enhancement: add a local cache map for previously fetched assets.
 */
class OfflineWebViewClient(
    private val context: Context,
    private val offlineBannerProvider: () -> Unit,
    private val onlineBannerProvider: () -> Unit
) : WebViewClient() {

    companion object {
        private const val MAX_TOTAL_DISK_BYTES = 50L * 1024 * 1024 // 50MB cleanup threshold (A)
        private const val MAX_HTML_ENTRIES = 30
        private const val MAX_ASSET_ENTRIES = 60

        private lateinit var diskDir: File
        private lateinit var manifestFile: File

        private val htmlMap: LinkedHashMap<String, ByteArray> = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean = size > MAX_HTML_ENTRIES
        }
        private val assetMap: LinkedHashMap<String, ByteArray> = object : LinkedHashMap<String, ByteArray>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean = size > MAX_ASSET_ENTRIES
        }
        private val imageCache = object : LruCache<String, ByteArray>(4 * 1024 * 1024) { override fun sizeOf(key: String, value: ByteArray) = value.size }
        private data class Entry(val url: String, val type: String, val ts: Long, val size: Int)
        private val manifest: MutableList<Entry> = Collections.synchronizedList(mutableListOf())
        private var initialized = false

        private fun ensureInit(context: Context) {
            if (initialized) return
            diskDir = File(context.cacheDir, "offline_web").apply { if (!exists()) mkdirs() }
            manifestFile = File(diskDir, "manifest.json")
            loadManifest()
            manifest.filter { it.type == "html" }.sortedByDescending { it.ts }.take(5).forEach { e ->
                loadDisk(e.url)?.let { htmlMap[e.url] = it }
            }
            initialized = true
        }

        // Prefetch list of URLs (D)
        fun prefetch(context: Context, urls: List<String>) {
            ensureInit(context)
            thread(name = "offline-prefetch") {
                urls.forEach { url ->
                    cacheIfNeeded(context, url, inferType(url))
                }
            }
        }

        private fun inferType(url: String): String = when {
            isImage(url) -> "image"
            isCss(url) -> "css"
            isJs(url) -> "js"
            else -> "html"
        }

        private fun isLikelyHtml(url: String): Boolean = url.contains(".html") || !url.contains('.')
        private fun isImage(url: String): Boolean = url.endsWith(".png") || url.endsWith(".jpg") || url.endsWith(".jpeg") || url.endsWith(".webp")
        private fun isCss(url: String): Boolean = url.endsWith(".css")
        private fun isJs(url: String): Boolean = url.endsWith(".js")
        private fun getImageMime(url: String): String = when {
            url.endsWith(".png") -> "image/png"
            url.endsWith(".jpg") || url.endsWith(".jpeg") -> "image/jpeg"
            url.endsWith(".webp") -> "image/webp"
            else -> "image/*"
        }
        private fun getCssJsMime(url: String): String = if (isCss(url)) "text/css" else "application/javascript"

        private fun cacheIfNeeded(context: Context, url: String, type: String) {
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                if (connection.responseCode == 200) {
                    val enc = connection.getHeaderField("Content-Encoding")?.lowercase()
                    val rawStream = connection.inputStream
                    val stream = if (enc != null && enc.contains("gzip")) GZIPInputStream(rawStream) else rawStream // (C) gzip decode
                    val bytes = stream.use { it.readBytes() }
                    when (type) {
                        "html" -> {
                            htmlMap[url] = bytes
                            writeDisk(url, bytes, "html")
                            addManifest(url, "html", bytes.size)
                        }
                        "image" -> if (bytes.size <= 500 * 1024) {
                            imageCache.put(url, bytes)
                            assetMap[url] = bytes
                            writeDisk(url, bytes, "image")
                            addManifest(url, "image", bytes.size)
                        }
                        "css" -> if (bytes.size <= 200 * 1024) {
                            assetMap[url] = bytes
                            writeDisk(url, bytes, "css")
                            addManifest(url, "css", bytes.size)
                        }
                        "js" -> if (bytes.size <= 400 * 1024) {
                            assetMap[url] = bytes
                            writeDisk(url, bytes, "js")
                            addManifest(url, "js", bytes.size)
                        }
                    }
                    cleanupIfNeeded() // (A) disk cleanup after each addition
                }
                connection.disconnect()
            } catch (_: Exception) { }
        }

        private fun writeDisk(url: String, bytes: ByteArray, prefix: String) {
            val safeName = url.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val file = File(diskDir, "$prefix-$safeName")
            if (!file.exists()) {
                try { FileOutputStream(file).use { it.write(bytes) } } catch (_: Exception) { }
            }
        }
        private fun loadDisk(url: String): ByteArray? {
            val patterns = listOf("html", "image", "css", "js")
            val safe = url.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            patterns.forEach { prefix ->
                val file = File(diskDir, "$prefix-$safe")
                if (file.exists()) return try { file.readBytes() } catch (_: Exception) { null }
            }
            return null
        }
        private fun generateListingHtml(): ByteArray {
            val sb = StringBuilder()
            sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Offline - Open Eyes on China</title><style>body{font-family:system-ui,Arial;margin:16px;}h1{color:#c62828;}a{color:#c62828;text-decoration:none;}li{margin:4px 0;}small{color:#555;} .banner{background:#c62828;color:#fff;padding:8px 12px;border-radius:8px;display:inline-block;margin-bottom:12px;} </style></head><body>")
            sb.append("<div class='banner'>Offline Mode</div><h1>Recently Viewed</h1><ul>")
            manifest.filter { it.type == "html" }.sortedByDescending { it.ts }.take(25).forEach { e ->
                sb.append("<li><a href='${e.url}'>${e.url}</a> <small>${(e.size/1024)}KB</small></li>")
            }
            if (manifest.none { it.type == "html" }) sb.append("<li>No pages cached yet.</li>")
            sb.append("</ul><p>Reconnect to update live content.</p><footer><small>&copy; ${System.currentTimeMillis()} Open Eyes on China</small></footer></body></html>")
            return sb.toString().toByteArray(Charsets.UTF_8)
        }
        @Synchronized private fun addManifest(url: String, type: String, size: Int) {
            manifest.removeAll { it.url == url }
            manifest.add(Entry(url, type, System.currentTimeMillis(), size))
            saveManifest()
        }
        private fun saveManifest() {
            try {
                val arr = JSONArray()
                manifest.sortedByDescending { it.ts }.forEach { e ->
                    val obj = JSONObject()
                    obj.put("url", e.url)
                    obj.put("type", e.type)
                    obj.put("ts", e.ts)
                    obj.put("size", e.size)
                    arr.put(obj)
                }
                manifestFile.writeText(arr.toString())
            } catch (_: Exception) { }
        }
        private fun loadManifest() {
            if (!manifestFile.exists()) return
            try {
                val txt = manifestFile.readText()
                val arr = JSONArray(txt)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    manifest.add(Entry(o.getString("url"), o.getString("type"), o.getLong("ts"), o.getInt("size")))
                }
            } catch (_: Exception) { }
        }
        private fun cleanupIfNeeded() {
            val total = diskDir.listFiles()?.sumOf { it.length() } ?: 0L
            if (total <= MAX_TOTAL_DISK_BYTES) return
            // Remove oldest manifest entries' files until under threshold
            manifest.sortBy { it.ts }
            val iter = manifest.iterator()
            while (iter.hasNext() && (diskDir.listFiles()?.sumOf { it.length() } ?: 0L) > MAX_TOTAL_DISK_BYTES) {
                val e = iter.next()
                val safeName = e.url.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val file = File(diskDir, "${e.type}-$safeName")
                if (file.exists()) file.delete()
                iter.remove()
            }
            saveManifest()
        }

        data class CacheStats(
            val diskBytes: Long,
            val manifestEntries: Int,
            val htmlInMemory: Int,
            val assetInMemory: Int,
            val imageLruBytes: Int
        )

        fun getCacheStats(context: Context): CacheStats {
            ensureInit(context)
            val diskBytes = diskDir.listFiles()?.sumOf { it.length() } ?: 0L
            val manifestEntries = manifest.size
            val htmlInMemory = htmlMap.size
            val assetInMemory = assetMap.size
            // Approximate image LRU size by summing values
            var imageBytes = 0
            synchronized(imageCache) {
                // LruCache has no direct iterator; we rely on reflection fallback or tracked sizes. For simplicity, track via manifest entries of type image.
                imageBytes = manifest.filter { it.type == "image" }.sumOf { it.size }
            }
            return CacheStats(diskBytes, manifestEntries, htmlInMemory, assetInMemory, imageBytes)
        }
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        if (request == null) return null
    ensureInit(context)
    val url = request.url.toString()
    val isHtmlMainFrame = request.isForMainFrame && url.startsWith("http") && Companion.isLikelyHtml(url)

        // Offline fallback for main frame
        if (!hasNetwork(context)) {
            if (isHtmlMainFrame) {
                Companion.htmlMap[url]?.let { return makeResponse("text/html", it) }
                Companion.loadDisk(url)?.let { return makeResponse("text/html", it) }
                return makeResponse("text/html", Companion.generateListingHtml())
            }
            if (Companion.isImage(url)) {
                Companion.imageCache.get(url)?.let { return makeResponse(Companion.getImageMime(url), it) }
                Companion.assetMap[url]?.let { return makeResponse(Companion.getImageMime(url), it) }
                Companion.loadDisk(url)?.let { return makeResponse(Companion.getImageMime(url), it) }
            } else if (Companion.isCss(url) || Companion.isJs(url)) {
                Companion.assetMap[url]?.let { return makeResponse(Companion.getCssJsMime(url), it) }
                Companion.loadDisk(url)?.let { return makeResponse(Companion.getCssJsMime(url), it) }
            }
            return null
        }

        // Online: opportunistic caching of HTML & images
        if (isHtmlMainFrame) {
            Companion.cacheIfNeeded(context, url, type = "html")
        } else if (Companion.isImage(url)) {
            Companion.cacheIfNeeded(context, url, type = "image")
        } else if (Companion.isCss(url)) {
            Companion.cacheIfNeeded(context, url, type = "css")
        } else if (Companion.isJs(url)) {
            Companion.cacheIfNeeded(context, url, type = "js")
        }
        return null
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        if (!hasNetwork(context)) offlineBannerProvider() else onlineBannerProvider()
        super.onPageFinished(view, url)
    }

    private fun hasNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // Delegate type helpers to companion

    private fun cacheIfNeeded(url: String, type: String) {
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
            }
            if (connection.responseCode == 200) {
                val bytes = connection.inputStream.use { it.readBytes() }
                when (type) {
                    "html" -> {
                        htmlMap[url] = bytes
                        writeDisk(url, bytes, "html")
                        addManifest(url, "html", bytes.size)
                    }
                    "image" -> if (bytes.size <= 500 * 1024) {
                        imageCache.put(url, bytes)
                        assetMap[url] = bytes
                        writeDisk(url, bytes, "image")
                        addManifest(url, "image", bytes.size)
                    }
                    "css" -> if (bytes.size <= 200 * 1024) {
                        assetMap[url] = bytes
                        writeDisk(url, bytes, "css")
                        addManifest(url, "css", bytes.size)
                    }
                    "js" -> if (bytes.size <= 400 * 1024) {
                        assetMap[url] = bytes
                        writeDisk(url, bytes, "js")
                        addManifest(url, "js", bytes.size)
                    }
                }
            }
            connection.disconnect()
        } catch (_: Exception) { /* ignore network/cache errors */ }
    }

    private fun writeDisk(url: String, bytes: ByteArray, prefix: String) {
        val safeName = url.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(diskDir, "$prefix-$safeName")
        if (!file.exists()) {
            try { FileOutputStream(file).use { it.write(bytes) } } catch (_: Exception) { }
        }
    }

    private fun makeResponse(mime: String, bytes: ByteArray): WebResourceResponse {
        return WebResourceResponse(mime, "UTF-8", ByteArrayInputStream(bytes))
    }

    // Manifest handling
    @Synchronized private fun addManifest(url: String, type: String, size: Int) {
        manifest.removeAll { it.url == url }
        manifest.add(Entry(url, type, System.currentTimeMillis(), size))
        saveManifest()
    }

    private fun saveManifest() {
        try {
            val arr = JSONArray()
            manifest.sortedByDescending { it.ts }.forEach { e ->
                val obj = JSONObject()
                obj.put("url", e.url)
                obj.put("type", e.type)
                obj.put("ts", e.ts)
                obj.put("size", e.size)
                arr.put(obj)
            }
            manifestFile.writeText(arr.toString())
        } catch (_: Exception) { }
    }

    private fun loadManifest() {
        if (!manifestFile.exists()) return
        try {
            val txt = manifestFile.readText()
            val arr = JSONArray(txt)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                manifest.add(Entry(o.getString("url"), o.getString("type"), o.getLong("ts"), o.getInt("size")))
            }
        } catch (_: Exception) { }
    }

    private fun loadDisk(url: String): ByteArray? {
        val patterns = listOf("html", "image", "css", "js")
        val safe = url.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        patterns.forEach { prefix ->
            val file = File(diskDir, "$prefix-$safe")
            if (file.exists()) {
                return try { file.readBytes() } catch (_: Exception) { null }
            }
        }
        return null
    }

    private fun generateListingHtml(): ByteArray {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Offline - Open Eyes on China</title><style>body{font-family:system-ui,Arial;margin:16px;}h1{color:#c62828;}a{color:#c62828;text-decoration:none;}li{margin:4px 0;}small{color:#555;} .banner{background:#c62828;color:#fff;padding:8px 12px;border-radius:8px;display:inline-block;margin-bottom:12px;} </style></head><body>")
        sb.append("<div class='banner'>Offline Mode</div><h1>Recently Viewed</h1><ul>")
        manifest.filter { it.type == "html" }.sortedByDescending { it.ts }.take(25).forEach { e ->
            sb.append("<li><a href='${e.url}'>${e.url}</a> <small>${(e.size/1024)}KB</small></li>")
        }
        if (manifest.none { it.type == "html" }) sb.append("<li>No pages cached yet.</li>")
        sb.append("</ul><p>Reconnect to update live content.</p><footer><small>&copy; ${System.currentTimeMillis()} Open Eyes on China</small></footer></body></html>")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }
}