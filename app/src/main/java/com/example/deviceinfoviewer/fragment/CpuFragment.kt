package com.example.deviceinfoviewer.fragment

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.deviceinfoviewer.DeviceApplication
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.data.model.CpuCoreInfo
import com.example.deviceinfoviewer.data.model.CpuInfo
import com.example.deviceinfoviewer.data.model.HistoryDataPoint
import com.example.deviceinfoviewer.data.repository.DeviceRepository
import com.example.deviceinfoviewer.widget.MonitorChartView
import java.util.Locale

/**
 * CPU Fragment — 芯片信息 + 温度图表 + Per Core 频率
 */
class CpuFragment : Fragment() {

    companion object {
        private const val TAG = "CpuFragment"
        private const val COLOR_CPU = 0xFFFF9800.toInt()
        private const val COLOR_TEXT_SECONDARY_DARK = 0xFF8B949E.toInt()
        private const val COLOR_TEXT_PRIMARY_DARK = 0xFFE6EDF3.toInt()
        private const val COLOR_BG_TAB_INACTIVE = 0xFF30363D.toInt()
    }

    private var repo: DeviceRepository? = null
    private var tvCpuModel: TextView? = null
    private var tvCpuSpec: TextView? = null
    private var tvTempStatus: TextView? = null
    private var chartCpuTemp: MonitorChartView? = null
    private var perCoreView: LinearLayout? = null
    private var handler: Handler? = null
    private var chartUpdater: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            inflater.inflate(R.layout.fragment_cpu, container, false)
        } catch (e: Exception) {
            Log.e(TAG, "onCreateView failed", e)
            TextView(context ?: inflater.context).apply {
                text = "CPU 页面加载失败"
                setTextColor(COLOR_TEXT_SECONDARY_DARK)
                setPadding(48, 48, 48, 48)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            repo = DeviceApplication.getDeviceRepository()

            tvCpuModel = view.findViewById(R.id.tv_cpu_model)
            tvCpuSpec = view.findViewById(R.id.tv_cpu_spec)
            tvTempStatus = view.findViewById(R.id.tv_temp_status)
            chartCpuTemp = view.findViewById(R.id.chart_cpu_temp)
            perCoreView = view.findViewById(R.id.per_core_view)

            chartCpuTemp?.apply {
                setChartColor(COLOR_CPU)
                setValueFormat("%.1f", "°C")
            }

            repo?.cpuLiveData?.observe(viewLifecycleOwner) { cpu ->
                cpu?.let {
                    updateCpuInfo(it)
                    updateTempStatus(it)
                    updateCoreViews(it)
                }
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

    private fun updateCpuInfo(cpu: CpuInfo) {
        val arch = cpu.architecture
        tvCpuModel?.text = arch.takeIf { it.isNotEmpty() } ?: "未知处理器"

        val spec = buildString {
            append(cpu.coreCount).append(" 核心")
            if (arch.isNotEmpty()) append(" · ").append(arch)
        }
        tvCpuSpec?.text = spec
    }

    private fun updateTempStatus(cpu: CpuInfo) {
        val tv = tvTempStatus ?: return
        val temp = cpu.temperatureCelsius
        if (temp.isNaN()) {
            tv.text = "未知"
            tv.setTextColor(COLOR_TEXT_SECONDARY_DARK)
        } else {
            tv.text = String.format(Locale.US, "%.1f°C", temp)
            tv.setTextColor(
                when {
                    temp < 45 -> 0xFF4CAF50.toInt()
                    temp < 60 -> 0xFFFF9800.toInt()
                    else -> 0xFFF44336.toInt()
                }
            )
        }
    }

    private fun updateCoreViews(cpu: CpuInfo) {
        val cores = cpu.cores
        if (cores.isNullOrEmpty()) return
        val ctx = context ?: return

        perCoreView?.let { pcv ->
            pcv.removeAllViews()
            val inflater = LayoutInflater.from(ctx)
            for (core in cores) {
                val item = inflater.inflate(R.layout.item_cpu_core_bar, pcv, false)

                item.findViewById<TextView>(R.id.tv_core_name)?.apply {
                    text = "核心 ${core.coreIndex}"
                    setTextColor(COLOR_TEXT_PRIMARY_DARK)
                }
                item.findViewById<TextView>(R.id.tv_core_freq)?.apply {
                    text = if (core.currentFreqKHz > 0)
                        String.format(Locale.US, "%.0f MHz", core.currentFreqKHz / 1000.0)
                    else "N/A"
                    setTextColor(COLOR_TEXT_SECONDARY_DARK)
                }
                item.findViewById<View>(R.id.view_core_bar_bg)?.apply {
                    setBackgroundColor(COLOR_BG_TAB_INACTIVE)
                }

                item.findViewById<View>(R.id.view_core_bar_fill)?.let { barFill ->
                    val ratio = if (core.maxFreqKHz > 0)
                        (core.currentFreqKHz.toFloat() / core.maxFreqKHz).coerceIn(0f, 1f)
                    else 0f
                    val maxW = (pcv.width - dpToPx(ctx, 160)).coerceAtLeast(dpToPx(ctx, 200))
                    barFill.layoutParams = barFill.layoutParams.apply { width = (maxW * ratio).toInt() }
                    barFill.setBackgroundColor(getFreqColor(core.currentFreqKHz))
                }
                pcv.addView(item)
            }
        }
    }

    private fun updateCharts() {
        val r = repo ?: return
        val data = r.historyCache.getSeries("cpu_temp")
        if (data.isNotEmpty()) chartCpuTemp?.setData(data)
    }

    private fun getFreqColor(khz: Long): Int = when {
        khz <= 0 -> COLOR_TEXT_SECONDARY_DARK
        khz < 1_500_000L -> 0xFF4CAF50.toInt()
        khz < 2_500_000L -> 0xFFFFC107.toInt()
        else -> 0xFFF44336.toInt()
    }

    private fun dpToPx(ctx: Context, dp: Int): Int =
        (dp * ctx.resources.displayMetrics.density).toInt()
}
