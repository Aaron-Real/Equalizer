package com.aaron.equalizer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.*
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aaron.equalizer.audio.SuperpoweredEQ
import java.io.*
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecording by mutableStateOf(false)
    private var recordingFile: File? = null
    private var wavOutputStream: DataOutputStream? = null
    private var totalBytes: Long = 0L
    private val stopLock = Any()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            Log.d("MainActivity", "RECORD_AUDIO permission: $isGranted")
            if (isGranted) {
                initializeAudio()
            } else {
                Toast.makeText(
                    this,
                    "Microphone permission denied. Enable in Settings > Apps > Equalizer > Permissions.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        val audioPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        Log.d("MainActivity", "Initial permission: audio=$audioPerm")
        if (audioPerm == PackageManager.PERMISSION_GRANTED) {
            initializeAudio()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            MainUI(requestPermissionLauncher)
        }
    }

    private fun initializeAudio() {
        Log.d("MainActivity", "Audio initialized")
    }

    private fun getStorageDir(): File {
        val externalDir = File(getExternalFilesDir(null), "Recordings")
        if (externalDir.exists() || externalDir.mkdirs()) {
            Log.d("MainActivity", "Using external storage: ${externalDir.absolutePath}")
            return externalDir
        }
        Log.w("MainActivity", "Failed to create external Recordings directory, falling back to internal storage")
        val internalDir = File(filesDir, "Recordings")
        if (!internalDir.exists() && !internalDir.mkdirs()) {
            Log.e("MainActivity", "Failed to create internal Recordings directory")
        }
        return internalDir
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startRecordingAndFeedback() {
        totalBytes = 0L
        Log.d("MainActivity", "Reset totalBytes to 0")

        val outputDir = getStorageDir()
        var recordingCounter = 1
        outputDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("Recording")) {
                val number = file.nameWithoutExtension.replace("Recording ", "").toIntOrNull() ?: 0
                recordingCounter = maxOf(recordingCounter, number + 1)
            }
        }
        recordingFile = File(outputDir, "Recording $recordingCounter.wav")
        Log.d("MainActivity", "Recording to: ${recordingFile?.absolutePath}")

        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_STEREO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            minBufferSize
        )
        audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(audioFormat)
                .build(),
            minBufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        try {
            wavOutputStream = DataOutputStream(BufferedOutputStream(FileOutputStream(recordingFile)))
            writeWavHeader(wavOutputStream!!, sampleRate, 2, 16)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to setup WAV file: ${e.message}", e)
            Toast.makeText(this, "Failed to setup recording: ${e.message}", Toast.LENGTH_SHORT).show()
            cleanup()
            return
        }

        try {
            audioRecord?.startRecording() ?: throw IllegalStateException("AudioRecord is null")
            audioTrack?.play() ?: throw IllegalStateException("AudioTrack is null")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start recording/feedback: ${e.message}", e)
            Toast.makeText(this, "Failed to start: ${e.message}", Toast.LENGTH_SHORT).show()
            cleanup()
            return
        }

        isRecording = true
        thread(priority = Thread.MAX_PRIORITY) {
            val buffer = ByteArray(minBufferSize)
            var lastReadTime = System.currentTimeMillis()
            while (isRecording && audioRecord != null) {
                val bytesRead = audioRecord?.read(buffer, 0, minBufferSize) ?: 0
                if (bytesRead > 0) {
                    try {
                        wavOutputStream?.write(buffer, 0, bytesRead)
                        wavOutputStream?.flush()
                        totalBytes += bytesRead
                        audioTrack?.write(buffer, 0, bytesRead)
                        lastReadTime = System.currentTimeMillis()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error writing audio: ${e.message}", e)
                        runOnUiThread {
                            Toast.makeText(this, "Recording error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        isRecording = false
                        break
                    }
                } else {
                    if (System.currentTimeMillis() - lastReadTime > 1000) {
                        runOnUiThread {
                            Toast.makeText(this, "Recording stopped unexpectedly", Toast.LENGTH_SHORT).show()
                        }
                        isRecording = false
                        break
                    }
                }
            }
            if (isRecording) {
                stopRecordingAndFeedback()
            }
        }
        Toast.makeText(this, "Recording and feedback started", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecordingAndFeedback() {
        synchronized(stopLock) {
            if (!isRecording) return
            isRecording = false
        }

        try {
            audioRecord?.stop()
            audioTrack?.stop()
            audioRecord?.release()
            audioTrack?.release()
            audioRecord = null
            audioTrack = null
        } catch (e: Exception) {
            Log.e("MainActivity", "Error stopping audio: ${e.message}", e)
        }

        try {
            wavOutputStream?.flush()
            wavOutputStream?.close()
            wavOutputStream = null

            recordingFile?.let { file ->
                if (file.exists() && file.length() > 44) {
                    updateWavHeader(file, totalBytes)
                    Toast.makeText(this, "Recording saved", Toast.LENGTH_SHORT).show()
                } else {
                    file.delete()
                    Toast.makeText(this, "Recording file invalid", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error finalizing WAV: ${e.message}", e)
            Toast.makeText(this, "Error saving recording: ${e.message}", Toast.LENGTH_SHORT).show()
            recordingFile?.delete()
        }

        recordingFile = null
        totalBytes = 0L
    }

    private fun writeWavHeader(output: DataOutputStream, sampleRate: Int, channels: Int, bitsPerSample: Int) {
        output.writeBytes("RIFF")
        output.writeInt(Integer.reverseBytes(36))
        output.writeBytes("WAVE")
        output.writeBytes("fmt ")
        output.writeInt(Integer.reverseBytes(16))
        output.writeShort(java.lang.Short.reverseBytes(1).toInt())
        output.writeShort(java.lang.Short.reverseBytes(channels.toShort()).toInt())
        output.writeInt(Integer.reverseBytes(sampleRate))
        output.writeInt(Integer.reverseBytes(sampleRate * channels * bitsPerSample / 8))
        output.writeShort(java.lang.Short.reverseBytes((channels * bitsPerSample / 8).toShort()).toInt())
        output.writeShort(java.lang.Short.reverseBytes(bitsPerSample.toShort()).toInt())
        output.writeBytes("data")
        output.writeInt(Integer.reverseBytes(0))
        output.flush()
    }

    private fun updateWavHeader(file: File, dataSize: Long) {
        RandomAccessFile(file, "rw").use { raf ->
            val fileSize = file.length()
            raf.seek(4)
            raf.writeInt(Integer.reverseBytes((fileSize - 8).toInt()))
            raf.seek(40)
            raf.writeInt(Integer.reverseBytes(dataSize.toInt()))
        }
    }

    private fun cleanup() {
        audioRecord?.release()
        audioTrack?.release()
        wavOutputStream?.close()
        recordingFile?.takeIf { it.length() <= 44 }?.delete()
        audioRecord = null
        audioTrack = null
        wavOutputStream = null
        recordingFile = null
        isRecording = false
        totalBytes = 0L
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    @Composable
    fun MainUI(requestPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(top = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Equalizer", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = {
                    val audioPerm = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    )
                    if (audioPerm == PackageManager.PERMISSION_GRANTED) {
                        if (isRecording) {
                            stopRecordingAndFeedback()
                        } else {
                            startRecordingAndFeedback()
                        }
                    } else {
                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                    tint = if (isRecording) Color.Red else Color.DarkGray,
                    modifier = Modifier.size(48.dp)
                )
            }
            Button(
                onClick = {
                    startActivity(Intent(this@MainActivity, FileManagerActivity::class.java))
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("My Recordings")
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Button(onClick = {
                val testBuffer = ShortArray(512) { (it % Short.MAX_VALUE).toShort() }
                try {
                    SuperpoweredEQ.applyEQ(testBuffer, testBuffer.size, 10f, 0f, -10f, 48000)
                    Toast.makeText(this@MainActivity, "EQ applied successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: UnsatisfiedLinkError) {
                    Toast.makeText(this@MainActivity, "Superpowered native call failed: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("EQTest", "JNI error", e)
                }
            }) {
                Text("Test Superpowered EQ")
            }
        }
    }
}
