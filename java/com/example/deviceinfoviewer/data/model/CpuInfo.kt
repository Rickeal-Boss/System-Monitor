package com.example.deviceinfoviewer.data.model

/**
 * CPU 整体信息
 */
data class CpuInfo(
    var architecture: String = "",
    var coreCount: Int = 0,
    var cores: MutableList<CpuCoreInfo> = mutableListOf(),
    var temperatureCelsius: Float = Float.NaN,
    var timestamp: Long = 0L
)
