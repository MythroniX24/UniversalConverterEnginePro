package com.universalconverter.pro.premium

import android.content.Context
import androidx.preference.PreferenceManager

object PremiumManager {

    private const val KEY_IS_PREMIUM = "pref_is_premium"
    private const val KEY_CONVERSIONS_TODAY = "pref_conversions_today"
    private const val KEY_LAST_RESET_DATE   = "pref_last_reset_date"

    private const val FREE_DAILY_LIMIT = 10

    fun isPremium(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(KEY_IS_PREMIUM, false)
    }

    fun setPremium(context: Context, premium: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean(KEY_IS_PREMIUM, premium).apply()
    }

    fun canConvert(context: Context): Boolean {
        if (isPremium(context)) return true
        return getDailyConversionsUsed(context) < FREE_DAILY_LIMIT
    }

    fun getRemainingFreeConversions(context: Context): Int {
        if (isPremium(context)) return Int.MAX_VALUE
        return maxOf(0, FREE_DAILY_LIMIT - getDailyConversionsUsed(context))
    }

    fun recordConversion(context: Context) {
        if (isPremium(context)) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val today = getCurrentDateString()
        val lastReset = prefs.getString(KEY_LAST_RESET_DATE, "")
        val count = if (lastReset == today) prefs.getInt(KEY_CONVERSIONS_TODAY, 0)
                    else 0
        prefs.edit()
            .putInt(KEY_CONVERSIONS_TODAY, count + 1)
            .putString(KEY_LAST_RESET_DATE, today)
            .apply()
    }

    private fun getDailyConversionsUsed(context: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val today = getCurrentDateString()
        val lastReset = prefs.getString(KEY_LAST_RESET_DATE, "")
        return if (lastReset == today) prefs.getInt(KEY_CONVERSIONS_TODAY, 0) else 0
    }

    private fun getCurrentDateString(): String {
        val cal = java.util.Calendar.getInstance()
        return "${cal.get(java.util.Calendar.YEAR)}-" +
               "${cal.get(java.util.Calendar.MONTH)}-" +
               "${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
    }

    data class PremiumFeature(
        val name: String,
        val description: String,
        val freeAllowed: Boolean
    )

    val features = listOf(
        PremiumFeature("Unlimited Conversions", "No daily limits", false),
        PremiumFeature("Batch Processing", "Convert multiple files at once", false),
        PremiumFeature("Target Size Control", "Set exact output file size", false),
        PremiumFeature("EXIF Removal", "Strip all metadata", true),
        PremiumFeature("High Quality Export", "Maximum quality output", true),
        PremiumFeature("PDF Merge/Split", "Combine and split PDF files", false),
        PremiumFeature("3D Model Viewer", "View OBJ and FBX files", true),
        PremiumFeature("Ad-Free", "No advertisements", false)
    )
}
