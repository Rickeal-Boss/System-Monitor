package com.example.deviceinfoviewer.data.source

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

import com.example.deviceinfoviewer.AppSettings
import com.example.deviceinfoviewer.data.model.BatteryInfo

/**
 * 电池数据源 — 全网方案版
 * 1. 温度：BatteryManager (decicelsius÷10) + sysfs fallback
 * 2. 功率：区分充电/放电，使用 double 安全计算
 * 3. 循环次数：20+ 路径多级 fallback（覆盖小米/华为/三星/OPPO/vivo/索尼/一加等）
 * 4. 容量：sysfs charge_full / charge_full_design + BatteryManager
 * 5. 双电芯：读取 AppSettings 中的 dualCell 开关
 */
class BatteryDataSource(private val context: Context) {

    private val appContext = context.applicationContext

    fun getBatteryInfo(): BatteryInfo {
        val info = BatteryInfo()
        info.timestamp = System.currentTimeMillis()

        // 双电芯开关
        info.dualCell = AppSettings.getInstance(appContext).dualCellBattery

        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = appContext.registerReceiver(null, ifilter)
            ?: return info

        // === 电量百分比 ===
        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) {
            info.levelPercent = (level * 100.0f / scale).toInt()
        }

        // === 温度 (decicelsius → celsius) ===
        val tempRaw = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        if (tempRaw > 0) {
            info.temperatureCelsius = tempRaw / 10.0f
        } else {
            // fallback: sysfs power_supply
            val sysTemp = SysFsReader.readFloat("/sys/class/power_supply/battery/temp")
            if (!sysTemp.isNaN() && sysTemp > 0) {
                // sysfs temp 单位可能为 decicelsius
                val temp = if (sysTemp > 100) sysTemp / 10.0f else sysTemp
                info.temperatureCelsius = temp
            }
        }

        // === 电压 (mV, 双电芯×2) ===
        info.voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)

        // === 充电状态 ===
        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        info.chargeStatus = chargeStatusToString(status)
        info.isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL

        // === 健康状态 ===
        val health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        info.health = healthToString(health)

        // === 电池技术 ===
        info.technology = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: ""

        // === 容量 (BatteryManager + sysfs) ===
        val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        bm?.let {
            val capacity = it.getLongProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (capacity != Long.MIN_VALUE && capacity > 0) {
                info.capacityDesignMAh = capacity
            }
        }
        // sysfs 容量（更准确）
        val chargeFull = SysFsReader.readLong("/sys/class/power_supply/battery/charge_full")
        if (chargeFull > 0) info.chargeFullMAh = chargeFull / 1000
        val chargeFullDesign = SysFsReader.readLong("/sys/class/power_supply/battery/charge_full_design")
        if (chargeFullDesign > 0) info.chargeFullDesignMAh = chargeFullDesign / 1000
        // 如果 BatteryManager 没有容量，用 sysfs
        if (info.capacityDesignMAh <= 0 && chargeFullDesign > 0) {
            info.capacityDesignMAh = chargeFullDesign / 1000
        }
        if (info.capacityNowMAh <= 0 && chargeFull > 0) {
            info.capacityNowMAh = chargeFull / 1000
        }

        // === 电流 (µA, 带符号) ===
        val currentNow = getCurrentNow()
        info.currentNowUA = currentNow

        // === 功率 = |电压(V) × 电流(A)| = |电压(mV)/1000 × 电流(µA)/1000000| = |电压×电流|/1e9 (W) → ×1000 = mW
        // 简化：功率(mW) = |电压(mV) × 电流(µA)| / 1000000
        val effVoltage = info.effectiveVoltage
        if (effVoltage > 0 && currentNow != 0L) {
            val powerMw = Math.abs(effVoltage.toDouble() * currentNow.toDouble()) / 1_000_000.0
            if (currentNow > 0) {
                info.chargingPowerMw = powerMw.toLong()
                info.isCharging = true
            } else {
                info.dischargingPowerMw = powerMw.toLong()
            }
        }

        // === 循环次数 ===
        info.cycleCount = getBatteryCycleCount()

        // === dumpsys battery 附加信息 ===
        try {
            val dumpsysBattery = ShellCommandDataSource.getDumpsysBattery()
            if (dumpsysBattery.isNotEmpty()) {
                // 最大充电电流
                val maxCurrent = ShellCommandDataSource.extractLong(dumpsysBattery, "Max charging current")
                if (maxCurrent > 0) info.maxChargingCurrentUA = maxCurrent

                // 最大充电电压
                val maxVoltage = ShellCommandDataSource.extractLong(dumpsysBattery, "Max charging voltage")
                if (maxVoltage > 0) info.maxChargingVoltageUV = maxVoltage

                // Charge counter (已充电量)
                val chargeCounter = ShellCommandDataSource.extractLong(dumpsysBattery, "Charge counter")
                if (chargeCounter > 0) info.chargeCounterUAh = chargeCounter

                // 充电类型
                val acOnline = ShellCommandDataSource.extractDumpsysValue(dumpsysBattery, "AC powered")
                val usbOnline = ShellCommandDataSource.extractDumpsysValue(dumpsysBattery, "USB powered")
                val wirelessOnline = ShellCommandDataSource.extractDumpsysValue(dumpsysBattery, "Wireless powered")
                val dockOnline = ShellCommandDataSource.extractDumpsysValue(dumpsysBattery, "Dock powered")
                val chargerType = StringBuilder()
                if ("true".equals(acOnline, ignoreCase = true)) chargerType.append("AC")
                if ("true".equals(usbOnline, ignoreCase = true)) {
                    if (chargerType.isNotEmpty()) chargerType.append(" + ")
                    chargerType.append("USB")
                }
                if ("true".equals(wirelessOnline, ignoreCase = true)) {
                    if (chargerType.isNotEmpty()) chargerType.append(" + ")
                    chargerType.append("无线")
                }
                if ("true".equals(dockOnline, ignoreCase = true)) {
                    if (chargerType.isNotEmpty()) chargerType.append(" + ")
                    chargerType.append("底座")
                }
                if (chargerType.isNotEmpty()) {
                    info.chargerType = chargerType.toString()
                }
            }
        } catch (_: Exception) {}

        return info
    }

    // ========== 全网循环次数方案 ==========

    private fun getBatteryCycleCount(): Int {
        // Level 1: 直接读取 cycle_count
        val cnt: Long = SysFsReader.readLong("/sys/class/power_supply/battery/cycle_count")
        if (cnt > 0) return cnt.toInt()

        // Level 2: 小米方案 — charge_counter ÷ 设计容量
        val counter = SysFsReader.readLong("/sys/class/power_supply/battery/charge_counter")
        var designCap = SysFsReader.readLong("/sys/class/power_supply/battery/charge_full_design")
        if (designCap <= 0) designCap = SysFsReader.readLong("/sys/class/power_supply/bms/charge_full_design")
        if (counter > 0 && designCap > 0) return (counter / designCap).toInt()

        // Level 3: 三星方案
        val cnt2 = SysFsReader.readLong("/sys/class/power_supply/battery/batt_cycle")
        if (cnt2 > 0) return cnt2.toInt()

        // Level 4: 各厂商系统属性
        val props = arrayOf(
            "ro.vendor.battery.cycle_count",      // 通用厂商
            "persist.vendor.battery.cycle_count",
            "ro.battery.cycle_count",
            "persist.battery.cycle_count",
            "ro.vendor.battery.cycle",             // OPPO/Realme
            "persist.vendor.battery.cycle",
            "ro.vendor.battery.charge_cycle",      // vivo/iQOO
            "persist.vendor.battery.charge_cycle",
            "ro.batt.cycle_count",                 // 华为/荣耀
            "persist.batt.cycle_count",
            "ro.battery_cycle",                    // 索尼
            "persist.battery_cycle",
            "ro.vendor.power.battery_cycle",       // 一加
            "persist.vendor.power.battery_cycle",
            "ro.boot.battery_cycle",               // bootloader传入
        )
        for (prop in props) {
            val value = SysFsReader.readProp(prop)
            if (value.isNotEmpty()) {
                value.trim().toIntOrNull()?.let { if (it > 0) return it }
            }
        }

        // Level 5: 三星 healthd 属性
        val samsungProps = arrayOf(
            "ro.vendor.battery.healthd_cycle",
            "persist.vendor.battery.healthd_cycle",
        )
        for (prop in samsungProps) {
            val value = SysFsReader.readProp(prop)
            if (value.isNotEmpty()) {
                value.trim().toIntOrNull()?.let { if (it > 0) return it }
            }
        }

        return -1
    }

    /**
     * 获取电流 (µA)，按优先级多路径尝试
     */
    private fun getCurrentNow(): Long {
        // 主要路径
        var value = SysFsReader.readLong("/sys/class/power_supply/battery/current_now")
        if (value != -1L && value != Long.MIN_VALUE) return value

        // 备选
        value = SysFsReader.readLong("/sys/class/power_supply/battery/battery_current")
        if (value != -1L && value != Long.MIN_VALUE) return value

        // 三星方案
        value = SysFsReader.readLong("/sys/class/power_supply/battery/current_avg")
        if (value != -1L && value != Long.MIN_VALUE) return value

        // 高通 BMS
        value = SysFsReader.readLong("/sys/class/power_supply/bms/current_now")
        if (value != -1L && value != Long.MIN_VALUE) return value

        // MediaTek
        value = SysFsReader.readLong("/sys/class/power_supply/battery/Charger_Current")
        if (value != -1L && value != Long.MIN_VALUE) return value

        return 0
    }

    private fun chargeStatusToString(status: Int): String = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
        BatteryManager.BATTERY_STATUS_FULL -> "已充满"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
        else -> "未知"
    }

    private fun healthToString(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
        BatteryManager.BATTERY_HEALTH_DEAD -> "损坏"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "故障"
        BatteryManager.BATTERY_HEALTH_COLD -> "过冷"
        else -> "未知"
    }
}
