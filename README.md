Nuvei Cashier Helper SDK for Android
==========================================

SETUP
------------
Manual integration: 
Download the latest library (nuvei-cashier-helper.aar). 
Download all the relevant third party libraries (nuvei-paycards.aar and nuvei-zxing-android-embedded.aar).
All the above may be downloaded from [the latest release](https://github.com/SafeChargeInternational/NuveiCashierHelper-Android/releases/tag/2.1.0)
Put all the above libraries files under libs folder in your project.
Add the next line in your app build.gradle file:
```gradle
implementation(name:'nuvei-cashier-helper', ext:'aar')
implementation(name:'nuvei-paycards', ext:'aar')
implementation(name:'nuvei-zxing-android-embedded', ext:'aar')
api 'com.google.zxing:core:3.4.0'
```


Maven integration temporarilly unavailable


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
    
    NuveiCashierHelper.connect(webview, this) // “this” is the current activity

    // Register as WebView's webViewClient to tracj the URL changes and inform the CashierScanner SDK
    webview.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            return NuveiCashierHelper.handleURL(
                request?.url,
                this@WebViewActivity
            )
        }
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            return NuveiCashierHelper.handleURL(
                Uri.parse(url),
                this@WebViewActivity
            )
        }
        // Implement other methods if needed...
    }
}
```

For a proper tearing down of the activity add the next line in your `onDestroy` method:
```kotlin
override fun onDestroy() {
    NuveiCashierHelper.disconnect()
    super.onDestroy()
}
```

Implement the `onActivityResult` method in the activity that contains the web view as follows:
```kotlin
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    // Inform NuveiCashierHelper of an activity result (for handling QR/card scanner result or GooglePay result)
    if (!NuveiCashierHelper.handleActivityResult(requestCode, resultCode, data)) {
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

THIRD PARTY LIBS
------------
* [Pay.Cards](https://github.com/SafeChargeInternational/PayCards_Android)
* [ZXing](https://github.com/SafeChargeInternational/zxing-android-embedded)

LICENSE
------------
See: [LICENSE](https://github.com/SafeChargeInternational/NuveiCashierScanner/blob/master/LICENSE.md)
