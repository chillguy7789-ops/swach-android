package com.swach.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
    private val handler = Handler(Looper.getMainLooper())

    // Separate callback for native XML file picker
    private var xmlPickerCallback: ((String?) -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
        }
        setContentView(root)

        webView = createWebView()
        root.addView(webView)

        configureWebView()
        setupWebClients()
        setupBackHandler()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl("file:///android_asset/holy.html")
        }
    }

    private fun createWebView(): WebView {
        return WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#121212"))
            overScrollMode = View.OVER_SCROLL_NEVER
            isScrollbarFadingEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            isHapticFeedbackEnabled = true
            isSoundEffectsEnabled = false
            isLongClickable = false
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
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
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            textZoom = 100
            minimumFontSize = 1
        }
        webView.addJavascriptInterface(AndroidBridge(this), "AndroidBridge")
    }

    /**
     * Open a native file picker for XML files and return content to JS.
     * This is called from AndroidBridge.pickXmlFile().
     */
    fun openXmlFilePicker(callback: (String?) -> Unit) {
        xmlPickerCallback = callback
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"  // Accept everything — let user pick any file
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(Intent.createChooser(intent, "Select MAL XML"), XML_PICKER_REQUEST)
        } catch (e: Exception) {
            callback(null)
            xmlPickerCallback = null
        }
    }

    private fun setupWebClients() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("file://") || url.startsWith("content://") ||
                    url.contains("localhost") || url.contains("127.0.0.1")) return false
                return try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))); true }
                catch (e: Exception) { false }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                injectNativeCSS()
                injectThemeMode()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) super.onReceivedError(view, request, error)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                fullscreenView = view
                fullscreenCallback = callback
                val root = findViewById<FrameLayout>(android.R.id.content)
                root.addView(view, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                webView.visibility = View.GONE
                enterImmersive()
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }

            override fun onHideCustomView() {
                fullscreenView?.let { (it.parent as? ViewGroup)?.removeView(it) }
                fullscreenView = null
                fullscreenCallback?.onCustomViewHidden()
                fullscreenCallback = null
                webView.visibility = View.VISIBLE
                exitImmersive()
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }

            override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>,
                                           fileChooserParams: FileChooserParams): Boolean {
                return try {
                    filePickerCallback = filePathCallback
                    startActivityForResult(fileChooserParams.createIntent(), FILE_CHOOSER_REQUEST)
                    true
                } catch (e: Exception) { filePathCallback.onReceiveValue(null); false }
            }

            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                android.util.Log.d("SwachJS", "[${msg.messageLevel()}] ${msg.message()}")
                return true
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                callback.invoke(origin, false, false)
            }
        }
    }

    private fun injectNativeCSS() {
        val css = """*{-webkit-tap-highlight-color:transparent!important;-webkit-touch-callout:none!important;outline:none!important;}html,body{overscroll-behavior:none!important;touch-action:pan-y;}input,textarea,[contenteditable]{-webkit-user-select:text!important;user-select:text!important;}.arow,.siblist,.lib-filter-bar,.lib-filter-scroll{-webkit-overflow-scrolling:touch;scroll-behavior:auto!important;}"""
        webView.evaluateJavascript("""(function(){var s=document.getElementById('_ncss');if(!s){s=document.createElement('style');s.id='_ncss';document.head.appendChild(s);}s.textContent='$css';})();""", null)
    }

    private fun injectThemeMode() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        webView.evaluateJavascript("if(typeof setNativeTheme==='function')setNativeTheme('${if (isDark) "dark" else "light"}');", null)
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullscreenView != null) {
                    webView.webChromeClient?.onHideCustomView()
                } else {
                    webView.evaluateJavascript(
                        "typeof navBack==='function'?navBack():'minimize'",
                        { result -> if (result?.trim('"') == "minimize") moveTaskToBack(true) }
                    )
                }
            }
        })
    }

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
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private var filePickerCallback: ValueCallback<Array<Uri>>? = null

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            FILE_CHOOSER_REQUEST -> {
                filePickerCallback?.onReceiveValue(
                    if (resultCode == Activity.RESULT_OK) WebChromeClient.FileChooserParams.parseResult(resultCode, data) else null)
                filePickerCallback = null
            }
            XML_PICKER_REQUEST -> {
                if (resultCode == Activity.RESULT_OK && data?.data != null) {
                    val uri = data.data!!
                    try {
                        // Read XML content and pass to JS
                        val inputStream = contentResolver.openInputStream(uri)
                        val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                        inputStream?.close()
                        // Escape for JS string
                        val escaped = content
                            .replace("\\", "\\\\")
                            .replace("'", "\\'")
                            .replace("\n", "\\n")
                            .replace("\r", "")
                        handler.post {
                            webView.evaluateJavascript(
                                "if(typeof _onNativeXmlPicked==='function')_onNativeXmlPicked('$escaped');",
                                null
                            )
                        }
                    } catch (e: Exception) {
                        handler.post {
                            webView.evaluateJavascript(
                                "if(typeof _onNativeXmlPicked==='function')_onNativeXmlPicked(null);",
                                null
                            )
                        }
                    }
                } else {
                    handler.post {
                        webView.evaluateJavascript(
                            "if(typeof _onNativeXmlPicked==='function')_onNativeXmlPicked(null);",
                            null
                        )
                    }
                }
                xmlPickerCallback = null
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        injectThemeMode()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        handler.postDelayed({ injectNativeCSS() }, 400)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.clearHistory()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val FILE_CHOOSER_REQUEST = 1001
        private const val XML_PICKER_REQUEST = 1002
    }
}
