package com.roam.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roam.engine.Detect
import com.roam.engine.DetectResult
import com.roam.engine.EngineConfig
import com.roam.engine.PhotoMeta
import com.roam.engine.TripCandidate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 首页 UI 模型(引擎类型不外泄到 Compose) */
data class HomeUi(
    val state: State = State.IDLE,
    val photoCount: Int = 0,
    val validCount: Int = 0,
    val gpsCount: Int = 0,
    val homeKnown: Boolean = false,
    val trips: List<TripCard> = emptyList(),
    val possibles: List<PossibleCard> = emptyList(),
) {
    enum class State { IDLE, SCANNING, DONE }

    data class DaySection(val date: String, val times: List<String>, val photoIds: List<Long>)

    data class TripCard(
        val id: String,
        val city: String,        // "邵阳" 或 "未知地点"
        val province: String,   // "湖南"(可为空)
        val subtitle: String,   // "2026年10月1日 — 10月2日"
        val days: Int,
        val shots: Int,
        val confidence: Int,
        val awayKm: Int?,
        val sections: List<DaySection>,   // 详情页时间线数据
        val photoIds: List<Long>,          // 详情页照片墙(按时间序)
    )

    data class PossibleCard(val title: String, val subtitle: String)
}

fun photoUri(id: Long): Uri =
    Uri.withAppendedPath(
        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()
    )

class RoamViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(HomeUi())
    val ui: StateFlow<HomeUi> = _ui

    fun runScan() {
        if (_ui.value.state == HomeUi.State.SCANNING) return
        _ui.value = HomeUi(state = HomeUi.State.SCANNING)
        viewModelScope.launch {
            val (photos, stats) = MediaScanner.scan(getApplication())
            val home = Detect.inferHome(photos)
            val cfg = home?.let { EngineConfig(homeLatLon = it) } ?: EngineConfig()
            val result = Detect.detect(photos, cfg)
            _ui.value = result.toHomeUi(getApplication(), stats, home)
        }
    }
}

private fun DetectResult.toHomeUi(
    ctx: android.content.Context,
    stats: MediaScanner.ScanStats,
    home: Pair<Double, Double>?,
): HomeUi {
    val trips = candidates.filter { it.type == TripCandidate.Type.TRIP }
        .sortedByDescending { it.startDate }
        .map { it.toCard(ctx) }

    val possibles = candidates.filter { it.type == TripCandidate.Type.POSSIBLE }
        .sortedByDescending { it.startDate }
        .map {
            HomeUi.PossibleCard(
                title = "${it.startDate.year}年${it.startDate.monthValue}月${it.startDate.dayOfMonth}日",
                subtitle = "${it.shots} 张 · ${it.reason}",
            )
        }

    return HomeUi(
        state = HomeUi.State.DONE,
        photoCount = stats.total,
        validCount = valid.size,
        gpsCount = stats.withGps,
        homeKnown = home != null,
        trips = trips,
        possibles = possibles,
    )
}

private fun TripCandidate.toCard(ctx: android.content.Context): HomeUi.TripCard {
    val around = days.flatMap { it.photos }.filter { it.latitude != null }
    val cLat = around.map { it.latitude!! }.average().let { if (around.isEmpty()) null else it }
    val cLon = around.map { it.longitude!! }.average().let { if (around.isEmpty()) null else it }
    val city = if (cLat != null && cLon != null)
        CityResolver.nearest(ctx, cLat, cLon) else null
    val s = startDate; val e = endDate
    val sameYear = s.year == e.year
    val sameMonth = sameYear && s.monthValue == e.monthValue
    val subtitle = when {
        sameMonth -> "${s.year}年${s.monthValue}月${s.dayOfMonth}日 — ${e.dayOfMonth}日"
        sameYear -> "${s.year}年${s.monthValue}月${s.dayOfMonth}日 — ${e.monthValue}月${e.dayOfMonth}日"
        else -> "${s.year}年${s.monthValue}月${s.dayOfMonth}日 — ${e.year}年${e.monthValue}月${e.dayOfMonth}日"
    }
    return HomeUi.TripCard(
        id = "$startDate-$endDate",
        city = city?.name ?: "尚未识别",
        province = city?.province ?: "",
        subtitle = subtitle,
        days = days.size,
        shots = shots,
        confidence = (confidence * 100).toInt(),
        awayKm = awayFromHomeKm?.toInt(),
        sections = days.map { ds ->
            HomeUi.DaySection(
                date = "${ds.day.year}年${ds.day.monthValue}月${ds.day.dayOfMonth}日",
                times = ds.photos.map { p ->
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                        .format(java.util.Date(p.takenAt!!))
                },
                photoIds = ds.photos.map { it.id },
            )
        },
        photoIds = days.flatMap { it.photos.map { p -> p.id } },
    )
}
