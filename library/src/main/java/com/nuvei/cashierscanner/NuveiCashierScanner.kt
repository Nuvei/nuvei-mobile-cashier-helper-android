package com.nuvei.cashierscanner

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.webkit.WebView
import com.google.zxing.integration.android.IntentIntegrator
import java.lang.ref.WeakReference

public object NuveiCashierScanner {

    private const val messageName = "sccardscanner"
    const val SCAN_CARD_REQUEST_CODE = 8493

    private val hostWhiteList = arrayListOf(
        "apmtest.gate2shop.com",// QA
        "ppp-test.safecharge.com",// Integration
        "secure.safecharge.com"// Production
    )

    private var webView: WebView? = null
    private var activity = WeakReference<Activity>(null)

    public fun connect(webView: WebView, activity: Activity) {
        NuveiCashierScanner.activity = WeakReference(activity)
        NuveiCashierScanner.webView = webView
    }

    public fun handleURL(url: Uri?, activity: Activity) =
        url?.takeIf { it.toString().contains("nuveicashier://scanQR", ignoreCase = true) }?.let {
            val integrator = IntentIntegrator(activity)
            integrator.setOrientationLocked(false)
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            integrator.initiateScan()

            true
        } ?: false

    public fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) =
        IntentIntegrator.parseActivityResult(requestCode, resultCode, data)?.contents?.let { result ->
            didScan(result)
            true
        } ?: false

    private fun didScan(qrString: String) {
        updateCashier("{\"qrCode\":\"${qrString}\"}")
    }

    private fun didFail(error: SCCardScannerError) {
        updateCashier("{" +
                "\"source\":\"scanCard\"" +
                ",\"status\":\"NOK\"" +
                ",\"errorCode\":\"${error.code()}\"" +
                ",\"errorMessage\":\"${error.description()}\"" +
                "}")
//        val js = """
//            window.postMessage({
//            source: 'scanCard',
//            status: 'NOK',
//            errorCode: ${error.code()},
//            errorMessage: ${error.description()}
//            },"*");
//            """
//        webView?.evaluateJavascript(js, null)
    }

    private fun updateCashier(data: String) {
        val base64 = Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP)
        val url = webView?.url

        url?.split("#")?.firstOrNull()?.let {
            webView?.loadUrl("$it#$base64")
        }
    }

    private enum class SCCardScannerError {
        CANCEL, MISSING_PERMISSION, UNSUPPORTED_DEVICE, UNKNOWN;

        fun code() = when (this) {
            CANCEL -> 101
            MISSING_PERMISSION -> 102
            UNSUPPORTED_DEVICE -> 103
            UNKNOWN -> 104
        }

        fun description() = when (this) {
            CANCEL ->
                "User cancelled"
            MISSING_PERMISSION ->
                "No permission given to use camera"
            UNSUPPORTED_DEVICE ->
                "Your device does not support this functionality"
            UNKNOWN ->
                "Unknown error"
        }
    }
}