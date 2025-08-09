package com.example.aboboomini

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.android.exoplayer2.ExoPlayer
import kotlin.math.abs

class WaveformView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint().apply { isAntiAlias = true }
    private var player: ExoPlayer? = null
    private var samples: FloatArray = FloatArray(0)
    private var segments: List<Pair<Long, Long>> = emptyList()

    private var selStart = 0f; private var selEnd = 0f
    private var looping = false

    private var listener: SelectionListener? = null

    fun setOnSelectionListener(l: SelectionListener) { listener = l }

    fun attachPlayer(p: ExoPlayer) {
        player = p
        VisualizerHook.startCapture(p, ::onNewSamples)
    }
    fun detachPlayer() { VisualizerHook.stopCapture(); player = null }

    private fun onNewSamples(s: FloatArray) {
        samples = s
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width; val h = height
        paint.color = 0xFF66BB6A.toInt()
        if (samples.isNotEmpty()) {
            val n = samples.size
            for (i in 0 until n - 1) {
                val x1 = i * w / n.toFloat()
                val y1 = h/2f - samples[i] / 128f * (h/2f)
                val x2 = (i+1) * w / n.toFloat()
                val y2 = h/2f - samples[i+1] / 128f * (h/2f)
                canvas.drawLine(x1, y1, x2, y2, paint)
            }
        }

        // draw segments markers
        paint.color = 0x44FFFFFF
        for (seg in segments) {
            // map ms->x
            val dur = player?.duration ?: 1
            val left = seg.first.toFloat() / dur * w
            val right = seg.second.toFloat() / dur * w
            canvas.drawRect(left, 0f, right, h.toFloat(), paint)
        }

        // draw selection
        if (selEnd > selStart + 0.01f) {
            paint.color = 0x77FFEB3B.toInt()
            canvas.drawRect(selStart * w, 0f, selEnd * w, h.toFloat(), paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                selStart = x / width; selEnd = selStart
                listener?.onSelectionChanged((selStart * (player?.duration ?: 0)).toLong(), (selEnd * (player?.duration ?: 0)).toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                selEnd = (x / width).coerceIn(0f, 1f)
                listener?.onSelectionChanged((selStart * (player?.duration ?: 0)).toLong(), (selEnd * (player?.duration ?: 0)).toLong())
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                listener?.onSelectionChanged((selStart * (player?.duration ?: 0)).toLong(), (selEnd * (player?.duration ?: 0)).toLong())
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun hasSelection(): Boolean = selEnd > selStart + 0.01f
    fun getSelectionInMs(totalMs: Long): Pair<Long, Long> {
        val s = (selStart * totalMs).toLong(); val e = (selEnd * totalMs).toLong(); return s to e
    }

    fun setSegments(list: List<Pair<Long, Long>>) { segments = list; invalidate() }
    fun getSegmentsMs(): List<Segment> = segments.map { Segment(it.first, it.second) }
    fun setLooping(v: Boolean) { looping = v }

    interface SelectionListener {
        fun onSelectionChanged(startMs: Long, endMs: Long)
        fun onSegmentClicked(startMs: Long, endMs: Long)
    }
}
