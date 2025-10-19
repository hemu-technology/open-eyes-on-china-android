package com.openeyesonchina.app.net

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds cache headers to successful GET responses so WebView (through OkHttp URL loading, if integrated) or
 * future image fetches can reuse offline. We set a max-age for fresh content and allow stale usage when offline.
 */
class CacheInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val originalResponse = chain.proceed(request)
        if (request.method == "GET" && originalResponse.isSuccessful) {
            // Cache for 1 hour, allow 1 day stale
            val cacheControl = "public, max-age=3600, max-stale=86400"
            return originalResponse.newBuilder()
                .header("Cache-Control", cacheControl)
                .build()
        }
        return originalResponse
    }
}