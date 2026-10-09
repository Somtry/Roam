package com.roam.app

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class MiniExifGpsTest {
    // 本地验证开关:Mac 上跑时指向 data/沉水;CI 无照片时跳过
    private val dir = File(System.getProperty("roam.photos", "data/沉水"))

    @Test fun readsRealXiaomiGps() {
        if (!dir.isDirectory) return
        val files = dir.listFiles { f -> f.extension == "jpg" } ?: return
        var hit = 0
        for (f in files) {
            val gps = MiniExifGps.read(FileInputStream(f))
            if (gps != null) {
                hit++
                println("${f.name} -> %.5f, %.5f".format(gps.first, gps.second))
            }
        }
        println("GPS 命中 $hit / ${files.size}")
        assertTrue("小米照片 GPS 应被解析", hit >= files.size - 1)
    }
}
