package com.roam.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val vm by lazy { ViewModelProvider(this)[RoamViewModel::class.java] }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) vm.runScan()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // 权限进入 Compose 状态:变化即重组
                var hasPermission by remember {
                    mutableStateOf(checkMediaPermission())
                }
                // 从系统对话框回到前台/授完权,重新校验
                LaunchedEffect(Unit) { hasPermission = checkMediaPermission() }
                val ui by vm.ui.collectAsState()
                Scaffold(topBar = { TopAppBar(title = { Text("Roam") }) }) { padding ->
                    HomeScreen(
                        modifier = Modifier.padding(padding),
                        ui = ui,
                        hasPermission = hasPermission,
                        onPermissionGranted = {
                            hasPermission = true
                            vm.runScan()
                        },
                        onRequestPermission = {
                            if (checkMediaPermission()) {
                                hasPermission = true; vm.runScan()
                            } else permLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                        },
                    )
                }
            }
        }
    }

    private fun checkMediaPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.READ_MEDIA_IMAGES
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    ui: HomeUi,
    hasPermission: Boolean,
    onPermissionGranted: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    when {
        !hasPermission -> Onboarding(onRequestPermission)
        ui.state == HomeUi.State.SCANNING -> Scanning(modifier)
        ui.state == HomeUi.State.DONE -> Result(ui, modifier)
        // 已授权但还没开始扫(比如进程重建):自动触发
        else -> Box(modifier.fillMaxSize()) { LaunchedEffect(Unit) { onPermissionGranted() } }
    }
}

@Composable
private fun Scanning(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator()
        Text("正在扫描你的回忆…", style = MaterialTheme.typography.titleMedium)
        Text("只读取时间与位置信息,不上传", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Onboarding(request: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "你的回忆,早已在手机里。",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text("Roam 只读取照片的时间和位置信息,不上传、不看内容,自动发现你走过的旅行。")
        Button(onClick = request) { Text("授权照片访问") }
    }
}

@Composable
private fun Result(ui: HomeUi, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                if (ui.trips.isEmpty()) "还没有发现旅行。"
                else "发现了 ${ui.trips.size} 次旅行。",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${ui.validCount} 张正片 · 带 GPS ${ui.gpsCount} 张 · 待确认 ${ui.possibles.size} 个",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        items(ui.trips, key = { it.id }) { t ->
            Card(
                Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t.subtitle, style = MaterialTheme.typography.bodyMedium)
                    Text("${t.shots} 张照片 · 置信度 ${t.confidence}%", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (ui.possibles.isNotEmpty()) {
            item {
                Text(
                    "可能是旅行(${ui.possibles.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(ui.possibles, key = { p -> "p" + p.title }) { p ->
                TextButton(onClick = {}) {
                    Column {
                        Text(p.title, style = MaterialTheme.typography.bodyMedium)
                        Text(p.subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
