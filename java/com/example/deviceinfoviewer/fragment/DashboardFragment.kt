package com.example.deviceinfoviewer.fragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.deviceinfoviewer.DeviceApplication
import com.example.deviceinfoviewer.FormatUtils
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.data.model.CpuCoreInfo
import com.example.deviceinfoviewer.data.model.MemoryInfo
import com.example.deviceinfoviewer.data.repository.DeviceRepository
import com.example.deviceinfoviewer.widget.HistoryChartView

/**
 * 仪表盘 Fragment — DevCheck Pro 风格 2x2 卡片网格 + 历史趋势
 */
class DashboardFragment : Fragment() {

    companion object {
        private const val COLOR_CPU = 0xFFFF9800.toInt()
        private const val COLOR_BATTERY = 0xFF66BB6A.toInt()
        private const val COLOR_MEMORY = 0xFF42A5F5.toInt()
        private const val COLOR_STORAGE = 0xFF7E57C2.toInt()
    }

    private var repo: DeviceRepository? = null

    private var tvCpuTemp: TextView? = null
    private var tvCpuFreq: TextView? = null
    private var tvBatteryLevel: TextView? = null
    private var tvBatteryTemp: TextView? = null
    private var tvRamUsage: TextView? = null
    private var tvRamDetail: TextView? = null
    private var pbRam: ProgressBar? = null
    private var cardRam: View? = null
    private var tvStorageUsage: TextView? = null
    private var tvStorageDetail: TextView? = null
    private var pbStorage: ProgressBar? = null
    private var tvGpuTemp: TextView? = null
    private var chartHistory: HistoryChartView? = null
    private var swipeRefresh: SwipeRefreshLayout? = null
    private var chartHandler: Handler? = null
    private var chartUpdater: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = DeviceApplication.getDeviceRepository()

        tvCpuTemp = view.findViewById(R.id.tv_cpu_temp)
        tvCpuFreq = view.findViewById(R.id.tv_cpu_freq)
        tvBatteryLevel = view.findViewById(R.id.tv_battery_level)
        tvBatteryTemp = view.findViewById(R.id.tv_battery_temp)
        tvRamUsage = view.findViewById(R.id.tv_ram_usage)
        tvRamDetail = view.findViewById(R.id.tv_ram_detail)
        pbRam = view.findViewById(R.id.pb_ram)
        cardRam = view.findViewById(R.id.card_ram)
        tvStorageUsage = view.findViewById(R.id.tv_storage_usage)
        tvStorageDetail = view.findViewById(R.id.tv_storage_detail)
        pbStorage = view.findViewById(R.id.pb_storage)
        chartHistory = view.findViewById(R.id.chart_history)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        tvGpuTemp = view.findViewById(R.id.tv_gpu_temp)

        repo ?: return

        // CPU
        repo!!.getCpuLiveData().observe(viewLifecycleOwner) { cpu ->
            cpu ?: return@observe
            tvCpuTemp?.text = FormatUtils.formatTempCelsius(cpu.getTemperatureCelsius())
            var maxFreq = 0L
            for (core in cpu.getCores()) {
                if (core.getCurrentFreqKHz() > maxFreq) maxFreq = core.getCurrentFreqKHz()
            }
            tvCpuFreq?.text = if (maxFreq > 0) FormatUtils.formatFreq(maxFreq) else "N/A"
        }

        // GPU
        repo!!.getGpuLiveData().observe(viewLifecycleOwner) { gpu ->
            gpu ?: return@observe
            var gpuText = FormatUtils.formatTempCelsius(gpu.getTemperatureCelsius())
            if (!gpu.getLoadPercentage().isNaN() && gpu.getLoadPercentage() > 0) {
                gpuText += " | ${String.format("%.0f%%", gpu.getLoadPercentage())}"
            }
            tvGpuTemp?.text = gpuText
        }

        // 电池
        repo!!.getBatteryLiveData().observe(viewLifecycleOwner) { bat ->
            bat ?: return@observe
            tvBatteryLevel?.text = if (bat.getLevelPercent() >= 0) "${bat.getLevelPercent()}%" else "N/A"
            tvBatteryTemp?.text = FormatUtils.formatTempCelsius(bat.getTemperatureCelsius())
        }

        // 内存
        repo!!.getMemoryLiveData().observe(viewLifecycleOwner) { mem ->
            if (mem == null || mem.getTotalKB() <= 0) {
                tvRamUsage?.text = "N/A"
                tvRamDetail?.text = "N/A"
                pbRam?.progress = 0
                return@observe
            }
            val pct = (mem.getUsedKB().toFloat() / mem.getTotalKB() * 100).toInt()
            tvRamUsage?.text = FormatUtils.formatBytes(mem.getUsedKB() * 1024L)
            tvRamDetail?.text = "$pct% | ${FormatUtils.formatBytes(mem.getTotalKB() * 1024L)} 总"
            pbRam?.apply {
                progress = pct
                progressTintList = getProgressColor(pct)
            }
        }

        // 存储
        repo!!.getStorageLiveData().observe(viewLifecycleOwner) { sto ->
            if (sto == null || sto.getInternalTotalBytes() <= 0) {
                tvStorageUsage?.text = "N/A"
                tvStorageDetail?.text = "N/A"
                pbStorage?.progress = 0
                return@observe
            }
            val pct = (sto.getInternalUsedBytes().toFloat() / sto.getInternalTotalBytes() * 100).toInt()
            tvStorageUsage?.text = FormatUtils.formatBytes(sto.getInternalUsedBytes())
            tvStorageDetail?.text = "$pct% | ${FormatUtils.formatBytes(sto.getInternalTotalBytes())} 总"
            pbStorage?.apply {
                progress = pct
                progressTintList = getProgressColor(pct)
            }
        }

        // 历史趋势
        chartHistory?.setData("CPU温度", repo!!.getHistoryCache().getSeries("cpu_temp"))

        cardRam?.setOnClickListener { showZramDialog() }
        swipeRefresh?.setOnRefreshListener {
            swipeRefresh?.isRefreshing = false
            repo?.loadStaticData()
            updateChart()
        }

        chartHandler = Handler(Looper.getMainLooper())
        chartUpdater = object : Runnable {
            override fun run() {
                updateChart()
                chartHandler?.postDelayed(this, 3000)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        chartHandler?.post(chartUpdater!!)
    }

    override fun onPause() {
        super.onPause()
        chartUpdater?.let { chartHandler?.removeCallbacks(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        chartHandler?.removeCallbacksAndMessages(null)
        chartHandler = null
        chartUpdater = null
    }

    private fun updateChart() {
        repo ?: return
        chartHistory?.setData("CPU温度", repo!!.getHistoryCache().getSeries("cpu_temp"))
    }

    private fun getProgressColor(pct: Int): ColorStateList {
        val color = when {
            pct < 70 -> 0xFF4CAF50.toInt()
            pct < 90 -> 0xFFFF9800.toInt()
            else -> 0xFFF44336.toInt()
        }
        return ColorStateList.valueOf(color)
    }

    private fun showZramDialog() {
        val memory: MemoryInfo = repo?.getMemoryLiveData()?.value ?: return
        val msg = buildString {
            append("原始数据: ").append(
                if (memory.getZramOriginalKB() > 0) FormatUtils.formatBytes(memory.getZramOriginalKB() * 1024L) else "N/A"
            ).append("\n")
            append("压缩后: ").append(
                if (memory.getZramCompressedKB() > 0) FormatUtils.formatBytes(memory.getZramCompressedKB() * 1024L) else "N/A"
            ).append("\n")
            append("实际占用: ").append(
                if (memory.getZramMemUsedTotalKB() > 0) FormatUtils.formatBytes(memory.getZramMemUsedTotalKB() * 1024L) else "N/A"
            ).append("\n")
            append("压缩比: ").append(
                if (memory.getCompressionRatio() > 0) String.format("%.2f:1", memory.getCompressionRatio()) else "N/A"
            ).append("\n\n")
            append("Swap 总量: ").append(
                if (memory.getSwapTotalKB() > 0) FormatUtils.formatBytes(memory.getSwapTotalKB() * 1024L) else "N/A"
            ).append("\n")
            append("Swap 已用: ").append(
                if (memory.getSwapUsedKB() > 0) FormatUtils.formatBytes(memory.getSwapUsedKB() * 1024L) else "N/A"
            )
        }
        AlertDialog.Builder(requireContext())
            .setTitle("ZRAM 详情")
            .setMessage(msg)
            .setPositiveButton("确定") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
