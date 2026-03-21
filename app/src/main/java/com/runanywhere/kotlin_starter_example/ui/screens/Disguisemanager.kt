package com.runanywhere.kotlin_starter_example.security

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Manages the App Disguise feature.
 *
 * When disguise is ENABLED  → "Calculator" alias shown on home screen, real alias hidden.
 * When disguise is DISABLED → Real app alias shown, calculator alias hidden.
 *
 * Alias names MUST match android:name in AndroidManifest.xml exactly.
 */
object DisguiseManager {

    private const val PREFS_NAME   = "disguise_prefs"
    private const val KEY_DISGUISE = "disguise_enabled"

    // Match exactly what is in AndroidManifest.xml
    private const val REAL_ALIAS       = "com.runanywhere.kotlin_starter_example.RealAppAlias"
    private const val CALCULATOR_ALIAS = "com.runanywhere.kotlin_starter_example.CalculatorAlias"

    fun isDisguiseEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DISGUISE, false)

    /**
     * Toggles the launcher icon between the real app and the "Calculator" disguise.
     * The home screen launcher may take a few seconds to reflect the change —
     * this is a normal Android behaviour.
     */
    fun setDisguiseEnabled(context: Context, enabled: Boolean) {
        // Persist the preference
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DISGUISE, enabled)
            .apply()

        val pm = context.packageManager
        if (enabled) {
            // Show Calculator alias, hide real alias
            setComponent(pm, context, CALCULATOR_ALIAS, true)
            setComponent(pm, context, REAL_ALIAS, false)
        } else {
            // Show real alias, hide Calculator alias
            setComponent(pm, context, REAL_ALIAS, true)
            setComponent(pm, context, CALCULATOR_ALIAS, false)
        }
    }

    private fun setComponent(pm: PackageManager, context: Context, name: String, enable: Boolean) {
        val state = if (enable)
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        try {
            pm.setComponentEnabledSetting(
                ComponentName(context, name),
                state,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}