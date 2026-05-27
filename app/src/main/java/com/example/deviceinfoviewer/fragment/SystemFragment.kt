package com.example.deviceinfoviewer.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.deviceinfoviewer.DeviceApplication
import com.example.deviceinfoviewer.FormatUtils
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.data.model.StorageInfo
import com.example.deviceinfoviewer.data.repository.DeviceRepository

/**
 * 系统 Fragment — Build.* 参数 + 内核/JVM + 存储分区，直接观察 Repository LiveData
 */
class SystemFragment : Fragment() {

    private var repo: DeviceRepository? = null

    private var searchView: SearchView? = null
    private var buildParamsContainer: LinearLayout? = null
    private var tvKernel: TextView? = null
    private var tvJvm: TextView? = null
    private var tvBootloader: TextView? = null
    private var partitionsContainer: LinearLayout? = null
    private var swipeRefresh: SwipeRefreshLayout? = null

    private val allBuildParams = mutableListOf<MutableMap.MutableEntry<String, String>>()
    private var currentFilter = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_system, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repo = DeviceApplication.getDeviceRepository()

        searchView = view.findViewById(R.id.search_view)
        buildParamsContainer = view.findViewById(R.id.build_params_container)
        tvKernel = view.findViewById(R.id.tv_kernel)
        tvJvm = view.findViewById(R.id.tv_jvm)
        tvBootloader = view.findViewById(R.id.tv_bootloader)
        partitionsContainer = view.findViewById(R.id.partitions_container)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)

        // 搜索过滤
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                currentFilter = newText?.lowercase()?.trim() ?: ""
                rebuildBuildParamsView()
                return true
            }
        })

        repo ?: return

        // 观察系统信息 LiveData
        repo!!.getSystemLiveData().observe(viewLifecycleOwner) { sys ->
            sys ?: return@observe

            // 内核版本
            val kernel = sys.getKernelVersion()
            tvKernel?.text = kernel?.takeIf { it.isNotEmpty() } ?: "N/A"

            // Java VM
            val vmName = sys.getJavaRuntimeName()
            val vmVer = sys.getJavaVmVersion()
            tvJvm?.text = when {
                !vmName.isNullOrEmpty() -> vmName + if (!vmVer.isNullOrEmpty()) " $vmVer" else ""
                !vmVer.isNullOrEmpty() -> vmVer
                else -> "N/A"
            }

            // Bootloader
            val bootloader = sys.getBootloader()
            tvBootloader?.text = bootloader?.takeIf { it.isNotEmpty() } ?: "N/A"

            // Build 参数
            allBuildParams.clear()
            sys.getBuildFields()?.let { fields ->
                allBuildParams.addAll(fields.entries)
            }
            rebuildBuildParamsView()
        }

        // 观察存储信息 LiveData
        repo!!.getStorageLiveData().observe(viewLifecycleOwner) { sto ->
            sto ?: return@observe
            partitionsContainer?.let { container ->
                container.removeAllViews()
                val inflater = LayoutInflater.from(context)
                for (p in sto.getPartitions()) {
                    val row = createDetailRow(
                        inflater,
                        p.getMountPoint(),
                        "总 ${FormatUtils.formatBytes(p.getTotalBytes())} / 可用 ${FormatUtils.formatBytes(p.getAvailableBytes())}"
                    )
                    container.addView(row)
                }
            }
        }

        swipeRefresh?.setOnRefreshListener {
            swipeRefresh?.isRefreshing = false
            repo?.loadStaticData()
        }
    }

    /**
     * 根据当前过滤词重建 Build 参数视图
     */
    private fun rebuildBuildParamsView() {
        buildParamsContainer?.let { container ->
            container.removeAllViews()
            val inflater = LayoutInflater.from(context)
            for (entry in allBuildParams) {
                if (currentFilter.isNotEmpty()) {
                    if (!entry.key.lowercase().contains(currentFilter)
                        && !entry.value.lowercase().contains(currentFilter)
                    ) {
                        continue
                    }
                }
                val row = createDetailRow(inflater, "${entry.key}:", entry.value)
                val key = entry.key
                val value = entry.value
                row.setOnLongClickListener {
                    val text = "$key = $value"
                    val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("system_info", text))
                    Toast.makeText(requireContext(), R.string.copy_toast, Toast.LENGTH_SHORT).show()
                    true
                }
                container.addView(row)
            }
        }
    }

    /**
     * 创建一个 detail_row（标签: 值）行
     */
    private fun createDetailRow(inflater: LayoutInflater, label: String, value: String): View {
        val row = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dpToPx(6), 0, dpToPx(6))
        }

        val tvLabel = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
            text = label
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            textSize = 14f
        }

        val tvValue = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = value
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_dark))
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
        }

        row.addView(tvLabel)
        row.addView(tvValue)
        return row
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}
