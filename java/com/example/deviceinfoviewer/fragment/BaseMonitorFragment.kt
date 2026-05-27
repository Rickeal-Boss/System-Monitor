package com.example.deviceinfoviewer.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.deviceinfoviewer.DeviceApplication
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.adapter.NetworkInterfaceAdapter
import com.example.deviceinfoviewer.adapter.SensorListAdapter
import com.example.deviceinfoviewer.data.repository.DeviceRepository

/**
 * 监控类 Fragment 基类，封装 SwipeRefreshLayout、Repository、通用 Adapter 等。
 * 子类自行管理具体的 ViewModel。
 */
abstract class BaseMonitorFragment : Fragment() {

    protected var repository: DeviceRepository? = null
    protected var swipeRefresh: SwipeRefreshLayout? = null

    // 可复用的适配器（子类按需使用）
    protected val sensorAdapter = SensorListAdapter()
    protected val netInterfaceAdapter = NetworkInterfaceAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(getLayoutResId(), container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        // 使用单例，避免强转 Activity 导致的空指针和 ClassCastException
        repository = DeviceApplication.getDeviceRepository()

        onViewBound(view)
        observeViewModelData()
        loadAdditionalData()

        swipeRefresh?.setOnRefreshListener {
            swipeRefresh?.isRefreshing = false
            repository?.loadStaticData()
        }
    }

    /** 子类提供布局资源 ID */
    protected abstract fun getLayoutResId(): Int

    /** 子类初始化 View 引用（findViewById）和 ViewModel */
    protected abstract fun onViewBound(view: View)

    /** 子类注册 LiveData 观察者 */
    protected abstract fun observeViewModelData()

    /** 子类加载额外数据（如传感器、系统信息） */
    protected abstract fun loadAdditionalData()
}
