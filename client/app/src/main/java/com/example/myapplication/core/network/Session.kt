package com.example.myapplication.core.network

import android.content.Context
import android.content.SharedPreferences

object Session {
    private const val PREF_NAME = "nungil_session"
    private const val KEY_ID   = "guardianId"
    private const val KEY_IDX  = "userIdx"
    private const val KEY_NAME = "guardianName"
    private const val KEY_ONBOARDED = "isOnboarded"
    private const val KEY_ROLE = "role"

    const val ROLE_GUARDIAN = "guardian"
    const val ROLE_USER     = "user"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    var guardianId: String
        get() = prefs?.getString(KEY_ID, "") ?: ""
        set(v) { prefs?.edit()?.putString(KEY_ID, v)?.apply() }

    var userIdx: Int
        get() = prefs?.getInt(KEY_IDX, 0) ?: 0
        set(v) { prefs?.edit()?.putInt(KEY_IDX, v)?.apply() }

    var guardianName: String
        get() = prefs?.getString(KEY_NAME, "") ?: ""
        set(v) { prefs?.edit()?.putString(KEY_NAME, v)?.apply() }

    var isOnboarded: Boolean
        get() = prefs?.getBoolean(KEY_ONBOARDED, false) ?: false
        set(v) { prefs?.edit()?.putBoolean(KEY_ONBOARDED, v)?.apply() }

    var role: String
        get() = prefs?.getString(KEY_ROLE, "") ?: ""
        set(v) { prefs?.edit()?.putString(KEY_ROLE, v)?.apply() }

    private const val KEY_NOTIF_SCHEDULE       = "notif_schedule"
    private const val KEY_NOTIF_REPEAT         = "notif_repeat"
    private const val KEY_DND_ENABLED          = "dnd_enabled"
    private const val KEY_DND_START            = "dnd_start"
    private const val KEY_DND_END              = "dnd_end"

    var notifScheduleEnabled: Boolean
        get() = prefs?.getBoolean(KEY_NOTIF_SCHEDULE, true) ?: true
        set(v) { prefs?.edit()?.putBoolean(KEY_NOTIF_SCHEDULE, v)?.apply() }

    var notifRepeatEnabled: Boolean
        get() = prefs?.getBoolean(KEY_NOTIF_REPEAT, true) ?: true
        set(v) { prefs?.edit()?.putBoolean(KEY_NOTIF_REPEAT, v)?.apply() }

    var dndEnabled: Boolean
        get() = prefs?.getBoolean(KEY_DND_ENABLED, false) ?: false
        set(v) { prefs?.edit()?.putBoolean(KEY_DND_ENABLED, v)?.apply() }

    var dndStart: Int
        get() = prefs?.getInt(KEY_DND_START, 22) ?: 22
        set(v) { prefs?.edit()?.putInt(KEY_DND_START, v)?.apply() }

    var dndEnd: Int
        get() = prefs?.getInt(KEY_DND_END, 8) ?: 8
        set(v) { prefs?.edit()?.putInt(KEY_DND_END, v)?.apply() }

    fun isInDnd(): Boolean {
        if (!dndEnabled) return false
        val now = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return if (dndStart <= dndEnd) now in dndStart until dndEnd
        else now >= dndStart || now < dndEnd
    }

    fun hasRole() = role.isNotEmpty()
    fun isLoggedIn() = (prefs?.contains(KEY_ID) == true) && guardianId.isNotEmpty()

    fun logout() = prefs?.edit()?.clear()?.apply()
}
