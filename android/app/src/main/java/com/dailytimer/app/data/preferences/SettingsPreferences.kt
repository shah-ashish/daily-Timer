package com.dailytimer.app.data.preferences

import android.content.Context
import android.content.SharedPreferences

object SettingsPreferences {

    private const val PREFS_NAME = "daily_timer_settings"
    private const val KEY_REMINDER_LEAD_MINUTES = "reminder_lead_minutes"
    private const val DEFAULT_REMINDER_MINUTES = 10

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getReminderLeadMinutes(context: Context): Int {
        return getPrefs(context).getInt(KEY_REMINDER_LEAD_MINUTES, DEFAULT_REMINDER_MINUTES)
    }

    fun setReminderLeadMinutes(context: Context, minutes: Int) {
        val validMinutes = if (minutes > 0) minutes else DEFAULT_REMINDER_MINUTES
        getPrefs(context).edit().putInt(KEY_REMINDER_LEAD_MINUTES, validMinutes).apply()
    }
}
