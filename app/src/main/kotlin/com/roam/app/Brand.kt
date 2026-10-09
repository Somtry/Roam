package com.roam.app

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Roam 品牌 Canvas 图案:
 * 一条断续的旅线(虚线路径)绕过圆环轨道,尾端一枚琥珀定位点——
 * "路线 · 有迹可循"的产品隐喻。纯矢量自绘,零图片资源。
 */
@Composable
fun RoamLogo(
    modifier: Modifier = Modifier,
    line: Color = Color.White,
    accent: Color = Color(0xFFFFC46B),
    ring: Color = Color(0x59FFFFFF),
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val c = Offset(w * 0.5f, h * 0.55f)
        val r = w * 0.34f

        // 轨道环(虚线弱音)
        val ringPath = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(c, r))
        }
        drawPath(
            ringPath, color = ring,
            style = Stroke(
                width = w * 0.045f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.09f, w * 0.07f)),
            ),
        )

        // 旅线:从左下入,顺势绕环,到右上升出(贝塞尔两段)
        val travel = Path().apply {
            moveTo(w * 0.10f, h * 0.88f)
            cubicTo(w * 0.30f, h * 0.72f, w * 0.34f, h * 0.40f, w * 0.55f, h * 0.42f)
            cubicTo(w * 0.74f, h * 0.44f, w * 0.78f, h * 0.22f, w * 0.90f, h * 0.12f)
        }
        drawPath(
            travel, color = line,
            style = Stroke(width = w * 0.075f, cap = StrokeCap.Round),
        )

        // 途经点(线上小点)
        drawCircle(color = line, radius = w * 0.030f, center = Offset(w * 0.42f, h * 0.52f))
        // 尾端定位点(琥珀重音 + 外圈)
        drawCircle(color = accent, radius = w * 0.058f, center = Offset(w * 0.90f, h * 0.12f))
        drawCircle(
            color = accent.copy(alpha = 0.30f), radius = w * 0.100f,
            center = Offset(w * 0.90f, h * 0.12f),
        )
    }
}

/**
 * 毛玻璃悬浮容器(iOS 风玻璃胶囊):
 * 半透明白(72%)+ 上深下浅内部渐变(玻璃厚度感)+ 顶部边缘 1px 高亮(高光棱)。
 * 内容滚到底栏后隐约可见,形成"透"的错觉(等效毛玻璃,免快照开销)。
 */
@Composable
fun FrostedBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .clip(shape)
            .border(1.dp, Color(0x99FFFFFF), shape)
            .shadow(20.dp, shape, ambientColor = Color(0x228A8FB8), spotColor = Color(0x33808CC0))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color(0xEBFFFFFF),      // 顶厚
                    0.55f to Color(0xD9FFFFFF),
                    1f to Color(0xCCFAFBFF),      // 底薄泛蓝光
                )
            )
    ) {
        content()
    }
}
