package com.universalconverter.pro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import androidx.work.WorkManager

class ConverterApplication : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        createChannels()
    }
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(NotificationChannel(CH_CONV, "Conversion", NotificationManager.IMPORTANCE_LOW))
            mgr.createNotificationChannel(NotificationChannel(CH_DONE, "Complete", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
    companion object {
        const val CH_CONV = "ch_conversion"
        const val CH_DONE = "ch_done"
    }
}
