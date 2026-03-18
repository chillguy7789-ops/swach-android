# Keep WebView JavaScript interface methods
-keepclassmembers class com.swach.app.AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# Keep activity
-keep class com.swach.app.** { *; }
