package com.example.deviceinfoviewer.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.deviceinfoviewer.DeviceApplication
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.data.model.BatteryInfo
import com.example.deviceinfoviewer.data.repository.DeviceRepository
import com.example.deviceinfoviewer.widget.MonitorChartView
import kotlin.math.abs
import kotlin.math.max

/**
 * Battery Fragment — DevCheck Pro 风格：绿色主题
 */
class BatteryFragment : Fragment() {

    companion object {
        private const val TAG = "BatteryFragment"
        private const val COLOR_BATTERY = 0xFF66BB6A.toInt()   // 电池绿色
    }

    private var repo: DeviceRepository? = null
    private var tvBatteryStatus: TextView? = null
    private var tvBatteryPercent: TextView? = null
    private var tvCycleCount: TextView? = null
    private var tvCapacity: TextView? = null
    private var tvVoltage: TextView? = null
    private var tvCurrent: TextView? = null
    private var pbBattery: ProgressBar? = null
    private var chartPower: MonitorChartView? = null
    private var chartTemp: MonitorChartView? = null
    private var handler: Handler? = null
    private var chartUpdater: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            inflater.inflate(R.layout.fragment_battery_new, container, false)
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
            tvBatteryStatus = view.findViewById(R.id.tv_battery_status)
            tvBatteryPercent = view.findViewById(R.id.tv_battery_percent)
            tvCycleCount = view.findViewById(R.id.tv_cycle_count)
            tvCapacity = view.findViewById(R.id.tv_capacity)
            tvVoltage = view.findViewById(R.id.tv_voltage)
            tvCurrent = view.findViewById(R.id.tv_current)
            pbBattery = view.findViewById(R.id.pb_battery)
            chartPower = view.findViewById(R.id.chart_power)
            chartTemp = view.findViewById(R.id.chart_temp)

            // 电池绿色主题图表
            chartPower?.apply {
                setChartColor(COLOR_BATTERY)
                setValueFormat("%.0f", " mW")
            }
            chartTemp?.apply {
                setChartColor(COLOR_BATTERY)
                setValueFormat("%.1f", "°C")
            }

            repo ?: return

            repo!!.batteryLiveData.observe(viewLifecycleOwner) { bat ->
                bat?.let { updateBatteryInfo(it) }
            }

            handler = Handler(Looper.getMainLooper())
            chartUpdater = object : Runnable {
                override fun run() {
                    updateCharts()
                    handler?.postDelayed(this, 2000)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onViewCreated failed", e)
        }
    }

    override fun onResume() {
        super.onResume()
        handler?.post(chartUpdater!!)
    }

    override fun onPause() {
        super.onPause()
        chartUpdater?.let { handler?.removeCallbacks(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler?.removeCallbacksAndMessages(null)
        handler = null
    }

    private fun updateBatteryInfo(bat: BatteryInfo) {
        val level = bat.levelPercent
        tvBatteryPercent?.text = if (level >= 0) "$level%" else "N/A"
        pbBattery?.progress = max(0, level)

        val status = bat.chargeStatus
        tvBatteryStatus?.text = if (!status.isNullOrEmpty()) status else "未知"

        val cycles = bat.cycleCount
        tvCycleCount?.text = if (cycles > 0) "$cycles 次" else "N/A"

        val nowCap = if (bat.chargeFullMAh > 0) bat.chargeFullMAh else bat.capacityNowMAh
        tvCapacity?.text = if (nowCap > 0) "$nowCap mAh" else "N/A"

        val voltage = bat.effectiveVoltage
        tvVoltage?.text = if (voltage > 0) "$voltage mV" else "N/A"

        val currentUA = bat.currentNowUA
        tvCurrent?.text = when {
            currentUA > 0 -> String.format("+%.0f mA", currentUA / 1000.0)
            currentUA < 0 -> String.format("%.0f mA", abs(currentUA) / 1000.0)
            else -> "N/A"
        }
    }

    private fun updateCharts() {
        repo ?: return
        chartPower?.setData(repo!!.getHistoryCache().getSeries("battery_power"))
        chartTemp?.setData(repo!!.getHistoryCache().getSeries("battery_temp"))
    }
}
