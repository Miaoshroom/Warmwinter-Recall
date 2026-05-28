package com.miaoshroom.ndjy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoundPlaybackService.restart(this)
        closeWithoutAnimation()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SoundPlaybackService.restart(this)
        closeWithoutAnimation()
    }

    private fun closeWithoutAnimation() {
        finishAndRemoveTask()
        overridePendingTransition(0, 0)
    }
}

class SoundPlaybackService : Service() {
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForegroundService()
        restartSound()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun restartSound() {
        release()

        val player = MediaPlayer.create(applicationContext, R.raw.ndjy)
        if (player == null) {
            stopSelf()
            return
        }

        mediaPlayer = player
        player.setOnCompletionListener {
            if (mediaPlayer === player) {
                release()
                returnToHomeScreen()
                stopSelf()
            }
        }
        player.setOnErrorListener { _, _, _ ->
            if (mediaPlayer === player) {
                release()
                stopSelf()
            }
            true
        }

        runCatching { player.start() }
            .onFailure {
                if (mediaPlayer === player) {
                    release()
                    stopSelf()
                }
            }
    }

    private fun startAsForegroundService() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.playing_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.playback_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun release() {
        val player = mediaPlayer ?: return
        mediaPlayer = null
        player.setOnCompletionListener(null)
        player.setOnErrorListener(null)
        runCatching {
            if (player.isPlaying) {
                player.stop()
            }
        }
        player.release()
    }

    // 回城之后能回到主屏幕很合理吧（
    private fun returnToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { startActivity(homeIntent) }
    }

    companion object {
        private const val CHANNEL_ID = "sound_playback"
        private const val NOTIFICATION_ID = 1

        fun restart(context: Context) {
            val intent = Intent(context, SoundPlaybackService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
