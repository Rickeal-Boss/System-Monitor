package com.example.deviceinfoviewer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.deviceinfoviewer.R
import com.example.deviceinfoviewer.data.model.NetworkInterfaceInfo

/**
 * 网络接口列表 RecyclerView Adapter
 */
class NetworkInterfaceAdapter : RecyclerView.Adapter<NetworkInterfaceAdapter.ViewHolder>() {

    private val interfaces = mutableListOf<NetworkInterfaceInfo>()

    fun setInterfaces(interfaces: List<NetworkInterfaceInfo>?) {
        this.interfaces.clear()
        interfaces?.let { this.interfaces.addAll(it) }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_network_interface, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val iface = interfaces[position]
        holder.tvName.text = iface.name
        holder.tvIp.text = "IP: ${iface.ipAddress.ifEmpty { "N/A" }}"
        holder.tvMac.text = "MAC: ${iface.macAddress.ifEmpty { "N/A" }}"

        val traffic = StringBuilder()
        traffic.append("MTU: ${if (iface.mtu > 0) iface.mtu else "N/A"}")
        if (iface.rxBytes >= 0) {
            traffic.append("  RX: ${formatBytes(iface.rxBytes)}")
        }
        if (iface.txBytes >= 0) {
            traffic.append("  TX: ${formatBytes(iface.txBytes)}")
        }
        holder.tvTraffic.text = traffic.toString()
    }

    override fun getItemCount(): Int = interfaces.size

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> String.format("%.2f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024L -> String.format("%.0f KB", bytes / 1_024.0)
        else -> "$bytes B"
    }

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_iface_name)
        val tvIp: TextView = itemView.findViewById(R.id.tv_iface_ip)
        val tvMac: TextView = itemView.findViewById(R.id.tv_iface_mac)
        val tvTraffic: TextView = itemView.findViewById(R.id.tv_iface_traffic)
    }
}
