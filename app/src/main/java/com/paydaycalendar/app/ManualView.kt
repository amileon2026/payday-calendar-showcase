package com.paydaycalendar.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/// 手动模式主体：卡片按「当前周期还款日是几号」分三组（1–10 / 11–20 / 21–31 日），
/// 每组一个居中浅灰分区标题，下面用 FlowRow 排小框；组内顺序与默认排序一致。
/// 空组整段不显示（连标题也不显示）。
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualView(
    pairs: List<Pair<CreditCard, CycleInfo>>,
    onTogglePaid: (CreditCard) -> Unit
) {
    val groups: List<Pair<String, IntRange>> = listOf(
        "1–10日" to (1..10),
        "11–20日" to (11..20),
        "21–31日" to (21..31)
    )
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        groups.forEach { (label, range) ->
            val groupPairs = pairs.filter { it.second.dueDate.dayOfMonth in range }
            if (groupPairs.isEmpty()) return@forEach
            Column {
                Text(
                    label,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = Color(0xFF8E8E93),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupPairs.forEach { (card, info) ->
                        ManualChip(card = card, info = info) { onTogglePaid(card) }
                    }
                }
            }
        }
    }
}

/// 手动模式小框：宽度只由卡名长短决定（wrap content），绝不拉伸占满行宽。
/// 点一下切换「本期已还」标记：框带动画缩到约 0.55 边长（面积约 1/4），
/// 框上叠一个半透明醒目绿圆＋白勾；再点恢复。已结束的单次卡置灰且不可点。
@Composable
fun ManualChip(card: CreditCard, info: CycleInfo, onClick: () -> Unit) {
    val finished = info.isFinished
    val paid = !finished && card.manualPaidDueDate == info.dueDate.toEpochDay()
    val scale by animateFloatAsState(
        targetValue = if (paid) 0.55f else 1f,
        animationSpec = tween(durationMillis = 250),
        label = "manualChipScale"
    )
    val shape = RoundedCornerShape(14.dp)
    Box {
        Box(
            modifier = Modifier
                // 视觉缩放（锚点左上）＋布局占位同步缩小，FlowRow 里不留大片空白
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val w = (placeable.width * scale).roundToInt().coerceAtLeast(0)
                    val h = (placeable.height * scale).roundToInt().coerceAtLeast(0)
                    layout(w, h) { placeable.placeRelative(0, 0) }
                }
                .shadow(4.dp, shape)
                .clip(shape)
                .then(
                    if (finished) Modifier.background(Color(0xFF8E8E93))
                    else Modifier.background(CardPalette.brush(card.themeIndex))
                )
                .clickable(enabled = !finished) { onClick() }
        ) {
            Text(
                card.name.ifEmpty { "未命名卡片" },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        if (paid) {
            // 半透明但醒目的绿勾：居中叠在缩小后的框上，不随框一起缩小
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF34C759).copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "本期已还",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
