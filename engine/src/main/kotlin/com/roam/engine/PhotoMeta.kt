package com.roam.engine

/** 单张照片的引擎输入(来自 MediaStore/EXIF 的提炼,与 App 层解耦) */
data class PhotoMeta(
    val id: Long,              // MediaStore id 或语料行号
    val takenAt: Long?,         // Epoch 毫秒;null = 无法判定拍摄时间
    val latitude: Double?,      // WGS-84;null = 无 GPS
    val longitude: Double?,
    val subSec: String? = null, // 亚秒(排序用)
    val offsetMinutes: Int? = null, // 拍摄时区偏移(跨时区铁证)
    val width: Int = 0,
    val height: Int = 0,
    val maker: String? = null,
    val model: String? = null,
    ) {
        /** 正片判定:有相机字段 + 尺寸达标(过滤截图/表情包/缓存图) */
        val isOriginal: Boolean
        get() = !maker.isNullOrBlank() && width >= MIN_WIDTH

        companion object { const val MIN_WIDTH = 1000 }
}
