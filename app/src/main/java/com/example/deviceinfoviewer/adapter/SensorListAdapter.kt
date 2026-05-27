package com.example.deviceinfoviewer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.data.model.SensorItemInfo

/**
 * 传感器列表 RecyclerView Adapter
 */
class SensorListAdapter : RecyclerView.Adapter<SensorListAdapter.ViewHolder>() {

    private val sensors = mutableListOf<SensorItemInfo>()

    fun setSensors(sensors: List<SensorItemInfo>?) {
        this.sensors.clear()
        sensors?.let { this.sensors.addAll(it) }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sensor, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sensor = sensors[position]
        holder.tvName.text = sensor.name
        holder.tvType.text = "类型: ${getSensorTypeName(sensor.type)}"
        holder.tvVendor.text = "厂商: ${sensor.vendor ?: "未知"}"

        val detail = StringBuilder()
        if (!sensor.maxRange.isNaN()) {
            detail.append("最大量程: ${String.format("%.2f", sensor.maxRange)}")
        }
        if (!sensor.resolution.isNaN()) {
            if (detail.isNotEmpty()) detail.append("  ")
            detail.append("分辨率: ${String.format("%.4f", sensor.resolution)}")
        }
        if (!sensor.powerMa.isNaN()) {
            if (detail.isNotEmpty()) detail.append("  ")
            detail.append("功耗: ${String.format("%.2f", sensor.powerMa)}mA")
        }
        if (sensor.minDelay > 0) {
            if (detail.isNotEmpty()) detail.append("  ")
            detail.append("最小延迟: ${sensor.minDelay}μs")
        }
        holder.tvDetail.text = detail.toString()
    }

    override fun getItemCount(): Int = sensors.size

    private fun getSensorTypeName(type: Int): String = when (type) {
        1 -> "加速度计"
        2 -> "磁力计"
        3 -> "方向"
        4 -> "陀螺仪"
        5 -> "光线"
        6 -> "压力"
        7 -> "温度"
        8 -> "距离"
        9 -> "重力"
        10 -> "线性加速度"
        11 -> "旋转矢量"
        12 -> "相对湿度"
        13 -> "环境温度"
        14 -> "磁场未校准"
        15 -> "游戏旋转矢量"
        16 -> "陀螺仪未校准"
        17 -> "重要运动"
        18 -> "步行检测"
        19 -> "计步器"
        20 -> "地磁旋转矢量"
        21 -> "心率"
        else -> "传感器($type)"
    }

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_sensor_name)
        val tvType: TextView = itemView.findViewById(R.id.tv_sensor_type)
        val tvVendor: TextView = itemView.findViewById(R.id.tv_sensor_vendor)
        val tvDetail: TextView = itemView.findViewById(R.id.tv_sensor_detail)
    }
}
