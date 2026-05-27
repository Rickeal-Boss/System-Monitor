package com.example.deviceinfoviewer.data.source

import android.content.Context
import android.location.GnssStatus
import android.location.GpsSatellite
import android.location.GpsStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper

import com.example.deviceinfoviewer.data.model.GpsSatelliteInfo
import com.example.deviceinfoviewer.data.model.GpsStatusInfo

/**
 * GPS 数据源，兼容 API 21-23 GpsStatus.Listener 和 API 24+ GnssStatus.Callback
 */
class GpsDataSource(private val context: Context) {

    private val appContext = context.applicationContext
    private val locationManager: LocationManager? = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private var listening = false
    private var locationListener: LocationListener? = null
    private var gnssCallback: GnssStatus.Callback? = null
    private var gpsListener: GpsStatus.Listener? = null

    fun interface GpsCallback {
        fun onGpsStatusUpdate(statusInfo: GpsStatusInfo)
    }

    /**
     * 开始监听 GPS 状态
     */
    @Suppress("MissingPermission")
    fun startListening(callback: GpsCallback) {
        val lm = locationManager ?: return
        if (listening) return
        listening = true

        try {
            // 创建共享的 LocationListener（保存为成员变量以便 stopListening 移除）
            locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    val info = GpsStatusInfo()
                    info.isGpsEnabled = true
                    info.isFixAcquired = true
                    info.latitude = location.latitude
                    info.longitude = location.longitude
                    info.accuracy = location.accuracy
                    callback.onGpsStatusUpdate(info)
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // API 24+ 使用 GnssStatus.Callback
                gnssCallback = object : GnssStatus.Callback() {
                    override fun onSatelliteStatusChanged(status: GnssStatus) {
                        val info = GpsStatusInfo()
                        info.isGpsEnabled = true
                        info.satelliteCount = status.satelliteCount
                        val satellites = mutableListOf<GpsSatelliteInfo>()
                        var usedCount = 0
                        for (i in 0 until status.satelliteCount) {
                            val sat = GpsSatelliteInfo()
                            val svid = status.getSvid(i)
                            val constellation = status.getConstellationType(i)
                            sat.prn = getStandardPrn(svid, constellation)
                            sat.constellation = getConstellationName(constellation)
                            sat.snr = status.getCn0DbHz(i)
                            sat.elevation = status.getElevationDegrees(i)
                            sat.azimuth = status.getAzimuthDegrees(i)
                            sat.isUsedInFix = status.usedInFix(i)
                            if (status.usedInFix(i)) usedCount++
                            satellites.add(sat)
                        }
                        info.satellites = satellites
                        info.isFixAcquired = usedCount > 0
                        callback.onGpsStatusUpdate(info)
                    }
                }
                lm.registerGnssStatusCallback(gnssCallback!!, null)
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1000L, 0f,
                    locationListener!!, Looper.getMainLooper()
                )
            } else {
                // API 21-23 使用 GpsStatus.Listener
                gpsListener = GpsStatus.Listener { event ->
                    try {
                        @Suppress("DEPRECATION")
                        val gpsStatus = lm.getGpsStatus(null) ?: return@Listener

                        val info = GpsStatusInfo()
                        info.isGpsEnabled = true
                        info.satelliteCount = gpsStatus.maxSatellites

                        val satellites = mutableListOf<GpsSatelliteInfo>()
                        var usedCount = 0
                        @Suppress("DEPRECATION")
                        val iterable: Iterable<GpsSatellite> = gpsStatus.satellites
                        for (sat in iterable) {
                            val si = GpsSatelliteInfo()
                            si.prn = sat.prn
                            si.snr = sat.snr
                            si.elevation = sat.elevation
                            si.azimuth = sat.azimuth
                            si.isUsedInFix = sat.usedInFix()
                            if (sat.usedInFix()) usedCount++
                            satellites.add(si)
                        }
                        info.satellites = satellites
                        info.isFixAcquired = usedCount > 0
                        callback.onGpsStatusUpdate(info)
                    } catch (_: Exception) {}
                }
                lm.addGpsStatusListener(gpsListener!!)
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1000L, 0f,
                    locationListener!!, Looper.getMainLooper()
                )
            }
        } catch (_: SecurityException) {
            // 权限未授予
            val info = GpsStatusInfo()
            info.isGpsEnabled = false
            callback.onGpsStatusUpdate(info)
        }
    }

    fun stopListening() {
        listening = false
        locationManager?.let { lm ->
            try {
                // 移除 GnssStatus.Callback
                if (gnssCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    lm.unregisterGnssStatusCallback(gnssCallback!!)
                    gnssCallback = null
                }
                // 移除 GpsStatus.Listener
                if (gpsListener != null) {
                    lm.removeGpsStatusListener(gpsListener!!)
                    gpsListener = null
                }
                // 移除 LocationListener
                if (locationListener != null) {
                    lm.removeUpdates(locationListener!!)
                    locationListener = null
                }
            } catch (_: Exception) {}
        }
    }

    private fun getConstellationName(type: Int): String = when (type) {
        GnssStatus.CONSTELLATION_GPS -> "GPS"
        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
        GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
        GnssStatus.CONSTELLATION_QZSS -> "QZSS"
        GnssStatus.CONSTELLATION_BEIDOU -> "BEIDOU"
        GnssStatus.CONSTELLATION_GALILEO -> "GALILEO"
        GnssStatus.CONSTELLATION_IRNSS -> "IRNSS"
        else -> "UNKNOWN"
    }

    /** SVID → 标准 PRN 映射 */
    private fun getStandardPrn(svid: Int, constellationType: Int): Int = when (constellationType) {
        GnssStatus.CONSTELLATION_BEIDOU -> svid + 200
        GnssStatus.CONSTELLATION_GLONASS -> svid + 64
        GnssStatus.CONSTELLATION_QZSS -> svid + 192
        else -> svid // GPS/SBAS/Galileo/IRNSS: SVID = PRN
    }
}
