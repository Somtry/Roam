package com.roam.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.ui.graphics.BlendMode

// ─────────────────────────── 主题 ───────────────────────────

private val Palette = com.roam.app.RoamVisuals

private val LightColors = androidx.compose.material3.lightColorScheme(
    primary = Palette.Dusk,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EAF6),
    onPrimaryContainer = Palette.Ink,
    secondary = Palette.Sky,
    surface = Color(0xFFFFFFFF),
    background = Palette.Mist,
    surfaceVariant = Color(0xFFECECF2),
    onSurface = Palette.Ink,
    onSurfaceVariant = Color(0xFF6A6A75),
    outlineVariant = Color(0xFFDDDE4),
)

// ─────────────────────────── Activity ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val vm by lazy { ViewModelProvider(this)[RoamViewModel::class.java] }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.READ_MEDIA_IMAGES] == true) vm.runScan()
        }

    private fun mediaPerms(): Array<String> = buildList {
        add(Manifest.permission.READ_MEDIA_IMAGES)
        add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = LightColors) {
                var hasPermission by remember { mutableStateOf(checkMediaPermission()) }
                LaunchedEffect(Unit) { hasPermission = checkMediaPermission() }
                val ui by vm.ui.collectAsState()

                if (!hasPermission) {
                    Onboarding(
                        onRequest = { permLauncher.launch(mediaPerms()) },
                        onGranted = { hasPermission = true; vm.runScan() },
                    )
                } else {
                    // 已授权但未扫过(进程重建/重装):自动触发
                    LaunchedEffect(ui.state) { if (ui.state == HomeUi.State.IDLE) vm.runScan() }
                    RoamApp(vm, ui, onRescan = { vm.runScan() })
                }
            }
        }
    }

    private fun checkMediaPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.READ_MEDIA_IMAGES
    ) == PackageManager.PERMISSION_GRANTED
}

// ─────────────────────────── App 骨架:底部三 Tab ───────────────────────────

@Composable
private fun RoamApp(
    vm: RoamViewModel,
    ui: HomeUi,
    onRescan: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<HomeUi.TripCard?>(null) }
    val haze = rememberHazeState()

    Box(Modifier.fillMaxSize().background(RoamVisuals.pageBg())) {
        // 内容区 — haze 源:内容滚到底栏身后(不在布局上预留底,毛玻璃才有内容可糊;
        // 列表自身用 contentPadding 留出滚动末尾净空)
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            androidx.compose.animation.AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(tween(240)) + slideInVertically(tween(260)) { it / 18 })
                        .togetherWith(fadeOut(tween(160)))
                },
                label = "tab",
            ) { t ->
                when (t) {
                    0 -> TripsPage(ui = ui, onOpenTrip = { detail = it })
                    1 -> MapPage(ui = ui, onOpenTrip = { detail = it })
                    else -> MinePage(ui = ui, onRescan = onRescan)
                }
            }
        }

        // 底部导航:悬浮毛玻璃胶囊(真模糊:内容层经 Haze 高斯后透入)
        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp)) {
            FrostedBar(
                Modifier
                    .padding(horizontal = 26.dp)
                    .hazeEffect(haze),
                hazeStyle = HazeStyle(
                    blurRadius = 30.dp,
                    backgroundColor = Color(0xFFF7FAFF),
                    tints = listOf(HazeTint(Color(0xCCF4F8FF), BlendMode.SrcOver)),
                ),
            ) {
                Row(
                    Modifier.padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BottomItem("✈", "旅行", tab == 0, Modifier.weight(1f)) { tab = 0 }
                    BottomItem("◎", "地图", tab == 1, Modifier.weight(1f)) { tab = 1 }
                    BottomItem("☰", "我的", tab == 2, Modifier.weight(1f)) { tab = 2 }
                }
            }
        }

        // 详情页浮层(覆盖底部栏)
        detail?.let { card ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(230)) + scaleIn(initialScale = 0.94f, animationSpec = spring(stiffness = 400f)),
                exit = fadeOut(tween(180)),
            ) {
                TripDetailScreen(card = card, onBack = { detail = null })
            }
        }
    }
}

@Composable
private fun BottomItem(
    icon: String, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit,
) {
    // 选中态动画:图标整体 弹簧缩放+上浮,文字从半透明入
    val iconScale by animateFloatAsState(
        if (selected) 1.12f else 1f,
        spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = 480f),
        label = "is",
    )
    val iconLift by animateFloatAsState(
        if (selected) -3f else 0f, tween(240), label = "il",
    )
    val labelAlpha by animateFloatAsState(
        if (selected) 1f else 0.55f, tween(240), label = "la",
    )
    val tint = if (selected) Palette.Dusk else Color(0xFFA6A6B4)
    Column(
        modifier.clickable(onClick = onClick)
            .graphicsLayer { iconScale.also { } }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            icon, fontSize = 20.sp, color = tint,
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale; scaleY = iconScale; translationY = iconLift
            },
        )
        Text(
            label, fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = tint, modifier = Modifier.graphicsLayer { alpha = labelAlpha },
        )
    }
}

// ─────────────────────────── Tab 1: 旅行 ───────────────────────────

@Composable
private fun TripsPage(ui: HomeUi, onOpenTrip: (HomeUi.TripCard) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TripHeader(ui) }
        val tripList = ui.trips
        items(tripList.size, key = { tripList[it].id }) { i ->
            Box(tripEnterModifier(i)) { TripCardRow(tripList[i], onOpenTrip) }
        }
        if (ui.possibles.isNotEmpty()) {
            item { MaybeHeader(count = ui.possibles.size) }
            items(ui.possibles, key = { "p" + it.title }) { p ->
                MaybeCardRow(p)
            }
        }
    }
}

/** 旅行页顶部:白幕留白 + 品牌角标(Logo 与字分离)+ 双统计铭牌 */
@Composable
private fun TripHeader(ui: HomeUi) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.statusBarHeight())
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoamLogo(Modifier.size(34.dp), line = Palette.Dusk)
            Spacer(Modifier.size(10.dp))
            Text(
                "Roam",
                fontSize = 17.sp, fontWeight = FontWeight.Bold,
                color = Palette.Ink, letterSpacing = 2.sp,
            )
            Spacer(Modifier.weight(1f))
            StatBadge("${ui.trips.size}", "旅程")
            Spacer(Modifier.size(8.dp))
            StatBadge(ui.gpsCount.let { if (it > 999) "${it / 1000}k" else "$it" }, "定位")
        }
        Text(
            if (ui.trips.isEmpty()) "还没有发现旅行"
            else "你的足迹 · ${ui.trips.size} 段旅程",
            Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.Ink,
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun StatBadge(num: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(num, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.Dusk)
        Spacer(Modifier.size(2.dp))
        Text(label, fontSize = 11.sp, color = Color(0xFF9A9AA8))
    }
}

/** 「可能是旅行」分组头:细线 + 悬浮计数胶囊,对齐卡片边距(20dp) */
@Composable
private fun MaybeHeader(count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(Color(0x148A94C8)))
        Row(
            Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(6.dp).clip(CircleShape).background(Color(0x669AA0BC))
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "可能是旅行",
                fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = Color(0xFF9AA0BC), letterSpacing = 2.sp,
            )
            Spacer(Modifier.size(10.dp))
            Box(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(Color(0x129AA0BC))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    "$count", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF7A82B8),
                )
            }
        }
        Box(Modifier.weight(1f).height(1.dp).background(Color(0x148A94C8)))
    }
}

/** 「可能是旅行」卡:与 TripCardRow 同构件的海报弱化版——图文分区,文字不压图 */
@Composable
private fun MaybeCardRow(p: HomeUi.PossibleCard) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        // 海报式(与正式卡同构):Box 叠层——照片 + 底部渐变遮罩 + 右上待确认胶囊。
        // 不压任何信息字(日期/张数省,用户裁决:候选阶段让照片自己说话)
        Box(Modifier.fillMaxWidth().height(130.dp)) {
            if (p.photoIds.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(photoUri(p.photoIds.first()))
                        .crossfade(true).size(540).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Palette.Dawn, Palette.Dusk))
                ))
            }
            Box(Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color(0x00101830), 0.4f to Color(0x26121840), 1f to Color(0x73121B44)
                )
            ))
            // 左下角张数:纯文字压遮罩,无底条(遮罩渐变保证可读)
            Text(
                p.subtitle,
                Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 10.dp),
                fontSize = 12.sp,
                color = Color(0xE6FFFFFF),
                fontWeight = FontWeight.Medium,
            )
            Box(
                Modifier.align(Alignment.TopEnd).padding(12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0x3D121B44))
                    .padding(horizontal = 11.dp, vertical = 5.dp)
            ) {
                Text("待确认", fontSize = 11.sp, color = Color(0xF2FFFFFF), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun TripCardRow(t: HomeUi.TripCard, onOpen: (HomeUi.TripCard) -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .clickable { onOpen(t) },
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Box {
            if (t.photoIds.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(photoUri(t.photoIds.first()))
                        .crossfade(true).size(720).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().height(190.dp)
                        .background(Brush.linearGradient(listOf(Palette.Dawn, Palette.Dusk)))
                )
            }
            Box(Modifier.matchParentSize().background(RoamVisuals.heroScrims()))
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(t.city, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    if (t.province.isNotBlank()) {
                        Spacer(Modifier.size(6.dp))
                        Text(" ${t.province}", fontSize = 15.sp, color = Color(0xFFD9DEFF))
                    }
                }
                Text(t.subtitle, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFE6EAFF))
            }
            Row(
                Modifier.align(Alignment.TopEnd).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Pill("${t.days} 天")
                Pill("${t.shots} 张")
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(Color(0x4D121B44)).padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────── Tab 2: 地图 ───────────────────────────

@Composable
private fun MapPage(ui: HomeUi, onOpenTrip: (HomeUi.TripCard) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.statusBarHeight())
        Text(
            "足迹地图",
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.Ink,
        )
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 2.dp,
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("地图渲染 · 建设中", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Text(
                    "将接入双瓦片地图(国内高德 / 海外 OSM),\n去过的地方会插上你的标记。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 已识别城市 chips(现阶段替代地图的信息展示)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val seen = ui.trips.map { it.city }.distinct()
                    items(seen) { c ->
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp))
                                .background(Palette.Mist).padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(c, fontSize = 13.sp, color = Palette.Dusk, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────── Tab 3: 我的 ───────────────────────────

@Composable
private fun MinePage(ui: HomeUi, onRescan: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.statusBarHeight())
        Spacer(Modifier.height(8.dp))
        RoamLogo(Modifier.size(52.dp), line = Palette.Dusk)
        Spacer(Modifier.height(12.dp))
        Text("Roam", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, letterSpacing = 3.sp)
        Text("让每一段旅程都有迹可循", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(26.dp))

        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color.White, shadowElevation = 2.dp,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("数据档案", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MineStat("${ui.photoCount}", "扫过照片")
                    MineStat("${ui.validCount}", "正片")
                    MineStat("${ui.gpsCount}", "带位置")
                }
                if (ui.homeKnown) {
                    Text("常住地已自动识别(设置里可修改)", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color.White, shadowElevation = 2.dp,
        ) {
            Column {
                MineRow("重新扫描相册", onRescan)
                MineRow("导出数据", {})      // 建设中
                MineRow("隐私说明", {})      // 建设中
                MineRow("关于 Roam", {})
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "本地优先 · 照片不出设备 · 无账号无云端",
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall, color = Color(0xFF9A9AA8),
        )
    }
}

@Composable
private fun MineStat(num: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(num, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Palette.Dusk)
        Text(label, fontSize = 12.sp, color = Color(0xFF9A9AA8))
    }
}

@Composable
private fun MineRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), fontSize = 15.sp, color = Palette.Ink)
        Text("›", fontSize = 18.sp, color = Color(0xFFC0C0CC))
    }
}

// ─────────────────────────── 首启/扫描 ───────────────────────────

@Composable
private fun Onboarding(onRequest: () -> Unit, onGranted: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF232A8F), Color(0xFF4C63E6), Palette.Mist))
        ).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.statusBarHeight())
        Spacer(Modifier.height(40.dp))
        RoamLogo(Modifier.size(72.dp))
        Spacer(Modifier.height(4.dp))
        Text("Roam", fontSize = 40.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = Color.White, letterSpacing = 4.sp)
        Text("让每一段旅程都有迹可循", fontSize = 16.sp, color = Color(0xCCFFFFFF))
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.clip(RoundedCornerShape(22.dp)).background(Color(0x1FFFFFFF))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("你的回忆,早已在手机里。", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                "Roam 只读取照片的时间和位置信息,\n不上传、不看内容,自动整理你走过的旅行。",
                color = Color(0xB3FFFFFF), fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
        ) {
            Text("授权照片访问", fontSize = 16.sp,
                fontWeight = FontWeight.Bold, color = Color(0xFF2A3199))
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun Scanning() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(Modifier.size(44.dp), color = Palette.Dusk)
        Text("正在扫描你的回忆…", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = Palette.Ink)
        Text("只读取时间与位置信息,不上传", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─────────────────────────── 详情页(同前,提级复用) ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(card: HomeUi.TripCard, onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf("时间线", "照片", "地图")
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← 返回") }
                },
                title = {
                    Column {
                        Text("${card.city} · ${card.province}", fontWeight = FontWeight.Bold)
                        Text(card.subtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).background(Color.White)) {
            TabRow(selectedTabIndex = tab) {
                titles.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            when (tab) {
                0 -> TimelineTab(card)
                1 -> PhotosTab(card)
                else -> MapPlaceholder(card)
            }
        }
    }
}

@Composable
private fun TimelineTab(card: HomeUi.TripCard) {
    LazyColumn(
        Modifier.fillMaxSize().background(Color.White),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 18.dp, bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(card.sections, key = { it.date }) { s ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(s.date, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.Dusk)
                if (s.photoIds.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.photoIds, key = { it }) { id -> Thumb(id, 132.dp) }
                    }
                }
                Text(
                    "共 ${s.times.size} 张 · ${s.times.firstOrNull() ?: ""} — ${s.times.lastOrNull() ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PhotosTab(card: HomeUi.TripCard) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        gridItems(card.photoIds, key = { it }) { id -> Thumb(id) }
    }
}

@Composable
private fun MapPlaceholder(card: HomeUi.TripCard) {
    Column(
        Modifier.fillMaxSize().background(Color.White).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🗺", fontSize = 52.sp)
        Text("地图渲染 · 建设中", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        Text("将接入双瓦片地图:国内高德 · 海外 OSM", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Thumb(id: Long, size: androidx.compose.ui.unit.Dp? = null) {
    val m = Modifier
        .then(if (size != null) Modifier.size(size, size * 1.2f)
              else Modifier.fillMaxWidth().aspectRatio(1f))
        .clip(RoundedCornerShape(10.dp))
    AsyncImage(
        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
            .data(photoUri(id))
            .crossfade(true)
            .size(360)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = m,
    )
}

// ─────────────────────────── 通用 ───────────────────────────

@Composable
private fun Modifier.statusBarHeight(): Modifier {
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    val id = res.getIdentifier("status_bar_height", "dimen", "android")
    val h = if (id > 0) res.getDimensionPixelSize(id) else 0
    val d = androidx.compose.ui.platform.LocalDensity.current
    return this.then(Modifier.padding(top = with(d) { h.toDp() }))
}

/** 列表入场:滑入+淡入,index 错峰(普通 Composable 版,绕 composed 扩展) */
@Composable
private fun tripEnterModifier(index: Int): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index * 60L).coerceAtMost(420))
        shown = true
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(340), label = "a")
    val ty by animateFloatAsState(
        if (shown) 0f else 42f,
        spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
            stiffness = 340f
        ),
        label = "t",
    )
    return Modifier.graphicsLayer { this.alpha = alpha; translationY = ty }
}
