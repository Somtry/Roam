package com.roam.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Roam 视觉系统:
 *  - 品牌渐变(晨曦→远山):主背景、卡头、统计胶囊
 *  - 列表项进入动画(弹簧滑动+淡入),滚动懒加载逐项播放
 */
object RoamVisuals {

    // 品牌色板
    val Dawn = Color(0xFF6A8DFF)      // 晨曦蓝
    val Dusk = Color(0xFF3D47C9)      // 远山靛
    val Mist = Color(0xFFEAF0FF)      // 雾白(浅底)
    val Ink = Color(0xFF171B33)       // 墨字
    val Sky = Color(0xFF8FA5FF)       // 点缀天蓝
    val Accent = Color(0xFFFF7A59)    // 珊瑚橘(统计强调)

    /** 页面层背景:上雾白下浅蓝的柔和天幕 */
    fun pageBg(): Brush = Brush.verticalGradient(
        listOf(Color(0xFFF7F9FF), Mist, Color(0xFFE3EAFF))
    )

    /** 卡片头图渐变(照片上叠字用) */
    fun heroScrims(): Brush = Brush.verticalGradient(
        listOf(Color(0x00121B44), Color(0xB3121B44))
    )
}

/** 列表项:进入动画(向上滑入+淡入,弹簧落位),配合 LazyColumn index 逐项错峰 */
fun Modifier.animateItemIn(index: Int): Modifier = composed {
    val progress = rememberInfiniteTransition(label = "in").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1), RepeatMode.Restart), label = "noop"
    )
    // 组合:进入动画一次性(以 remember 状态计时);index 控制错峰延迟
    val shown = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index * 60L).coerceAtMost(420))
        shown.value = true
    }
    val alpha = animateFloatAsState(
        targetValue = if (shown.value) 1f else 0f,
        animationSpec = tween(360), label = "a"
    ).value
    val ty = animateFloatAsState(
        targetValue = if (shown.value) 0f else 44f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 340f),
        label = "t"
    ).value
    graphicsLayer { this.alpha = alpha; translationY = ty }
}

/** 通用:首页→详情页的转场(缩放+淡入) */
fun Modifier.zoomIn(visible: Boolean): Modifier = composed {
    val s = animateFloatAsState(
        if (visible) 1f else 0.92f, spring(stiffness = 380f), label = "z"
    ).value
    graphicsLayer { scaleX = s; scaleY = s; alpha = if (visible) 1f else 0f }
}

/** 微动效:扫描时呼吸 */
fun Modifier.pulse(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "p").animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "pf"
    )
    graphicsLayer { scaleX = t.value; scaleY = t.value }
}
