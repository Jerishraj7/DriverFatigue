package com.example.driverfatiguedetection

import android.content.Context


//singleton it used only one instance
object Prefs {
    private const val FILE = "fatigue_prefs"
    private const val KEY_EMERGENCY = "emergency_number"

    fun setEmergencyNumber(ctx: Context, number: String) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_EMERGENCY, number).apply()

    fun getEmergencyNumber(ctx: Context): String? =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_EMERGENCY, null)
}
