package com.example.deviceinfoviewer.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.deviceinfoviewer.data.model.HistoryDataPoint
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.text.SimpleDateFormat
import java.util.*

/**
 * DevCheck Pro 风格监控图表
 */
class MonitorChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "MonitorChart"
        private const val COLOR_TEXT_PRIMARY = 0xFFE6EDF3.toInt()
        private const val COLOR_TEXT_SECONDARY = 0xFF8B949E.toInt()
        private const val COLOR_GRID = 0xFF30363D.toInt()
        private const val COLOR_AXIS = 0xFF484F58.toInt()
        private const val DEFAULT_CHART_COLOR = 0xFFFF9800.toInt()

        private fun dp(dp: Float, ctx: Context): Int =
            (dp * ctx.resources.displayMetrics.density).toInt()
    }

    private var tvTitle: TextView? = null
    private var tvCurrentValue: TextView? = null
    var lineChart: LineChart? = null
        private set

    private var chartColor: Int = DEFAULT_CHART_COLOR
    private var valueFormat: String = "%.1f"
    private var valueSuffix: String = ""
    private var seriesName: String = ""

    init {
        safeInit(context)
    }

    private fun safeInit(ctx: Context) {
        try {
            orientation = VERTICAL
            tvTitle = buildHeaderRow(ctx)
            try {
                lineChart = buildChart(ctx)
                lineChart?.let { configureChart(it) }
            } catch (t: Throwable) {
                Log.e(TAG, "LineChart init failed", t)
                lineChart = null
                tvTitle?.text = "${tvTitle?.text} (加载失败)"
            }
            tvCurrentValue = findCurrentValueView()
        } catch (t: Throwable) {
            Log.e(TAG, "safeInit failed", t)
            try {
                val err = TextView(ctx)
                err.text = "图表加载失败"
                err.setTextColor(COLOR_TEXT_SECONDARY)
                err.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                val p = dp(16, ctx)
                err.setPadding(p, p, p, p)
                addView(err)
            } catch (_: Throwable) {}
        }
    }

    private fun buildHeaderRow(ctx: Context): TextView {
        val headerRow = LinearLayout(ctx).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p4 = dp(4, ctx)
            val p2 = dp(2, ctx)
            setPadding(p4, p4, p4, p2)
        }

        val title = TextView(ctx).apply {
            id = View.generateViewId()
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        val current = TextView(ctx).apply {
            id = View.generateViewId()
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }

        headerRow.addView(title)
        headerRow.addView(current)
        addView(headerRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        return title
    }

    private fun buildChart(ctx: Context): LineChart =
        LineChart(ctx).apply {
            id = View.generateViewId()
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(130, ctx))
            addView(this)
        }

    private fun findCurrentValueView(): TextView? {
        try {
            if (childCount > 0) {
                val header = getChildAt(0)
                if (header is LinearLayout && header.childCount >= 2) {
                    val v = header.getChildAt(1)
                    if (v is TextView) return v
                }
            }
        } catch (_: Throwable) {}
        return null
    }

    private fun configureChart(chart: LineChart) {
        try {
            chart.description.isEnabled = false
            chart.setTouchEnabled(true)
            chart.isDragEnabled = true
            chart.isScaleXEnabled = true
            chart.isScaleYEnabled = true
            chart.setPinchZoom(true)
            chart.setDrawGridBackground(false)
            chart.setExtraOffsets(0f, 4f, 0f, 8f)
            chart.axisRight.isEnabled = false
            chart.legend.isEnabled = false
            chart.setNoDataText("等待数据...")
            chart.setNoDataTextColor(COLOR_TEXT_SECONDARY)

            chart.xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(true)
                axisLineColor = COLOR_AXIS
                textColor = COLOR_TEXT_SECONDARY
                textSize = 10f
                granularity = 1f
                setLabelCount(3, true)
                valueFormatter = object : ValueFormatter() {
                    private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    override fun getFormattedValue(value: Float): String =
                        sdf.format(Date(value.toLong()))
                }
            }

            chart.axisLeft.apply {
                setDrawGridLines(true)
                gridColor = COLOR_GRID
                gridLineWidth = 0.5f
                setDrawAxisLine(false)
                textColor = COLOR_TEXT_SECONDARY
                textSize = 10f
                axisMinimum = 0f
                setLabelCount(3, true)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "configureChart failed", t)
        }
    }

    // ---- 公开 API ----

    fun setTitle(title: String?) {
        seriesName = title ?: ""
        tvTitle?.text = title
    }

    fun setCurrentValue(value: Float) {
        tvCurrentValue?.text = try {
            String.format(Locale.US, valueFormat, value) + valueSuffix
        } catch (_: Exception) { tvCurrentValue?.text }
    }

    fun setChartColor(color: Int) {
        chartColor = color
        tvCurrentValue?.setTextColor(color)
    }

    fun setValueFormat(format: String, suffix: String) {
        valueFormat = format
        valueSuffix = suffix
    }

    fun setData(points: List<HistoryDataPoint>?) {
        val chart = lineChart
        if (chart == null || points.isNullOrEmpty()) {
            chart?.clear()
            return
        }
        try {
            val entries = points.map { Entry(it.timestampMillis.toFloat(), it.value) }
            val set = LineDataSet(entries, seriesName)
            safeStyleDataSet(set)
            chart.data = LineData(set)
            setCurrentValue(points.last().value)
            chart.notifyDataSetChanged()
            chart.invalidate()
        } catch (e: Exception) { Log.e(TAG, "setData failed", e) }
    }

    fun addDataPoint(timestampMillis: Long, value: Float) {
        val chart = lineChart ?: return
        try {
            var data = chart.data
            if (data == null) {
                data = LineData()
                chart.data = data
            }
            val set = when (val es = data.getDataSetByIndex(0)) {
                is LineDataSet -> es
                else -> LineDataSet(ArrayList(), seriesName).also { safeStyleDataSet(it); data.addDataSet(it) }
            }
            data.addEntry(Entry(timestampMillis.toFloat(), value), 0)
            setCurrentValue(value)
            data.notifyDataChanged()
            chart.notifyDataSetChanged()
            chart.setVisibleXRangeMaximum(60f)
            chart.moveViewToX(timestampMillis.toFloat())
        } catch (_: Exception) {}
    }

    private fun safeStyleDataSet(set: LineDataSet) {
        try {
            set.color = chartColor
            set.setCircleColor(chartColor)
            set.lineWidth = 2.5f
            set.circleRadius = 2f
            set.setDrawCircleHole(false)
            set.setDrawValues(false)
            set.mode = LineDataSet.Mode.CUBIC_BEZIER
            set.cubicIntensity = 0.05f
            set.setDrawFilled(true)
            try {
                val r = Color.red(chartColor)
                val g = Color.green(chartColor)
                val b = Color.blue(chartColor)
                set.fillDrawable = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.argb(90, r, g, b), Color.argb(5, r, g, b))
                )
            } catch (_: Exception) {
                set.fillColor = (chartColor and 0x00FFFFFF) or 0x30000000.toInt()
            }
        } catch (e: Exception) { Log.e(TAG, "styleDataSet failed", e) }
    }

    fun clear() = lineChart?.clear()
}
