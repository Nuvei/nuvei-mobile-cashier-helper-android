package com.safecharge.nuveicashiercardscannersdk_source_android

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.safecharge.nuveicashiercardscannersdk.SCCardScannerSDK
import kotlinx.android.synthetic.main.activity_web_view.*

class WebViewActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_view)

        SCCardScannerSDK.connect(webview, this)
        webview.clearCache(true)
        webview.clearHistory()
        webview.settings.javaScriptEnabled = true
        webview.loadUrl(intent.getStringExtra("url"))
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCCardScannerSDK.SCAN_CARD_REQUEST_CODE) {
            SCCardScannerSDK.didScan(data)
        }
    }
}
