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
 * 系统 Fragment — Build.* 参数 + 内核/JVM + 存储分区
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

    private val allBuildParams = mutableListOf<Map.Entry<String, String>>()
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

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                currentFilter = newText?.lowercase()?.trim() ?: ""
                rebuildBuildParamsView()
                return true
            }
        })

        val r = repo ?: return

        r.systemLiveData.observe(viewLifecycleOwner) { sys ->
            sys ?: return@observe

            tvKernel?.text = sys.kernelVersion.takeIf { it.isNotEmpty() } ?: "N/A"

            val vmName = sys.javaRuntimeName
            val vmVer = sys.javaVmVersion
            tvJvm?.text = when {
                vmName.isNotEmpty() -> vmName + if (vmVer.isNotEmpty()) " $vmVer" else ""
                vmVer.isNotEmpty() -> vmVer
                else -> "N/A"
            }

            tvBootloader?.text = sys.bootloader.takeIf { it.isNotEmpty() } ?: "N/A"

            allBuildParams.clear()
            allBuildParams.addAll(sys.buildFields.entries)
            rebuildBuildParamsView()
        }

        r.storageLiveData.observe(viewLifecycleOwner) { sto ->
            sto ?: return@observe
            partitionsContainer?.let { container ->
                container.removeAllViews()
                val inflater = LayoutInflater.from(context)
                for (p in sto.partitions) {
                    val row = createDetailRow(
                        inflater,
                        p.mountPoint,
                        "总 ${FormatUtils.formatBytes(p.totalBytes)} / 可用 ${FormatUtils.formatBytes(p.availableBytes)}"
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

    private fun rebuildBuildParamsView() {
        buildParamsContainer?.let { container ->
            container.removeAllViews()
            val inflater = LayoutInflater.from(context)
            for (entry in allBuildParams) {
                if (currentFilter.isNotEmpty() &&
                    !entry.key.lowercase().contains(currentFilter) &&
                    !entry.value.lowercase().contains(currentFilter)
                ) continue

                val row = createDetailRow(inflater, "${entry.key}:", entry.value)
                row.setOnLongClickListener {
                    val text = "${entry.key} = ${entry.value}"
                    val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("system_info", text))
                    Toast.makeText(requireContext(), R.string.copy_toast, Toast.LENGTH_SHORT).show()
                    true
                }
                container.addView(row)
            }
        }
    }

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

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()
}
