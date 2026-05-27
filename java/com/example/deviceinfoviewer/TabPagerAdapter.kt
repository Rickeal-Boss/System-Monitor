package com.example.deviceinfoviewer

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.deviceinfoviewer.fragment.*

/**
 * ViewPager2 Adapter — 5 主 Tab
 */
class TabPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    companion object {
        const val TAB_COUNT = 5
        private val TAB_TITLES = arrayOf("CPU", "GPU", "内存", "电池", "网络")
        val TAB_COLORS = intArrayOf(
            0xFFFF9800.toInt(),  // CPU 橙
            0xFFAB47BC.toInt(),  // GPU 紫
            0xFF42A5F5.toInt(),  // 内存 蓝
            0xFF66BB6A.toInt(),  // 电池 绿
            0xFF26C6DA.toInt(),  // 网络 青
        )

        fun getTabTitle(pos: Int): String = TAB_TITLES[pos]
        fun getTabColor(pos: Int): Int = TAB_COLORS[pos]
    }

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> CpuFragment()
        1 -> GpuFragment()
        2 -> MemoryFragment()
        3 -> BatteryFragment()
        4 -> NetworkFragment()
        else -> SafePlaceholderFragment()
    }

    override fun getItemCount(): Int = TAB_COUNT
}
