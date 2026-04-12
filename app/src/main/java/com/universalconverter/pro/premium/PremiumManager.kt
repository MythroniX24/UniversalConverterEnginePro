package com.universalconverter.pro.premium

import android.content.Context
import androidx.preference.PreferenceManager

object PremiumManager {
    private const val KEY_PREMIUM = "is_premium"
    private const val KEY_COUNT   = "daily_count"
    private const val KEY_DATE    = "last_date"
    const val FREE_LIMIT = 15

    fun isPremium(ctx: Context) = prefs(ctx).getBoolean(KEY_PREMIUM, false)
    fun setPremium(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_PREMIUM, v).apply()
    fun canConvert(ctx: Context) = isPremium(ctx) || dailyCount(ctx) < FREE_LIMIT
    fun remaining(ctx: Context) = if(isPremium(ctx)) Int.MAX_VALUE else maxOf(0, FREE_LIMIT - dailyCount(ctx))

    fun record(ctx: Context) {
        if (isPremium(ctx)) return
        val today = today()
        val p = prefs(ctx); val last = p.getString(KEY_DATE,"")
        val cnt = if(last == today) p.getInt(KEY_COUNT,0) else 0
        p.edit().putInt(KEY_COUNT, cnt+1).putString(KEY_DATE, today).apply()
    }

    private fun dailyCount(ctx: Context): Int {
        val p = prefs(ctx); val today = today()
        return if(p.getString(KEY_DATE,"") == today) p.getInt(KEY_COUNT,0) else 0
    }
    private fun prefs(ctx: Context) = PreferenceManager.getDefaultSharedPreferences(ctx)
    private fun today(): String { val c = java.util.Calendar.getInstance(); return "${c.get(java.util.Calendar.YEAR)}-${c.get(java.util.Calendar.DAY_OF_YEAR)}" }

    val features = listOf(
        Triple("Unlimited Conversions","No daily limit",false),
        Triple("Smart Compress AI","AI-powered quality optimization",false),
        Triple("Compression Racing","Best algorithm auto-selected",false),
        Triple("Target Size Enforcer","Hit exact file sizes",false),
        Triple("PDF All Tools","Password, watermark, split",false),
        Triple("Background Remover","Transparent PNG output",false),
        Triple("Image Upscaler","2x/4x quality upscaling",false),
        Triple("Cloud Integration","Google Drive & Dropbox",false),
        Triple("Privacy Scanner","Detect sensitive metadata",true),
        Triple("Conversion History","Track all conversions",true),
        Triple("SSIM Quality Score","Measure output quality",true),
        Triple("Ad-Free","No advertisements",false)
    )
}
