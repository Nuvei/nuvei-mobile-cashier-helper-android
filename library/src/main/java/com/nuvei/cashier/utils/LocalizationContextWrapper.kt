package com.nuvei.cashier.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.*

class LocalizationContextWrapper(base: Context) : ContextWrapper(base) {
    companion object {
        fun wrap(context: Context, locale: Locale): ContextWrapper {
            val ctx: Context
            val res = context.resources
            val configuration = Configuration(res.configuration)
            Locale.setDefault(locale)

            ctx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                configuration.setLocale(locale)
                val localeList = LocaleList(locale)
                LocaleList.setDefault(localeList)
                configuration.setLocales(localeList)
                context.createConfigurationContext(configuration)
            } else {
                configuration.setLocale(locale)
                context.createConfigurationContext(configuration)
            }

            return LocalizationContextWrapper(ctx)
        }
    }
}