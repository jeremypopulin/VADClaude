package com.example.visualduress.util

import android.content.Context
import android.provider.Settings
import android.util.Base64
import android.util.Log
import java.security.MessageDigest

object LicenseManager {
    private const val SECRET = "JBP-VAD23"
    private const val PREFS_NAME = "LicensePrefs"
    private const val LICENSE_KEY = "license_key"

    private val basicKeys = listOf(
        "3121",
        "VADBIRDBASIC-ACCESS",
    )

    private val premiumKeys = listOf(
        "MASTER-KEY-3121",
        "VADBEARPREM",
        "JBPBEAR2023",
        "VAD3121"
    )

    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    private fun hashKey(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun validateLicense(context: Context, enteredKey: String): Boolean {
        val deviceId = getDeviceId(context)
        val validKeyBasic = hashKey("$SECRET-$deviceId-basic")
        val validKeyPremium = hashKey("$SECRET-$deviceId-premium")

        Log.d("LicenseManager", "Device ID: $deviceId")
        Log.d("LicenseManager", "Expected BASIC hash: $validKeyBasic")
        Log.d("LicenseManager", "Expected PREMIUM hash: $validKeyPremium")
        Log.d("LicenseManager", "Entered key: $enteredKey")

        return enteredKey == validKeyBasic ||
                enteredKey == validKeyPremium ||
                enteredKey in basicKeys ||
                enteredKey in premiumKeys
    }

    fun saveLicense(context: Context, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LICENSE_KEY, key)
            .apply()
    }

    fun isLicenseValid(context: Context): Boolean {
        val storedKey = getStoredKey(context)
        return storedKey?.let { validateLicense(context, it) } ?: false
    }

    fun getStoredKey(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(LICENSE_KEY, null)
    }

/*fun getLicenseType(context: Context): String {
        val deviceId = getDeviceId(context)
        val key = getStoredKey(context).orEmpty()

        val validKeyBasic = hashKey("$SECRET-$deviceId-basic")
        val validKeyPremium = hashKey("$SECRET-$deviceId-premium")

        return when {
            key == validKeyPremium || key in premiumKeys -> "PREMIUM"
            key == validKeyBasic || key in basicKeys -> "BASIC"
            else -> "BASIC" // fallback
        }
    }*/
    fun getLicenseType(context: Context): String {
        val deviceId = getDeviceId(context)
        val key = getStoredKey(context).orEmpty()

        Log.d("LicenseManager", "Getting license type for key: $key")

        val validKeyBasic = hashKey("$SECRET-$deviceId-basic")
        val validKeyPremium = hashKey("$SECRET-$deviceId-premium")

        val result = when {
            key == validKeyPremium || key in premiumKeys -> {
                Log.d("LicenseManager", "Matched PREMIUM license")
                "PREMIUM"
            }
            key == validKeyBasic || key in basicKeys -> {
                Log.d("LicenseManager", "Matched BASIC license")
                "BASIC"
            }
            else -> {
                Log.d("LicenseManager", "No match found, defaulting to BASIC")
                "BASIC"
            }
        }

        return result
    }
}
