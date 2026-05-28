package com.miaoshroom.ndjy

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    // 允许通知后再开始回城，主要是第一次得允许通知权限x
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        startReturnAndClose()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startReturnIfNotificationPermissionReady()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startReturnIfNotificationPermissionReady()
    }

    private fun startReturnIfNotificationPermissionReady() {
        // 听说 Android 13 起需要先请求通知权限，不知道，ai说的
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        startReturnAndClose()
    }

    private fun startReturnAndClose() {
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
        // 通知按钮用这个动作打断回城
        if (intent?.action == ACTION_CANCEL_RETURN) {
            release()
            stopSelf()
            return START_NOT_STICKY
        }

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
        // 给通知栏的取消按钮发送打断指令
        val cancelIntent = Intent(this, SoundPlaybackService::class.java)
            .setAction(ACTION_CANCEL_RETURN)
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cancelAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            getString(R.string.cancel_return_action),
            cancelPendingIntent,
        ).build()

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.playing_notification_text))
            .setContentIntent(pendingIntent)
            .addAction(cancelAction)
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
        private const val ACTION_CANCEL_RETURN = "com.miaoshroom.ndjy.action.CANCEL_RETURN"
        private const val CHANNEL_ID = "sound_playback"
        private const val NOTIFICATION_ID = 1

        fun restart(context: Context) {
            val intent = Intent(context, SoundPlaybackService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
