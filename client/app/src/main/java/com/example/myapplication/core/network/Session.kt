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

    /** init() 호출 보장 — prefs가 null이면 명시적 예외 */
    private fun requirePrefs(): SharedPreferences =
        prefs ?: throw IllegalStateException(
            "Session.init(context) must be called before accessing Session. " +
            "Check that NungilApplication.onCreate() calls Session.init(this)."
        )

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
        // dndStart <= dndEnd : 같은 날 구간 (예: 08~22시)
        // dndStart >  dndEnd : 자정 걸침  (예: 22~08시) → 별도 처리
        return if (dndStart <= dndEnd) now in dndStart..dndEnd
        else now >= dndStart || now <= dndEnd
    }

    val isInitialized: Boolean get() = prefs != null

    fun hasRole() = role.isNotEmpty()
    fun isLoggedIn() = (prefs?.contains(KEY_ID) == true) && guardianId.isNotEmpty()

    /** 여러 값을 하나의 edit/apply 트랜잭션으로 저장 */
    fun saveAll(block: SessionEditor.() -> Unit) {
        val editor = prefs?.edit() ?: return
        SessionEditor(editor).block()
        editor.apply()
    }

    /** saveAll 블록 내에서만 사용하는 DSL 래퍼
     *  nested class는 Session 의 private const 에 접근 불가 → 키 문자열 직접 사용 */
    class SessionEditor(private val editor: android.content.SharedPreferences.Editor) {
        fun guardianId(v: String)            { editor.putString("guardianId",       v) }
        fun userIdx(v: Int)                  { editor.putInt("userIdx",             v) }
        fun guardianName(v: String)          { editor.putString("guardianName",     v) }
        fun isOnboarded(v: Boolean)          { editor.putBoolean("isOnboarded",     v) }
        fun role(v: String)                  { editor.putString("role",             v) }
        fun notifScheduleEnabled(v: Boolean) { editor.putBoolean("notif_schedule",  v) }
        fun notifRepeatEnabled(v: Boolean)   { editor.putBoolean("notif_repeat",    v) }
        fun dndEnabled(v: Boolean)           { editor.putBoolean("dnd_enabled",     v) }
        fun dndStart(v: Int)                 { editor.putInt("dnd_start",           v) }
        fun dndEnd(v: Int)                   { editor.putInt("dnd_end",             v) }
    }

    fun logout() {
        val savedRole = role
        prefs?.edit()?.clear()?.apply()
        role = savedRole
    }
}
