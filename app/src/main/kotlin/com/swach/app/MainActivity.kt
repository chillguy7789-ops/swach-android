package com.swach.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: let the WebView render behind status/nav bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        // Dark status bar icons → white (dark background app)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        // Root frame
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        setContentView(root)

        // WebView setup
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }
        root.addView(webView)

        configureWebView()
        setupWebClients()
        setupBackHandler()

        // Restore or load initial page
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl("file:///android_asset/holy.html")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW // needed for localhost API calls
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            // Allow video autoplay
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
        }
        // Hardware acceleration for smooth video
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        // Inject Android bridge
        webView.addJavascriptInterface(AndroidBridge(this), "AndroidBridge")
    }

    private fun setupWebClients() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                // Keep in-app: file://, content://, localhost, our asset paths
                if (url.startsWith("file://") ||
                    url.startsWith("content://") ||
                    url.contains("localhost") ||
                    url.contains("127.0.0.1")) {
                    return false
                }
                // Open external URLs in device browser
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    true
                } catch (e: Exception) {
                    false
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                // Silently ignore sub-resource errors (fonts, CDN assets, etc.)
                if (request.isForMainFrame) {
                    super.onReceivedError(view, request, error)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // Native fullscreen for video elements
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                fullscreenView = view
                fullscreenCallback = callback
                val root = findViewById<FrameLayout>(android.R.id.content)
                root.addView(view, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
                webView.visibility = View.GONE
                enterImmersive()
                // Lock to landscape in fullscreen
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }

            override fun onHideCustomView() {
                fullscreenView?.let {
                    (it.parent as? ViewGroup)?.removeView(it)
                }
                fullscreenView = null
                fullscreenCallback?.onCustomViewHidden()
                fullscreenCallback = null
                webView.visibility = View.VISIBLE
                exitImmersive()
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }

            // Allow media file chooser (for import)
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                val intent = fileChooserParams.createIntent()
                return try {
                    filePickerCallback = filePathCallback
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST)
                    true
                } catch (e: Exception) {
                    filePathCallback.onReceiveValue(null)
                    false
                }
            }

            // JS console → Android Logcat
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                android.util.Log.d("SwachJS",
                    "[${msg.messageLevel()}] ${msg.message()} (${msg.sourceId()}:${msg.lineNumber()})")
                return true
            }

            // Geolocation (not needed but prevents silent failures)
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                callback.invoke(origin, false, false)
            }
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    // Exit fullscreen first
                    fullscreenView != null -> {
                        webView.webChromeClient?.onHideCustomView()
                    }
                    // Let WebView handle back if it can (hash navigation)
                    webView.canGoBack() -> {
                        webView.evaluateJavascript(
                            "typeof navBack === 'function' ? navBack() : null", null)
                    }
                    // Otherwise minimize app (don't close — keep scraper running in background)
                    else -> moveTaskToBack(true)
                }
            }
        })
    }

    // ── Immersive mode ────────────────────────────────────────────────────────

    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun exitImmersive() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ── File chooser result ───────────────────────────────────────────────────

    private var filePickerCallback: ValueCallback<Array<Uri>>? = null

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST) {
            filePickerCallback?.onReceiveValue(
                if (resultCode == Activity.RESULT_OK)
                    WebChromeClient.FileChooserParams.parseResult(resultCode, data)
                else null
            )
            filePickerCallback = null
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val FILE_CHOOSER_REQUEST = 1001
    }
}
