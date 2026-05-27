package com.example.deviceinfoviewer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.deviceinfoviewer.service.FloatingWindowService
import com.example.deviceinfoviewer.util.PermissionHelper
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

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "System Monitor"

        // 将 status bar 内边距应用到 CoordinatorLayout 根布局，避免 Toolbar/TabLayout 重叠
        val root = findViewById<androidx.coordinatorlayout.widget.CoordinatorLayout>(R.id.root_layout)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(0, top, 0, 0)
            insets
        }

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

        Handler(Looper.getMainLooper()).postDelayed({
            DeviceApplication.getDeviceRepository()?.apply {
                startMonitoring(2000L)
                loadStaticData()
            }
        }, 500)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_floating_window -> {
                requestOverlayPermissionAndStart()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun requestOverlayPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                PermissionHelper.requestOverlayPermission(this)
                return
            }
        }
        startFloatingWindow()
    }

    private fun startFloatingWindow() {
        val intent = Intent(this, FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 200) {
            // 从系统悬浮窗权限设置返回：检查权限，已授予则启动悬浮窗
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startFloatingWindow()
            }
        }
    }
}
