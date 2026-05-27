package com.example.deviceinfoviewer.data.model

data class WifiDetailInfo(
    var ssid: String = "",
    var bssid: String = "",
    var signalDbm: Int = Int.MIN_VALUE,
    var linkSpeedMbps: Int = -1,
    var ipv4: String = "",
    var ipv6: String = "",
    var macAddress: String = "",
    var gateway: String = "",
    var dns: String = "",
    var subnetMask: String = ""
)
