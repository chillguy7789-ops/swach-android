# Swach Android

Minimal native WebView wrapper for Swach (holy.html).

## Structure

```
swach-android/
├── app/src/main/
│   ├── kotlin/com/swach/app/
│   │   ├── MainActivity.kt      # WebView host activity
│   │   └── AndroidBridge.kt     # JS ↔ native bridge
│   ├── assets/
│   │   └── holy.html            # The app (copy here before building)
│   ├── res/
│   │   ├── xml/network_security_config.xml
│   │   └── values/themes.xml
│   └── AndroidManifest.xml
├── holy.html                    # Source (root — GitHub Actions copies to assets)
└── .github/workflows/build.yml
```

## Building

### From Termux
```bash
cd ~/swach-android
chmod +x gradlew
cp ~/path/to/holy.html .
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Via GitHub Actions
Push to `main` — Actions copies holy.html to assets and builds APK automatically.

## JS Bridge

Available in holy.html as `window.AndroidBridge`:

```js
// Check if running in native app
if (window.AndroidBridge?.isNative()) { ... }

// Lock orientation
window.AndroidBridge?.setOrientation('landscape') // or 'portrait', 'unspecified'

// Haptic feedback
window.AndroidBridge?.vibrate(50) // ms

// App version
const v = window.AndroidBridge?.getAppVersion()
```

## Key differences from Capacitor

- No plugins, no npm, no node_modules — pure Kotlin
- `allowUniversalAccessFromFileURLs = true` so `file://` can fetch `localhost:3000`
- Cleartext allowed for localhost/127.0.0.1 via network_security_config
- Back button: calls `navBack()` in JS, falls back to minimize (keeps scraper running)
- Fullscreen video: native `onShowCustomView` → locks landscape
