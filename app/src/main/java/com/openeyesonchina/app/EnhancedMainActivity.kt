package com.openeyesonchina.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.openeyesonchina.app.databinding.ActivityMainBinding

class EnhancedMainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private var userInitiatedRefresh = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Restore root padding for system bars
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Apply explicit system bar colors (dark background) & light icons
        window.statusBarColor = getColor(R.color.status_bar_color)
        window.navigationBarColor = getColor(R.color.navigation_bar_color)
        // LightStatusBars=false -> light icons on dark background
        androidx.core.view.WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = false

        val webView = binding.webView
        val swipe = binding.swipeRefresh
        val progressBar = binding.progressBar

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                forceDark = WebSettings.FORCE_DARK_AUTO
            }
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().setAcceptCookie(true)

        val density = resources.displayMetrics.density
        swipe.setDistanceToTriggerSync((160 * density).toInt())
        webView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            swipe.isEnabled = scrollY == 0
        }
        swipe.setOnRefreshListener {
            userInitiatedRefresh = true
            webView.reload()
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress in 1..99) {
                    if (progressBar.visibility != View.VISIBLE) progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                swipe.isRefreshing = userInitiatedRefresh
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                swipe.isRefreshing = false
                userInitiatedRefresh = false
                progressBar.visibility = View.GONE
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        if (savedInstanceState == null) {
            webView.loadUrl(getString(R.string.default_url))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && binding.webView.canGoBack()) {
            binding.webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
