package com.roam.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roam.engine.Detect
import com.roam.engine.DetectResult
import com.roam.engine.EngineConfig
import com.roam.engine.TripCandidate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 帧数:首页渲染用的精简模型(UI 不碰引擎类型) */
data class HomeUi(
    val state: State = State.IDLE,
    val photoCount: Int = 0,
    val validCount: Int = 0,
    val gpsCount: Int = 0,
    val trips: List<TripCard> = emptyList(),
    val possibles: List<PossibleCard> = emptyList(),
    val homeKnown: Boolean = false,
) {
    enum class State { IDLE, SCANNING, DONE }

    data class TripCard(
        val id: String,
        val title: String,
        val subtitle: String,
        val shots: Int,
        val confidence: Int,
    )

    data class PossibleCard(val title: String, val subtitle: String)
}

class RoamViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(HomeUi())
    val ui: StateFlow<HomeUi> = _ui

    fun runScan() {
        _ui.value = _ui.value.copy(state = HomeUi.State.SCANNING)
        viewModelScope.launch {
            val (photos, stats) = MediaScanner.scan(getApplication())
            // 常住地:数据自动推断(照片年复一年最密的落脚点);失败则引擎仅按时间聚类
            val home = Detect.inferHome(photos)
            val cfg = home?.let { EngineConfig(homeLatLon = it) } ?: EngineConfig()
            val result: DetectResult = Detect.detect(photos, cfg)
            _ui.value = result.toHomeUi(stats, home)
        }
    }
}

private fun DetectResult.toHomeUi(stats: MediaScanner.ScanStats, home: Pair<Double, Double>?): HomeUi {
    // Trip 候选 → 首页卡片(高置信正面)
    val trips = candidates.filter { it.type == TripCandidate.Type.TRIP }.map { c ->
        HomeUi.TripCard(
            id = "${c.startDate}-${c.endDate}",
            title = "还未命名的旅行",
            subtitle = "${c.startDate.year}年${c.startDate.monthValue}月${c.startDate.dayOfMonth}日 — ${c.endDate.year}年${c.endDate.monthValue}月${c.endDate.dayOfMonth}日",
            shots = c.shots,
            confidence = (c.confidence * 100).toInt(),
        )
    }
    // 折叠区(低置信,不上 C 位)
    val possibles = candidates.filter { it.type == TripCandidate.Type.POSSIBLE }.map { c ->
        HomeUi.PossibleCard(
            title = "${c.startDate.year}年${c.startDate.monthValue}月${c.startDate.dayOfMonth}日",
            subtitle = c.reason,
        )
    }
    return HomeUi(
        state = HomeUi.State.DONE,
        photoCount = stats.total,
        validCount = valid.size,
        gpsCount = stats.withGps,
        trips = trips,
        possibles = possibles,
        homeKnown = home != null,
    )
}
