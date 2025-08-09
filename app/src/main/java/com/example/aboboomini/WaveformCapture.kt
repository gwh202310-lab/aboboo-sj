package com.example.aboboomini

object WaveformCapture {
    private val buffer = mutableListOf<Float>()
    var capturedDurationMs: Long = 3000 // approximate sample capture window length

    fun pushSamples(f: FloatArray) {
        synchronized(buffer) {
            for (v in f) buffer.add(v)
            if (buffer.size > 20000) buffer.subList(0, buffer.size - 20000).clear()
        }
    }

    // capture a copy of recent samples (approximate)
    suspend fun captureFromCurrent(): FloatArray {
        synchronized(buffer) {
            return buffer.toFloatArray()
        }
    }

    // called by AudioProcessor
    fun captureFromPlayer(player: com.google.android.exoplayer2.ExoPlayer, ms: Int): FloatArray {
        // For demo: return current buffer snapshot and set capturedDurationMs to ms
        capturedDurationMs = ms.toLong()
        synchronized(buffer) {
            return buffer.toFloatArray()
        }
    }
}
