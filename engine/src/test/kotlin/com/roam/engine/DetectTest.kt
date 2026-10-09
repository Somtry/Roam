package com.roam.engine

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 直接移植冒烟测试的三轮场景为回归测试 */
class DetectTest {
    private val Z = ZoneId.of("Asia/Shanghai")
    private fun at(day: String, hm: String) = java.time.LocalDateTime.parse(
        "${day}T${hm.replace(":", ":")}:00"
    ).atZone(Z).toInstant().toEpochMilli()

    private fun d(y: Int, m: Int, dd: Int, h: Int, min: Int, lat: Double? = null, lon: Double? = null) =
        PhotoMeta(
            id = 0, takenAt = java.time.LocalDateTime.of(y, m, dd, h, min)
                .atZone(Z).toInstant().toEpochMilli(),
            latitude = lat, longitude = lon, width = 3072, height = 4096,
            maker = "Xiaomi", model = "REDMI K80 Pro",
        )

    /** 冒烟轮2: 单日站桩连拍(73米/24分钟) → 必须 0 trips,折叠 */
    @Test
    fun singleDayStationBurst_isNotTrip() {
        val photos = (0..5).map { d(2026, 10, 2, 15, 17 + it * 4) }
        val r = Detect.detect(photos)
        assertEquals(0, r.tripCount)
        assertEquals(1, r.possibles.size)
    }

    /** 冒烟轮3: 跨天多点位(含GPS) → 1 trip */
    @Test
    fun crossDayMultiPoint_gps_isTrip() {
        val photos = listOf(
            d(2026, 10, 1, 10, 23), d(2026, 10, 1, 13, 48), d(2026, 10, 1, 14, 31),
            d(2026, 10, 2, 13, 47),
            d(2026, 10, 2, 15, 17), d(2026, 10, 2, 15, 23), d(2026, 10, 2, 15, 31),
        ).mapIndexed { i, p ->
            // 都在邵阳一带
            p.copy(latitude = 27.22 + i * 0.001, longitude = 111.48 + i * 0.001)
        }
        val r = Detect.detect(photos)
        assertEquals(1, r.tripCount)
        assertEquals(LocalDate.of(2026, 10, 1), r.trips.first().startDate)
        assertEquals(LocalDate.of(2026, 10, 2), r.trips.first().endDate)
    }

    /** 截图/无相机字段 → 被过滤 */
    @Test
    fun screenshots_filteredOut() {
        val junk = (0..2).map {
            PhotoMeta(id = 0, takenAt = at("2026-10-01", "10:00"),
                      latitude = null, longitude = null,
                      width = 1080, height = 2400, maker = null, model = null)
        }
        val good = listOf(
            d(2026, 10, 1, 12, 0, lat = 27.2, lon = 111.4),
            d(2026, 10, 2, 12, 0, lat = 27.3, lon = 111.5),
        )
        val r = Detect.detect(junk + good)
        assertEquals(2, r.valid.size)
        assertEquals(3, r.droppedCount)
        assertEquals(1, r.tripCount)
    }

    /** knownHome: 距常住地 5km 的本地跨天活动 → 不判 TRIP */
    @Test
    fun localActivity_nearHome_isNotTrip() {
        val photos = listOf(
            d(2026, 10, 1, 9, 0, lat = 30.27, lon = 120.15),
            d(2026, 10, 1, 15, 0, lat = 30.27, lon = 120.16),
            d(2026, 10, 2, 10, 0, lat = 30.28, lon = 120.15),
        )
        val r = Detect.detect(photos, EngineConfig(homeLatLon = 30.27 to 120.15))
        assertEquals(0, r.tripCount)
        assertTrue(r.possibles.isNotEmpty())
    }

    /** knownHome: 距常住地 800km → TRIP */
    @Test
    fun farFromHome_isTrip() {
        val photos = listOf(
            d(2026, 10, 1, 10, 0, lat = 27.23, lon = 111.47),
            d(2026, 10, 2, 15, 0, lat = 27.22, lon = 111.48),
        )
        val r = Detect.detect(photos, EngineConfig(homeLatLon = 30.27 to 120.15)) // 杭州
        assertEquals(1, r.tripCount)
        assertTrue((r.trips.first().awayFromHomeKm ?: 0.0) > 700)
    }
}
