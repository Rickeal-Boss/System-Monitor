package com.example.deviceinfoviewer.data.repository

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.example.deviceinfoviewer.data.model.*
import com.example.deviceinfoviewer.data.source.*
import kotlinx.coroutines.*

/**
 * 核心数据仓库 — Kotlin 协程驱动
 */
class DeviceRepository(context: Context) {

    companion object {
        const val DEFAULT_INTERVAL_MS = 2000L
    }

    // DataSources
    private val cpuDataSource = CpuDataSource()
    private val gpuDataSource = GpuDataSource()
    private val batteryDataSource = BatteryDataSource(context.applicationContext)
    private val memoryDataSource = MemoryDataSource()
    private val storageDataSource = StorageDataSource()
    private val wifiDataSource = WifiDataSource(context.applicationContext)
    private val mobileNetworkDataSource = MobileNetworkDataSource(context.applicationContext)
    private val networkInterfaceDataSource = NetworkInterfaceDataSource()
    private val gpsDataSource = GpsDataSource(context.applicationContext)
    private val sensorDataSource = SensorDataSource(context.applicationContext)
    private val systemDataSource = SystemDataSource()

    // History
    val historyCache = HistoryCache()

    // LiveData — 单向数据流 (只读视图)
    val cpuLiveData = MutableLiveData<CpuInfo>()
    val gpuLiveData = MutableLiveData<GpuInfo>()
    val batteryLiveData = MutableLiveData<BatteryInfo>()
    val memoryLiveData = MutableLiveData<MemoryInfo>()
    val storageLiveData = MutableLiveData<StorageInfo>()
    val wifiLiveData = MutableLiveData<WifiDetailInfo>()
    val mobileNetworkLiveData = MutableLiveData<MobileNetworkInfo>()
    val networkInterfacesLiveData = MutableLiveData<List<NetworkInterfaceInfo>>()
    val gpsLiveData = MutableLiveData<GpsStatusInfo>()
    val sensorsLiveData = MutableLiveData<List<SensorItemInfo>>()
    val systemLiveData = MutableLiveData<SystemInfo>()

    // Coroutine
    private var intervalMs = DEFAULT_INTERVAL_MS
    private var monitoringJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var monitoring = false

    /**
     * 启动后台数据采集（幂等）
     */
    fun startMonitoring(intervalMs: Long) {
        if (monitoring) return
        monitoring = true
        this.intervalMs = intervalMs

        monitoringJob = scope.launch {
            while (isActive && monitoring) {
                collectData()
                delay(intervalMs)
            }
        }

        gpsDataSource.startListening { gpsLiveData.postValue(it) }
    }

    fun stopMonitoring() {
        monitoring = false
        monitoringJob?.cancel()
        gpsDataSource.stopListening()
        historyCache.shutdown()
    }

    private suspend fun collectData() = withContext(Dispatchers.Default) {
        runCatching {
            val cpu = cpuDataSource.getCpuInfo()
            cpuLiveData.postValue(cpu)

            if (!cpu.temperatureCelsius.isNaN())
                historyCache.addPoint("cpu_temp", cpu.temperatureCelsius)

            val maxFreq = cpu.cores.maxOfOrNull { it.currentFreqKHz } ?: 0L
            if (maxFreq > 0) historyCache.addPoint("cpu_freq", maxFreq.toFloat())
        }

        runCatching { gpuLiveData.postValue(gpuDataSource.getGpuInfo()) }

        runCatching {
            val bat = batteryDataSource.getBatteryInfo()
            batteryLiveData.postValue(bat)
            if (!bat.temperatureCelsius.isNaN())
                historyCache.addPoint("battery_temp", bat.temperatureCelsius)
            if (bat.powerMilliwatts >= 0)
                historyCache.addPoint("battery_power", bat.powerMilliwatts.toFloat())
            if (bat.levelPercent >= 0)
                historyCache.addPoint("battery_level", bat.levelPercent.toFloat())
        }

        runCatching {
            val mem = memoryDataSource.getMemoryInfo()
            memoryLiveData.postValue(mem)
            if (mem.totalKB > 0) {
                val pct = mem.usedKB.toFloat() / mem.totalKB * 100f
                historyCache.addPoint("ram_usage", pct)
            }
        }

        runCatching { storageLiveData.postValue(storageDataSource.getStorageInfo()) }
        runCatching { wifiLiveData.postValue(wifiDataSource.getWifiDetail()) }
        runCatching { mobileNetworkLiveData.postValue(mobileNetworkDataSource.getMobileNetworkInfo()) }
        runCatching { networkInterfacesLiveData.postValue(networkInterfaceDataSource.getNetworkInterfaces()) }
    }

    fun loadStaticData() {
        scope.launch(Dispatchers.Default) {
            runCatching { systemLiveData.postValue(systemDataSource.getSystemInfo()) }
            runCatching { storageLiveData.postValue(storageDataSource.getStorageInfo()) }
            runCatching { sensorsLiveData.postValue(sensorDataSource.getAllSensors()) }
        }
    }

    fun setIntervalMs(ms: Long) {
        if (ms != intervalMs) {
            stopMonitoring()
            startMonitoring(ms)
        }
    }

    fun getIntervalMs(): Long = intervalMs
}
