```kotlin
package com.carpa.acc2

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
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

    private lateinit var inputSpinner: Spinner
    private lateinit var outputSpinner: Spinner
    private lateinit var volume: SeekBar
    private lateinit var talkButton: Button
    private lateinit var status: TextView

    private var inputDevices = emptyList<AudioDeviceInfo>()
    private var outputDevices = emptyList<AudioDeviceInfo>()

    private var selectedInput: AudioDeviceInfo? = null
    private var selectedOutput: AudioDeviceInfo? = null

    @Volatile
    private var running = false

    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 隐藏状态栏和导航栏
        try {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        } catch (_: Exception) {
        }

        audioManager =
            getSystemService(Context.AUDIO_SERVICE) as AudioManager

        buildUi()

        // 请求麦克风权限
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_MIC
            )
        }

        refreshDevices()
    }

    // ============================================================
    // UI
    // ============================================================

    private fun buildUi() {

        val root = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            setPadding(
                30,
                20,
                30,
                20
            )

            setBackgroundColor(
                0xFF101010.toInt()
            )
        }

        // 标题
        val title = TextView(this).apply {

            text = "喊话"

            textSize = 30f

            setTextColor(
                0xFFFFFFFF.toInt()
            )

            gravity = Gravity.CENTER
        }

        root.addView(
            title,
            LinearLayout.LayoutParams(
                -1,
                70
            )
        )

        // 麦克风 / 喇叭
        val deviceRow = LinearLayout(this).apply {

            orientation =
                LinearLayout.HORIZONTAL
        }

        inputSpinner = Spinner(this)

        outputSpinner = Spinner(this)

        deviceRow.addView(
            labelled(
                "麦克风",
                inputSpinner
            ),
            LinearLayout.LayoutParams(
                0,
                -2,
                1f
            )
        )

        deviceRow.addView(
            labelled(
                "喇叭 / 输出",
                outputSpinner
            ),
            LinearLayout.LayoutParams(
                0,
                -2,
                1f
            )
        )

        root.addView(deviceRow)

        // 状态
        status = TextView(this).apply {

            text = "正在检测音频设备..."

            textSize = 17f

            setTextColor(
                0xFFBDBDBD.toInt()
            )

            gravity = Gravity.CENTER
        }

        root.addView(
            status,
            LinearLayout.LayoutParams(
                -1,
                70
            )
        )

        // ========================================================
        // 长按按钮
        // ========================================================

        talkButton = Button(this).apply {

            text = "长按说话"

            textSize = 28f

            isAllCaps = false

            setOnTouchListener { _, event ->

                try {

                    when (event.actionMasked) {

                        MotionEvent.ACTION_DOWN -> {

                            startTalk()

                            true
                        }

                        MotionEvent.ACTION_UP -> {

                            stopTalk()

                            true
                        }

                        MotionEvent.ACTION_CANCEL -> {

                            stopTalk()

                            true
                        }

                        else -> true
                    }

                } catch (e: Exception) {

                    stopTalk()

                    text = "长按说话"

                    status.text =
                        "音频错误：${e.message ?: "未知错误"}"

                    true
                }
            }
        }

        root.addView(
            talkButton,
            LinearLayout.LayoutParams(
                -1,
                260
            ).apply {

                setMargins(
                    0,
                    20,
                    0,
                    20
                )
            }
        )

        // ========================================================
        // 音量
        // ========================================================

        val volumeTitle =
            TextView(this).apply {

                text = "喇叭音量"

                textSize = 18f

                setTextColor(
                    0xFFFFFFFF.toInt()
                )
            }

        root.addView(volumeTitle)

        volume = SeekBar(this).apply {

            max = 100

            progress = 70

            setOnSeekBarChangeListener(

                object :
                    SeekBar.OnSeekBarChangeListener {

                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {

                        try {

                            player?.setVolume(
                                progress / 100f
                            )

                        } catch (_: Exception) {
                        }
                    }

                    override fun onStartTrackingTouch(
                        seekBar: SeekBar?
                    ) {
                    }

                    override fun onStopTrackingTouch(
                        seekBar: SeekBar?
                    ) {
                    }
                }
            )
        }

        root.addView(volume)

        // ========================================================
        // 重新扫描
        // ========================================================

        val refresh =
            Button(this).apply {

                text = "重新扫描音频设备"

                isAllCaps = false

                setOnClickListener {

                    stopTalk()

                    refreshDevices()
                }
            }

        root.addView(refresh)

        setContentView(root)
    }

    private fun labelled(
        name: String,
        spinner: Spinner
    ): LinearLayout {

        return LinearLayout(this).apply {

            orientation =
                LinearLayout.VERTICAL

            addView(
                TextView(this@MainActivity).apply {

                    text = name

                    textSize = 18f

                    setTextColor(
                        0xFFFFFFFF.toInt()
                    )
                }
            )

            addView(spinner)
        }
    }

    // ============================================================
    // 扫描设备
    // ============================================================

    private fun refreshDevices() {

        try {

            inputDevices =
                audioManager
                    .getDevices(
                        AudioManager.GET_DEVICES_INPUTS
                    )
                    .toList()

            outputDevices =
                audioManager
                    .getDevices(
                        AudioManager.GET_DEVICES_OUTPUTS
                    )
                    .toList()

            // 麦克风
            inputSpinner.adapter =
                ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_dropdown_item,
                    inputDevices.map {

                        "${it.productName}  [ID ${it.id}]  ${typeName(it.type)}"
                    }
                )

            // 喇叭
            outputSpinner.adapter =
                ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_dropdown_item,
                    outputDevices.map {

                        "${it.productName}  [ID ${it.id}]  ${typeName(it.type)}"
                    }
                )

            inputSpinner.onItemSelectedListener =

                object :
                    AdapterView.OnItemSelectedListener {

                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {

                        selectedInput =
                            inputDevices.getOrNull(
                                position
                            )
                    }

                    override fun onNothingSelected(
                        parent: AdapterView<*>?
                    ) {

                        selectedInput = null
                    }
                }

            outputSpinner.onItemSelectedListener =

                object :
                    AdapterView.OnItemSelectedListener {

                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {

                        selectedOutput =
                            outputDevices.getOrNull(
                                position
                            )
                    }

                    override fun onNothingSelected(
                        parent: AdapterView<*>?
                    ) {

                        selectedOutput = null
                    }
                }

            status.text =
                "检测到：输入 ${inputDevices.size} 个，输出 ${outputDevices.size} 个"

        } catch (e: Exception) {

            inputDevices = emptyList()

            outputDevices = emptyList()

            selectedInput = null

            selectedOutput = null

            status.text =
                "扫描失败：${e.message ?: "音频接口异常"}"
        }
    }

    // ============================================================
    // 开始喊话
    // ============================================================

    private fun startTalk() {

        if (running) {
            return
        }

        // 检查权限
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            status.text =
                "请先允许麦克风权限"

            return
        }

        var localRecorder: AudioRecord? = null

        var localPlayer: AudioTrack? = null

        try {

            // ====================================================
            // 先使用最兼容的 48kHz
            // ====================================================

            val sampleRate =
                findWorkingSampleRate()

            var bufferSize =
                AudioRecord.getMinBufferSize(

                    sampleRate,

                    AudioFormat.CHANNEL_IN_MONO,

                    AudioFormat.ENCODING_PCM_16BIT
                )

            // 某些车机返回负数
            if (bufferSize <= 0) {

                bufferSize = 8192
            }

            bufferSize =
                maxOf(
                    bufferSize * 2,
                    8192
                )

            // ====================================================
            // 输入
            // ====================================================

            val inputFormat =
                AudioFormat.Builder()

                    .setSampleRate(
                        sampleRate
                    )

                    .setChannelMask(
                        AudioFormat.CHANNEL_IN_MONO
                    )

                    .setEncoding(
                        AudioFormat.ENCODING_PCM_16BIT
                    )

                    .build()

            localRecorder =
                AudioRecord.Builder()

                    .setAudioSource(
                        MediaRecorder.AudioSource.MIC
                    )

                    .setAudioFormat(
                        inputFormat
                    )

                    .setBufferSizeInBytes(
                        bufferSize
                    )

                    .build()

            // 检查初始化
            if (
                localRecorder.state !=
                AudioRecord.STATE_INITIALIZED
            ) {

                throw Exception(
                    "麦克风初始化失败"
                )
            }

            /*
             * 重要：
             *
             * 暂时不强制设置 preferredDevice。
             *
             * 很多车机 Audio HAL 虽然可以扫描出很多
             * 麦克风，但是普通第三方 APP 强制路由
             * 会导致 AudioRecord 初始化/启动异常。
             *
             * 第一阶段先使用系统默认麦克风。
             */

            // ====================================================
            // 输出
            // ====================================================

            val outputFormat =
                AudioFormat.Builder()

                    .setSampleRate(
                        sampleRate
                    )

                    .setChannelMask(
                        AudioFormat.CHANNEL_OUT_MONO
                    )

                    .setEncoding(
                        AudioFormat.ENCODING_PCM_16BIT
                    )

                    .build()

            val attributes =
                AudioAttributes.Builder()

                    .setUsage(
                        AudioAttributes.USAGE_MEDIA
                    )

                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_SPEECH
                    )

                    .build()

            localPlayer =
                AudioTrack.Builder()

                    .setAudioAttributes(
                        attributes
                    )

                    .setAudioFormat(
                        outputFormat
                    )

                    .setBufferSizeInBytes(
                        bufferSize
                    )

                    .setTransferMode(
                        AudioTrack.MODE_STREAM
                    )

                    .build()

            // 检查初始化
            if (
                localPlayer.state !=
                AudioTrack.STATE_INITIALIZED
            ) {

                throw Exception(
                    "喇叭初始化失败"
                )
            }

            /*
             * 暂时不强制设置 preferredDevice。
             *
             * 使用系统默认输出。
             */

            localPlayer.setVolume(
                volume.progress / 100f
            )

            // ====================================================
            // 到这里全部成功后，再保存对象
            // ====================================================

            recorder = localRecorder

            player = localPlayer

            localRecorder = null

            localPlayer = null

            // ====================================================
            // 开始录音
            // ====================================================

            recorder!!.startRecording()

            if (
                recorder!!.recordingState !=
                AudioRecord.RECORDSTATE_RECORDING
            ) {

                throw Exception(
                    "麦克风无法开始录音"
                )
            }

            // ====================================================
            // 开始播放
            // ====================================================

            player!!.play()

            if (
                player!!.playState !=
                AudioTrack.PLAYSTATE_PLAYING
            ) {

                throw Exception(
                    "喇叭无法开始播放"
                )
            }

            running = true

            talkButton.text =
                "正在喊话…"

            status.text =
                "正在喊话：${sampleRate}Hz"

            val activeRecorder =
                recorder!!

            val activePlayer =
                player!!

            // ====================================================
            // 音频线程
            // ====================================================

            thread(
                name = "CarPA-Audio"
            ) {

                try {

                    Process.setThreadPriority(
                        Process.THREAD_PRIORITY_AUDIO
                    )

                    val buffer =
                        ByteArray(
                            bufferSize
                        )

                    while (running) {

                        val read =
                            activeRecorder.read(

                                buffer,

                                0,

                                buffer.size,

                                AudioRecord.READ_BLOCKING
                            )

                        if (
                            read > 0 &&
                            running
                        ) {

                            activePlayer.write(

                                buffer,

                                0,

                                read,

                                AudioTrack.WRITE_BLOCKING
                            )
                        }

                        if (read < 0) {
                            break
                        }
                    }

                } catch (_: Exception) {

                    // 音频线程异常绝对不能让 APP 崩溃

                } finally {

                    runOnUiThread {

                        if (running) {

                            stopTalk()
                        }
                    }
                }
            }

        } catch (e: Exception) {

            // ====================================================
            // 任意初始化失败都在这里处理
            // 不让 APP 闪退
            // ====================================================

            running = false

            try {
                localRecorder?.release()
            } catch (_: Exception) {
            }

            try {
                localPlayer?.release()
            } catch (_: Exception) {
            }

            releaseAudioObjects()

            talkButton.text =
                "长按说话"

            status.text =
                "启动失败：${e.message ?: "车机音频设备不支持"}"
        }
    }

    // ============================================================
    // 自动选择可用采样率
    // ============================================================

    private fun findWorkingSampleRate(): Int {

        val rates =
            intArrayOf(
                48000,
                44100,
                16000
            )

        for (rate in rates) {

            try {

                val size =
                    AudioRecord.getMinBufferSize(

                        rate,

                        AudioFormat.CHANNEL_IN_MONO,

                        AudioFormat.ENCODING_PCM_16BIT
                    )

                if (size > 0) {

                    return rate
                }

            } catch (_: Exception) {
            }
        }

        return 44100
    }

    // ============================================================
    // 停止喊话
    // ============================================================

    private fun stopTalk() {

        running = false

        try {
            recorder?.stop()
        } catch (_: Exception) {
        }

        try {
            player?.pause()
        } catch (_: Exception) {
        }

        try {
            recorder?.release()
        } catch (_: Exception) {
        }

        try {
            player?.release()
        } catch (_: Exception) {
        }

        recorder = null

        player = null

        talkButton.text =
            "长按说话"

        if (!isFinishing) {

            status.text =
                "喊话已停止"
        }
    }

    // ============================================================
    // 释放
    // ============================================================

    private fun releaseAudioObjects() {

        try {
            recorder?.stop()
        } catch (_: Exception) {
        }

        try {
            player?.pause()
        } catch (_: Exception) {
        }

        try {
            recorder?.release()
        } catch (_: Exception) {
        }

        try {
            player?.release()
        } catch (_: Exception) {
        }

        recorder = null

        player = null
    }

    // ============================================================
    // 权限结果
    // ============================================================

    override fun onRequestPermissionsResult(

        requestCode: Int,

        permissions: Array<out String>,

        grantResults: IntArray

    ) {

        super.onRequestPermissionsResult(

            requestCode,

            permissions,

            grantResults
        )

        if (
            requestCode ==
            REQUEST_MIC
        ) {

            if (
                grantResults.isNotEmpty() &&
                grantResults[0] ==
                PackageManager.PERMISSION_GRANTED
            ) {

                status.text =
                    "麦克风权限已允许"

            } else {

                status.text =
                    "未获得麦克风权限，无法喊话"
            }
        }
    }

    // ============================================================
    // APP 失去焦点
    // ============================================================

    override fun onPause() {

        stopTalk()

        super.onPause()
    }

    // ============================================================
    // APP 销毁
    // ============================================================

    override fun onDestroy() {

        running = false

        releaseAudioObjects()

        super.onDestroy()
    }

    // ============================================================
    // 音频设备名称
    // ============================================================

    private fun typeName(
        type: Int
    ): String {

        return when (type) {

            AudioDeviceInfo.TYPE_BUILTIN_MIC ->
                "内置麦克风"

            AudioDeviceInfo.TYPE_USB_DEVICE ->
                "USB"

            AudioDeviceInfo.TYPE_USB_HEADSET ->
                "USB耳机"

            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ->
                "蓝牙A2DP"

            AudioDeviceInfo.TYPE_BLUETOOTH_SCO ->
                "蓝牙SCO"

            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ->
                "内置扬声器"

            AudioDeviceInfo.TYPE_WIRED_HEADPHONES ->
                "有线耳机"

            AudioDeviceInfo.TYPE_WIRED_HEADSET ->
                "有线耳麦"

            else ->
                "其他($type)"
        }
    }

    companion object {

        private const val REQUEST_MIC =
            100
    }
}
```
