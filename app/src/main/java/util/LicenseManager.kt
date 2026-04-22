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
    private const val LICENSE_DURATION_DAYS = 365   // ← set to 365 for production
    private const val GRACE_PERIOD_DAYS = 30       // ← set to 30 for production

    private val legacyBasicKeys   = listOf("3121", "VADBIRDBASIC-ACCESS")
    private val legacyPremiumKeys = listOf("MASTER-KEY-3121", "VADBEARPREM", "JBPBEAR2023", "VAD3121")

    enum class LicenceState {
        ACTIVE, GRACE, LOCKED, NONE
    }

    fun getDeviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    private fun hashKey(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP).take(16)
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

    fun getLicenceState(context: Context): LicenceState {
        val key = getStoredKey(context) ?: return LicenceState.NONE
        if (!validateLicense(context, key)) return LicenceState.NONE
        if (key in legacyBasicKeys || key in legacyPremiumKeys) return LicenceState.ACTIVE

        val daysOverExpiry = getDaysOverExpiry(context) ?: return LicenceState.ACTIVE

        return when {
            // Not yet expired
            daysOverExpiry < 0 -> {
                val daysLeft = getDaysUntilExpiry(context) ?: return LicenceState.ACTIVE
                if (daysLeft <= GRACE_PERIOD_DAYS) LicenceState.GRACE else LicenceState.ACTIVE
            }
            // Past expiry + full grace period = LOCKED
            daysOverExpiry >= GRACE_PERIOD_DAYS -> LicenceState.LOCKED
            // Past expiry but within grace period = GRACE
            else -> LicenceState.GRACE
        }
    }

    fun isLicenseValid(context: Context): Boolean {
        val state = getLicenceState(context)
        return state == LicenceState.ACTIVE || state == LicenceState.GRACE
    }

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

    fun getDaysUntilExpiry(context: Context): Int? {
        val key = getStoredKey(context) ?: return 0
        if (key in legacyBasicKeys || key in legacyPremiumKeys) return null
        val activationDate = getActivationDate(context) ?: return 0
        val expiryMs = activationDate + TimeUnit.DAYS.toMillis(LICENSE_DURATION_DAYS.toLong())
        val daysLeft = TimeUnit.MILLISECONDS.toDays(expiryMs - System.currentTimeMillis()).toInt()
        return daysLeft.coerceAtLeast(0)
    }

    private fun getDaysOverExpiry(context: Context): Int? {
        val key = getStoredKey(context) ?: return 0
        if (key in legacyBasicKeys || key in legacyPremiumKeys) return null
        val activationDate = getActivationDate(context) ?: return 0
        val expiryMs = activationDate + TimeUnit.DAYS.toMillis(LICENSE_DURATION_DAYS.toLong())
        val msOver = System.currentTimeMillis() - expiryMs
        return if (msOver >= 0) {
            TimeUnit.MILLISECONDS.toDays(msOver).toInt().coerceAtLeast(0)
        } else {
            -1  // not yet expired
        }
    }

    fun getDaysUntilLocked(context: Context): Int? {
        val daysOver = getDaysOverExpiry(context) ?: return null
        return if (daysOver < 0) {
            // Still within licence duration but in warning window
            // Days until locked = days until expiry + grace period
            val daysLeft = getDaysUntilExpiry(context) ?: return null
            daysLeft + GRACE_PERIOD_DAYS
        } else {
            // Past expiry — count down remaining grace days
            val graceDaysLeft = GRACE_PERIOD_DAYS - daysOver
            graceDaysLeft.coerceAtLeast(0)
        }
    }

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