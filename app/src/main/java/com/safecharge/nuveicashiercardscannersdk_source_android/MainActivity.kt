package com.safecharge.nuveicashiercardscannersdk_source_android

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    private val LAST_URL_KEY = "LAST_URL_KEY"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val sharedPref = getPreferences(Context.MODE_PRIVATE) ?: return

        val url = sharedPref.getString(
            LAST_URL_KEY,
            "https://apmtest.gate2shop.com/ppp/resources/cdn/v1/scan_card/index.html"
        )

        edittext.setText(url)


        button.setOnClickListener {
            edittext.text.trim().toString().takeIf { it.length > 4 }?.let { url ->
                sharedPref.edit().putString(LAST_URL_KEY, url).apply()

                val intent = Intent(this, WebViewActivity::class.java)
                intent.putExtra("url", url)

                startActivity(intent)
            }
        }

    }
}