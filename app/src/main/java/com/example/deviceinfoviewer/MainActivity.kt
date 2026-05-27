package com.example.deviceinfoviewer

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val settings = AppSettings.getInstance(this)
        AppCompatDelegate.setDefaultNightMode(
            if (settings.darkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            window.setDecorFitsSystemWindows(false)

        setContentView(R.layout.activity_main)

        // Toolbar + edge-to-edge insets
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "System Monitor"
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(0, top, 0, 0)
            insets
        }

        // ViewPager2 + TabLayout (防递归互锁)
        val vp = findViewById<ViewPager2>(R.id.view_pager)
        val tl = findViewById<TabLayout>(R.id.tab_layout)
        vp.adapter = TabPagerAdapter(this)

        for (i in 0 until TabPagerAdapter.TAB_COUNT)
            tl.addTab(tl.newTab().setText(TabPagerAdapter.getTabTitle(i)))

        var syncing = false
        tl.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) {
                if (!syncing) {
                    syncing = true
                    vp.setCurrentItem(t.position, false)
                    syncing = false
                }
            }
            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) {}
        })

        vp.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(pos: Int) {
                if (!syncing) {
                    tl.getTabAt(pos)?.let { if (!it.isSelected) it.select() }
                }
            }
        })

        // 延迟启动监控
        Handler(Looper.getMainLooper()).postDelayed({
            DeviceApplication.getDeviceRepository()?.apply {
                startMonitoring(2000L)
                loadStaticData()
            }
        }, 500)
    }
}
