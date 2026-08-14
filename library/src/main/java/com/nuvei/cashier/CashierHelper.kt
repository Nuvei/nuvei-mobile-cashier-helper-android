package com.nuvei.cashier

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.wallet.AutoResolveHelper
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import com.google.zxing.client.android.BuildConfig
import com.google.zxing.integration.android.IntentIntegrator
import com.nuvei.cashier.PermissionManager.askPermission
import com.nuvei.cashier.ui.QRScanActivity
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.URLEncoder
import java.util.InputMismatchException
import java.util.Locale

public enum class CashierAbility(public val title: String) {
    QR("scanQR"), CARD("scanCard")
}

@SuppressLint("StaticFieldLeak")
public object CashierHelper {

    private const val TAG = "NuveiCashierHelper"
    private const val messageName = "NuveiCashierHelper"

    const val REQUEST_CODE_SCAN_CARD = 8493
    const val REQUEST_CODE_GOOGLE_PAY = 9912

    private var source = ""

    private val cashierDomains = listOf(
        "nuvei.com",
        "safecharge.com",
        "gate2shop.com",
        "sccdev-qa.com"
    )

    private var webView: WebView? = null
    private var activity = WeakReference<Activity>(null)

    var cashierBackButtonClicked: (() -> Unit)? = null

    /**
     * Schemes to hand off even when the cashier is not served from a Nuvei domain.
     * Only needed when self-hosting the cashier. Never list your own app's schemes.
     */
    public var externalSchemes: List<String> = emptyList()

    // Foreground return support (like iOS didBecomeActive -> dispatch onAppFocusReturn)
    private var hasPendingFocusReturn: Boolean = false
    private var activityLifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null

    // Held directly so callbacks can be removed after the activity is collected.
    private var application: Application? = null

    public fun updateURL(url: String, abilities: List<CashierAbility>): String {
        if (url.contains("#")) {
            throw InputMismatchException("Input url already contains '#'")
        }
        val abilitiesString = abilities.joinToString("_") { it.title }
        return "$url#$abilitiesString"
    }

    public fun connect(webView: WebView, activity: Activity, locale: Locale = Locale.getDefault()) {
        val versionName = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE
        Log.i(TAG, "v$versionName ($versionCode)")
        LocaleManager.currentLocale = locale

        CashierHelper.activity = WeakReference(activity)
        CashierHelper.webView = webView

        registerForegroundReturnObserver(activity)

        webView.post {
            // Exposes window.NuveiCashierHelper.postMessage(...)
            webView.addJavascriptInterface(WebAppInterface(), messageName)
        }
    }

    public fun disconnect() {
        unregisterForegroundReturnObserver()
        activity = WeakReference<Activity>(null)
        webView = null
        hasPendingFocusReturn = false
    }

    public fun handleURL(url: Uri?, activity: Activity): Boolean {
        if (url == null) return false

        when (url.nuveiCommand()) {
            "scanqr" -> {
                checkCameraPermission(activity) {
                    source = "scanQR"
                    val integrator = IntentIntegrator(activity)
                    integrator.setOrientationLocked(false)
                    integrator.setCaptureActivity(QRScanActivity::class.java)
                    integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                    integrator.initiateScan()
                }
                return true
            }

            "scancard" -> {
                checkCameraPermission(activity) {
                    source = "scanCard"
                    val intent = ScanCardIntent.Builder(activity).build()
                    activity.startActivityForResult(intent, REQUEST_CODE_SCAN_CARD)
                }
                return true
            }

            "gpay" -> {
                source = "GPay"
                val data = url.getQueryParameter("data")
                val browserIntent = Intent(Intent.ACTION_VIEW)
                val backUrl = URLEncoder.encode("nuvei://cashier", "UTF-8")
                val nuveiUrl =
                    "https://devmobile.sccdev-qa.com/googlepay/gpay.html?data=$data&backurl=$backUrl"
                Log.d(TAG, "Open url in external browser: $nuveiUrl")
                browserIntent.data = Uri.parse(nuveiUrl)
                activity.startActivity(browserIntent)
                return true
            }

            "back" -> {
                cashierBackButtonClicked?.invoke()
                return cashierBackButtonClicked != null
            }
        }

        val urlString = url.toString()
        // The WebView loads these itself; handing them off would send ordinary
        // cashier navigation to the browser.
        if (URLUtil.isValidUrl(urlString) || URLUtil.isDataUrl(urlString)) return false

        // On a merchant's own pages every URL belongs to the merchant, including
        // their private command schemes.
        val onCashierPage = webView?.url
            ?.let { Uri.parse(it).host }
            ?.isCashierDomain() == true
        val optedIn = externalSchemes.any { it.equals(url.scheme, ignoreCase = true) }
        if (!onCashierPage && !optedIn) return false

        val opened = tryOpenExternal(activity, urlString)
        if (opened) hasPendingFocusReturn = true
        return opened
    }

    private fun Uri.nuveiCommand(): String? {
        if (!scheme.equals("nuveicashier", ignoreCase = true)) return null
        val raw = host ?: schemeSpecificPart.orEmpty().trimStart('/')
        return raw.substringBefore('?').lowercase().takeIf { it.isNotEmpty() }
    }

    private fun String.isCashierDomain(): Boolean =
        cashierDomains.any { equals(it, true) || endsWith(".$it", true) }

    public fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) =
        IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
            ?.contents
            ?.let { result ->
                didScan(result)
                true
            } ?: handleActivityResultAsCreditCard(requestCode, resultCode, data) ||
                handleActivityResultAsGooglePay(requestCode, resultCode, data)

    private fun handleActivityResultAsCreditCard(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean = when (requestCode) {
        REQUEST_CODE_SCAN_CARD -> {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    data?.getParcelableExtra<Card>(ScanCardIntent.RESULT_PAYCARDS_CARD)?.let {
                        didScan(it)
                    }
                }

                Activity.RESULT_CANCELED -> {
                    // TODO
                }

                else -> {
                    // TODO
                }
            }
            true
        }

        else -> false
    }

    private fun handleActivityResultAsGooglePay(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean {
        return when (requestCode) {
            REQUEST_CODE_GOOGLE_PAY -> {
                when (resultCode) {
                    Activity.RESULT_OK ->
                        data?.let { intent ->
                            PaymentData.getFromIntent(intent)?.let(::onGooglePaySuccess)
                        }

                    Activity.RESULT_CANCELED -> onGooglePayCancel()

                    AutoResolveHelper.RESULT_ERROR ->
                        AutoResolveHelper.getStatusFromIntent(data)?.let(::onGooglePayError)
                }
                true
            }

            else -> false
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
        println(card.toString())
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
    }

    private fun updateCashier(data: String, isBase64Encoded: Boolean = false) {
        println(data.toString())
        val base64 =
            if (isBase64Encoded) data else Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP)
        val url = webView?.url
        url?.split("#")?.firstOrNull()?.let {
            val newUrl = "$it#$base64"
            Log.d(TAG, "updateCashier: $newUrl")
            webView?.post { webView?.loadUrl(newUrl) }
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
            CANCEL -> "User cancelled"
            MISSING_PERMISSION -> "No permission given to use camera"
            UNSUPPORTED_DEVICE -> "Your device does not support this functionality"
            UNKNOWN -> "Unknown error"
        }
    }

    private fun onGooglePaySuccess(paymentData: PaymentData) {
        val paymentInformation = paymentData.toJson() ?: return
        Log.d(TAG, "GPay.handleGooglePaySuccess: paymentInformation = $paymentInformation")

        val js = "handleGooglePayResult($paymentInformation, null)"
        webView?.post { webView?.evaluateJavascript(js, null) }
    }

    private fun onGooglePayError(status: Status) {
        val statusMap = mapOf(
            "isCanceled" to status.isCanceled,
            "isInterrupted" to status.isInterrupted,
            "isSuccess" to status.isSuccess,
            "statusCode" to status.statusCode,
            "statusMessage" to status.statusMessage
        )
        val statusJson = JSONObject(statusMap).toString()

        Log.w(TAG, "GPay.handleGooglePayError: statusJson = $statusJson")

        val js = "handleGooglePayResult(null, $statusJson)"
        webView?.post { webView?.evaluateJavascript(js, null) }
    }

    private fun onGooglePayCancel() {
        Log.w(TAG, "GPay.handleGooglePayCancel")

        val js = "handleGooglePayResult(null, {\"isCanceled\":true})"
        webView?.post { webView?.evaluateJavascript(js, null) }
    }

    private fun setGooglePayAvailable(available: Boolean) {
        Log.d(TAG, "GPay.setGooglePayAvailable: available = $available")

        val js = "handleGooglePayAvailability(${if (available) "true" else "false"})"
        webView?.post { webView?.evaluateJavascript(js, null) }
    }

    /**
     * Exposed to JS as: window.NuveiCashierHelper.postMessage(...)
     *
     * Payload example:
     * {
     *   "action":"openExternalLink",
     *   "link":"bepgenapp://DoTx?...",
     *   "callbackID":"tx_001"
     * }
     */
    class WebAppInterface {

        @JavascriptInterface
        fun postMessage(jsonString: String) {
            val act = CashierHelper.activity.get() ?: return
            val wv = CashierHelper.webView ?: return

            var callbackId = ""
            var opened = false

            try {
                val obj = JSONObject(jsonString)
                val action = obj.optString("action", "")
                callbackId = obj.optString("callbackID", "")

                when (action) {
                    "openExternalLink" -> {
                        val link = obj.optString("link", "")
                        opened = tryOpenExternal(act, link)
                        if (opened) CashierHelper.hasPendingFocusReturn = true
                    }

                    // You can extend here in the future:
                    // "openGooglePay" -> openGooglePay(obj.getJSONObject("data").toString())
                    // "checkGooglePayAvailability" -> checkGooglePayAvailability(obj.getJSONObject("data").toString())

                    else -> {
                        Log.w(TAG, "postMessage: unknown action='$action' payload=$jsonString")
                        opened = false
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "postMessage parse failed: $t")
                opened = false
            }

            if (callbackId.isNotBlank()) {
                sendJsNativeCallback(wv, callbackId, opened)
            }
        }

        // --- Existing functions can stay as-is ---
        @JavascriptInterface
        fun checkGooglePayAvailability(input: String) {
            Log.d(TAG, "WebAppInterface.checkGooglePayAvailability: input = $input")

            val act = CashierHelper.activity.get() ?: return
            try {
                val request = IsReadyToPayRequest.fromJson(input) ?: return
                val paymentUtils = NuveiGooglePaymentUtils(JSONObject(input))
                val paymentsClient = paymentUtils.createPaymentsClient(act)

                paymentsClient.isReadyToPay(request).addOnCompleteListener { completedTask ->
                    try {
                        completedTask.getResult(ApiException::class.java)?.let(::setGooglePayAvailable)
                    } catch (exception: ApiException) {
                        Log.w("isReadyToPay failed", exception)
                        setGooglePayAvailable(false)
                    }
                }
            } catch (ex: Throwable) {
                Log.d(TAG, "WebAppInterface.checkGooglePayAvailability: ex = $ex")
                setGooglePayAvailable(false)
            }
        }

        @JavascriptInterface
        fun openGooglePay(input: String) {
            Log.d(TAG, "WebAppInterface.openGooglePay: input = $input")

            val act = CashierHelper.activity.get() ?: return
            try {
                val paymentUtils = NuveiGooglePaymentUtils(JSONObject(input))
                val paymentsClient = paymentUtils.createPaymentsClient(act)
                val request = PaymentDataRequest.fromJson(input)

                AutoResolveHelper.resolveTask(
                    paymentsClient.loadPaymentData(request),
                    act,
                    REQUEST_CODE_GOOGLE_PAY
                )
            } catch (ex: Throwable) {
                Log.d(TAG, "WebAppInterface.openGooglePay: ex = $ex")
            }
        }
    }

    /**
     * Try startActivity and decide by exception (works on all versions; avoids package visibility pitfalls).
     */
    private fun tryOpenExternal(activity: Activity, link: String): Boolean {
        if (link.isBlank()) return false

        val uri = runCatching { Uri.parse(link) }.getOrNull() ?: return false
        val intent = Intent(Intent.ACTION_VIEW, uri)

        return try {
            activity.startActivity(intent)
            true
        } catch (e: android.content.ActivityNotFoundException) {
            Log.w(TAG, "No app can handle: $link")
            false
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to open: $link ($t)")
            false
        }
    }

    private fun sendJsNativeCallback(webView: WebView, callbackID: String, opened: Boolean) {
        // window.onNativeCallback({ callbackID, success:true, data:{status:'true/false'} })
        val payload = JSONObject().apply {
            put("callbackID", callbackID)
            put("success", true)
            put("data", JSONObject().apply {
                put("status", opened.toString())
            })
        }

        val js = "window.onNativeCallback($payload);"
        webView.post { webView.evaluateJavascript(js, null) }
    }

    private fun registerForegroundReturnObserver(activity: Activity) {
        unregisterForegroundReturnObserver()

        val app = activity.application
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) {
                val current = CashierHelper.activity.get()
                if (current != null && a === current) {
                    appDidBecomeActive()
                }
            }

            override fun onActivityCreated(a: Activity, s: android.os.Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        }

        app.registerActivityLifecycleCallbacks(callbacks)
        activityLifecycleCallbacks = callbacks
        application = app
    }

    private fun unregisterForegroundReturnObserver() {
        val app = application ?: return
        val callbacks = activityLifecycleCallbacks ?: return
        app.unregisterActivityLifecycleCallbacks(callbacks)
        activityLifecycleCallbacks = null
        application = null
    }

    private fun appDidBecomeActive() {
        if (!hasPendingFocusReturn) return
        val wv = webView ?: return

        val js = "window.dispatchEvent(new CustomEvent('onAppFocusReturn'));"
        wv.post { wv.evaluateJavascript(js, null) }

        hasPendingFocusReturn = false
    }

    private class NuveiGooglePaymentUtils(val json: JSONObject) {
        fun createPaymentsClient(activity: Activity): PaymentsClient {
            val environmentString =
                json["environment"] as? String ?: throw NuveiException("Missing environment")

            val environment: Int = when (environmentString) {
                "TEST" -> WalletConstants.ENVIRONMENT_TEST
                "PRODUCTION" -> WalletConstants.ENVIRONMENT_PRODUCTION
                else -> throw NuveiException("Unknown environment")
            }

            val walletOptions = Wallet.WalletOptions.Builder()
                .setEnvironment(environment)
                .build()

            return Wallet.getPaymentsClient(activity, walletOptions)
        }
    }

    private class NuveiException(val reason: String = "") : Exception()

    private fun checkCameraPermission(context: Activity, completion: () -> Unit) {
        PermissionManager.checkPermission(context, PermissionManager.Permission.Camera) {
            when (it) {
                PermissionManager.Status.Unknown,
                PermissionManager.Status.Granted -> completion()

                PermissionManager.Status.Ask -> askPermission(context, PermissionManager.Permission.Camera) { st ->
                    if (st == PermissionManager.Status.Granted) completion()
                }

                PermissionManager.Status.Denied -> showAlert(context)
            }
        }
    }

    private fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val uri = Uri.fromParts("package", context.packageName, null)
        intent.data = uri
        context.startActivity(intent)
    }

    private fun showAlert(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(R.string.permission_alert_title)
            .setMessage(R.string.permission_alert_rationale)
            .setPositiveButton(R.string.permission_alert_button_settings) { _, _ ->
                openSettings(context)
            }
            .setNeutralButton(R.string.permission_alert_button_ok) { _, _ -> }
            .show()
    }
}
