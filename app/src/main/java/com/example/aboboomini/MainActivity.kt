package com.example.aboboomini

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var waveView: WaveformView
    private lateinit var btnOpen: Button
    private lateinit var btnPlay: Button
    private lateinit var btnLoop: Button
    private lateinit var btnAutoSegment: Button
    private lateinit var btnSave: Button
    private lateinit var btnLoad: Button
    private lateinit var tvInfo: TextView
    private val uiScope = CoroutineScope(Dispatchers.Main + Job())

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { onPicked(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        waveView = findViewById(R.id.waveView)
        btnOpen = findViewById(R.id.btnOpen)
        btnPlay = findViewById(R.id.btnPlay)
        btnLoop = findViewById(R.id.btnLoop)
        btnAutoSegment = findViewById(R.id.btnAutoSegment)
        btnSave = findViewById(R.id.btnSaveSegments)
        btnLoad = findViewById(R.id.btnLoadSegments)
        tvInfo = findViewById(R.id.tvInfo)

        btnOpen.setOnClickListener { pickFile.launch(arrayOf("audio/*","video/*")) }
        btnPlay.setOnClickListener { togglePlay() }
        btnLoop.setOnClickListener { toggleLoop() }
        btnAutoSegment.setOnClickListener { autoSegment() }
        btnSave.setOnClickListener { saveSegments() }
        btnLoad.setOnClickListener { loadSegments() }

        waveView.setOnSelectionListener(object: WaveformView.SelectionListener{
            override fun onSelectionChanged(startMs: Long, endMs: Long) {
                tvInfo.text = "选择: ${'$'}{startMs}ms - ${'$'}{endMs}ms"
            }

            override fun onSegmentClicked(startMs: Long, endMs: Long) {
                // 点击分句直接跳到该段并播放
                player?.seekTo(startMs)
                player?.play()
            }
        })
    }

    private fun onPicked(uri: Uri) {
        tvInfo.text = getFileName(uri)
        releasePlayer()
        player = ExoPlayer.Builder(this).build().also { exo ->
            val item = MediaItem.fromUri(uri)
            exo.setMediaItem(item)
            exo.prepare()
            // feed audio visualization
            waveView.attachPlayer(exo)
        }
    }

    private fun getFileName(uri: Uri): String {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else uri.lastPathSegment ?: "file"
            } ?: uri.lastPathSegment ?: "file"
        } catch (e: Exception) { uri.lastPathSegment ?: "file" }
    }

    private fun togglePlay() {
        player?.let {
            if (it.isPlaying) { it.pause(); btnPlay.text = "播放" }
            else { // 如果有选择则从选择开始播放
                if (waveView.hasSelection()) {
                    val sel = waveView.getSelectionInMs(it.duration)
                    it.seekTo(sel.first)
                }
                it.play(); btnPlay.text = "暂停"
            }
        }
    }

    private var looping = false
    private fun toggleLoop() {
        looping = !looping
        btnLoop.text = if (looping) "循环: 开" else "循环选择"
        waveView.setLooping(looping)
    }

    private fun autoSegment() {
        // 启动后台处理并传回 segments
        player?.let { p ->
            val ap = AudioProcessor(this, p)
            uiScope.launch {
                val segments = ap.autoSegment(400, 0.5f) // 静音阈值/静默长度(ms)
                waveView.setSegments(segments)
                tvInfo.text = "检测到 ${'$'}{segments.size} 段"
            }
        }
    }

    private fun saveSegments() {
        val segs = waveView.getSegmentsMs()
        val gson = Gson()
        val json = gson.toJson(segs)
        val f = File(filesDir, "segments.json")
        f.writeText(json)
        tvInfo.text = "已保存 ${'$'}{segs.size} 段 到 ${'$'}{f.absolutePath}"
    }

    private fun loadSegments() {
        val f = File(filesDir, "segments.json")
        if (!f.exists()) { tvInfo.text = "没有找到已保存的分句"; return }
        val json = f.readText()
        val gson = Gson()
        val arr = gson.fromJson(json, Array<Segment>::class.java).toList()
        waveView.setSegments(arr.map { it.start to it.end })
        tvInfo.text = "已加载 ${'$'}{arr.size} 段"
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        waveView.detachPlayer()
    }
}
