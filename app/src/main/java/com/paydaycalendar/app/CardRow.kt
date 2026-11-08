package com.paydaycalendar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/// 紧迫度颜色（表单预览也用）：已结束灰、≤3 天红、≤7 天橙、其余绿。
fun urgencyColor(info: CycleInfo): Color = when {
    info.isFinished -> Color.Gray
    info.daysRemaining <= 3 -> Color.Red
    info.daysRemaining <= 7 -> Color(0xFFFF9500)
    else -> Color(0xFF34C759)
}

/// 一张卡的卡面行：细长条样式。
/// 普通模式：名字、银行尾号、剩余天数、进度条、账单日/还款日。
/// 极简模式：只留名字、剩余天数、进度条三样。
/// 进度条是「剩余量」：越接近还款日越短，到期归零并变红；
/// 到期（含逾期）时右侧出现「已还款」按钮，点后才进入下一周期。
@Composable
fun CardRow(
    card: CreditCard,
    info: CycleInfo,
    minimal: Boolean,
    onPaid: () -> Unit,
    onClick: () -> Unit
) {
    val isDue = info.daysRemaining <= 0 && !info.isFinished
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(CardPalette.brush(card.themeIndex))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (minimal) {
                    Text(
                        card.name.ifEmpty { "未命名卡片" },
                        color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.weight(1f))
                } else {
                    Column(Modifier.weight(1f)) {
                        Text(
                            card.name.ifEmpty { "未命名卡片" },
                            color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = listOf(card.bank, if (card.last4.isEmpty()) "" else "•••• ${card.last4}")
                            .filter { it.isNotEmpty() }.joinToString(" ")
                        if (subtitle.isNotEmpty()) {
                            Text(
                                subtitle,
                                color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                DaysBlock(info)
                if (isDue) {
                    Spacer(Modifier.width(10.dp))
                    PaidButton(onPaid)
                }
            }

            ProgressBar(progress = info.progress, alert = isDue)

            if (!minimal) {
                Row {
                    Text(
                        "账单日 ${info.statementDate.format(DateFmt.monthDay)}",
                        color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "还款日 ${info.dueDate.format(DateFmt.monthDay)}",
                        color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/// 右侧天数：数字 + 单位 + 状态小字；已结束的单次卡只显示「已结束」
@Composable
fun DaysBlock(info: CycleInfo) {
    if (info.isFinished) {
        Text(
            "已结束",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 17.sp, fontWeight = FontWeight.SemiBold
        )
        return
    }
    Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                info.daysText,
                color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold
            )
            if (info.daysRemaining != 0L) {
                Text(
                    "天",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 3.dp, start = 2.dp)
                )
            }
        }
        Text(
            when {
                info.daysRemaining > 0 -> "后还款"
                info.daysRemaining == 0L -> "今天还款"
                else -> "已逾期"
            },
            color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp
        )
    }
}

@Composable
fun PaidButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text("已还款", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/// 剩余量进度条：白色填充（到期/逾期变红），轨道为半透明白。
@Composable
fun ProgressBar(progress: Double, alert: Boolean) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.35f))
    ) {
        if (progress > 0) {
            val w = (maxWidth * progress.toFloat()).coerceAtLeast(6.dp)
            Box(
                modifier = Modifier
                    .width(w)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(if (alert) Color.Red else Color.White)
            )
        }
    }
}
