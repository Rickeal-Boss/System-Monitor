package com.example.deviceinfoviewer.fragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.deviceinfoviewer.DeviceApplication
import com.example.deviceinfoviewer.FormatUtils
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.adapter.SensorListAdapter
import com.example.deviceinfoviewer.data.model.CpuCoreInfo
import com.example.deviceinfoviewer.data.repository.DeviceRepository
import com.example.deviceinfoviewer.widget.MonitorChartView

/**
 * 硬件 Fragment —— CPU 核心动态列表 + GPU 详情 + 传感器列表，直接观察 Repository LiveData
 */
class HardwareFragment : Fragment() {

    private var repo: DeviceRepository? = null

    private var cpuCoresContainer: LinearLayout? = null
    private var tvGpuModel: TextView? = null
    private var tvGpuVendor: TextView? = null
    private var tvGpuFreq: TextView? = null
    private var tvGpuTemp: TextView? = null
    private var tvGpuLoad: TextView? = null
    private var tvGpuFreqRange: TextView? = null
    private var tvGpuGovernor: TextView? = null
    private var tvGpuRenderer: TextView? = null
    private var recyclerSensors: RecyclerView? = null
    private var sensorAdapter: SensorListAdapter? = null
    private var swipeRefresh: SwipeRefreshLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_hardware, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repo = DeviceApplication.getDeviceRepository()

        cpuCoresContainer = view.findViewById(R.id.cpu_cores_container)
        tvGpuModel = view.findViewById(R.id.tv_gpu_model)
        tvGpuVendor = view.findViewById(R.id.tv_gpu_vendor)
        tvGpuFreq = view.findViewById(R.id.tv_gpu_freq)
        tvGpuTemp = view.findViewById(R.id.tv_gpu_temp)
        tvGpuLoad = view.findViewById(R.id.tv_gpu_load)
        tvGpuFreqRange = view.findViewById(R.id.tv_gpu_freq_range)
        tvGpuGovernor = view.findViewById(R.id.tv_gpu_governor)
        tvGpuRenderer = view.findViewById(R.id.tv_gpu_renderer)
        recyclerSensors = view.findViewById(R.id.recycler_sensors)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)

        // 传感器 RecyclerView
        sensorAdapter = SensorListAdapter()
        recyclerSensors?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = sensorAdapter
        }

        repo ?: return

        // 观察 CPU LiveData
        repo!!.cpuLiveData.observe(viewLifecycleOwner) { cpu ->
            cpu ?: return@observe
            val container = cpuCoresContainer ?: return@observe
            container.removeAllViews()
            val inflater = LayoutInflater.from(context)
            for (core in cpu.cores) {
                val row = inflater.inflate(R.layout.item_cpu_core, container, false)
                row.findViewById<TextView>(R.id.tv_core_name)?.text = "核心 ${core.coreIndex}"

                val freqText = if (core.currentFreqKHz > 0)
                    FormatUtils.formatFreq(core.currentFreqKHz) else "N/A"
                row.findViewById<TextView>(R.id.tv_core_freq)?.text = freqText

                row.findViewById<TextView>(R.id.tv_core_gov)?.text =
                    core.governor?.takeIf { it.isNotEmpty() } ?: "N/A"

                val pbFreq = row.findViewById<ProgressBar>(R.id.pb_core_freq)
                if (core.maxFreqKHz > 0 && core.currentFreqKHz > 0) {
                    var pct = (core.currentFreqKHz * 100 / core.maxFreqKHz).toInt()
                    pct = minOf(pct, 100)
                    pbFreq.progress = pct
                    pbFreq.progressTintList =
                        ColorStateList.valueOf(getFreqColor(pct))
                } else {
                    pbFreq.progress = 0
                }

                container.addView(row)
            }
        }

        // 观察 GPU LiveData
        repo!!.gpuLiveData.observe(viewLifecycleOwner) { gpu ->
            gpu ?: return@observe
            tvGpuModel?.text = gpu.model?.takeIf { it.isNotEmpty() } ?: "N/A"
            tvGpuVendor?.text = gpu.vendor?.takeIf { it.isNotEmpty() } ?: "N/A"
            tvGpuFreq?.text = if (gpu.frequencyKHz > 0)
                FormatUtils.formatFreq(gpu.frequencyKHz) else "N/A"
            tvGpuTemp?.text = FormatUtils.formatTempCelsius(gpu.temperatureCelsius)

            // 负载
            tvGpuLoad?.text = if (!gpu.loadPercentage.isNaN() && gpu.loadPercentage > 0)
                String.format("%.1f%%", gpu.loadPercentage) else "N/A"

            // 频率范围
            val freqRange = buildString {
                if (gpu.minFreqKHz > 0) append(FormatUtils.formatFreq(gpu.minFreqKHz)) else append("?")
                append(" ~ ")
                if (gpu.maxFreqKHz > 0) append(FormatUtils.formatFreq(gpu.maxFreqKHz)) else append("?")
            }
            tvGpuFreqRange?.text = freqRange

            // 调速器
            tvGpuGovernor?.text = gpu.governor?.takeIf { it.isNotEmpty() } ?: "N/A"

            // 渲染器
            tvGpuRenderer?.text = gpu.renderer?.takeIf { it.isNotEmpty() } ?: "N/A"
        }

        // 观察传感器 LiveData（一次性加载）
        repo!!.sensorsLiveData.observe(viewLifecycleOwner) { sensors ->
            sensorAdapter?.setSensors(sensors)
        }

        swipeRefresh?.setOnRefreshListener {
            swipeRefresh?.isRefreshing = false
            repo?.loadStaticData()
        }
    }

    /**
     * 根据频率百分比返回颜色
     */
    private fun getFreqColor(pct: Int): Int {
        val ctx = context ?: return 0xFF4CAF50.toInt()
        return when {
            pct >= 90 -> ContextCompat.getColor(ctx, R.color.status_critical)
            pct >= 50 -> ContextCompat.getColor(ctx, R.color.status_warning)
            else -> ContextCompat.getColor(ctx, R.color.status_good)
        }
    }
}
