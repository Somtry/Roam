package com.roam.app

import android.content.Context
import android.provider.MediaStore
import com.roam.engine.PhotoMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MediaStore 扫描器 — 只读元数据,不读图像、不复制原图
 * (产品二稿 §11.4 隐私=架构:零字节上传,元数据本地喂引擎)
 */
object MediaScanner {

    suspend fun scan(context: Context): List<PhotoMeta> = withContext(Dispatchers.IO) {
        val out = mutableListOf<PhotoMeta>()
        val proj = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.LATITUDE,
            MediaStore.Images.Media.LONGITUDE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj,
            null, null, "${MediaStore.Images.Media.DATE_TAKEN} ASC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val iLat = c.getColumnIndexOrThrow(MediaStore.Images.Media.LATITUDE)
            val iLon = c.getColumnIndexOrThrow(MediaStore.Images.Media.LONGITUDE)
            val iW = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            while (c.moveToNext()) {
                val taken = c.getLong(iTaken)
                if (taken <= 0) continue // 无拍摄时间:文件名兜底属深度题,V0.1 先跳过
                val lat = c.getDouble(iLat)
                val lon = c.getDouble(iLon)
                out += PhotoMeta(
                    id = c.getLong(iId),
                    takenAt = taken,
                    latitude = if (lat != 0.0) lat else null,
                    longitude = if (lon != 0.0) lon else null,
                    width = c.getInt(iW),
                    height = c.getInt(iH),
                    // MediaStore 不给 Make/Model,正片过滤用尺寸宽通道兜底:
                    // by Design(二稿 §7.1),截图宽<1000 被滤;此处先按 有尺寸 即正片
                    maker = "media", model = null,
                )
            }
        }
        out
    }
}
