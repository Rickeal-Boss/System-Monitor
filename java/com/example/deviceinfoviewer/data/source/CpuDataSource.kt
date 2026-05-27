package com.example.deviceinfoviewer.data.source

import com.example.deviceinfoviewer.data.model.CpuCoreInfo
import com.example.deviceinfoviewer.data.model.CpuInfo

/**
 * CPU 数据源，通过 sysfs 获取 CPU 频率和温度
 */
class CpuDataSource {

    fun getCpuInfo(): CpuInfo {
        val info = CpuInfo()
        info.architecture = System.getProperty("os.arch", "unknown") ?: "unknown"
        info.timestamp = System.currentTimeMillis()

        val cores = mutableListOf<CpuCoreInfo>()
        var coreIndex = 0
        while (true) {
            val cpuDir = CPU_BASE + "cpu$coreIndex/cpufreq/"
            if (!SysFsReader.fileExists(cpuDir)) {
                break
            }
            val core = CpuCoreInfo()
            core.coreIndex = coreIndex
            core.currentFreqKHz = SysFsReader.readLong(cpuDir + "scaling_cur_freq")
            core.maxFreqKHz = SysFsReader.readLong(cpuDir + "scaling_max_freq")
            core.minFreqKHz = SysFsReader.readLong(cpuDir + "scaling_min_freq")
            core.governor = SysFsReader.readLine(cpuDir + "scaling_governor")
            cores.add(core)
            coreIndex++
        }
        info.coreCount = cores.size
        info.cores = cores
        info.temperatureCelsius = getCpuTemperature()
        return info
    }

    /**
     * 获取 CPU 温度，扫描所有 thermal zone 查找 CPU 相关传感器
     */
    fun getCpuTemperature(): Float {
        // 扫描所有 thermal zone 查找匹配的传感器类型
        var zones = SysFsReader.listDir(THERMAL_BASE)
        for (zone in zones) {
            val typePath = THERMAL_BASE + zone + "/type"
            val type = SysFsReader.readLine(typePath)
            val tempPath = THERMAL_BASE + zone + "/temp"
            if (isCpuRelatedZone(type)) {
                val temp = SysFsReader.readFloat(tempPath)
                if (!temp.isNaN()) {
                    if (temp > 1000f) {
                        return temp / 1000f
                    }
                    return temp
                }
            }
        }

        // 回退：也搜索 /sys/devices/virtual/thermal/
        val virtualThermalBase = "/sys/devices/virtual/thermal/"
        zones = SysFsReader.listDir(virtualThermalBase)
        for (zone in zones) {
            val typePath = virtualThermalBase + zone + "/type"
            val type = SysFsReader.readLine(typePath)
            val tempPath = virtualThermalBase + zone + "/temp"
            if (isCpuRelatedZone(type)) {
                val temp = SysFsReader.readFloat(tempPath)
                if (!temp.isNaN()) {
                    if (temp > 1000f) {
                        return temp / 1000f
                    }
                    return temp
                }
            }
        }

        return Float.NaN
    }

    companion object {
        private const val CPU_BASE = "/sys/devices/system/cpu/"
        private const val THERMAL_BASE = "/sys/class/thermal/"

        /**
         * 判断 thermal zone type 是否与 CPU 相关
         */
        private fun isCpuRelatedZone(type: String?): Boolean {
            if (type.isNullOrEmpty()) return false
            val lower = type.lowercase()
            return lower.contains("cpu") || lower.contains("tsens") || lower.contains("soc")
                    || lower.contains("x86_pkg_temp") || lower.contains("acpitz")
                    || lower.contains("t-sen") || lower.contains("bcl")
                    || lower.contains("virtual") || lower.contains("ddr")
        }
    }
}
