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
    private const val GRACE_PERIOD_DAYS = 30

    private val legacyBasicKeys   = listOf("3121", "VADBIRDBASIC-ACCESS")
    private val legacyPremiumKeys = listOf("MASTER-KEY-3121", "VADBEARPREM", "JBPBEAR2023", "VAD3121")

    // -------------------------------------------------------------------------
    // Licence state enum
    // -------------------------------------------------------------------------

    enum class LicenceState {
        /** Valid, more than 30 days remaining */
        ACTIVE,
        /** Valid, within 30-day grace period warning */
        GRACE,
        /** Past 365 + 30 days — app must lock */
        LOCKED,
        /** No valid key stored */
        NONE
    }

    fun getDeviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    private fun hashKey(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    private fun generateKey(deviceId: String, type: String, year: Int): String =
        hashKey("$SECRET-${deviceId.lowercase()}-$type-$year")

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    fun validateLicense(context: Context, enteredKey: String): Boolean {
        val deviceId = getDeviceId(context)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        for (year in (currentYear - 1)..(currentYear + 1)) {
            if (enteredKey == generateKey(deviceId, "basic",   year)) return true
            if (enteredKey == generateKey(deviceId, "premium", year)) return true
        }
        if (enteredKey in legacyBasicKeys)   return true
        if (enteredKey in legacyPremiumKeys) return true
        return false
    }

    fun saveLicense(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingKey = prefs.getString(LICENSE_KEY, null)
        prefs.edit().putString(LICENSE_KEY, key).apply()
        if (existingKey != key) {
            prefs.edit().putLong(ACTIVATION_DATE, System.currentTimeMillis()).apply()
            Log.d("LicenseManager", "New activation recorded")
        } else {
            Log.d("LicenseManager", "Same key re-entered — activation date unchanged")
        }
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /**
     * Returns the full licence state including grace period and locked state.
     * This is the primary function all UI should use.
     */
    fun getLicenceState(context: Context): LicenceState {
        val key = getStoredKey(context) ?: return LicenceState.NONE
        if (!validateLicense(context, key)) return LicenceState.NONE
        if (key in legacyBasicKeys || key in legacyPremiumKeys) return LicenceState.ACTIVE

        val daysOverExpiry = getDaysOverExpiry(context)
        return when {
            daysOverExpiry == null       -> LicenceState.ACTIVE  // legacy
            daysOverExpiry > GRACE_PERIOD_DAYS -> LicenceState.LOCKED
            daysOverExpiry > 0           -> LicenceState.GRACE
            else                         -> {
                // Still within 365 days — check days until expiry
                val daysLeft = getDaysUntilExpiry(context) ?: return LicenceState.ACTIVE
                if (daysLeft <= GRACE_PERIOD_DAYS) LicenceState.GRACE else LicenceState.ACTIVE
            }
        }
    }

    /** True only if state is ACTIVE or GRACE — app can run */
    fun isLicenseValid(context: Context): Boolean {
        val state = getLicenceState(context)
        return state == LicenceState.ACTIVE || state == LicenceState.GRACE
    }

    /** True only if state is LOCKED — app must show expired screen */
    fun isLocked(context: Context): Boolean = getLicenceState(context) == LicenceState.LOCKED

    fun getStoredKey(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(LICENSE_KEY, null)

    private fun getActivationDate(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var date = prefs.getLong(ACTIVATION_DATE, -1L)
        if (date == -1L && getStoredKey(context) != null) {
            date = System.currentTimeMillis()
            prefs.edit().putLong(ACTIVATION_DATE, date).apply()
            Log.d("LicenseManager", "No activation date found — setting to today")
        }
        return if (date == -1L) null else date
    }

    /**
     * Returns "PREMIUM", "BASIC", "EXPIRED", or "NONE".
     * EXPIRED covers both grace period and locked state.
     */
    fun getLicenseType(context: Context): String {
        val key = getStoredKey(context).orEmpty()
        if (key.isEmpty()) return "NONE"
        if (!validateLicense(context, key)) return "NONE"
        if (key in legacyPremiumKeys) return "PREMIUM"
        if (key in legacyBasicKeys)   return "BASIC"

        val state = getLicenceState(context)
        if (state == LicenceState.LOCKED) return "EXPIRED"

        val deviceId = getDeviceId(context)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        for (year in (currentYear - 1)..(currentYear + 1)) {
            if (key == generateKey(deviceId, "premium", year)) return "PREMIUM"
            if (key == generateKey(deviceId, "basic",   year)) return "BASIC"
        }
        return "BASIC"
    }

    // -------------------------------------------------------------------------
    // Expiry helpers
    // -------------------------------------------------------------------------

    /**
     * Days remaining until 365-day expiry.
     * Null = legacy key (no expiry). 0 = at or past expiry.
     */
    fun getDaysUntilExpiry(context: Context): Int? {
        val key = getStoredKey(context) ?: return 0
        if (key in legacyBasicKeys || key in legacyPremiumKeys) return null
        val activationDate = getActivationDate(context) ?: return 0
        val expiryMs = activationDate + TimeUnit.DAYS.toMillis(LICENSE_DURATION_DAYS.toLong())
        val daysLeft = TimeUnit.MILLISECONDS.toDays(expiryMs - System.currentTimeMillis()).toInt()
        return daysLeft.coerceAtLeast(0)
    }

    /**
     * Days past the 365-day expiry (i.e. how far into the grace period).
     * 0 or negative = not yet expired.
     * Null = legacy key.
     */
    private fun getDaysOverExpiry(context: Context): Int? {
        val key = getStoredKey(context) ?: return 0
        if (key in legacyBasicKeys || key in legacyPremiumKeys) return null
        val activationDate = getActivationDate(context) ?: return 0
        val expiryMs = activationDate + TimeUnit.DAYS.toMillis(LICENSE_DURATION_DAYS.toLong())
        val daysOver = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - expiryMs).toInt()
        return daysOver
    }

    /**
     * Days remaining in the grace period (after 365-day expiry).
     * Null = legacy key or not yet expired.
     * 0 = grace period ended — app should lock.
     */
    fun getDaysUntilLocked(context: Context): Int? {
        val daysOver = getDaysOverExpiry(context) ?: return null
        if (daysOver <= 0) return null  // not yet in grace period
        val graceDaysLeft = GRACE_PERIOD_DAYS - daysOver
        return graceDaysLeft.coerceAtLeast(0)
    }

    /**
     * Expiry date as readable string e.g. "17 Apr 2026".
     * Null for legacy keys.
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