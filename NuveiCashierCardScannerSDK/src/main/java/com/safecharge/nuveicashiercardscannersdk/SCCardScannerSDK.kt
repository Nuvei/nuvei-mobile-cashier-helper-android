package com.safecharge.nuveicashiercardscannersdk

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import io.card.payment.CardIOActivity
import io.card.payment.CreditCard
import java.lang.ref.WeakReference
import java.net.URI


object SCCardScannerSDK {

    private const val messageName = "sccardscanner"
    const val SCAN_CARD_REQUEST_CODE = 8493

    private val hostWhiteList = arrayListOf(
        "apmtest.gate2shop.com",// QA
        "ppp-test.safecharge.com",// Integration
        "secure.safecharge.com"// Production
    )

    private var webView: WebView? = null
    private var activity = WeakReference<Activity>(null)

    fun connect(webView: WebView, activity: Activity) {

        if (!CardIOActivity.canReadCardWithCamera()) {
            return
        }
        this.activity = WeakReference(activity)
        this.webView = webView
        webView.addJavascriptInterface(WebAppInterface(), messageName)

    }

    fun didScan(data: Intent?) {
        data?.let { it ->
            if (it.hasExtra(CardIOActivity.EXTRA_SCAN_RESULT)) {
                val scanResult =
                    data.getParcelableExtra<CreditCard>(CardIOActivity.EXTRA_SCAN_RESULT)
                scanResult?.let { cardInfo ->
                    val expiryDate: String =
                        if (cardInfo.expiryMonth > 0 && cardInfo.expiryYear > 0) {
                            "${cardInfo.expiryMonth}/${cardInfo.expiryYear % 100}"
                        } else {
                            ""
                        }

                    val js = """
                    window.postMessage({
                    source: 'scanCard',
                    status: 'OK',
                    cardHolderName: '${cardInfo.cardholderName ?: ""}',
                    cardNumber: '${cardInfo.cardNumber ?: ""}',
                    expDate: '${expiryDate}',
                    cvv: '${cardInfo.cvv ?: ""}'
                    },"*");
                    """
                    webView?.evaluateJavascript(js, null)
                } ?: didFail(SCCardScannerError.UNKNOWN)
            } else {
                didFail(SCCardScannerError.UNKNOWN)
            }
        } ?: didFail(SCCardScannerError.UNKNOWN)
    }

    private fun didFail(error: SCCardScannerError) {
        val js = """
            window.postMessage({
            source: 'scanCard',
            status: 'NOK',
            errorCode: ${error.code()},
            errorMessage: ${error.description()}
            },"*");
            """
        webView?.evaluateJavascript(js, null)
    }

    private class WebAppInterface {

        @JavascriptInterface
        fun scanCard() {
            val activity = activity.get() ?: return

            webView?.post {
                webView?.takeIf { URI(it.url).host in hostWhiteList } ?: return@post

                if (!CardIOActivity.canReadCardWithCamera()) {
                    didFail(SCCardScannerError.UNSUPPORTED_DEVICE)
                    return@post
                }

                Handler(Looper.getMainLooper()).post {
                    val scanIntent = Intent(activity, CardIOActivity::class.java)

                    scanIntent.putExtra(CardIOActivity.EXTRA_REQUIRE_EXPIRY, true)
                    scanIntent.putExtra(CardIOActivity.EXTRA_SCAN_EXPIRY, true)
                    scanIntent.putExtra(CardIOActivity.EXTRA_REQUIRE_CVV, false)
                    scanIntent.putExtra(CardIOActivity.EXTRA_REQUIRE_POSTAL_CODE, false)
                    scanIntent.putExtra(CardIOActivity.EXTRA_SUPPRESS_MANUAL_ENTRY, true)
                    scanIntent.putExtra(CardIOActivity.EXTRA_SUPPRESS_CONFIRMATION, true)
                    scanIntent.putExtra(CardIOActivity.EXTRA_HIDE_CARDIO_LOGO, true)

                    activity.startActivityForResult(scanIntent, SCAN_CARD_REQUEST_CODE)
                }
            }
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