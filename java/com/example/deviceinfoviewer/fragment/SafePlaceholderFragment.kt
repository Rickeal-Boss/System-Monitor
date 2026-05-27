package com.example.deviceinfoviewer.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

/**
 * 安全占位 Fragment — 用于崩溃定位
 * 如果这个能显示，说明 CpuFragment/GpuFragment 有问题
 */
class SafePlaceholderFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return TextView(context).apply {
            text = "App is alive! Crash is in other fragments."
            textSize = 20f
            setPadding(48, 48, 48, 48)
        }
    }
}
