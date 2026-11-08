package com.paydaycalendar.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/// 18 种卡面渐变，与 iOS 版数值完全一致（序号不变，已有卡的颜色不会错位）。
object CardPalette {
    val gradients: List<List<Color>> = listOf(
        listOf(Color(0xFF1C1C1E), Color(0xFF3A3A3C)), // 0 石墨黑
        listOf(Color(0xFF0A4FD6), Color(0xFF3D8BFF)), // 1 银行蓝
        listOf(Color(0xFF7A1FA2), Color(0xFFC86DD7)), // 2 紫
        listOf(Color(0xFFC21807), Color(0xFFFF7043)), // 3 红橙
        listOf(Color(0xFF00796B), Color(0xFF4DB6AC)), // 4 青绿
        listOf(Color(0xFFB8860B), Color(0xFFF2C94C)), // 5 金
        listOf(Color(0xFF283593), Color(0xFF5C6BC0)), // 6 靛蓝
        listOf(Color(0xFFAD1457), Color(0xFFEC407A)), // 7 玫红
        listOf(Color(0xFF37474F), Color(0xFF78909C)), // 8 蓝灰
        listOf(Color(0xFF1B5E20), Color(0xFF66BB6A)), // 9 森林绿
        listOf(Color(0xFF01579B), Color(0xFF4FC3F7)), // 10 天空蓝
        listOf(Color(0xFF3E2723), Color(0xFFA1887F)), // 11 咖啡棕
        listOf(Color(0xFF4A148C), Color(0xFFAB47BC)), // 12 葡萄紫
        listOf(Color(0xFFE65100), Color(0xFFFFCC80)), // 13 蜜桃橙
        listOf(Color(0xFF827717), Color(0xFFD4E157)), // 14 青柠绿
        listOf(Color(0xFF002171), Color(0xFF5472D3)), // 15 深海蓝
        listOf(Color(0xFF5C0A1E), Color(0xFFB23A48)), // 16 酒红
        listOf(Color(0xFF5D4037), Color(0xFFD7A98C))  // 17 玫瑰金
    )

    private fun pair(index: Int): List<Color> {
        val n = gradients.size
        return gradients[((index % n) + n) % n]
    }

    /// 左上 → 右下的对角渐变
    fun brush(index: Int): Brush = Brush.linearGradient(
        colors = pair(index),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    fun primary(index: Int): Color = pair(index)[1]
}

/// 界面强调色（与 iOS 系统蓝接近）
val AccentColor = Color(0xFF007AFF)
val PageBackground = Color(0xFFF2F2F7)
val FieldBackground = Color(0xFFEFEFF4)
