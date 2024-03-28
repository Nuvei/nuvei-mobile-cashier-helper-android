package com.nuvei.cashier.ui

import android.content.Context
import android.widget.Button
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.nuvei.cashier.LocaleManager
import com.nuvei.cashier.R
import com.nuvei.cashier.utils.LocalizationContextWrapper

class QRScanActivity : CaptureActivity() {
    override fun attachBaseContext(context: Context) {
        super.attachBaseContext(
            LocalizationContextWrapper.wrap(context, LocaleManager.currentLocale)
        )
    }

    override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.activity_qr_scan)
        initViews()
        return findViewById(R.id.zxing_barcode_scanner)
    }

    private fun initViews() {
        val button = findViewById<Button>(R.id.cancel_action)
        button.setOnClickListener {
            onBackPressed()
        }
    }
}