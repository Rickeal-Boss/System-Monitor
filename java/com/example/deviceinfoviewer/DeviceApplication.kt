package com.example.deviceinfoviewer

import android.app.Application
import android.os.Build
import android.os.Process
import android.util.Log
import com.example.deviceinfoviewer.data.repository.DeviceRepository
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Application — 全局 Context、DeviceRepository 单例、崩溃日志
 */
class DeviceApplication : Application() {

    companion object {
        private const val TAG = "DeviceApp"

        @Volatile
        private var appContext: Application? = null

        @Volatile
        private var deviceRepository: DeviceRepository? = null

        val context: Application get() = appContext!!

        @Synchronized
        fun getDeviceRepository(): DeviceRepository? {
            if (deviceRepository == null && appContext != null) {
                deviceRepository = try {
                    DeviceRepository(appContext!!)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create DeviceRepository", e)
                    null
                }
            }
            return deviceRepository
        }
    }

    override fun onCreate() {
        super.onCreate()
        appContext = this

        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Log.e(TAG, "=== FATAL CRASH ===", e)
            Log.e(TAG, "Thread: ${t.name}")
            Log.e(TAG, "SDK: ${Build.VERSION.SDK_INT}")

            try {
                val sw = StringWriter()
                PrintWriter(sw).use { pw ->
                    pw.println("=== CRASH ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} ===")
                    pw.println("SDK=${Build.VERSION.SDK_INT}")
                    pw.println("Device=${Build.MODEL}")
                    e.printStackTrace(pw)
                }
                File(filesDir, "crash.log").appendText(sw.toString())
            } catch (_: Exception) {}

            oldHandler?.uncaughtException(t, e)
                ?: Process.killProcess(Process.myPid())
        }
    }
}
