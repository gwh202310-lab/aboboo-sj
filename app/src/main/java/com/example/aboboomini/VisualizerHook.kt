package com.example.aboboomini

import android.media.audiofx.Visualizer
import com.google.android.exoplayer2.ExoPlayer
import kotlin.math.abs

object VisualizerHook {
    private var visualizer: Visualizer? = null
    private var currentPlayer: ExoPlayer? = null
    private var listener: ((FloatArray)->Unit)? = null

    fun startCapture(player: ExoPlayer, onSamples: (FloatArray)->Unit) {
        stopCapture()
        currentPlayer = player
        listener = onSamples
        try {
            val sessionId = player.audioSessionId
            if (sessionId == 0) return
            visualizer = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object: Visualizer.OnDataCaptureListener{
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, rate: Int) {
                        waveform?.let {
                            val f = FloatArray(it.size)
                            for (i in it.indices) f[i] = abs(it[i].toInt()).toFloat()
                            listener?.invoke(f)
                            WaveformCapture.pushSamples(f)
                        }
                    }
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {}
                }, Visualizer.getMaxCaptureRate()/2, true, false)
                enabled = true
            }
        } catch (e: Exception) { }
    }

    fun stopCapture() {
        try { visualizer?.enabled = false; visualizer?.release() } catch (e: Exception) {}
        visualizer = null; currentPlayer = null; listener = null
    }
}
