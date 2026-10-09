package com.roam.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private var onGranted: () -> Unit = {}

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) onGranted()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = { TopAppBar(title = { Text("Roam") }) }
                ) { padding ->
                    HomeScreen(
                        modifier = Modifier.padding(padding),
                        hasPermission = hasMediaPermission(),
                        onRequestPermission = { ask() },
                    )
                }
            }
        }
    }

    private fun hasMediaPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.READ_MEDIA_IMAGES
    ) == PackageManager.PERMISSION_GRANTED

    private fun ask() {
        onGranted = { setContent { } } // 权限变化后由重组刷新;V0.1 后续接扫描器
        permLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
    }
}

@androidx.compose.runtime.Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Your memories are already in your phone.", style = MaterialTheme.typography.titleMedium)
        if (!hasPermission) {
            Text("Roam 需要读取相册元数据(只读,不上传)来发现你的旅行。")
            Button(onClick = onRequestPermission) { Text("授权照片访问") }
        } else {
            Text("已授权 — 扫描器接线中(engine 模块就位后自动扫描)")
        }
    }
}
