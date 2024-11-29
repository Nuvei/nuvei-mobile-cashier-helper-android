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
import com.google.android.gms.wallet.AutoResolveHelper
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import com.google.zxing.integration.android.IntentIntegrator
import com.nuvei.cashier.PermissionManager.askPermission
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
    const val REQUEST_CODE_SCAN_CARD = 8493

    private var source = ""

    private var activity = WeakReference<Activity>(null)

    var cashierBackButtonClicked: (() -> Unit)? = null

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
    }

    public fun disconnect() {
        activity = WeakReference<Activity>(null)
    }

    public fun handleURL(url: Uri?, activity: Activity) =
        url?.takeIf { it.toString().contains("nuveicashier://scanCard", ignoreCase = true) }
            ?.let {
                checkCameraPermission(activity) {
                    source = "scanCard"
                    val intent = ScanCardIntent.Builder(activity).build()
                    activity.startActivityForResult(intent, REQUEST_CODE_SCAN_CARD)
                }
                true
            } ?: false

    public fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) =
        handleActivityResultAsCreditCard(requestCode, resultCode, data)

    private fun handleActivityResultAsCreditCard(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean = when (requestCode) {
        REQUEST_CODE_SCAN_CARD -> {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    data?.getParcelableExtra<Card>(
                        ScanCardIntent.RESULT_PAYCARDS_CARD
                    )?.let {
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

    private fun updateCashier(data: String, isBase64Encoded: Boolean = false) {
        Log.d(TAG, "updateCashier with data: $data")
    }

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
