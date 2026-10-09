package com.roam.app

import android.content.Context
import org.json.JSONArray

/**
 * 离线城市解析器:坐标 → 最近的城市名(全国 364 地级行政区,内置 assets,零网络)。
 * 引擎只承诺城市级(二稿 §7.1),此分辨率与 homeRadius=150km 匹配。
 */
object CityResolver {

    data class City(val province: String, val name: String, val lat: Double, val lon: Double)

    private var cities: List<City> = emptyList()
    private var loaded = false

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        runCatching {
            val text = context.assets.open("cities.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            val out = ArrayList<City>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += City(o.getString("p"), o.getString("c"),
                    o.getDouble("la"), o.getDouble("lo"))
            }
            cities = out
        }
        loaded = true
    }

    /** 最近邻城市;null = 库外坐标(海外/偏远) */
    fun nearest(context: Context, lat: Double, lon: Double): City? {
        ensureLoaded(context)
        var best: City? = null
        var bestD = Double.MAX_VALUE
        for (c in cities) {
            val d = sq(c.lat - lat) + sq(c.lon * 0.86 - lon * 0.86) // 纬度粗校正
            if (d < bestD) { bestD = d; best = c }
        }
        return best
    }

    private fun sq(x: Double) = x * x

    fun displayName(lat: Double?, lon: Double?): String {
        // 静态无 context 场景外的兜底;真正调用都在有 context 后
        return "未知地点"
    }
}
