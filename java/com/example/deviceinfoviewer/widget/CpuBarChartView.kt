package com.example.deviceinfoviewer.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import com.example.deviceinfoviewer.data.model.CpuCoreInfo

/**
 * 自定义 CPU 核心频率条形图，Canvas 绘制水平条形图
 * 颜色按频率渐变：绿(<1.5GHz) 黄(1.5-2.5GHz) 红(>2.5GHz)
 */
class CpuBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val BAR_HEIGHT_DP = 36f
        private const val TEXT_SIZE_SP = 12f
        private const val PADDING_DP = 8f
        private const val GREEN_THRESHOLD_KHZ = 1_500_000L  // 1.5 GHz
        private const val YELLOW_THRESHOLD_KHZ = 2_500_000L // 2.5 GHz
    }

    private val cores = mutableListOf<CpuCoreInfo>()
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint()
    private val textBounds = Rect()

    private val density: Float = resources.displayMetrics.density
    private val barHeight: Float = BAR_HEIGHT_DP * density
    private val textSize: Float = TEXT_SIZE_SP * density
    private val padding: Float = PADDING_DP * density
    private var maxFreqKHz: Long = 1

    init {
        textPaint.textSize = textSize
        textPaint.color = Color.BLACK
        textPaint.isFakeBoldText = false
        bgPaint.color = Color.parseColor("#F5F5F5")
    }

    /**
     * 更新 CPU 核心数据
     */
    fun setCores(cores: List<CpuCoreInfo>?) {
        this.cores.clear()
        cores?.let { this.cores.addAll(it) }
        // 计算最大频率以确定条形图比例
        maxFreqKHz = this.cores.maxOfOrNull { it.maxFreqKHz } ?: 1L
        if (maxFreqKHz <= 0) {
            maxFreqKHz = 1
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (cores.size * (barHeight + padding * 2) + padding * 2).toInt()
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, maxOf(desiredHeight, (barHeight + padding * 3).toInt()))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width - paddingLeft - paddingRight
        if (width <= 0 || cores.isEmpty()) {
            textPaint.color = Color.GRAY
            canvas.drawText("无数据", padding, barHeight, textPaint)
            return
        }

        var y = padding
        // 标签区域宽度
        val labelWidth = width * 0.25f
        val barAreaWidth = width - labelWidth - padding

        for (core in cores) {
            // 背景
            bgPaint.color = Color.parseColor("#F5F5F5")
            canvas.drawRect(padding, y, width.toFloat(), y + barHeight, bgPaint)

            // 标签
            textPaint.color = Color.DKGRAY
            val label = "核心${core.coreIndex}"
            canvas.drawText(label, padding, y + barHeight * 0.65f, textPaint)

            // 条形图
            val ratio = if (core.currentFreqKHz > 0)
                core.currentFreqKHz.toFloat() / maxFreqKHz
            else 0f
            val barWidth = ratio * barAreaWidth
            val barX = labelWidth + padding

            // 颜色根据频率比例
            val color = getFreqColor(core.currentFreqKHz)
            barPaint.color = color
            canvas.drawRoundRect(
                barX, y + padding, barX + barWidth, y + barHeight - padding,
                density * 4, density * 4, barPaint
            )

            // 频率文字
            textPaint.color = Color.WHITE
            val freqText = formatFreq(core.currentFreqKHz)
            textPaint.getTextBounds(freqText, 0, freqText.length, textBounds)
            val textY = y + barHeight / 2f + textBounds.height() / 2f
            if (barWidth > textBounds.width() + padding) {
                canvas.drawText(freqText, barX + padding, textY, textPaint)
            } else {
                textPaint.color = Color.DKGRAY
                canvas.drawText(freqText, barX + barWidth + padding, textY, textPaint)
            }

            y += barHeight + padding
        }
    }

    private fun getFreqColor(freqKHz: Long): Int {
        if (freqKHz <= 0) return Color.GRAY
        if (freqKHz < GREEN_THRESHOLD_KHZ) return Color.rgb(76, 175, 80)    // 绿
        if (freqKHz < YELLOW_THRESHOLD_KHZ) return Color.rgb(255, 193, 7)   // 黄
        return Color.rgb(244, 67, 54) // 红
    }

    private fun formatFreq(khz: Long): String {
        if (khz <= 0) return "N/A"
        if (khz >= 1_000_000L) return String.format("%.2f GHz", khz / 1_000_000.0)
        if (khz >= 1_000L) return String.format("%.0f MHz", khz / 1_000.0)
        return "$khz KHz"
    }
}
