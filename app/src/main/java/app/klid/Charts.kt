package app.klid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** Seven days as bars, today last and highlighted, the value above each bar. */
class BarChart(
    context: Context,
    private val todayColor: Int,
    private val barColor: Int,
    private val emptyColor: Int,
    private val labelColor: Int,
    private val strongColor: Int,
    /** Shown across the chart while every day is zero, so an empty chart doesn't look broken. */
    private val emptyText: String,
) : View(context) {

    private var values = IntArray(0)
    private var labels = arrayOf<String>()
    private val density = resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        fontFeatureSettings = "tnum"
    }
    private val rect = RectF()
    private val path = Path()

    fun setData(newValues: IntArray, newLabels: Array<String>) {
        if (newValues.contentEquals(values) && newLabels.contentEquals(labels)) return
        values = newValues.copyOf()
        labels = newLabels.copyOf()
        contentDescription = "Last 7 days: " +
            labels.indices.joinToString(", ") { "${labels[it]} ${values[it]}" }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = values.size
        if (n == 0 || labels.size != n) return
        val line = textPaint.fontSpacing
        val chartTop = line + 6 * density
        val chartBottom = height - line - 6 * density
        val chartHeight = max(chartBottom - chartTop, 1f)
        val slot = width / n.toFloat()
        val barWidth = min(slot * 0.56f, 28 * density)
        val radius = min(barWidth / 2f, 6 * density)
        val corners = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        val peak = max(values.maxOrNull() ?: 0, 1)
        for (i in 0 until n) {
            val v = values[i]
            val isToday = i == n - 1
            val cx = slot * i + slot / 2f
            val barHeight = if (v == 0) 3 * density else max(chartHeight * v / peak, 6 * density)
            rect.set(cx - barWidth / 2f, chartBottom - barHeight, cx + barWidth / 2f, chartBottom)
            barPaint.color = when {
                v == 0 -> emptyColor
                isToday -> todayColor
                else -> barColor
            }
            path.reset()
            path.addRoundRect(rect, corners, Path.Direction.CW)
            canvas.drawPath(path, barPaint)
            if (v > 0) {
                textPaint.color = strongColor
                canvas.drawText(v.toString(), cx, rect.top - 4 * density, textPaint)
            }
            textPaint.color = if (isToday) strongColor else labelColor
            canvas.drawText(labels[i], cx, height - 2 * density - textPaint.descent(), textPaint)
        }
        if (values.all { it == 0 }) {
            textPaint.color = labelColor
            val baseline = chartTop + chartHeight / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(emptyText, width / 2f, baseline, textPaint)
        }
    }
}

/** A thin rounded progress line that changes to [overColor] once it is full. */
class UsageBar(
    context: Context,
    private val trackColor: Int,
    private val fillColor: Int,
    private val overColor: Int,
) : View(context) {

    var fraction = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (clamped != field) {
                field = clamped
                invalidate()
            }
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = height / 2f
        paint.color = trackColor
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, r, r, paint)
        if (fraction > 0f) {
            paint.color = if (fraction >= 1f) overColor else fillColor
            rect.set(0f, 0f, max(height.toFloat(), width * fraction), height.toFloat())
            canvas.drawRoundRect(rect, r, r, paint)
        }
    }
}
