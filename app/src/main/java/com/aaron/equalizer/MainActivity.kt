package com.aaron.equalizer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aaron.equalizer.audio.SuperpoweredEQ
import com.aaron.equalizer.ui.theme.EqualizerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()

        // Initialize Superpowered license
        SuperpoweredEQ.initializeLicense("ExampleLicenseKey-WillExpire-OnNextUpdate")

        setContent {
            EqualizerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainUI()
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 0)
        }
    }

    @Composable
    fun MainUI() {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Equalizer App", style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                val testBuffer = ShortArray(512) { (it % Short.MAX_VALUE).toShort() }
                try {
                    SuperpoweredEQ.applyEQ(testBuffer, testBuffer.size, 10f, 0f, -10f)
                    Toast.makeText(this@MainActivity, "EQ applied successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "EQ processing failed: ${e.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            }) {
                Text("Test Superpowered EQ")
            }
        }
    }
}
