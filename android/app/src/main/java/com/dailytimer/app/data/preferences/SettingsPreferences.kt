package com.dailytimer.app.data.preferences

import android.content.Context
import android.content.SharedPreferences

object SettingsPreferences {

    private const val PREFS_NAME = "daily_timer_settings"
    private const val KEY_REMINDER_LEAD_MINUTES = "reminder_lead_minutes"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val DEFAULT_REMINDER_MINUTES = 10

    const val MIN_LEAD_MINUTES = 1
    const val MAX_LEAD_MINUTES = 180

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getReminderLeadMinutes(context: Context): Int {
        return getPrefs(context).getInt(KEY_REMINDER_LEAD_MINUTES, DEFAULT_REMINDER_MINUTES)
    }

    fun setReminderLeadMinutes(context: Context, minutes: Int) {
        val validMinutes = minutes.coerceIn(MIN_LEAD_MINUTES, MAX_LEAD_MINUTES)
        getPrefs(context).edit().putInt(KEY_REMINDER_LEAD_MINUTES, validMinutes).apply()
    }

    fun getDarkModeOverride(context: Context): Boolean? {
        val prefs = getPrefs(context)
        return if (prefs.contains(KEY_DARK_MODE)) prefs.getBoolean(KEY_DARK_MODE, false) else null
    }

    fun setDarkModeOverride(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    fun clearDarkModeOverride(context: Context) {
        getPrefs(context).edit().remove(KEY_DARK_MODE).apply()
    }
}
