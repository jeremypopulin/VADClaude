package com.example.visualduress.util

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.example.visualduress.MainActivity
import com.example.visualduress.receiver.KioskAdminReceiver

/** Walk up the Context chain to find the hosting Activity (needed from Compose dialogs). */
fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * All kiosk / Device Owner control in one place.
 * Used by MainActivity (auto-lock, hidden corner) and the Settings → Service tab.
 */
object KioskManager {

    /** Installer PIN for the hidden corner and the Service tab. */
    const val SERVICE_PIN = "3121"

    private const val PREFS = "duress_prefs"
    private const val KEY_KIOSK_ENABLED = "kiosk_enabled"

    private fun dpm(ctx: Context) =
        ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun admin(ctx: Context) = ComponentName(ctx, KioskAdminReceiver::class.java)

    fun isDeviceOwner(ctx: Context): Boolean = dpm(ctx).isDeviceOwnerApp(ctx.packageName)

    /** Per-unit switch: off on dev/demo tablets, on (default) for customer units. */
    fun isKioskEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_KIOSK_ENABLED, true)

    fun isLocked(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    fun setKioskEnabled(activity: Activity, enabled: Boolean) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_KIOSK_ENABLED, enabled).apply()
        if (enabled) enable(activity) else exit(activity, showToast = false)
    }

    /** Called from MainActivity.onResume — locks the unit if it is Device Owner and kiosk is enabled. */
    fun enable(activity: Activity) {
        if (!isDeviceOwner(activity)) return
        val dpm = dpm(activity)
        val admin = admin(activity)
        try {
            // VAD is always the home screen on a Device Owner unit (no launcher picker)
            val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(
                admin, homeFilter, ComponentName(activity, MainActivity::class.java)
            )

            if (!isKioskEnabled(activity)) return

            dpm.setLockTaskPackages(admin, arrayOf(activity.packageName))
            dpm.setStatusBarDisabled(admin, true)
            dpm.setKeyguardDisabled(admin, true)
            if (!isLocked(activity)) activity.startLockTask()
        } catch (e: Exception) {
            Log.e("Kiosk", "Enable failed: ${e.message}")
        }
    }

    /** Temporary unlock — re-locks next time VAD comes to the front (if kiosk enabled). */
    fun exit(activity: Activity, showToast: Boolean = true) {
        if (!isDeviceOwner(activity)) return
        val dpm = dpm(activity)
        val admin = admin(activity)
        try {
            dpm.setStatusBarDisabled(admin, false)
            dpm.setKeyguardDisabled(admin, false)
            if (isLocked(activity)) activity.stopLockTask()
            if (showToast) {
                Toast.makeText(activity, "Kiosk unlocked — re-locks when VAD returns to the front", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("Kiosk", "Exit failed: ${e.message}")
        }
    }

    /** Unlock and jump straight into Android Settings (network changes etc.). */
    fun openAndroidSettings(activity: Activity) {
        exit(activity, showToast = false)
        activity.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Permanent release — removes Device Owner and home lock, no factory reset needed. */
    @Suppress("DEPRECATION")
    fun removeDeviceOwner(activity: Activity) {
        if (!isDeviceOwner(activity)) return
        exit(activity, showToast = false)
        val dpm = dpm(activity)
        val admin = admin(activity)
        try {
            dpm.clearPackagePersistentPreferredActivities(admin, activity.packageName)
            dpm.setLockTaskPackages(admin, emptyArray())
            dpm.clearDeviceOwnerApp(activity.packageName)
            Toast.makeText(activity, "Device Owner removed — kiosk disabled", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("Kiosk", "Remove Device Owner failed: ${e.message}")
        }
    }
}