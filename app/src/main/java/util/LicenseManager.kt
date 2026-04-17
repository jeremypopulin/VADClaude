package com.example.visualduress.util

import android.content.Context
import android.provider.Settings
import android.util.Base64
import android.util.Log
import java.security.MessageDigest
import java.util.Calendar
import java.util.concurrent.TimeUnit

object LicenseManager {
    private const val SECRET = "JBP-VAD23"
    private const val PREFS_NAME = "LicensePrefs"
    private const val LICENSE_KEY = "license_key"
    private const val ACTIVATION_DATE = "activation_date"
    private const val LICENSE_DURATION_DAYS = 365

    // Legacy universal keys — still accepted, no expiry applied
    private val legacyBasicKeys = listOf("3121", "VADBIRDBASIC-ACCESS")
    private val legacyPremiumKeys = listOf("MASTER-KEY-3121", "VADBEARPREM", "JBPBEAR2023", "VAD3121")

    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    private fun hashKey(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    private fun generateKey(deviceId: String, type: String, year: Int): String {
        return hashKey("$SECRET-${deviceId.lowercase()}-$type-$year")
    }

    /**
     * Check if the key is cryptographically valid for this device.
     * Does NOT check expiry — expiry is checked in isLicenseValid().
     */
    fun validateLicense(context: Context, enteredKey: String): Boolean {
        val deviceId = getDeviceId(context)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        // Accept keys for current year or adjacent years
        for (year in (currentYear - 1)..(currentYear + 1)) {
            if (enteredKey == generateKey(deviceId, "basic",   year)) return true
            if (enteredKey == generateKey(deviceId, "premium", year)) return true
        }

        if (enteredKey in legacyBasicKeys)   return true
        if (enteredKey in legacyPremiumKeys) return true

        return false
    }

    /**
     * Save the license key and record activation date.
     * Activation date is only set on first activation — re-entering
     * the same key does NOT reset the 365-day clock.
     */
    fun saveLicense(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingKey = prefs.getString(LICENSE_KEY, null)
        prefs.edit().putString(LICENSE_KEY, key).apply()

        if (existingKey != key) {
            prefs.edit().putLong(ACTIVATION_DATE, System.currentTimeMillis()).apply()
            Log.d("LicenseManager", "New activation recorded for key")
        } else {
            Log.d("LicenseManager", "Same key re-entered — activation date unchanged")
        }
    }

    /**
     * Returns true if the stored key is valid AND not expired.
     */
    fun isLicenseValid(context: Context): Boolean {
        val storedKey = getStoredKey(context) ?: return false
        if (!validateLicense(context, storedKey)) return false
        if (storedKey in legacyBasicKeys || storedKey in legacyPremiumKeys) return true
        val daysLeft = getDaysUntilExpiry(context)
        return daysLeft == null || daysLeft > 0
    }

    fun getStoredKey(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(LICENSE_KEY, null)
    }

    private fun getActivationDate(context: Context): Long? {
        val date = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(ACTIVATION_DATE, -1L)
        return if (date == -1L) null else date
    }

    /**
     * Returns "PREMIUM", "BASIC", "EXPIRED", or "NONE".
     */
    fun getLicenseType(context: Context): String {
        val key = getStoredKey(context).orEmpty()
        if (key.isEmpty()) return "NONE"
        if (!validateLicense(context, key)) return "NONE"

        // Legacy keys — no expiry
        if (key in legacyPremiumKeys) return "PREMIUM"
        if (key in legacyBasicKeys)   return "BASIC"

        // Check expiry
        val daysLeft = getDaysUntilExpiry(context)
        if (daysLeft != null && daysLeft <= 0) return "EXPIRED"

        // Determine type
        val deviceId = getDeviceId(context)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        for (year in (currentYear - 1)..(currentYear + 1)) {
            if (key == generateKey(deviceId, "premium", year)) return "PREMIUM"
            if (key == generateKey(deviceId, "basic",   year)) return "BASIC"
        }

        return "BASIC"
    }

    /**
     * Returns days remaining. Null = legacy key (no expiry). 0 = expired.
     */
    fun getDaysUntilExpiry(context: Context): Int? {
        val key = getStoredKey(context) ?: return 0
        if (key in legacyBasicKeys || key in legacyPremiumKeys) return null

        val activationDate = getActivationDate(context) ?: return 0
        val expiryMs = activationDate + TimeUnit.DAYS.toMillis(LICENSE_DURATION_DAYS.toLong())
        val daysLeft = TimeUnit.MILLISECONDS.toDays(expiryMs - System.currentTimeMillis()).toInt()
        Log.d("LicenseManager", "Days until expiry: $daysLeft")
        return daysLeft.coerceAtLeast(0)
    }

    /**
     * Returns expiry date as readable string e.g. "17 Apr 2026".
     * Returns null for legacy keys.
     */
    fun getExpiryDateString(context: Context): String? {
        val key = getStoredKey(context) ?: return null
        if (key in legacyBasicKeys || key in legacyPremiumKeys) return null
        val activationDate = getActivationDate(context) ?: return null
        val expiryMs = activationDate + TimeUnit.DAYS.toMillis(LICENSE_DURATION_DAYS.toLong())
        val cal = Calendar.getInstance().apply { timeInMillis = expiryMs }
        val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        return "${cal.get(Calendar.DAY_OF_MONTH)} ${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
    }
}
