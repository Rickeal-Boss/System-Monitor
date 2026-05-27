package com.example.deviceinfoviewer.data.model

/**
 * GPU 信息 — 增强版
 * 频率 / 温度 / 负载 / 调速器 / 频率范围
 */
data class GpuInfo(
    var model: String = "",
    var vendor: String = "",
    var renderer: String = "",              // OpenGL ES Renderer
    var frequencyKHz: Long = -1L,
    var minFreqKHz: Long = -1L,
    var maxFreqKHz: Long = -1L,
    var governor: String = "",
    var availableGovernors: String = "",
    var loadPercentage: Float = Float.NaN,  // GPU 使用率 (%)
    var temperatureCelsius: Float = Float.NaN,
    var timestamp: Long = 0L
)
