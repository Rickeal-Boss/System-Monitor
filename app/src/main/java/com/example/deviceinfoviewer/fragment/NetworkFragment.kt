package com.example.deviceinfoviewer.fragment

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.deviceinfoviewer.DeviceApplication
import com.example.deviceinfoviewer.FormatUtils
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.adapter.NetworkInterfaceAdapter
import com.example.deviceinfoviewer.data.model.GpsSatelliteInfo
import com.example.deviceinfoviewer.data.model.GpsStatusInfo
import com.example.deviceinfoviewer.data.model.MobileNetworkInfo
import com.example.deviceinfoviewer.data.model.WifiDetailInfo
import com.example.deviceinfoviewer.data.repository.DeviceRepository
import com.example.deviceinfoviewer.widget.MonitorChartView
import java.util.Locale

/**
 * Network Fragment — DevCheck Pro 风格：青色主题
 */
class NetworkFragment : Fragment() {

    companion object {
        private const val TAG = "NetworkFragment"
        private const val COLOR_NETWORK = 0xFF26C6DA.toInt()   // 网络青色
    }

    private var repo: DeviceRepository? = null
    private var tvWifiSsid: TextView? = null
    private var tvWifiSignal: TextView? = null
    private var tvWifiSpeed: TextView? = null
    private var tvWifiIp: TextView? = null
    private var tvMobileType: TextView? = null
    private var tvMobileOperator: TextView? = null
    private var tvMobileSignal: TextView? = null
    private var tvMobileRoaming: TextView? = null
    private var tvGpsEnabled: TextView? = null
    private var tvGpsSatellites: TextView? = null
    private var tvGpsCoord: TextView? = null
    private var chartNetActivity: MonitorChartView? = null
    private var recyclerSatellites: RecyclerView? = null
    private var recyclerNetInterfaces: RecyclerView? = null
    private var satelliteAdapter: SatelliteAdapter? = null
    private var netInterfaceAdapter: NetworkInterfaceAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            inflater.inflate(R.layout.fragment_network_new, container, false)
        } catch (e: Exception) {
            Log.e(TAG, "onCreateView failed", e)
            TextView(context ?: inflater.context).apply {
                text = "页面加载失败"
                setPadding(48, 48, 48, 48)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            repo = DeviceApplication.getDeviceRepository()
            tvWifiSsid = view.findViewById(R.id.tv_wifi_ssid)
            tvWifiSignal = view.findViewById(R.id.tv_wifi_signal)
            tvWifiSpeed = view.findViewById(R.id.tv_wifi_speed)
            tvWifiIp = view.findViewById(R.id.tv_wifi_ip)
            tvMobileType = view.findViewById(R.id.tv_mobile_type)
            tvMobileOperator = view.findViewById(R.id.tv_mobile_operator)
            tvMobileSignal = view.findViewById(R.id.tv_mobile_signal)
            tvMobileRoaming = view.findViewById(R.id.tv_mobile_roaming)
            tvGpsEnabled = view.findViewById(R.id.tv_gps_enabled)
            tvGpsSatellites = view.findViewById(R.id.tv_gps_satellites)
            tvGpsCoord = view.findViewById(R.id.tv_gps_coord)
            chartNetActivity = view.findViewById(R.id.chart_net_activity)
            recyclerSatellites = view.findViewById(R.id.recycler_satellites)
            recyclerNetInterfaces = view.findViewById(R.id.recycler_net_interfaces)

            // 网络青色主题图表
            chartNetActivity?.apply {
                setChartColor(COLOR_NETWORK)
                setValueFormat("%.0f", " KB/s")
            }

            recyclerSatellites?.apply {
                layoutManager = LinearLayoutManager(context)
                satelliteAdapter = SatelliteAdapter()
                adapter = satelliteAdapter
            }
            recyclerNetInterfaces?.apply {
                layoutManager = LinearLayoutManager(context)
                netInterfaceAdapter = NetworkInterfaceAdapter()
                adapter = netInterfaceAdapter
            }

            repo ?: return
            repo!!.getWifiLiveData().observe(viewLifecycleOwner) { updateWifi(it) }
            repo!!.getMobileNetworkLiveData().observe(viewLifecycleOwner) { updateMobile(it) }
            repo!!.getGpsLiveData().observe(viewLifecycleOwner) { updateGps(it) }
            repo!!.getNetworkInterfacesLiveData().observe(viewLifecycleOwner) { interfaces ->
                netInterfaceAdapter?.setInterfaces(interfaces)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onViewCreated failed", e)
        }
    }

    private fun updateWifi(wifi: WifiDetailInfo?) {
        wifi ?: return
        tvWifiSsid?.text = wifi.getSsid()?.takeIf { it.isNotEmpty() } ?: "未连接 WiFi"
        tvWifiSignal?.text = FormatUtils.formatDbm(wifi.getSignalDbm())
        tvWifiSpeed?.text = if (wifi.getLinkSpeedMbps() > 0) "${wifi.getLinkSpeedMbps()} Mbps" else "N/A"
        tvWifiIp?.text = wifi.getIpv4()?.takeIf { it.isNotEmpty() } ?: ""
    }

    private fun updateMobile(mobile: MobileNetworkInfo?) {
        mobile ?: return
        tvMobileType?.text = mobile.getNetworkType()?.takeIf { it.isNotEmpty() } ?: "N/A"
        tvMobileOperator?.text = mobile.getOperatorName()?.takeIf { it.isNotEmpty() } ?: "N/A"
        tvMobileSignal?.text = FormatUtils.formatDbm(mobile.getSignalStrengthDbm())
        tvMobileRoaming?.text = if (mobile.isRoaming()) "是" else "否"
    }

    private fun updateGps(gps: GpsStatusInfo?) {
        gps ?: return
        tvGpsEnabled?.text = if (gps.isGpsEnabled()) (if (gps.isFixAcquired()) "已定位" else "未定位") else "未启用"
        tvGpsSatellites?.text = gps.getSatelliteCount().toString()
        if (!gps.getLatitude().isNaN() && !gps.getLongitude().isNaN()) {
            tvGpsCoord?.text = String.format(Locale.US, "%.6f, %.6f", gps.getLatitude(), gps.getLongitude())
        }
        satelliteAdapter?.setSatellites(gps.getSatellites())
    }

    private class SatelliteAdapter : RecyclerView.Adapter<SatelliteAdapter.VH>() {
        private val satellites = mutableListOf<GpsSatelliteInfo>()

        fun setSatellites(s: List<GpsSatelliteInfo>?) {
            satellites.clear()
            if (s != null) satellites.addAll(s)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_satellite, parent, false))
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val sat = satellites[position]
            val con = sat.getConstellation()
            holder.tvSatFlag.text = getSymbol(con)
            holder.tvSatName.text =
                (if (sat.getPrn() >= 0) "PRN ${sat.getPrn()}" else "") +
                        (if (!con.isNullOrEmpty()) " | $con" else "")
            val d = StringBuilder()
            if (!sat.getSnr().isNaN()) d.append("SNR ").append(String.format(Locale.US, "%.0f", sat.getSnr())).append("dB")
            if (!sat.getElevation().isNaN()) d.append(" ").append(String.format(Locale.US, "%.0f", sat.getElevation())).append("°")
            holder.tvSatDetail.text = if (d.isNotEmpty()) d.toString() else "N/A"

            if (!sat.getSnr().isNaN()) {
                holder.tvSatSnr.text = String.format(Locale.US, "%.0fdB", sat.getSnr())
                val snr = sat.getSnr()
                val cr = when {
                    snr >= 35 -> R.color.status_good
                    snr >= 25 -> R.color.status_warning
                    else -> R.color.text_secondary
                }
                holder.tvSatSnr.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, cr)
                )
            } else {
                holder.tvSatSnr.text = "N/A"
            }
        }

        override fun getItemCount(): Int = satellites.size

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvSatFlag: TextView = itemView.findViewById(R.id.tv_sat_flag)
            val tvSatName: TextView = itemView.findViewById(R.id.tv_sat_name)
            val tvSatDetail: TextView = itemView.findViewById(R.id.tv_sat_detail)
            val tvSatSnr: TextView = itemView.findViewById(R.id.tv_sat_snr)
        }

        companion object {
            private fun getSymbol(c: String?): String = when (c?.uppercase()) {
                "GPS" -> "\uD83C\uDDFA\uD83C\uDDF8"
                "GLONASS" -> "\uD83C\uDDF7\uD83C\uDDFA"
                "BEIDOU" -> "\uD83C\uDDE8\uD83C\uDDF3"
                "GALILEO" -> "\uD83C\uDDEA\uD83C\uDDFA"
                else -> "\uD83D\uDEF0"
            }
        }
    }
}
