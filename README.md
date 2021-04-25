Nuvei Cashier Scanner SDK for Android
==========================================

SETUP
------------
Add the next lines in your main project build.gradle file:
```gradle
buildscript {
    repositories {
        google()
        jcenter()
        maven { url 'https://jitpack.io' }
    }
}
allprojects {
    repositories {
        google()
        jcenter()
        maven { url 'https://jitpack.io' }
    }
}
```

Add the next line in your app build.gradle file:
```gradle
implementation "com.github.SafeChargeInternational.NuveiCashierScanner:CashierScanner:1.0.6"
```

HARDWARE ACCELERATION
------------
Hardware acceleration is required since TextureView is used.

Make sure it is enabled in your manifest file:

```xml
    <application android:hardwareAccelerated="true" ... >
```

USAGE
------------
The SDK works with WebView, so add the next line before you load Nuvei cashier page in the web view:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_web_view)
    
    NuveiCashierScanner.connect(webview, this) // “this” is the current activity

    // Register as WebView's webViewClient to tracj the URL changes and inform the CashierScanner SDK
    webview.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            return NuveiCashierScanner.handleURL(
                request?.url,
                this@WebViewActivity
            )
        }
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            return NuveiCashierScanner.handleURL(
                Uri.parse(url),
                this@WebViewActivity
            )
        }
        // Implement other methods if needed...
    }
}
```

Implement the `onActivityResult` method in the activity that contains the web view as follows:
```kotlin
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    // Inform CashierScanner SDK of an activity result (for handling QR scanner result)
    if (!NuveiCashierScanner.handleActivityResult(requestCode, resultCode, data)) {
        super.onActivityResult(requestCode, resultCode, data)
    }
}
```

HINTS & TIPS
------------
* This library uses ZXing library for scanning the QR code. See [ZXing README](https://github.com/journeyapps/zxing-android-embedded) for a complete reference and license.
* Note: the correct proguard file is automatically imported into your gradle project from the aar package. Anyone not using gradle will need to extract the proguard file and add it to their proguard config.
* Processing images can be memory intensive.
* [Memory Analysis for Android Applications](https://android-developers.blogspot.com/2011/03/memory-analysis-for-android.html) provides some useful information about how to track and reduce your app's memory useage.

LICENSE
------------
See: [LICENSE](https://github.com/SafeChargeInternational/NuveiCashierScanner/blob/master/LICENSE.md)
