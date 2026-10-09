package com.roam.engine

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** 引擎参数(与产品二稿 §7.2 对齐;home=常住地可空) */
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
        val (lat1, lon1) = a; val (lat2, lon2) = b
        val la1 = Math.toRadians(lat1); val la2 = Math.toRadians(lat2)
        val dLat = la2 - la1
        val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2).let { it * it } +
                cos(la1) * cos(la2) * sin(dLon / 2).let { it * it }
        return 2 * 6371.0 * asin(min(1.0, sqrt(h)))
    }

    /**
     * 常住地推断:0.1°(≈11km) 网格内"有照片的不同天数"最多者即家
     * —— 家的特点是"年复一年地出现",旅行最多待几天。
     * 不足 minDays 天的格不足以断言,返回 null(引擎退化为不判远近)。
     */
    fun inferHome(
        photos: List<PhotoMeta>,
        cfg: EngineConfig = EngineConfig(),
        minDays: Int = 30,
    ): Pair<Double, Double>? {
        if (cfg.homeLatLon != null) return cfg.homeLatLon
        val cells = HashMap<Pair<Int, Int>, HashSet<LocalDate>>()
        for (p in photos) {
            val t = p.takenAt ?: continue
            val la = p.latitude ?: continue
            val lo = p.longitude ?: continue
            if (!p.isOriginal) continue
            val day = Instant.ofEpochMilli(t).atZone(cfg.zone).toLocalDate()
            cells.getOrPut(Math.round(la * 10).toInt() to Math.round(lo * 10).toInt()) { HashSet() }
                .add(day)
        }
        val best = cells.maxByOrNull { it.value.size } ?: return null
        if (best.value.size < minDays) return null
        val pts = photos.filter {
            it.latitude != null && (Math.round(it.latitude!! * 10).toInt() to
                    Math.round(it.longitude!! * 10).toInt()) == best.key
        }
        if (pts.isEmpty()) return null
        return pts.map { it.latitude!! }.average() to pts.map { it.longitude!! }.average()
    }

    /** 管道: 过滤 → 日聚合 → burst 聚类 → 逐日判远近 → Trip 判定 */
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

    /** 一天的异地性:true=距家>阈值;false=在家;null=该天无GPS照片 */
    private class DayCls(val stat: DayStat, val away: Boolean?)

    /**
     * 逐日判定 + 连续段扫描:
     *  - Trip = 连续 ≥ tripMinDays 个"异地日"(容忍 ≤ suspectDayGap 个桥日)
     *  - 本地生活日组(连续在家)不产生任何候选 → 日常随手拍不再刷屏
     *  - 折叠区只收:单日异地(短途疑似)/无 GPS 跨天组/单日连拍亮点
     */
    private fun findTrips(dayStats: List<DayStat>, cfg: EngineConfig): List<TripCandidate> {
        val home = cfg.homeLatLon
        val out = mutableListOf<TripCandidate>()

        val cls = dayStats.map { ds ->
            val gps = ds.photos.filter { it.latitude != null }
            val away: Boolean? = when {
                gps.isEmpty() -> null
                home == null -> true // 常住地未知:有GPS即按异地论(保守前提,App 层应传 home)
                else -> {
                    val c = gps.map { it.latitude!! }.average() to gps.map { it.longitude!! }.average()
                    haversineKm(home, c) > cfg.homeRadiusKm
                }
            }
            DayCls(ds, away)
        }

        var i = 0
        val n = cls.size
        while (i < n) {
            if (cls[i].away != true) {
                // 本地/无GPS日组:收拢成组,只对"疑似形态"出折叠候选
                var j = i
                var unknowns = 0
                while (j < n && cls[j].away != true) {
                    if (cls[j].away == null) unknowns++; j++
                }
                val group = cls.subList(i, j)
                val days = group.map { it.stat }
                val shots = days.sumOf { it.photos.size }
                when {
                    // 全组无 GPS 且跨天 → 值得人工确认
                    group.size >= 2 && unknowns == group.size ->
                        out += foldout(days, shots, "跨天活动但缺GPS,无法判断远近")
                    // 单日无GPS的连拍亮点(away!=false,本地日除外)
                    group.size == 1 && shots >= cfg.foldoutMinShots && cls[i].away != false ->
                        out += foldout(days, shots, "单日活动,缺少跨天证据")
                    // 其余(本地生活/零星) → 不出候选,避免刷屏
                }
                i = j
                continue
            }

            // 异地日起点:扩展连续段。两日历日差 > suspectDayGap+1 视为断程,
            // 防止相隔数月/年的活跃日被"桥日"连进同一次旅行
            var j = i
            var bridge = 0
            while (j + 1 < n) {
                val nxt = cls[j + 1]
                val gap = nxt.stat.day.toEpochDay() - cls[j].stat.day.toEpochDay()
                if (gap > cfg.suspectDayGap + 1) break
                if (nxt.away == true) { j++; bridge = 0 } else if (bridge < cfg.suspectDayGap) {
                    j++; bridge++
                } else break
            }
            while (j > i && cls[j].away != true) j-- // 回收尾部桥日
            val run = cls.subList(i, j + 1)
            val days = run.map { it.stat }
            val shots = days.sumOf { it.photos.size }
            val awayDays = run.count { it.away == true }

            if (awayDays >= cfg.tripMinDays) {
                val gpsAll = days.flatMap { it.photos }.filter { it.latitude != null }
                val cLat = if (gpsAll.isNotEmpty()) gpsAll.map { it.latitude!! }.average() else null
                val cLon = if (gpsAll.isNotEmpty()) gpsAll.map { it.longitude!! }.average() else null
                val awayKm = if (home != null && cLat != null)
                    haversineKm(home, cLat to cLon!!) else null
                val conf = min(1.0, 0.4 * min(awayDays / cfg.tripMinDays.toDouble(), 2.5) + 0.6)
                out += TripCandidate(
                    type = TripCandidate.Type.TRIP, days = days, spanDays = days.size,
                    shots = shots, confidence = conf,
                    reason = if (awayKm != null) "距常住地 ${awayKm.toInt()} 公里"
                    else "跨天GPS活动·常住地未设置",
                    centroidLat = cLat, centroidLon = cLon, awayFromHomeKm = awayKm,
                )
            } else {
                // 单个异地日:可能是一次短途游,折叠让人裁决
                out += foldout(days, shots, "单日异地活动,可能短途游")
            }
            i = j + 1
        }
        return out
    }

    private fun foldout(days: List<DayStat>, shots: Int, reason: String) = TripCandidate(
        type = TripCandidate.Type.POSSIBLE, days = days, spanDays = days.size,
        shots = shots, confidence = 0.3, reason = reason,
    )

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
