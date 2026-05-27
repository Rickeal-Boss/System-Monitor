package com.example.deviceinfoviewer.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.deviceinfoviewer.DeviceApplication
import com.example.deviceinfoviewer.FormatUtils
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.data.model.GpuInfo
import com.example.deviceinfoviewer.data.model.HistoryDataPoint
import com.example.deviceinfoviewer.data.repository.DeviceRepository
import com.example.deviceinfoviewer.widget.MonitorChartView

/**
 * GPU Fragment — DevCheck Pro 风格：紫色主题
 */
class GpuFragment : Fragment() {

    companion object {
        private const val TAG = "GpuFragment"
        private const val COLOR_GPU = 0xFFAB47BC.toInt()       // GPU 紫色
        private const val COLOR_GPU_LIGHT = 0xFFCE93D8.toInt()
    }

    private var repo: DeviceRepository? = null
    private var tvGpuModel: TextView? = null
    private var tvGpuFreqHeader: TextView? = null
    private var tvGpuLoad: TextView? = null
    private var tvGpuTemp: TextView? = null
    private var tvGpuVendor: TextView? = null
    private var tvGpuRenderer: TextView? = null
    private var tvGpuGovernor: TextView? = null
    private var tvGpuFreq: TextView? = null
    private var tvGpuFreqRange: TextView? = null
    private var chartGpuLoad: MonitorChartView? = null
    private var chartGpuTemp: MonitorChartView? = null
    private var handler: Handler? = null
    private var chartUpdater: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            inflater.inflate(R.layout.fragment_gpu, container, false)
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
            tvGpuModel = view.findViewById(R.id.tv_gpu_model)
            tvGpuFreqHeader = view.findViewById(R.id.tv_gpu_freq_header)
            tvGpuLoad = view.findViewById(R.id.tv_gpu_load)
            tvGpuTemp = view.findViewById(R.id.tv_gpu_temp)
            tvGpuVendor = view.findViewById(R.id.tv_gpu_vendor)
            tvGpuRenderer = view.findViewById(R.id.tv_gpu_renderer)
            tvGpuGovernor = view.findViewById(R.id.tv_gpu_governor)
            tvGpuFreq = view.findViewById(R.id.tv_gpu_freq)
            tvGpuFreqRange = view.findViewById(R.id.tv_gpu_freq_range)
            chartGpuLoad = view.findViewById(R.id.chart_gpu_load)
            chartGpuTemp = view.findViewById(R.id.chart_gpu_temp)

            // GPU 紫色主题图表
            chartGpuLoad?.apply {
                setChartColor(COLOR_GPU)
                setValueFormat("%.0f", "%")
            }
            chartGpuTemp?.apply {
                setChartColor(COLOR_GPU)
                setValueFormat("%.1f", "°C")
            }

            repo ?: return

            repo!!.getGpuLiveData().observe(viewLifecycleOwner) { gpu ->
                gpu?.let { updateGpuInfo(it) }
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

    private fun updateGpuInfo(gpu: GpuInfo) {
        var model: String? = gpu.getModel()
        if (model.isNullOrEmpty()) model = gpu.getVendor()
        if (model.isNullOrEmpty()) model = "未知 GPU"
        tvGpuModel?.text = model

        val headerInfo = StringBuilder()
        if (gpu.getFrequencyKHz() > 0) headerInfo.append(FormatUtils.formatFreq(gpu.getFrequencyKHz()))
        val load = gpu.getLoadPercentage()
        if (!load.isNaN()) {
            if (headerInfo.isNotEmpty()) headerInfo.append(" · ")
            headerInfo.append(String.format("%.0f%%", load))
        }
        tvGpuFreqHeader?.text = headerInfo.toString()

        tvGpuLoad?.text = if (load.isNaN()) "N/A" else String.format("%.0f%%", load)

        val temp = gpu.getTemperatureCelsius()
        tvGpuTemp?.text = if (temp.isNaN()) "N/A" else FormatUtils.formatTempCelsius(temp)

        tvGpuVendor?.text = gpu.getVendor()?.takeIf { it.isNotEmpty() } ?: "N/A"
        tvGpuRenderer?.text = gpu.getRenderer()?.takeIf { it.isNotEmpty() } ?: "N/A"
        tvGpuGovernor?.text = gpu.getGovernor()?.takeIf { it.isNotEmpty() } ?: "N/A"

        tvGpuFreq?.text = if (gpu.getFrequencyKHz() > 0) FormatUtils.formatFreq(gpu.getFrequencyKHz()) else "N/A"

        val range = if (gpu.getMinFreqKHz() > 0 && gpu.getMaxFreqKHz() > 0) {
            "${FormatUtils.formatFreq(gpu.getMinFreqKHz())} - ${FormatUtils.formatFreq(gpu.getMaxFreqKHz())}"
        } else ""
        tvGpuFreqRange?.text = range.ifEmpty { "N/A" }
    }

    private fun updateCharts() {
        repo ?: return
        val loadData: List<HistoryDataPoint>? = repo!!.getHistoryCache().getSeries("gpu_load")
        if (!loadData.isNullOrEmpty()) chartGpuLoad?.setData(loadData)
        val tempData: List<HistoryDataPoint>? = repo!!.getHistoryCache().getSeries("gpu_temp")
        if (!tempData.isNullOrEmpty()) chartGpuTemp?.setData(tempData)
    }
}
