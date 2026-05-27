package com.example.deviceinfoviewer.data.source

import android.content.Context
import android.net.DhcpInfo
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager

import com.example.deviceinfoviewer.data.model.WifiDetailInfo

import java.net.InetAddress
import java.net.UnknownHostException

/**
 * WiFi 数据源，通过 WifiManager 获取 WiFi 详细信息
 */
class WifiDataSource(private val context: Context) {

    private val appContext = context.applicationContext

    @Suppress("MissingPermission")
    fun getWifiDetail(): WifiDetailInfo {
        val info = WifiDetailInfo()

        val wm = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return info

        val wifiInfo: WifiInfo = wm.connectionInfo ?: return info

        info.ssid = wifiInfo.ssid.replace("\"", "")
        info.bssid = wifiInfo.bssid
        info.signalDbm = wifiInfo.rssi
        info.linkSpeedMbps = wifiInfo.linkSpeed
        info.macAddress = wifiInfo.macAddress

        // IPv4 地址
        val ipInt = wifiInfo.ipAddress
        if (ipInt != 0) {
            info.ipv4 = formatIp(ipInt)
        }

        // DHCP 信息（网关、DNS、子网掩码）
        val dhcp: DhcpInfo = wm.dhcpInfo ?: return info
        info.gateway = formatIp(dhcp.gateway)
        info.dns = formatIp(dhcp.dns1)
        info.subnetMask = formatIp(dhcp.netmask)

        return info
    }

    private fun formatIp(ip: Int): String {
        if (ip == 0) {
            return ""
        }
        return try {
            val bytes = byteArrayOf(
                (ip and 0xFF).toByte(),
                ((ip shr 8) and 0xFF).toByte(),
                ((ip shr 16) and 0xFF).toByte(),
                ((ip shr 24) and 0xFF).toByte()
            )
            InetAddress.getByAddress(bytes).hostAddress ?: ""
        } catch (_: UnknownHostException) {
            ""
        }
    }
}
