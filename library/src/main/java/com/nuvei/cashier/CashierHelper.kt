package com.nuvei.cashier

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.wallet.*
import com.google.zxing.integration.android.IntentIntegrator
import com.nuvei.cashier.PermissionManager.askPermission
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.URLEncoder
import java.util.*

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

    private val hostWhiteList = arrayListOf(
        "apmtest.gate2shop.com",// QA
        "ppp-test.safecharge.com",// Integration
        "secure.safecharge.com"// Production
    )

    private var webView: WebView? = null
    private var activity = WeakReference<Activity>(null)

    var cashierBackButtonClicked: (() -> Unit)? = null

    public fun updateURL(url: String, abilities: List<CashierAbility>): String {
        if (url.contains("#")) {
            throw InputMismatchException("Input url already contains '#'")
        }
        val abilitiesString = abilities.joinToString("_") { it.title }
        return "$url#$abilitiesString"
    }

    public fun connect(webView: WebView, activity: Activity) {
        val versionName = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE
        Log.i(TAG, "v$versionName ($versionCode)")

        CashierHelper.activity = WeakReference(activity)
        CashierHelper.webView = webView

        webView.post {
            webView.addJavascriptInterface(WebAppInterface(), messageName)
        }
    }

    public fun disconnect() {
        activity = WeakReference<Activity>(null)
    }

    public fun handleURL(url: Uri?, activity: Activity) =
        url?.takeIf { it.toString().contains("nuveicashier://scanQR", ignoreCase = true) }?.let {
            checkCameraPermission(activity) {
                source = "scanQR"
                val integrator = IntentIntegrator(activity)
                integrator.setOrientationLocked(false)
                integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                integrator.initiateScan()
            }
            true
        } ?: url?.takeIf { it.toString().contains("nuveicashier://scanCard", ignoreCase = true) }
            ?.let {
                checkCameraPermission(activity) {
                    source = "scanCard"
                    val intent = ScanCardIntent.Builder(activity).build()
                    activity.startActivityForResult(intent, REQUEST_CODE_SCAN_CARD)
                }
                true
            } ?: url?.takeIf { it.toString().contains("nuveicashier://GPay", ignoreCase = true) }
            ?.let {
                source = "GPay"
                val data = it.getQueryParameter("data")
                val browserIntent = Intent(Intent.ACTION_VIEW)
                val backUrl = URLEncoder.encode("nuvei://cashier", "UTF-8")
                val nuveiUrl =
                    "https://devmobile.sccdev-qa.com/googlepay/gpay.html?data=$data&backurl=$backUrl"
                Log.d(TAG, "Open url in external browser: $nuveiUrl")
                browserIntent.data = Uri.parse(nuveiUrl)
                activity.startActivity(browserIntent)

                true
            } ?: url?.takeIf { it.toString().contains("nuveicashier://back", ignoreCase = true) }
            ?.let {
                cashierBackButtonClicked?.invoke()

                cashierBackButtonClicked != null
            } ?: false

    public fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) =
        IntentIntegrator.parseActivityResult(
            requestCode,
            resultCode,
            data
        )?.contents?.let { result ->
            didScan(result)
            true
        } ?: handleActivityResultAsCreditCard(requestCode, resultCode, data) ||
                handleActivityResultAsGooglePay(requestCode, resultCode, data)

//    // Handle deep link with "nuvei://" scheme (was implemented for Google Pay in Chrome - not in use anymore)
//    public fun handleIntent(intent: Intent): Boolean =
//        intent
//            .data
//            ?.toString()
//            ?.takeIf { it.contains("nuvei://cashier?", ignoreCase = true) }
//            ?.replace("nuvei://cashier?", "")
//            ?.let {
//                updateCashier(it, true)
//                true
//            } ?: false

    private fun handleActivityResultAsCreditCard(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean = when (requestCode) {
        REQUEST_CODE_SCAN_CARD -> {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    data?.getParcelableExtra<Card>(
                        ScanCardIntent.RESULT_PAYCARDS_CARD)?.let {
                        didScan(it)
                    }
                }

                Activity.RESULT_CANCELED -> {
                    // TODO: Handle cancel
                }

                else -> {
                    // TODO: Handle error
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

                    Activity.RESULT_CANCELED -> {
                        onGooglePayCancel()
                    }

                    AutoResolveHelper.RESULT_ERROR ->
                        AutoResolveHelper.getStatusFromIntent(data)?.let {
                            onGooglePayError(it)
                        }
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
        val base64 =
            if (isBase64Encoded) data else Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP)
        val url = webView?.url

        url?.split("#")?.firstOrNull()?.let {
            val newUrl = "$it#$base64"
            Log.d(TAG, "updateCashier: $newUrl")
            webView?.let { webView ->
                webView.post {
                    webView.loadUrl(newUrl)
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
        webView?.let { webView ->
            webView.post {
                webView.evaluateJavascript(js, null)
            }
        }
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
        webView?.let { webView ->
            webView.post {
                webView.evaluateJavascript(js, null)
            }
        }
    }

    private fun onGooglePayCancel() {
        Log.w(TAG, "GPay.handleGooglePayCancel")

        val js = "handleGooglePayResult(null, {\"isCanceled\":true})"
        webView?.let { webView ->
            webView.post {
                webView.evaluateJavascript(js, null)
            }
        }
    }

    private fun setGooglePayAvailable(available: Boolean) {
        Log.d(TAG, "GPay.setGooglePayAvailable: available = $available")

        val js = "handleGooglePayAvailability(${if (available) "true" else "false"})"
        webView?.let { webView ->
            webView.post {
                webView.evaluateJavascript(js, null)
            }
        }
    }

    private class WebAppInterface {
        @JavascriptInterface
        fun checkGooglePayAvailability(input: String) {
            Log.d(TAG, "WebAppInterface.checkGooglePayAvailability: input = $input")

            val activity = activity.get() ?: return
            try {
                val request = IsReadyToPayRequest.fromJson(input) ?: return
                val paymentUtils = NuveiGooglePaymentUtils(JSONObject(input))
                val paymentsClient = paymentUtils.createPaymentsClient(activity)
                paymentsClient.isReadyToPay(request).addOnCompleteListener { completedTask ->
                    try {
                        completedTask.getResult(ApiException::class.java)
                            ?.let(::setGooglePayAvailable)
                    } catch (exception: ApiException) {
                        // Process error
                        Log.w("isReadyToPay failed", exception)
                        setGooglePayAvailable(false)
                    }
                }
            } catch (ex: Throwable) {
                Log.d(TAG, "WebAppInterface.checkGooglePayAvailability: ex = $ex")
                if (ex is NuveiException) {
                    Log.d(
                        TAG,
                        "WebAppInterface.checkGooglePayAvailability: ex(NuveiException) = ${ex.reason}"
                    )
                }
                setGooglePayAvailable(false)
            }
        }

        @JavascriptInterface
        fun openGooglePay(input: String) {
            Log.d(TAG, "WebAppInterface.openGooglePay: input = $input")

            val activity = activity.get() ?: return
            try {
                val paymentUtils = NuveiGooglePaymentUtils(JSONObject(input))
                val paymentsClient = paymentUtils.createPaymentsClient(activity)
                val request = PaymentDataRequest.fromJson(input)
                AutoResolveHelper.resolveTask(
                    paymentsClient.loadPaymentData(request),
                    activity,
                    REQUEST_CODE_GOOGLE_PAY
                )
            } catch (ex: Throwable) {
                Log.d(TAG, "WebAppInterface.openGooglePay: ex = $ex")
                if (ex is NuveiException) {
                    Log.d(TAG, "WebAppInterface.openGooglePay: ex(NuveiException) = ${ex.reason}")
                }
            }
        }
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
        PermissionManager.checkPermission(
            context,
            PermissionManager.Permission.Camera
        ) {
            when (it) {
                PermissionManager.Status.Unknown,
                PermissionManager.Status.Granted -> completion()

                PermissionManager.Status.Ask -> askPermission(
                    context,
                    PermissionManager.Permission.Camera
                ) {
                    if (it == PermissionManager.Status.Granted) {
                        completion()
                    }
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
