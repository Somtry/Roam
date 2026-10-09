package com.roam.engine

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** 引擎参数(与产品二稿 §7.2 对齐;HomePage=常住地可空) */
data class EngineConfig(
    val zone: ZoneId = ZoneId.of("Asia/Shanghai"),
    val burstGapMin: Long = 20,
    val tripMinDays: Int = 2,
    val suspectDayGap: Long = 1,
    val foldoutMinShots: Int = 5,
    val homeLatLon: Pair<Double, Double>? = null,
    val homeRadiusKm: Double = 150.0,
)

/** 日级聚合行 */
data class DayStat(
    val day: LocalDate,
    val photos: List<PhotoMeta>,
    val bursts: List<List<PhotoMeta>>,
    val timeFirst: String,
    val timeLast: String,
)

/** Trip 候选(trip 或 fold-out) */
data class TripCandidate(
    val type: Type,
    val days: List<DayStat>,
    val spanDays: Int,
    val shots: Int,
    val confidence: Double,
    val reason: String,
    val centroidLat: Double? = null,
    val centroidLon: Double? = null,
    val awayFromHomeKm: Double? = null,
) {
    enum class Type { TRIP, POSSIBLE }
    val startDate: LocalDate get() = days.first().day
    val endDate: LocalDate get() = days.last().day
}

object Detect {

    fun haversineKm(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val r = 6371.0
        val (lat1, lon1) = a; val (lat2, lon2) = b
        val la1 = Math.toRadians(lat1); val la2 = Math.toRadians(lat2)
        val dLat = la2 - la1
        val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2).let { it * it } +
                cos(la1) * cos(la2) * sin(dLon / 2).let { it * it }
        return 2 * r * asin(min(1.0, sqrt(h)))
    }

    /** 管道: 过滤 → 日聚合 → burst 聚类 → Trip 判定 */
    fun detect(photos: List<PhotoMeta>, cfg: EngineConfig = EngineConfig()): DetectResult {
        val valid = photos.filter { it.isOriginal && it.takenAt != null }
            .sortedBy { it.takenAt }
        val dropped = photos.size - valid.size

        val byDay = valid.groupBy {
            Instant.ofEpochMilli(it.takenAt!!).atZone(cfg.zone).toLocalDate()
        }.toSortedMap()

        val dayStats = byDay.map { (d, list) ->
            DayStat(
                day = d,
                photos = list,
                bursts = clusterBursts(list, cfg),
                timeFirst = fmt(list.first().takenAt!!),
                timeLast = fmt(list.last().takenAt!!),
            )
        }

        val candidates = findTrips(dayStats, cfg)
        return DetectResult(valid, dropped, dayStats, candidates)
    }

    internal fun clusterBursts(list: List<PhotoMeta>, cfg: EngineConfig): List<List<PhotoMeta>> {
        val bursts = mutableListOf<List<PhotoMeta>>()
        var cur = mutableListOf(list.first())
        for (i in 1 until list.size) {
            val gapMin = (list[i].takenAt!! - cur.last().takenAt!!) / 60_000
            if (gapMin <= cfg.burstGapMin) cur.add(list[i])
            else { bursts.add(cur); cur = mutableListOf(list[i]) }
        }
        bursts.add(cur)
        return bursts
    }

    private fun findTrips(dayStats: List<DayStat>, cfg: EngineConfig): List<TripCandidate> {
        val out = mutableListOf<TripCandidate>()
        var i = 0
        while (i < dayStats.size) {
            var j = i
            while (j + 1 < dayStats.size &&
                    dayStats[j + 1].day.toEpochDay() - dayStats[j].day.toEpochDay() <= cfg.suspectDayGap + 1
            ) j++
            val seg = dayStats.subList(i, j + 1)
            val nDays = seg.size
            val shots = seg.sumOf { it.photos.size }
            val gpsList = seg.flatMap { it.photos }.filter { it.latitude != null }
            val (cLat, cLon) = centroid(gpsList)

            if (nDays >= cfg.tripMinDays) {
                val awayKm = if (cfg.homeLatLon != null && cLat != null)
                    haversineKm(cfg.homeLatLon!!, cLat to cLon!!) else null
                val isAway = when {
                    cfg.homeLatLon != null && awayKm != null -> awayKm > cfg.homeRadiusKm
                    cfg.homeLatLon == null && gpsList.isNotEmpty() -> true // 常住地未知:有GPS即异地前提
                    else -> false
                }
                if (isAway) {
                    val conf = min(1.0, 0.4 * min(nDays / cfg.tripMinDays.toDouble(), 2.5) + 0.4 + 0.2) // 原型 gps_ratio 简化:全带GPS满档
                    out += TripCandidate(
                        type = TripCandidate.Type.TRIP, days = seg, spanDays = nDays,
                        shots = shots, confidence = conf,
                        reason = if (awayKm != null) "距常住地 ${awayKm.toInt()} 公里" else "跨天活动·常住地未设置",
                        centroidLat = cLat, centroidLon = cLon, awayFromHomeKm = awayKm,
                    )
                } else {
                    out += foldout(seg, shots, "跨天但距常住地太近,疑似本地生活")
                }
            } else {
                out += foldout(seg, shots, "单日活动,缺少跨天证据")
            }
            i = j + 1
        }
        return out
    }

    private fun foldout(seg: List<DayStat>, shots: Int, reason: String) = TripCandidate(
        type = TripCandidate.Type.POSSIBLE, days = seg, spanDays = 1,
        shots = shots, confidence = 0.3, reason = reason,
    )

    private fun centroid(gps: List<PhotoMeta>): Pair<Double?, Double?> {
        if (gps.isEmpty()) return null to null
        return gps.map { it.latitude!! }.average() to gps.map { it.longitude!! }.average()
    }

    private fun fmt(epochMs: Long): String {
        val t = Instant.ofEpochMilli(epochMs).atZone(ZoneId.of("Asia/Shanghai"))
        return "%02d:%02d".format(t.hour, t.minute)
    }
}

data class DetectResult(
    val valid: List<PhotoMeta>,
    val droppedCount: Int,
    val dayStats: List<DayStat>,
    val candidates: List<TripCandidate>,
) {
    val trips: List<TripCandidate> get() = candidates.filter { it.type == TripCandidate.Type.TRIP }
    val tripCount: Int get() = trips.size
    val possibles: List<TripCandidate> get() = candidates.filter { it.type == TripCandidate.Type.POSSIBLE }
}
