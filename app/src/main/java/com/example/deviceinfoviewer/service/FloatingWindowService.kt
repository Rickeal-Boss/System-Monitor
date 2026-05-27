package com.example.deviceinfoviewer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import com.example.deviceinfoviewer.AppSettings
import com.example.deviceinfoviewer.DeviceApplication
import com.example.deviceinfoviewer.FormatUtils
import com.example.deviceinfoviewer.MainActivity
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.data.model.CpuCoreInfo

/**
 * 悬浮窗前台 Service，在所有应用上方显示设备信息
 */
class FloatingWindowService : Service() {

    companion object {
        private const val CHANNEL_ID = "floating_window_channel"
        private const val NOTIFICATION_ID = 2001
        private const val LONG_PRESS_DURATION = 500L
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var repository = DeviceApplication.getDeviceRepository()
    private lateinit var settings: AppSettings
    private lateinit var handler: Handler
    private var refreshRunnable: Runnable? = null

    private var tvCpuTemp: TextView? = null
    private var tvCpuFreq: TextView? = null
    private var tvBattery: TextView? = null
    private var tvRam: TextView? = null

    // 拖拽相关
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialWindowX = 0
    private var initialWindowY = 0
    private var isLongPress = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Android 13+ 请求通知权限（前台服务必需）
        if (Build.VERSION.SDK_INT >= 33) {
            requestNotificationPermission()
        }

        settings = AppSettings.getInstance(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        handler = Handler(Looper.getMainLooper())

        // 启动前台通知
        showForegroundNotification()

        // 创建悬浮窗视图
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_window, null)

        tvCpuTemp = floatingView?.findViewById(R.id.tv_float_cpu_temp)
        tvCpuFreq = floatingView?.findViewById(R.id.tv_float_cpu_freq)
        tvBattery = floatingView?.findViewById(R.id.tv_float_battery)
        tvRam = floatingView?.findViewById(R.id.tv_float_ram)

        // 初始占位文本
        tvCpuTemp?.text = "CPU: 加载中..."
        tvCpuFreq?.text = "频率: -- MHz"
        tvBattery?.text = "电池: --%"
        tvRam?.text = "RAM: --%"

        // 设置透明度
        floatingView?.alpha = settings.floatingWindowOpacity

        // 拖拽处理
        floatingView?.setOnTouchListener { v, event ->
            val longPressHandler = Handler(Looper.getMainLooper())
            var longPressRunnable: Runnable? = null

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    val paramsDown = v.layoutParams as WindowManager.LayoutParams
                    initialWindowX = paramsDown.x
                    initialWindowY = paramsDown.y
                    isLongPress = false

                    longPressRunnable = Runnable {
                        isLongPress = true
                        showSettingsDialog()
                    }
                    longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_DURATION)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        val params = v.layoutParams as WindowManager.LayoutParams
                        params.x = (initialWindowX + deltaX).toInt()
                        params.y = (initialWindowY + deltaY).toInt()
                        windowManager?.updateViewLayout(v, params)

                        // 保存位置
                        settings.floatingWindowX = params.x
                        settings.floatingWindowY = params.y
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    true
                }

                else -> false
            }
        }

        // 添加到 WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        // 恢复上次位置或默认右上角
        val savedX = settings.floatingWindowX
        val savedY = settings.floatingWindowY
        if (savedX >= 0 && savedY >= 0) {
            params.x = savedX
            params.y = savedY
        } else {
            val screenWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowManager?.currentWindowMetrics?.bounds?.width() ?: 1080
            } else {
                windowManager?.defaultDisplay?.width ?: 1080
            }
            params.x = screenWidth - 200
            params.y = 100
        }

        floatingView?.let { windowManager?.addView(it, params) }

        // 立即刷新 + 延迟三次重试确保数据到达
        refreshData()
        handler.postDelayed({ refreshData() }, 800)
        handler.postDelayed({ refreshData() }, 1600)
        handler.postDelayed({ refreshData() }, 2400)

        // 定时刷新
        startRefresh()
    }

    private fun getWindowType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        WindowManager.LayoutParams.TYPE_PHONE
    }

    private fun startRefresh() {
        refreshRunnable = object : Runnable {
            override fun run() {
                refreshData()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(refreshRunnable!!)
    }

    private fun refreshData() {
        val repo = repository ?: return

        // CPU 温度
        val cpu = repo.cpuLiveData.value
        if (cpu != null) {
            tvCpuTemp?.text = "CPU: ${FormatUtils.formatTempCelsius(cpu.temperatureCelsius)}"

            val maxFreq = cpu.cores.maxOfOrNull { it.currentFreqKHz } ?: 0L
            tvCpuFreq?.text = "频率: ${FormatUtils.formatFreq(maxFreq)}"
        }

        // 电池（使用新字段）
        val battery = repo.batteryLiveData.value
        if (battery != null) {
            val sb = StringBuilder()
            sb.append("电池: ${FormatUtils.formatPercent(battery.levelPercent)}")
            // 温度
            if (!battery.temperatureCelsius.isNaN()) {
                sb.append(" ${FormatUtils.formatTempCelsius(battery.temperatureCelsius)}")
            }
            // 充电功率
            val chargingMw = battery.chargingPowerMw
            if (chargingMw > 0) {
                sb.append(" ⚡${chargingMw}mW")
            }
            tvBattery?.text = sb.toString()
        }

        // RAM
        val memory = repo.memoryLiveData.value
        if (memory != null && memory.totalKB > 0) {
            val usagePct = (memory.usedKB.toFloat() / memory.totalKB * 100).toInt()
            tvRam?.text = "RAM: ${FormatUtils.formatPercent(usagePct)}"
        }
    }

    private fun showSettingsDialog() {
        val builder = AlertDialog.Builder(this)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)

        val seekBar = dialogView.findViewById<SeekBar>(R.id.seekbar_opacity)
        val tvOpacity = dialogView.findViewById<TextView>(R.id.tv_opacity_value)
        val cbCpuTemp = dialogView.findViewById<CheckBox>(R.id.cb_show_cpu_temp)
        val cbCpuFreq = dialogView.findViewById<CheckBox>(R.id.cb_show_cpu_freq)
        val cbBattery = dialogView.findViewById<CheckBox>(R.id.cb_show_battery)
        val cbRam = dialogView.findViewById<CheckBox>(R.id.cb_show_ram)

        seekBar.progress = (settings.floatingWindowOpacity * 100).toInt()
        tvOpacity.text = "${(settings.floatingWindowOpacity * 100).toInt()}%"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val alpha = progress / 100f
                floatingView?.alpha = alpha
                settings.floatingWindowOpacity = alpha
                tvOpacity.text = "$progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        cbCpuTemp.isChecked = tvCpuTemp?.visibility == View.VISIBLE
        cbCpuFreq.isChecked = tvCpuFreq?.visibility == View.VISIBLE
        cbBattery.isChecked = tvBattery?.visibility == View.VISIBLE
        cbRam.isChecked = tvRam?.visibility == View.VISIBLE

        cbCpuTemp.setOnCheckedChangeListener { _, checked ->
            tvCpuTemp?.visibility = if (checked) View.VISIBLE else View.GONE
        }
        cbCpuFreq.setOnCheckedChangeListener { _, checked ->
            tvCpuFreq?.visibility = if (checked) View.VISIBLE else View.GONE
        }
        cbBattery.setOnCheckedChangeListener { _, checked ->
            tvBattery?.visibility = if (checked) View.VISIBLE else View.GONE
        }
        cbRam.setOnCheckedChangeListener { _, checked ->
            tvRam?.visibility = if (checked) View.VISIBLE else View.GONE
        }

        builder.setView(dialogView)
            .setTitle("悬浮窗设置")
            .setPositiveButton("确定") { dialog, _ -> dialog.dismiss() }
            .setCancelable(true)

        // 使用 TYPE_APPLICATION_OVERLAY 的 WindowManager 来显示对话框
        val dialog = builder.create()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
        dialog.show()
    }

    private fun showForegroundNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        var pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags = pendingFlags or PendingIntent.FLAG_IMMUTABLE
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, pendingFlags
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("设备信息悬浮窗")
            .setContentText("正在显示设备信息悬浮窗")
            .setSmallIcon(R.drawable.ic_float_window)
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
    }

    override fun onDestroy() {
        super.onDestroy()
        refreshRunnable?.let { handler.removeCallbacks(it) }
        floatingView?.let { windowManager?.removeView(it) }
        repository?.stopMonitoring()
    }

    /**
     * Android 13+ 运行时请求通知权限
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                // Service 中无法直接弹出权限对话框，记录日志等待主 Activity 处理
                Log.w(
                    "FloatingWindowService",
                    "POST_NOTIFICATIONS permission not granted; foreground notification may fail"
                )
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮窗",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "设备信息悬浮窗通知"
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
