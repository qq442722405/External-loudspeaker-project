package com.carpa

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Bundle
import android.os.Process
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var audioManager: AudioManager
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var running = false
    private var selectedInput: AudioDeviceInfo? = null
    private var selectedOutput: AudioDeviceInfo? = null
    private lateinit var status: TextView
    private lateinit var inputSpinner: Spinner
    private lateinit var outputSpinner: Spinner
    private lateinit var volume: SeekBar
    private lateinit var talkButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        buildUi()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
        refreshDevices()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 20, 30, 20)
            setBackgroundColor(0xFF101010.toInt())
        }
        val title = TextView(this).apply {
            text = "车外喊话器"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = 17
        }
        root.addView(title, LinearLayout.LayoutParams(-1, 70))

        val row = LinearLayout(this)
        inputSpinner = Spinner(this)
        outputSpinner = Spinner(this)
        row.addView(labelled("麦克风", inputSpinner), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(labelled("喇叭 / 输出", outputSpinner), LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row)

        status = TextView(this).apply {
            text = "正在检测音频设备..."
            textSize = 17f
            setTextColor(0xFFBDBDBD.toInt())
            gravity = 17
        }
        root.addView(status, LinearLayout.LayoutParams(-1, 60))

        talkButton = Button(this).apply {
            text = "长按说话"
            textSize = 28f
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startTalk()
                        text = "正在喊话…"
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        stopTalk()
                        text = "长按说话"
                        true
                    }
                    else -> true
                }
            }
        }
        root.addView(talkButton, LinearLayout.LayoutParams(-1, 260).apply {
            setMargins(0, 20, 0, 20)
        })

        val volText = TextView(this).apply {
            text = "音量"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
        }
        root.addView(volText)
        volume = SeekBar(this).apply {
            max = 100
            progress = 70
        }
        root.addView(volume)

        val refresh = Button(this).apply {
            text = "重新扫描音频设备"
            setOnClickListener { refreshDevices() }
        }
        root.addView(refresh)

        setContentView(root)
    }

    private fun labelled(name: String, spinner: Spinner): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val t = TextView(this).apply {
            text = name
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
        }
        box.addView(t)
        box.addView(spinner)
        return box
    }

    private fun refreshDevices() {
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()

        inputSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            inputs.map { "${it.productName}  [ID ${it.id}]  类型=${typeName(it.type)}" })
        outputSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            outputs.map { "${it.productName}  [ID ${it.id}]  类型=${typeName(it.type)}" })

        inputSpinner.onItemSelectedListener = SimpleSelect { p ->
            if (p in inputs.indices) selectedInput = inputs[p]
        }
        outputSpinner.onItemSelectedListener = SimpleSelect { p ->
            if (p in outputs.indices) selectedOutput = outputs[p]
        }

        status.text = "检测到：输入 ${inputs.size} 个，输出 ${outputs.size} 个"
    }

    private fun typeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "内置麦克风"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB耳机"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "蓝牙A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "蓝牙SCO"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "内置扬声器"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "有线耳机"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "有线耳麦"
        else -> "其他($type)"
    }

    private fun startTalk() {
        if (running) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return

        val sr = 48000
        val min = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSize = maxOf(min * 2, 4096)

        val format = AudioFormat.Builder()
            .setSampleRate(sr)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .build()

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        player = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        selectedInput?.let { recorder?.setPreferredDevice(it) }
        selectedOutput?.let { player?.preferredDevice = it }

        val vol = volume.progress / 100f
        player?.setVolume(vol)
        running = true
        recorder?.startRecording()
        player?.play()

        thread(start = true, name = "CarPA") {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val buf = ByteArray(bufferSize)
            while (running) {
                val n = recorder?.read(buf, 0, buf.size) ?: 0
                if (n > 0) player?.write(buf, 0, n)
            }
        }
        status.text = "正在使用：${selectedInput?.productName ?: "默认麦克风"} → ${selectedOutput?.productName ?: "默认输出"}"
    }

    private fun stopTalk() {
        running = false
        try { recorder?.stop() } catch (_: Exception) {}
        try { player?.pause() } catch (_: Exception) {}
        recorder?.release()
        player?.release()
        recorder = null
        player = null
        status.text = "喊话已停止"
    }

    override fun onDestroy() {
        stopTalk()
        super.onDestroy()
    }

    private class SimpleSelect(val fn: (Int) -> Unit) : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = fn(pos)
        override fun onNothingSelected(p: AdapterView<*>?) {}
    }
}
