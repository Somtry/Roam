package com.roam.app

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 迷你 EXIF GPS 读取器 — 绕过 androidx ExifInterface 对小米相机(
 * GPSLatitudeRef="North" 越界 count)的严格丢弃问题。
 * 只解析 TIFF/EXIF 结构到 GPS IFD,提取 GPSLatitude/Longitude(+Ref 符号)。
 * 实现 100% 局部、只读、无依赖;失败返回 null(引擎按无 GPS 降级)。
 */
object MiniExifGps {

    private val EXIF_MAGIC = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00) // "Exif\0\0"

    fun read(input: InputStream): Pair<Double, Double>? {
        // JPEG 段落扫描:找 APP1(0xFFE1) + "Exif\0\0"
        val head = input.readExact(2)
        if (head.size < 2 || head[0].toInt() and 0xFF != 0xFF || head[1].toInt() and 0xFF != 0xD8) return null
        while (true) {
            val m = input.readExact(2)
            if (m.size < 2) return null
            val marker = (m[0].toInt() and 0xFF shl 8) or (m[1].toInt() and 0xFF)
            if (marker == 0xFFD9) return null                    // EOI:无 EXIF
            if (marker == 0xFFDA) return null                    // SOS:图像数据,EXIF 已过
            val lenB = input.readExact(2)
            if (lenB.size < 2) return null
            val segLen = ((lenB[0].toInt() and 0xFF shl 8) or (lenB[1].toInt() and 0xFF)) - 2
            if (marker == 0xFFE1 && segLen > 6) {
                val exifTag = input.readExact(6)
                if (exifTag.size == 6 && exifTag.contentEquals(EXIF_MAGIC)) {
                    val tiff = input.readExact(segLen - 6)
                    return parseTiff(tiff)
                }
                input.skipNils(segLen - 6) // 不是 Exif APP1,跳过
            } else {
                input.skipNils(segLen)
            }
        }
    }

    private fun InputStream.skipNils(n: Int) { var left = n; val buf = ByteArray(64 * 1024)
        while (left > 0) { val r = read(buf, 0, minOf(left, buf.size)); if (r <= 0) return; left -= r } }

    private fun parseTiff(tiff: ByteArray): Pair<Double, Double>? {
        if (tiff.size < 8) return null
        val b0 = tiff[0].toInt() and 0xFF
        val b1 = tiff[1].toInt() and 0xFF
        val bo = when {
            b0 == 0x4D && b1 == 0x4D -> ByteOrder.BIG_ENDIAN   // 'M','M'
            b0 == 0x49 && b1 == 0x49 -> ByteOrder.LITTLE_ENDIAN // 'I','I'
            else -> return null
        }
        val buf = ByteBuffer.wrap(tiff).order(bo)
        val ifd0 = buf.getInt(4).toInt()
        if (ifd0 + 2 > tiff.size) return null
        var entries = buf.getShort(ifd0).toInt()
        var p = ifd0 + 2
        var gpsOff = -1
        repeat(entries) {
            if (p + 12 > tiff.size) return null
            val tag = buf.getShort(p).toInt() and 0xFFFF
            if (tag == 0x8825) { gpsOff = buf.getInt(p + 8); return@repeat }
            p += 12
        }
        if (gpsOff < 0 || gpsOff + 2 > tiff.size) return null
        entries = buf.getShort(gpsOff).toInt()
        var latRat: Double? = null; var lonRat: Double? = null
        var latRef = 'N'; var lonRef = 'E'
        p = gpsOff + 2
        repeat(entries) {
            if (p + 12 > tiff.size) return null
            val tag = buf.getShort(p).toInt() and 0xFFFF
            val count = buf.getInt(p + 4)
            val valOff = buf.getInt(p + 8)
            when (tag) {
                0x0001 -> if (count >= 1 && valOff < tiff.size) latRef = tiff[valOff].toInt().toChar()
                0x0002 -> if (count == 3) latRat = readDms(buf, tiff, valOff)
                0x0003 -> if (count >= 1 && valOff < tiff.size) lonRef = tiff[valOff].toInt().toChar()
                0x0004 -> if (count == 3) lonRat = readDms(buf, tiff, valOff)
            }
            p += 12
        }
        val lat = latRat ?: return null; val lon = lonRat ?: return null
        return (if (latRef == 'S') -lat else lat) to (if (lonRef == 'W') -lon else lon)
    }

    /** 读度分秒三分量有理数 → 十进制度 */
    private fun readDms(buf: ByteBuffer, tiff: ByteArray, off: Int): Double? {
        if (off + 24 > tiff.size) return null
        fun rat(i: Int): Double? {
            val num = buf.getInt(off + i * 8).toLong()
            val den = buf.getInt(off + i * 8 + 4).toLong()
            if (den == 0L || num < 0) return null
            return num.toDouble() / den
        }
        val d = rat(0) ?: return null; val m = rat(1) ?: return null; val s = rat(2) ?: return null
        if (d + m + s == 0.0) return null
        return d + m / 60.0 + s / 3600.0
    }
}

private fun InputStream.readExact(n: Int): ByteArray {
    val buf = ByteArray(n); var off = 0
    while (off < n) {
        val r = read(buf, off, n - off)
        if (r <= 0) return buf.copyOf(off)
        off += r
    }
    return buf
}
