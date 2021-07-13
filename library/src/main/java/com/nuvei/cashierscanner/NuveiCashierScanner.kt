package com.nuvei.cashierscanner

import android.R.attr
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.webkit.WebView
import cards.pay.paycardsrecognizer.sdk.Card
import cards.pay.paycardsrecognizer.sdk.ScanCardIntent
import com.google.zxing.integration.android.IntentIntegrator
import java.lang.ref.WeakReference


@SuppressLint("StaticFieldLeak")
public object NuveiCashierScanner {

    private const val messageName = "sccardscanner"
    const val SCAN_CARD_REQUEST_CODE = 8493

    private var source = ""


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
            source = "scanQR"
            val integrator = IntentIntegrator(activity)
            integrator.setOrientationLocked(false)
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            integrator.initiateScan()

            true
        } ?: url?.takeIf { it.toString().contains("nuveicashier://scanCard", ignoreCase = true) }?.let {
            source = "scanCard"
            val intent = ScanCardIntent.Builder(activity).build()
            activity.startActivityForResult(intent, SCAN_CARD_REQUEST_CODE)

            true
        } ?: false

    public fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) =
        IntentIntegrator.parseActivityResult(requestCode, resultCode, data)?.contents?.let { result ->
            didScan(result)
            true
        } ?: parseActivityResultAsCreditCard(requestCode, resultCode, data)?.let { card ->
            didScan(card)
            true
        } ?: false

    private fun parseActivityResultAsCreditCard(requestCode: Int, resultCode: Int, data: Intent?): Card? {
        return if (requestCode != SCAN_CARD_REQUEST_CODE || resultCode != Activity.RESULT_OK) {
            null
        } else {
            data?.getParcelableExtra(ScanCardIntent.RESULT_PAYCARDS_CARD)
        }
    }

    private fun didScan(qrString: String) {
        updateCashier("{\"qrCode\":\"${qrString}\"}")
    }

    private fun didScan(card: Card) {
        updateCashier(
            "{" +
                    "\"source\":\"$source\"" +
                    ",\"status\":\"OK\"" +
                    ",\"cardHolderName\":\"${card.cardHolderName}\"" +
                    ",\"cardNumber\":\"${card.cardNumber}\"" +
                    ",\"expDate\":\"${card.expirationDate}\"" +
                    "}"
        )
    }

    private fun didFail(error: SCCardScannerError) {
        updateCashier(
            "{" +
                    "\"source\":\"$source\"" +
                    ",\"status\":\"NOK\"" +
                    ",\"errorCode\":\"${error.code()}\"" +
                    ",\"errorMessage\":\"${error.description()}\"" +
                    "}"
        )
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