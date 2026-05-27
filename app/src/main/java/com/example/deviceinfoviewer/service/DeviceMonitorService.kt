package com.example.deviceinfoviewer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.deviceinfoviewer.DeviceApplication
import com.example.deviceinfoviewer.MainActivity
import com.example.deviceinfoviewer.R

/**
 * 前台服务，后台持有 DeviceRepository 进行持续数据采集
 */
class DeviceMonitorService : Service() {

    companion object {
        private const val CHANNEL_ID = "device_monitor_channel"
        private const val NOTIFICATION_ID = 1001
    }

    var repository = DeviceApplication.getDeviceRepository()
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Android 13+ 请求通知权限（前台服务必需）
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("DeviceMonitorService", "POST_NOTIFICATIONS permission not granted")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationIntent = Intent(this, MainActivity::class.java)
        var pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags = pendingFlags or PendingIntent.FLAG_IMMUTABLE
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, pendingFlags
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("设备信息监控")
            .setContentText("正在后台监控设备信息")
            .setSmallIcon(R.drawable.ic_dashboard)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        // API 34+ 需指定 foregroundServiceType
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 获取全局 Repository 单例并开始采集
        repository?.startMonitoring(2000)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        repository?.stopMonitoring()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "设备监控",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "设备信息后台监控通知"
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
