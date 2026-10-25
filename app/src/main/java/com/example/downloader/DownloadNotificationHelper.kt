package com.example.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class DownloadNotificationHelper(private val context: Context) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                "Sakura Music İndirmeleri",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Aktif şarkı ve medya indirme ilerlemesi"
                setShowBadge(false)
            }

            val completeChannel = NotificationChannel(
                CHANNEL_COMPLETE_ID,
                "İndirme Durum Bildirimleri",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Tamamlanan veya başarısız olan indirme bildirimleri"
            }

            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(completeChannel)
        }
    }

    fun buildProgressNotification(
        title: String,
        progressPercent: Int,
        statusLine: String,
        indeterminate: Boolean = false
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, DownloadService::class.java).apply {
                action = DownloadService.ACTION_CANCEL_DOWNLOAD
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title.ifBlank { "Sakura Music İndiriliyor" })
            .setContentText(statusLine)
            .setProgress(100, progressPercent.coerceIn(0, 100), indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "İptal",
                cancelIntent
            )
            .build()
    }

    fun notifyProgress(
        title: String,
        progressPercent: Int,
        statusLine: String,
        indeterminate: Boolean = false
    ) {
        try {
            notificationManager.notify(
                PROGRESS_NOTIFICATION_ID,
                buildProgressNotification(title, progressPercent, statusLine, indeterminate)
            )
        } catch (_: Exception) {
        }
    }

    fun notifyCompleted(title: String, artist: String) {
        try {
            notificationManager.cancel(PROGRESS_NOTIFICATION_ID)
            val openAppIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETE_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("İndirme Tamamlandı")
                .setContentText("$artist - $title")
                .setAutoCancel(true)
                .setContentIntent(openAppIntent)
                .build()
            notificationManager.notify(COMPLETE_NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }
    }

    fun notifyError(title: String, reason: String) {
        try {
            notificationManager.cancel(PROGRESS_NOTIFICATION_ID)
            val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETE_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("İndirme Başarısız: $title")
                .setContentText(reason)
                .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
                .setAutoCancel(true)
                .build()
            notificationManager.notify(COMPLETE_NOTIFICATION_ID + 1, notification)
        } catch (_: Exception) {
        }
    }

    fun cancelProgressNotification() {
        try {
            notificationManager.cancel(PROGRESS_NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val CHANNEL_PROGRESS_ID = "sakura_download_progress_channel"
        const val CHANNEL_COMPLETE_ID = "sakura_download_complete_channel"
        const val PROGRESS_NOTIFICATION_ID = 5201
        const val COMPLETE_NOTIFICATION_ID = 5202
    }
}
