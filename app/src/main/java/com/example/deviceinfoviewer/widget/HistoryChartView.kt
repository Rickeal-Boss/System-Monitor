package com.example.deviceinfoviewer.widget

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.widget.LinearLayout
import com.example.deviceinfoviewer.data.model.HistoryDataPoint
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 历史图表控件 — 深色主题适配
 */
class HistoryChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        // 深色主题色
        private const val COLOR_TEXT_SECONDARY = 0xFF8B949E.toInt()
        private const val COLOR_GRID = 0xFF30363D.toInt()
        private const val COLOR_AXIS = 0xFF484F58.toInt()
        private const val DEFAULT_LINE_COLOR = 0xFFFF7043.toInt()
    }

    val lineChart: LineChart

    init {
        orientation = VERTICAL
        lineChart = LineChart(context)
        lineChart.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(lineChart)

        lineChart.description.isEnabled = false
        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.isScaleXEnabled = true
        lineChart.isScaleYEnabled = true
        lineChart.isPinchZoomEnabled = true
        lineChart.setDrawGridBackground(false)

        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.setDrawAxisLine(true)
        xAxis.axisLineColor = COLOR_AXIS
        xAxis.textColor = COLOR_TEXT_SECONDARY
        xAxis.textSize = 10f
        xAxis.valueFormatter = object : ValueFormatter() {
            private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            override fun getFormattedValue(value: Float): String {
                return sdf.format(Date(value.toLong()))
            }
        }

        val leftAxis = lineChart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = COLOR_GRID
        leftAxis.gridLineWidth = 0.5f
        leftAxis.setDrawAxisLine(false)
        leftAxis.textColor = COLOR_TEXT_SECONDARY
        leftAxis.textSize = 10f
        leftAxis.axisMinimum = 0f

        lineChart.axisRight.isEnabled = false
        lineChart.legend.isEnabled = false
        lineChart.setNoDataText("暂无数据")
        lineChart.setNoDataTextColor(COLOR_TEXT_SECONDARY)
    }

    fun addDataPoint(label: String, timestampMillis: Long, value: Float) {
        var data = lineChart.data
        if (data == null) {
            data = LineData()
            lineChart.data = data
        }

        var set = data.getDataSetByLabel(label, true) as? LineDataSet
        if (set == null) {
            set = LineDataSet(ArrayList(), label)
            set.color = DEFAULT_LINE_COLOR
            set.setCircleColor(DEFAULT_LINE_COLOR)
            set.lineWidth = 2.5f
            set.circleRadius = 3f
            set.setDrawValues(false)
            set.mode = LineDataSet.Mode.CUBIC_BEZIER
            set.cubicIntensity = 0.05f
            set.setDrawFilled(true)
            try {
                set.fillDrawable = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0x60FF7043.toInt(), 0x05FF7043.toInt())
                )
            } catch (_: Exception) {}
            data.addDataSet(set)
        }

        data.addEntry(Entry(timestampMillis.toFloat(), value), data.getIndexOfDataSet(set))
        data.notifyDataChanged()
        lineChart.notifyDataSetChanged()
        lineChart.setVisibleXRangeMaximum(60f)
        lineChart.moveViewToX(timestampMillis.toFloat())
    }

    fun setData(label: String, points: List<HistoryDataPoint>?) {
        if (points.isNullOrEmpty()) {
            lineChart.clear()
            return
        }

        val entries = points.map { Entry(it.timestampMillis.toFloat(), it.value) }

        val set = LineDataSet(entries, label)
        set.color = DEFAULT_LINE_COLOR
        set.setCircleColor(DEFAULT_LINE_COLOR)
        set.lineWidth = 2.5f
        set.circleRadius = 3f
        set.setDrawValues(false)
        set.mode = LineDataSet.Mode.CUBIC_BEZIER
        set.cubicIntensity = 0.05f
        set.setDrawFilled(true)
        try {
            set.fillDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x60FF7043.toInt(), 0x05FF7043.toInt())
            )
        } catch (_: Exception) {}

        val data = LineData(set)
        lineChart.data = data
        lineChart.notifyDataSetChanged()
        lineChart.invalidate()
    }

    fun clear() {
        lineChart.clear()
    }
}
