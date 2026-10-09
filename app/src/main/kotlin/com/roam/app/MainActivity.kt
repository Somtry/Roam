package com.roam.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest

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
                var detail by remember { mutableStateOf<HomeUi.TripCard?>(null) }
                val ui by vm.ui.collectAsState()
                var hasPermission by remember { mutableStateOf(checkMediaPermission()) }
                LaunchedEffect(Unit) { hasPermission = checkMediaPermission() }

                val card = detail
                Box(Modifier.fillMaxSize().background(RoamVisuals.pageBg())) {
                    // 首页:滑动+淡入出现,缩放淡出退场
                    androidx.compose.animation.AnimatedVisibility(
                        visible = card == null,
                        enter = fadeIn(tween(260)) + slideInVertically(tween(280)) { it / 14 },
                        exit = fadeOut(tween(180)),
                    ) {
                        HomeScreen(
                            modifier = Modifier,
                            ui = ui,
                            hasPermission = hasPermission,
                            onPermissionGranted = {
                                hasPermission = true; vm.runScan()
                            },
                            onRequestPermission = {
                                if (checkMediaPermission()) {
                                    hasPermission = true; vm.runScan()
                                } else permLauncher.launch(mediaPerms())
                            },
                            onOpenTrip = { detail = it },
                        )
                    }
                    // 详情页:缩放入场(模拟"从卡片里长出来")
                    androidx.compose.animation.AnimatedVisibility(
                        visible = card != null,
                        enter = fadeIn(tween(240)) + scaleIn(
                            initialScale = 0.94f,
                            animationSpec = spring(stiffness = 380f)
                        ),
                        exit = scaleOut(targetScale = 0.96f) + fadeOut(tween(160)),
                    ) {
                        detail?.let { c -> TripDetailScreen(card = c, onBack = { detail = null }) }
                    }
                }
            }
        }
    }

    private fun checkMediaPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.READ_MEDIA_IMAGES
    ) == PackageManager.PERMISSION_GRANTED
}

// ─────────────────────────── 主题 ───────────────────────────

private val LightColors = androidx.compose.material3.lightColorScheme(
    primary = RoamVisuals.Dusk,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EAF6),
    onPrimaryContainer = RoamVisuals.Ink,
    secondary = Color(0xFF546E7A),
    surface = Color(0xFFFFFFFF),
    background = Color(0xFFF7F9FF),
    surfaceVariant = Color(0xFFECECF0),
    onSurface = RoamVisuals.Ink,
    onSurfaceVariant = Color(0xFF5F5F68),
    outlineVariant = Color(0xFFDDDE4),
)

// ─────────────────────────── 首页 ───────────────────────────

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    ui: HomeUi,
    hasPermission: Boolean,
    onPermissionGranted: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenTrip: (HomeUi.TripCard) -> Unit,
) {
    when {
        !hasPermission -> Onboarding(onRequestPermission, modifier)
        ui.state == HomeUi.State.SCANNING -> Scanning(modifier)
        ui.state == HomeUi.State.DONE -> ResultList(ui, onOpenTrip, modifier)
        else -> Box(modifier.fillMaxSize()) { LaunchedEffect(Unit) { onPermissionGranted() } }
    }
}

@Composable
private fun Scanning(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.pulse(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(86.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(RoamVisuals.Dawn, RoamVisuals.Dusk)))
            )
            Text("🧭", fontSize = 36.sp)
        }
        Text("正在扫描你的回忆…",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "只读取时间与位置信息,不上传",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Onboarding(request: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.statusBarHeight())
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(RoamVisuals.Dawn, RoamVisuals.Dusk))),
            contentAlignment = Alignment.Center,
        ) { Text("🧭", fontSize = 30.sp) }
        Spacer(Modifier.height(6.dp))
        Text("你的回忆,", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = RoamVisuals.Ink)
        Text(
            "早已在手机里。",
            fontSize = 38.sp, fontWeight = FontWeight.Bold,
            color = RoamVisuals.Dusk,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Roam 只读取照片的时间和位置信息,\n不上传、不看内容,自动整理你走过的旅行。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 26.sp,
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = request,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = RoamVisuals.Dusk
            ),
        ) {
            Text("授权照片访问", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun ResultList(
    ui: HomeUi,
    onOpenTrip: (HomeUi.TripCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { HeroHeader(ui) }

        val tripList = ui.trips
        items(tripList.size, key = { tripList[it].id }) { i ->
            Box(Modifier.animateItemIn(i)) { TripCardRow(tripList[i], onOpenTrip) }
        }

        if (ui.possibles.isNotEmpty()) {
            item {
                Spacer(Modifier.height(10.dp))
                Text(
                    "可能是旅行(${ui.possibles.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
            items(ui.possibles, key = { "p" + it.title }) { p ->
                PossibleRow(p)
            }
        }
    }
}

/** 沉浸式头部:深靛渐变横幅,问候语+主结果+白色统计胶囊;卡片列表叠在其下 */
@Composable
private fun HeroHeader(ui: HomeUi) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarHeight()
            .background(
                Brush.verticalGradient(listOf(RoamVisuals.Dusk, RoamVisuals.Dawn))
            )
            .padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🧭", fontSize = 20.sp)
            Spacer(Modifier.size(8.dp))
            Text(
                "Roam",
                color = Color(0xCCFFFFFF),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 2.sp,
            )
        }
        Text(
            if (ui.trips.isEmpty()) "还没有发现旅行"
            else "发现了 ${ui.trips.size} 次旅行",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroChip("${ui.validCount} 张正片")
            HeroChip("${ui.gpsCount} 张带GPS")
            if (ui.homeKnown) HeroChip("已识别常住地")
        }
    }
}

@Composable
private fun HeroChip(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(Color(0x26FFFFFF)).padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(text, color = Color(0xE6FFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Modifier.statusBarHeight(): Modifier {
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    val id = res.getIdentifier("status_bar_height", "dimen", "android")
    val h = if (id > 0) res.getDimensionPixelSize(id) else 0
    val d = androidx.compose.ui.platform.LocalDensity.current
    return this.then(Modifier.padding(top = with(d) { h.toDp() }))
}

@Composable
private fun TripCardRow(t: HomeUi.TripCard, onOpen: (HomeUi.TripCard) -> Unit) {
    val pressed = remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed.value) 0.97f else 1f,
        spring(stiffness = 400f), label = "cs"
    )
    Card(
        Modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable { onOpen(t) },
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Box {
            // 头图:该 Trip 的第一张照片(无则渐变座)
            if (t.photoIds.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(photoUri(t.photoIds.first()))
                        .crossfade(true).size(720).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().height(180.dp)
                        .background(Brush.linearGradient(listOf(RoamVisuals.Dawn, RoamVisuals.Dusk)))
                )
            }
            // 底部渐变遮罩 + 海报字
            Box(Modifier.matchParentSize().background(RoamVisuals.heroScrims()))
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(t.city, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    if (t.province.isNotBlank()) {
                        Spacer(Modifier.size(6.dp))
                        Text(" ${t.province}", fontSize = 18.sp,
                            color = Color(0xFFD5DDFF))
                    }
                }
                Text(t.subtitle, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFE6EAFF))
            }
            // 统计胶囊
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

@Composable
private fun PossibleRow(p: HomeUi.PossibleCard) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(p.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(p.subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────── Trip 详情 ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(card: HomeUi.TripCard, onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf("时间线", "照片", "地图")
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    androidx.compose.material3.TextButton(onClick = onBack) {
                        Text("← 返回")
                    }
                },
                title = { Column {
                    Text("${card.city} · ${card.province}", fontWeight = FontWeight.Bold)
                    Text(card.subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                titles.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i },
                        text = { Text(t.replace(" ", "")) })
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
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 18.dp, bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(card.sections, key = { it.date }) { s ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(s.date, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                if (s.photoIds.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.photoIds, key = { it }) { id ->
                            Thumb(id, 132.dp)
                        }
                    }
                }
                Text("共 ${s.times.size} 张 · ${s.times.firstOrNull() ?: ""} — ${s.times.lastOrNull() ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PhotosTab(card: HomeUi.TripCard) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
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
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🗺️", fontSize = 52.sp)
        Text("地图在路上", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(
            "下一版接入高德地图 SDK\n(需要申请开发者 Key)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 缩略图:Coil 直接加载 MediaStore uri(系统缩略管线,非原图) */
@Composable
private fun Thumb(id: Long, size: androidx.compose.ui.unit.Dp? = null) {
    val m = Modifier
        .then(if (size != null) Modifier.size(size, size * 1.2f) else Modifier.fillMaxWidth().aspectRatio(1f))
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
