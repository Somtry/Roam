package com.roam.app

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.roam.engine.PhotoMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * MediaStore 扫描器 — 只读元数据,不读图像内容、不复制原图(零上载)
 *
 * 读取策略(顺序):
 *  1. MediaStore 列:枚举(快)+ DATE_TAKEN(由系统保证)
 *  2. 单 URI 流式读 256KB 头部:
 *     - GPS   → MiniExifGps(自研字节级解析,兼容小米 North/East Ref 越界)
 *     - 时间兜底/宽高 → androidx ExifInterface(喂同一份字节)
 */
object MediaScanner {

    private const val TAG = "RoamScanner"
    private const val HEAD_LIMIT = 256 * 1024

    data class ScanStats(val total: Int, val withTakenAt: Int, val withGps: Int)

    suspend fun scan(context: Context): Pair<List<PhotoMeta>, ScanStats> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<PhotoMeta>()
            var withGps = 0
            var withTaken = 0
            val proj = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
            )
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj,
                null, null, "${MediaStore.Images.Media.DATE_TAKEN} ASC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val iTaken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val iW = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val baseUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    var taken = c.getLong(iTaken)
                    if (taken < 0) taken = 0L
                    var lat: Double? = null
                    var lon: Double? = null
                    var width = c.getInt(iW)
                    var height = c.getInt(iH)

                    try {
                        val uri = android.net.Uri.withAppendedPath(baseUri, id.toString())
                        val ins = runCatching {
                            // fd 直读:绕 MediaProvider 的流代理(AOSP 模拟器会清 EXIF 内嵌值;
                            // 真机不清洗,但 fd 路径两端一致,更保真)
                            java.io.FileInputStream(
                                context.contentResolver.openFileDescriptor(uri, "r")!!.fileDescriptor
                            )
                        }.getOrNull() ?: context.contentResolver.openInputStream(uri) ?: continue
                        ins.use { ins ->
                            // 头部字节一次读入(EXIF APP1 在文件前部)
                            val headBuf = ByteArrayOutputStream()
                            val b = ByteArray(64 * 1024)
                            var total = 0
                            while (total < HEAD_LIMIT) {
                                val r = ins.read(b)
                                if (r <= 0) break
                                headBuf.write(b, 0, r)
                                total += r
                            }
                            val bytes = headBuf.toByteArray()
                            // GPS:自研解析(修复小米 Ref 越界兼容问题)
                            // 注:AOSP 对外部贡献文件的流代理可能脱敏 EXIF 内嵌值(见验证记录)
                            MiniExifGps.read(ByteArrayInputStream(bytes))?.let { (la, lo) ->
                                lat = la; lon = lo
                            }
                            // 时间/宽高:ExifInterface(喂同一份字节;无文件依赖)
                            val exif = ExifInterface(ByteArrayInputStream(bytes))
                            if (taken <= 0L) {
                                exif.dateTimeOriginal?.let { taken = it }
                            }
                            if (width <= 0) width =
                                exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                            if (height <= 0) height =
                                exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                        }
                    } catch (e: Exception) {
                        // 单张读失败不阻塞全库
                        Log.w(TAG, "EXIF 读取失败: id=$id", e)
                    }

                    if (taken > 0L) withTaken++
                    if (lat != null) withGps++
                    out += PhotoMeta(
                        id = id, takenAt = if (taken > 0) taken else null,
                        latitude = lat, longitude = lon,
                        width = if (width > 0) width else 0,
                        height = if (height > 0) height else 0,
                        maker = "media", model = null,
                    )
                }
            }
            Log.i(TAG, "扫描完成: ${out.size} 张, 有时间 $withTaken, 有GPS $withGps")
            out.toList() to ScanStats(out.size, withTaken, withGps)
        }
}
