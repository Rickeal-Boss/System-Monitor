package com.example.deviceinfoviewer.fragment

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.example.deviceinfoviewer.DeviceApplication
import com.example.deviceinfoviewer.FormatUtils
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.data.model.CpuCoreInfo
import com.example.deviceinfoviewer.data.model.CpuInfo
import com.example.deviceinfoviewer.data.model.HistoryDataPoint
import com.example.deviceinfoviewer.data.repository.DeviceRepository
import com.example.deviceinfoviewer.widget.MonitorChartView
import kotlin.math.abs
import kotlin.math.min

/**
 * CPU Fragment — DevCheck Pro 风格：芯片信息 + 温度图表 + Cluster/Per Core 核心频率
 */
class CpuFragment : Fragment() {

    companion object {
        private const val TAG = "CpuFragment"

        // DevCheck Pro CPU 橙色系
        private const val COLOR_CPU = 0xFFFF9800.toInt()
        private const val COLOR_CPU_DARK = 0xFFE65100.toInt()
        private const val COLOR_TEXT_SECONDARY_DARK = 0xFF8B949E.toInt()
        private const val COLOR_TEXT_PRIMARY_DARK = 0xFFE6EDF3.toInt()
        private const val COLOR_BG_TAB_INACTIVE = 0xFF30363D.toInt()
    }

    private var repo: DeviceRepository? = null
    private var tvCpuModel: TextView? = null
    private var tvCpuSpec: TextView? = null
    private var tvTempStatus: TextView? = null
    private var chartCpuTemp: MonitorChartView? = null
    private var tabCluster: TextView? = null
    private var tabPerCore: TextView? = null
    private var clusterView: LinearLayout? = null
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
            tabCluster = view.findViewById(R.id.tab_cluster)
            tabPerCore = view.findViewById(R.id.tab_per_core)
            clusterView = view.findViewById(R.id.cluster_view)
            perCoreView = view.findViewById(R.id.per_core_view)

            // 配置图表 — 使用 CPU 橙色
            chartCpuTemp?.apply {
                setChartColor(COLOR_CPU)
                setValueFormat("%.1f", "°C")
            }

            tabCluster?.setOnClickListener { switchToCluster() }
            tabPerCore?.setOnClickListener { switchToPerCore() }

            repo ?: return

            repo!!.cpuLiveData.observe(viewLifecycleOwner) { cpu ->
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
        val model = if (!arch.isNullOrEmpty()) arch else "未知处理器"
        tvCpuModel?.text = model

        val spec = buildString {
            append(cpu.coreCount).append(" 核心")
            if (!arch.isNullOrEmpty()) append(" · ").append(arch)
        }
        tvCpuSpec?.text = spec
    }

    private fun updateTempStatus(cpu: CpuInfo) {
        tvTempStatus ?: return
        val temp = cpu.temperatureCelsius
        if (temp.isNaN()) {
            tvTempStatus!!.text = "未知"
            tvTempStatus!!.setTextColor(COLOR_TEXT_SECONDARY_DARK)
        } else {
            tvTempStatus!!.text = String.format("%.1f°C", temp)
            tvTempStatus!!.setTextColor(
                when {
                    temp < 45 -> 0xFF4CAF50.toInt()
                    temp < 60 -> 0xFFFF9800.toInt()
                    else -> 0xFFF44336.toInt()
                }
            )
        }
    }

    private fun updateCoreViews(cpu: CpuInfo) {
        val cores = cpu.cores ?: return
        if (cores.isEmpty()) return
        val ctx = context ?: return

        val clusters = groupByCluster(cores)

        clusterView?.let { cv ->
            cv.removeAllViews()
            val inflater = LayoutInflater.from(ctx)
            for ((maxFreq, clusterCores) in clusters) {
                val item = inflater.inflate(R.layout.item_cpu_cluster, cv, false)

                item.findViewById<TextView>(R.id.tv_cluster_type)?.apply {
                    text = getClusterType(maxFreq)
                    setTextColor(COLOR_CPU)
                }
                item.findViewById<TextView>(R.id.tv_cluster_cores)?.apply {
                    text = "${clusterCores.size} 核心 · 最高 ${FormatUtils.formatFreq(maxFreq)}"
                    setTextColor(COLOR_TEXT_PRIMARY_DARK)
                }
                item.findViewById<TextView>(R.id.tv_cluster_freq)?.apply {
                    text = FormatUtils.formatFreq(getAvgFreq(clusterCores))
                    setTextColor(COLOR_CPU)
                }

                cv.addView(item)
            }
        }

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
                    text = FormatUtils.formatFreq(core.currentFreqKHz)
                    setTextColor(COLOR_TEXT_SECONDARY_DARK)
                }
                item.findViewById<View>(R.id.view_core_bar_bg)?.apply {
                    setBackgroundColor(COLOR_BG_TAB_INACTIVE)
                }

                item.findViewById<View>(R.id.view_core_bar_fill)?.let { barFill ->
                    var ratio = if (core.maxFreqKHz > 0)
                        core.currentFreqKHz.toFloat() / core.maxFreqKHz else 0f
                    ratio = min(ratio, 1.0f)
                    var maxW = pcv.width - dpToPx(160)
                    if (maxW <= 0) maxW = dpToPx(200)
                    val lp = barFill.layoutParams
                    lp.width = (maxW * ratio).toInt()
                    barFill.layoutParams = lp
                    barFill.setBackgroundColor(getFreqColor(core.currentFreqKHz))
                }
                pcv.addView(item)
            }
        }
    }

    private fun updateCharts() {
        if (repo == null || chartCpuTemp == null) return
        val data: List<HistoryDataPoint>? = repo!!.getHistoryCache().getSeries("cpu_temp")
        if (!data.isNullOrEmpty()) chartCpuTemp?.setData(data)
    }

    private fun switchToCluster() {
        tabCluster?.apply {
            setBackgroundResource(R.drawable.bg_tab_left)
            ViewCompat.setBackgroundTintList(this, ColorStateList.valueOf(COLOR_CPU))
            setTextColor(Color.WHITE)
        }
        tabPerCore?.apply {
            setBackgroundColor(COLOR_BG_TAB_INACTIVE)
            setTextColor(COLOR_TEXT_SECONDARY_DARK)
        }
        clusterView?.visibility = View.VISIBLE
        perCoreView?.visibility = View.GONE
    }

    private fun switchToPerCore() {
        tabPerCore?.apply {
            setBackgroundResource(R.drawable.bg_tab_left)
            ViewCompat.setBackgroundTintList(this, ColorStateList.valueOf(COLOR_CPU))
            setTextColor(Color.WHITE)
        }
        tabCluster?.apply {
            setBackgroundColor(COLOR_BG_TAB_INACTIVE)
            setTextColor(COLOR_TEXT_SECONDARY_DARK)
        }
        perCoreView?.visibility = View.VISIBLE
        clusterView?.visibility = View.GONE
    }

    private fun groupByCluster(cores: List<CpuCoreInfo>): Map<Long, MutableList<CpuCoreInfo>> {
        val map = mutableMapOf<Long, MutableList<CpuCoreInfo>>()
        for (c in cores) {
            var key: Long? = null
            for (k in map.keys) {
                if (abs(k - c.maxFreqKHz) <= 100000L) {
                    key = k
                    break
                }
            }
            if (key == null) {
                key = c.maxFreqKHz
                map[key] = mutableListOf()
            }
            map[key]!!.add(c)
        }
        return map
    }

    private fun getAvgFreq(cores: List<CpuCoreInfo>): Long {
        var sum = 0L
        var cnt = 0
        for (c in cores) {
            if (c.currentFreqKHz > 0) {
                sum += c.currentFreqKHz
                cnt++
            }
        }
        return if (cnt > 0) sum / cnt else 0
    }

    private fun getClusterType(maxFreq: Long): String = when {
        maxFreq < 1200000L -> "Efficiency"
        maxFreq < 2200000L -> "Performance"
        else -> "Prime"
    }

    private fun getFreqColor(khz: Long): Int = when {
        khz <= 0 -> COLOR_TEXT_SECONDARY_DARK
        khz < 1500000L -> 0xFF4CAF50.toInt()
        khz < 2500000L -> 0xFFFFC107.toInt()
        else -> 0xFFF44336.toInt()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
