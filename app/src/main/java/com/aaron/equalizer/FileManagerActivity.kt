package com.aaron.equalizer

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.TimeUnit

class FileManagerActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private var exoPlayer: ExoPlayer? = null
    private var isUsingExoPlayer by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FileManagerUI()
        }
    }

    private fun getRecordingsDir(): File {
        val externalDir = File(getExternalFilesDir(null), "Recordings")
        if (externalDir.exists()) {
            Log.d("FileManagerActivity", "Using external storage: ${externalDir.absolutePath}")
            return externalDir
        }
        Log.w("FileManagerActivity", "External storage unavailable, falling back to internal")
        val internalDir = File(filesDir, "Recordings")
        if (!internalDir.exists()) {
            internalDir.mkdirs()
        }
        return internalDir
    }

    private fun getWavFiles(): List<File> {
        val dir = getRecordingsDir()
        Log.d("FileManagerActivity", "Listing files in: ${dir.absolutePath}")
        return dir.listFiles()?.filter { file ->
            val isWav = file.extension.lowercase() == "wav"
            Log.d("FileManagerActivity", "Checking file: ${file.name}, size=${file.length()} bytes")
            isWav
        }?.sortedBy { it.name } ?: emptyList()
    }

    private fun getFileDuration(file: File): Long {
        try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.absolutePath)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val duration = durationStr?.toLongOrNull() ?: 0L
                Log.d("FileManagerActivity", "Duration for ${file.name}: $duration ms (via MediaMetadataRetriever)")
                return duration
            }
        } catch (e: Exception) {
            Log.e("FileManagerActivity", "Error getting duration for ${file.name}: ${e.message}")
            val sampleRate = 44100
            val channels = 2
            val bitsPerSample = 16
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val dataSize = file.length() - 44
            val durationMs = (dataSize * 1000 / byteRate).toLong()
            Log.d("FileManagerActivity", "Fallback duration for ${file.name}: $durationMs ms (file size-based)")
            return durationMs
        }
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
        exoPlayer?.release()
        exoPlayer = null
        isUsingExoPlayer = false
        Log.d("FileManagerActivity", "MediaPlayer released")
    }

    private fun setupMediaPlayer(file: File?): Boolean {
        if (file == null || !file.exists()) {
            Log.w("FileManagerActivity", "Skipping MediaPlayer setup for invalid file: $file")
            return false
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                Log.d("FileManagerActivity", "MediaPlayer prepared, duration=${duration} ms")
            }
            isUsingExoPlayer = false
            return true
        } catch (e: Exception) {
            Log.e("FileManagerActivity", "Error preparing MediaPlayer for ${file.name}: ${e.message}")
            try {
                exoPlayer = ExoPlayer.Builder(this@FileManagerActivity).build().apply {
                    setMediaItem(MediaItem.fromUri(file.absolutePath))
                    prepare()
                    Log.d("FileManagerActivity", "ExoPlayer prepared for ${file.name}")
                }
                isUsingExoPlayer = true
                return true
            } catch (e2: Exception) {
                Log.e("FileManagerActivity", "Error preparing ExoPlayer for ${file.name}: ${e2.message}")
                Toast.makeText(this, "Cannot play ${file.name}: ${e2.message}", Toast.LENGTH_SHORT).show()
                return false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaPlayer()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun FileManagerUI() {
        val files by remember { mutableStateOf(getWavFiles()) }
        var selectedFile by remember { mutableStateOf<File?>(null) }
        var isPlaying by remember { mutableStateOf(false) }
        var currentPosition by remember { mutableStateOf(0) }
        var duration by remember { mutableStateOf(0L) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(selectedFile) {
            releaseMediaPlayer()
            if (selectedFile != null && setupMediaPlayer(selectedFile)) {
                duration = if (isUsingExoPlayer) {
                    exoPlayer?.duration?.toLong() ?: getFileDuration(selectedFile!!)
                } else {
                    mediaPlayer?.duration?.toLong() ?: getFileDuration(selectedFile!!)
                }
            } else {
                duration = selectedFile?.let { getFileDuration(it) } ?: 0L
            }
        }

        LaunchedEffect(isPlaying) {
            if (isPlaying) {
                scope.launch {
                    while (isPlaying && (mediaPlayer?.isPlaying == true || exoPlayer?.isPlaying == true)) {
                        currentPosition = if (isUsingExoPlayer) {
                            exoPlayer?.currentPosition?.toInt() ?: 0
                        } else {
                            mediaPlayer?.currentPosition ?: 0
                        }
                        delay(100)
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "My Recordings",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            Spacer(modifier = Modifier.height(64.dp)) // Big space before first file
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp) // Space between items
            ) {
                items(files) { file ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFile = file },
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = file.name,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatDuration(getFileDuration(file)),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
            if (files.isEmpty()) {
                Spacer(modifier = Modifier.height(64.dp)) // Align with list offset
                Text(
                    text = "No recordings found",
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        if (selectedFile != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    isPlaying = false
                    releaseMediaPlayer()
                    selectedFile = null
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = selectedFile?.name ?: "Unknown",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    if (isUsingExoPlayer) {
                                        exoPlayer?.pause()
                                    } else {
                                        mediaPlayer?.pause()
                                    }
                                    isPlaying = false
                                    Log.d("FileManagerActivity", "Paused")
                                } else {
                                    if (isUsingExoPlayer) {
                                        exoPlayer?.play()
                                    } else {
                                        mediaPlayer?.start()
                                    }
                                    isPlaying = true
                                    Log.d("FileManagerActivity", "Playing")
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                if (isUsingExoPlayer) {
                                    exoPlayer?.seekTo(0)
                                } else {
                                    mediaPlayer?.seekTo(0)
                                }
                                currentPosition = 0
                                Log.d("FileManagerActivity", "Rewound")
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Replay,
                                contentDescription = "Rewind",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { newPosition ->
                            currentPosition = newPosition.toInt()
                            if (isUsingExoPlayer) {
                                exoPlayer?.seekTo(currentPosition.toLong())
                            } else {
                                mediaPlayer?.seekTo(currentPosition)
                            }
                            Log.d("FileManagerActivity", "Seek to $currentPosition ms")
                        },
                        valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(currentPosition.toLong()),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDuration(duration),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    private fun formatDuration(ms: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format("%dm %02ds", minutes, seconds)
    }
}