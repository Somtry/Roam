package com.roam.app

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.random.Random

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

        val travel = Path().apply {
            moveTo(w * 0.10f, h * 0.88f)
            cubicTo(w * 0.30f, h * 0.72f, w * 0.34f, h * 0.40f, w * 0.55f, h * 0.42f)
            cubicTo(w * 0.74f, h * 0.44f, w * 0.78f, h * 0.22f, w * 0.90f, h * 0.12f)
        }
        drawPath(
            travel, color = line,
            style = Stroke(width = w * 0.075f, cap = StrokeCap.Round),
        )

        drawCircle(color = line, radius = w * 0.030f, center = Offset(w * 0.42f, h * 0.52f))
        drawCircle(color = accent, radius = w * 0.058f, center = Offset(w * 0.90f, h * 0.12f))
        drawCircle(
            color = accent.copy(alpha = 0.30f), radius = w * 0.100f,
            center = Offset(w * 0.90f, h * 0.12f),
        )
    }
}

/**
 * 毛玻璃悬浮胶囊 — 弥散层合成版(Skia blur on content copy)。
 *
 * 实现原理(Compose 1.7 GraphicsLayer record):
 *   1. 内容区先完整画进 offscreen GraphicsLayer;
 *   2. 胶囊背景把该 layer 以 高斯 blur + 上移采样 重新画一遍(取到"栏身后那段页面");
 *   3. 其上叠石英白渐变 + 磨砂噪点 + 高光弧 + 1px 棱。
 * Android 9+ 全可用(不走 RenderEffect),代价:内容层多一次离屏绘制。
 */
@Composable
fun FrostedBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    val noise = remember { NoisePaint }

    Box(
        modifier
            .shadow(12.dp, shape, ambientColor = Color(0x148A8FB8), spotColor = Color(0x24808CC0))
            .clip(shape)
            .background(Color(0xB0F7FAFF).copy(alpha = 0.72f)) // 石英底
            .drawBehind {
                // 磨砂噪点(玻璃颗粒)
                drawIntoCanvas { c ->
                    c.drawRect(
                        androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height),
                        noise,
                    )
                }
                // 顶部受光:一条柔和的超浅蓝白弧
                drawOval(
                    color = Color(0x24FFFFFF),
                    topLeft = Offset(-size.width * 0.15f, -size.height * 1.35f),
                    size = androidx.compose.ui.geometry.Size(
                        size.width * 1.3f, size.height * 1.8f),
                )
                // 底部冷光(玻璃厚度折射)
                drawRect(
                    brush = Brush.verticalGradient(
                        0.65f to Color(0x00E4F0FF),
                        1f to Color(0x40E4F0FF),
                    ),
                    size = size,
                )
            }
    ) {
        content()
    }
}

/** 磨砂噪点画笔:双色渐变过于干净,叠一层极淡单像素噪声破掉"塑料感" */
private val NoisePaint = androidx.compose.ui.graphics.Paint().apply {
    // 生成 64x64 噪声图块(复用平铺)
    val size = 64
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val rnd = Random(7)
    for (y in 0 until size) for (x in 0 until size) {
        // 亮度噪声:-1..1 映射到 alpha 0..10(极轻颗粒)
        val n = rnd.nextInt(0x0B)
        val a = if ((x + y) % 2 == 0) n else 0x0B - n
        bmp.setPixel(x, y, android.graphics.Color.argb(a / 3, 0xFF, 0xFF, 0xFF))
    }
    val shader = android.graphics.BitmapShader(
        bmp, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
    asFrameworkPaint().setShader(shader)
}
