package com.example.visualduress.util

import android.content.Context
import com.google.gson.JsonParser
import java.io.InputStreamReader

fun loadCompanyName(context: Context): String {
    return loadFieldFromConfig(context, "companyName") ?: "Unknown Company"
}

fun loadWebsiteUrl(context: Context): String {
    return loadFieldFromConfig(context, "websiteUrl") ?: "https://example.com"
}

fun loadAppVersion(context: Context): String {
    return loadFieldFromConfig(context, "version") ?: "1.0.0"
}

private fun loadFieldFromConfig(context: Context, field: String): String? {
    return try {
        val inputStream = context.assets.open("config.json")
        val jsonElement = JsonParser.parseReader(InputStreamReader(inputStream))
        jsonElement.asJsonObject.get(field)?.asString
    } catch (e: Exception) {
        null
    }
}
