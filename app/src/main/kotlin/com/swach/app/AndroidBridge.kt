package com.swach.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * JavaScript bridge exposed as window.AndroidBridge
 *
 * Called from holy.html via: AndroidBridge.someMethod(args)
 */
class AndroidBridge(private val activity: Activity) {

    /** Lock/unlock screen orientation from JS */
    @JavascriptInterface
    fun setOrientation(orientation: String) {
        activity.runOnUiThread {
            activity.requestedOrientation = when (orientation) {
                "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                "portrait"  -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                else        -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    /** Get safe area insets as JSON */
    @JavascriptInterface
    fun getSafeAreaInsets(): String {
        val dm = activity.resources.displayMetrics
        val insets = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            activity.window.decorView.rootWindowInsets?.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars()
            )
        } else null

        val top    = ((insets?.top    ?: 0) / dm.density).toInt()
        val bottom = ((insets?.bottom ?: 0) / dm.density).toInt()
        val left   = ((insets?.left   ?: 0) / dm.density).toInt()
        val right  = ((insets?.right  ?: 0) / dm.density).toInt()

        return JSONObject().apply {
            put("top", top); put("bottom", bottom)
            put("left", left); put("right", right)
        }.toString()
    }

    /** Vibrate (short haptic feedback) */
    @JavascriptInterface
    fun vibrate(ms: Int) {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vm = activity.getSystemService(android.os.VibratorManager::class.java)
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            activity.getSystemService(android.os.Vibrator::class.java)
        }
        vibrator?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                it.vibrate(android.os.VibrationEffect.createOneShot(
                    ms.toLong().coerceIn(1, 500),
                    android.os.VibrationEffect.DEFAULT_AMPLITUDE
                ))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(ms.toLong().coerceIn(1, 500))
            }
        }
    }

    /** App version */
    @JavascriptInterface
    fun getAppVersion(): String {
        return try {
            val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
            info.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    /** Check if running as native app (always true here) */
    @JavascriptInterface
    fun isNative(): Boolean = true
}
