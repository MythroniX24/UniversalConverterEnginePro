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
        createNotificationChannels()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val conversionChannel = NotificationChannel(
                CHANNEL_CONVERSION,
                "Conversion Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of file conversions"
                setShowBadge(false)
            }

            val completionChannel = NotificationChannel(
                CHANNEL_COMPLETE,
                "Conversion Complete",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when conversion is complete"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(conversionChannel)
            manager.createNotificationChannel(completionChannel)
        }
    }

    companion object {
        const val CHANNEL_CONVERSION = "channel_conversion"
        const val CHANNEL_COMPLETE   = "channel_complete"
    }
}
