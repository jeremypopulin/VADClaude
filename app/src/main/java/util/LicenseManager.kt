package com.example.visualduress.util

import android.content.Context
import android.provider.Settings
import android.util.Base64
import android.util.Log
import java.security.MessageDigest
import java.util.Calendar
import java.util.concurrent.TimeUnit

object LicenseManager {

    // Secret split so it's never a single plain string in the APK
    private fun getSecret(): String {
        val a = "JBP"; val b = "-V"; val c = "AD"; val d = "23"
        return a + b + c + d
    }

    private const val PREFS_NAME = "LicensePrefs"
    private const val LICENSE_KEY = "license_key"
    private const val ACTIVATION_DATE = "activation_date"
    private const val LICENSE_DURATION_DAYS = 365
    private const val DEMO_DURATION_DAYS = 5
    private const val GRACE_PERIOD_DAYS = 30

    // Legacy keys stored as hashes — original values never in APK
    private val legacyKeyHashes = setOf(
        "6B58tHAa5btdo2ok",  // BasicBpete          (basic)
        "tvBFBeLAX/2AOV/n",  // VADBIRDIEBASIC-ACCESS (basic)
        "48xLDVMzbptqoAxi",  // VADBEARPREM         (premium)
        "M7lIvDUIbalPHrB7",  // JBPBEAR2023         (premium)
        "663XcUZdGoltbxBC"   // VAD3121             (premium)
    )

    private val legacyPremiumHashes = setOf(
        "48xLDVMzbptqoAxi",
        "M7lIvDUIbalPHrB7",
        "663XcUZdGoltbxBC"
    )

    enum class LicenceState {
        ACTIVE, GRACE, LOCKED, NONE
    }

    // -------------------------------------------------------------------------
    // Key type detection
    // -------------------------------------------------------------------------

    enum class KeyType { BASIC, PREMIUM, DEMO, LEGACY_BASIC, LEGACY_PREMIUM, UNKNOWN }

    private fun detectKeyType(context: Context, key: String): KeyType {
        val keyHash = hashValue(key)
        if (keyHash in legacyPremiumHashes) return KeyType.LEGACY_PREMIUM
        if (keyHash in legacyKeyHashes)     return KeyType.LEGACY_BASIC

        val deviceId = getDeviceId(context)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        for (year in (currentYear - 1)..(currentYear + 1)) {
            if (key == generateKey(deviceId, "premium", year)) return KeyType.PREMIUM
            if (key == generateKey(deviceId, "basic",   year)) return KeyType.BASIC
            if (key == generateKey(deviceId, "demo",    year)) return KeyType.DEMO
        }
        return KeyType.UNKNOWN
    }

    private fun durationDaysForKey(context: Context, key: String): Int {
        return when (detectKeyType(context, key)) {
            KeyType.DEMO -> DEMO_DURATION_DAYS
            else         -> LICENSE_DURATION_DAYS
        }
    }

    fun getDeviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    private fun hashValue(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP).take(16)
    }

    private fun generateKey(deviceId: String, type: String, year: Int): String =
        hashValue("${getSecret()}-${deviceId.lowercase()}-$type-$year")

    private fun isLegacyKey(key: String): Boolean =
        hashValue(key) in legacyKeyHashes

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    fun validateLicense(context: Context, enteredKey: String): Boolean {
        val deviceId = getDeviceId(context)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        for (year in (currentYear - 1)..(currentYear + 1)) {
            if (enteredKey == generateKey(deviceId, "basic",   year)) return true
            if (enteredKey == generateKey(deviceId, "premium", year)) return true
            if (enteredKey == generateKey(deviceId, "demo",    year)) return true
        }
        if (hashValue(enteredKey) in legacyKeyHashes) return true
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
        if (isLegacyKey(key)) return LicenceState.ACTIVE

        val daysOverExpiry = getDaysOverExpiry(context) ?: return LicenceState.ACTIVE

        return when {
            daysOverExpiry < 0 -> {
                val daysLeft = getDaysUntilExpiry(context) ?: return LicenceState.ACTIVE
                if (daysLeft <= GRACE_PERIOD_DAYS) LicenceState.GRACE else LicenceState.ACTIVE
            }
            daysOverExpiry >= GRACE_PERIOD_DAYS -> LicenceState.LOCKED
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
        }
        return if (date == -1L) null else date
    }

    fun getLicenseType(context: Context): String {
        val key = getStoredKey(context).orEmpty()
        if (key.isEmpty()) return "NONE"
        if (!validateLicense(context, key)) return "NONE"

        return when (detectKeyType(context, key)) {
            KeyType.LEGACY_PREMIUM -> "PREMIUM"
            KeyType.LEGACY_BASIC   -> "BASIC"
            KeyType.PREMIUM        -> if (getLicenceState(context) == LicenceState.LOCKED) "EXPIRED" else "PREMIUM"
            KeyType.BASIC          -> if (getLicenceState(context) == LicenceState.LOCKED) "EXPIRED" else "BASIC"
            KeyType.DEMO           -> if (getLicenceState(context) == LicenceState.LOCKED) "EXPIRED" else "DEMO"
            KeyType.UNKNOWN        -> "NONE"
        }
    }

    // -------------------------------------------------------------------------
    // Expiry helpers
    // -------------------------------------------------------------------------

    fun getDaysUntilExpiry(context: Context): Int? {
        val key = getStoredKey(context) ?: return 0
        if (isLegacyKey(key)) return null
        val activationDate = getActivationDate(context) ?: return 0
        val duration = durationDaysForKey(context, key)
        val expiryMs = activationDate + TimeUnit.DAYS.toMillis(duration.toLong())
        val daysLeft = TimeUnit.MILLISECONDS.toDays(expiryMs - System.currentTimeMillis()).toInt()
        return daysLeft.coerceAtLeast(0)
    }

    private fun getDaysOverExpiry(context: Context): Int? {
        val key = getStoredKey(context) ?: return 0
        if (isLegacyKey(key)) return null
        val activationDate = getActivationDate(context) ?: return 0
        val duration = durationDaysForKey(context, key)
        val expiryMs = activationDate + TimeUnit.DAYS.toMillis(duration.toLong())
        val msOver = System.currentTimeMillis() - expiryMs
        return if (msOver >= 0) TimeUnit.MILLISECONDS.toDays(msOver).toInt().coerceAtLeast(0)
        else -1
    }

    fun getDaysUntilLocked(context: Context): Int? {
        val daysOver = getDaysOverExpiry(context) ?: return null
        return if (daysOver < 0) {
            val daysLeft = getDaysUntilExpiry(context) ?: return null
            daysLeft + GRACE_PERIOD_DAYS
        } else {
            (GRACE_PERIOD_DAYS - daysOver).coerceAtLeast(0)
        }
    }

    fun getExpiryDateString(context: Context): String? {
        val key = getStoredKey(context) ?: return null
        if (isLegacyKey(key)) return null
        val activationDate = getActivationDate(context) ?: return null
        val duration = durationDaysForKey(context, key)
        val expiryMs = activationDate + TimeUnit.DAYS.toMillis(duration.toLong())
        val cal = Calendar.getInstance().apply { timeInMillis = expiryMs }
        val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        return "${cal.get(Calendar.DAY_OF_MONTH)} ${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
    }
}