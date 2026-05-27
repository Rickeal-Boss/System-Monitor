package com.example.deviceinfoviewer.fragment

import android.content.res.ColorStateList
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
import com.example.deviceinfoviewer.FormatUtils
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.data.model.MemoryInfo
import com.example.deviceinfoviewer.data.repository.DeviceRepository
import com.example.deviceinfoviewer.widget.MonitorChartView

/**
 * Memory Fragment — DevCheck Pro 风格：蓝色主题
 */
class MemoryFragment : Fragment() {

    companion object {
        private const val TAG = "MemoryFragment"
        private const val COLOR_MEMORY = 0xFF42A5F5.toInt()    // 内存蓝色
        private const val COLOR_MEMORY_FILL = 0xFF1565C0.toInt()
    }

    private var repo: DeviceRepository? = null
    private var tvMemUsage: TextView? = null
    private var tvMemTotal: TextView? = null
    private var tvMemUsed: TextView? = null
    private var tvMemAvailable: TextView? = null
    private var pbMemory: ProgressBar? = null
    private var tvZramTitle: TextView? = null
    private var tvZramDetail: TextView? = null
    private var pbZram: ProgressBar? = null
    private var tvSwapTitle: TextView? = null
    private var tvSwapDetail: TextView? = null
    private var pbSwap: ProgressBar? = null
    private var chartMemAvailable: MonitorChartView? = null
    private var chartMemUsed: MonitorChartView? = null
    private var handler: Handler? = null
    private var chartUpdater: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            inflater.inflate(R.layout.fragment_memory, container, false)
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
            tvMemUsage = view.findViewById(R.id.tv_mem_usage)
            tvMemTotal = view.findViewById(R.id.tv_mem_total)
            tvMemUsed = view.findViewById(R.id.tv_mem_used)
            tvMemAvailable = view.findViewById(R.id.tv_mem_available)
            pbMemory = view.findViewById(R.id.pb_memory)
            tvZramTitle = view.findViewById(R.id.tv_zram_title)
            tvZramDetail = view.findViewById(R.id.tv_zram_detail)
            pbZram = view.findViewById(R.id.pb_zram)
            tvSwapTitle = view.findViewById(R.id.tv_swap_title)
            tvSwapDetail = view.findViewById(R.id.tv_swap_detail)
            pbSwap = view.findViewById(R.id.pb_swap)
            chartMemAvailable = view.findViewById(R.id.chart_mem_available)
            chartMemUsed = view.findViewById(R.id.chart_mem_used)

            // 内存蓝色主题图表
            chartMemAvailable?.apply {
                setChartColor(COLOR_MEMORY)
                setValueFormat("%.1f", " GB")
            }
            chartMemUsed?.apply {
                setChartColor(0xFFF44336.toInt())  // 已用用红色
                setValueFormat("%.1f", " GB")
            }

            repo ?: return

            repo!!.memoryLiveData.observe(viewLifecycleOwner) { mem ->
                if (mem != null && mem.totalKB > 0) updateMemoryInfo(mem)
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
        chartUpdater = null
    }

    private fun updateMemoryInfo(mem: MemoryInfo) {
        val totalKB = mem.totalKB
        val usedKB = mem.usedKB
        val availKB = mem.availableKB
        val totalBytes = totalKB * 1024L
        val usedBytes = usedKB * 1024L
        val availBytes = availKB * 1024L

        val pct = (usedKB.toFloat() / totalKB * 100).toInt()
        tvMemUsage?.text = "$pct%"
        tvMemTotal?.text = FormatUtils.formatBytes(totalBytes)
        tvMemUsed?.text = FormatUtils.formatBytes(usedBytes)
        tvMemAvailable?.text = FormatUtils.formatBytes(availBytes)
        pbMemory?.apply {
            progress = pct
            progressTintList = getProgressColor(pct)
        }

        // ZRAM
        val zramOrigKB = mem.zramOriginalKB
        val zramUsedKB = mem.zramMemUsedTotalKB
        val zramCompKB = mem.zramCompressedKB
        val compRatio = mem.compressionRatio

        if (zramOrigKB > 0) {
            val zramPct = (zramUsedKB.toFloat() / zramOrigKB * 100).toInt()
            tvZramTitle?.text = FormatUtils.formatBytes(zramUsedKB * 1024L)
            pbZram?.apply {
                progress = zramPct
                progressTintList = getProgressColor(zramPct)
            }
            val zramInfo = buildString {
                append("原始 ").append(FormatUtils.formatBytes(zramOrigKB * 1024L))
                if (zramCompKB > 0) append(" | 压缩 ").append(FormatUtils.formatBytes(zramCompKB * 1024L))
                if (compRatio > 0) append(" | 比 ").append(String.format("%.1f:1", compRatio))
            }
            tvZramDetail?.text = zramInfo
        } else {
            tvZramTitle?.text = "无 ZRAM"
            pbZram?.progress = 0
            tvZramDetail?.text = ""
        }

        // Swap
        val swapTotalKB = mem.swapTotalKB
        val swapUsedKB = mem.swapUsedKB
        if (swapTotalKB > 0) {
            val swapPct = (swapUsedKB.toFloat() / swapTotalKB * 100).toInt()
            tvSwapTitle?.text = FormatUtils.formatBytes(swapUsedKB * 1024L)
            pbSwap?.apply {
                progress = swapPct
                progressTintList = getProgressColor(swapPct)
            }
            tvSwapDetail?.text = "总量 ${FormatUtils.formatBytes(swapTotalKB * 1024L)}"
        } else {
            tvSwapTitle?.text = "无 Swap"
            pbSwap?.progress = 0
            tvSwapDetail?.text = ""
        }
    }

    private fun updateCharts() {
        repo ?: return
        val mem = repo!!.memoryLiveData.value ?: return
        if (mem.totalKB <= 0) return
        val now = System.currentTimeMillis()
        val availGB = mem.availableKB * 1024f / (1024f * 1024f * 1024f)
        val usedGB = mem.usedKB * 1024f / (1024f * 1024f * 1024f)
        chartMemAvailable?.addDataPoint(now, availGB)
        chartMemUsed?.addDataPoint(now, usedGB)
    }

    private fun getProgressColor(pct: Int): ColorStateList {
        val c = when {
            pct < 70 -> 0xFF4CAF50.toInt()
            pct < 90 -> 0xFFFF9800.toInt()
            else -> 0xFFF44336.toInt()
        }
        return ColorStateList.valueOf(c)
    }
}
