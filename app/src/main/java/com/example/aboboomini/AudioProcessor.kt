package com.example.aboboomini

import android.content.Context
import com.google.android.exoplayer2.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 注意：为了兼容性，本示例使用 ExoPlayer 播放时的音频抓取回调来近似计算能量并做静音检测。
// 若要更精确，请把音频完整解码为 PCM（使用 MediaExtractor+MediaCodec 或 FFmpeg）并基于采样帧做检测。

class AudioProcessor(private val ctx: Context, private val player: ExoPlayer) {

    // 自动分句（返回 List<Pair<startMs, endMs>>）
    suspend fun autoSegment(minSilenceMs: Int = 400, silenceRatio: Float = 0.5f): List<Pair<Long, Long>> {
        return withContext(Dispatchers.Default) {
            // 方案：在播放器播放短时段并通过 WaveformView 的实时缓冲获取幅度样本
            val samples = WaveformCapture.captureFromPlayer(player, 3000)
            if (samples.isEmpty()) return@withContext emptyList()

            // 平滑
            val window = 6
            val smooth = FloatArray(samples.size)
            for (i in samples.indices) {
                var s = 0f; var cnt = 0
                for (j in maxOf(0, i - window)..minOf(samples.size - 1, i + window)) { s += samples[j]; cnt++ }
                smooth[i] = s / cnt
            }

            val avg = smooth.average().toFloat()
            val threshold = avg * silenceRatio

            val silence = BooleanArray(smooth.size)
            for (i in smooth.indices) silence[i] = smooth[i] < threshold

            // 找非静音段
            val segsIdx = mutableListOf<Pair<Int, Int>>()
            var i = 0
            while (i < silence.size) {
                while (i < silence.size && silence[i]) i++
                if (i >= silence.size) break
                val s = i
                while (i < silence.size && !silence[i]) i++
                val e = i - 1
                segsIdx.add(s to e)
            }

            // 映射到毫秒：samples 采样时长 approx capturedMs
            val capturedMs = WaveformCapture.capturedDurationMs
            val durationMs = player.duration
            val segMs = segsIdx.map { (s, e) ->
                val start = (s.toLong() * durationMs / maxOf(1, smooth.size)).coerceAtLeast(0L)
                val end = (e.toLong() * durationMs / maxOf(1, smooth.size)).coerceAtMost(durationMs)
                start to end
            }
            segMs
        }
    }
}
