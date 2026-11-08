package com.paydaycalendar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/// 顶部横幅：大小固定不变；每个用户占一栏、横着并排，
/// 显示该用户最近还款的卡与剩余天数。当前用户的栏位带「被按下去」的凹陷效果。
/// 点一栏可切换到该用户。
@Composable
fun NearestBanner(rows: List<BannerRow>, activeID: String, onSelect: (String) -> Unit) {
    val accent = rows
        .filter { it.info != null && it.info?.isFinished == false }
        .minByOrNull { it.info!!.daysRemaining }
        ?.card?.themeIndex ?: 1
    val daysSize = when (rows.size) {
        1 -> 52
        2 -> 42
        3 -> 34
        4 -> 28
        else -> 24
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .shadow(10.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(CardPalette.brush(accent))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            rows.forEachIndexed { idx, row ->
                if (idx > 0) {
                    Spacer(Modifier.width(5.dp))
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(64.dp)
                            .background(Color.White.copy(alpha = 0.35f))
                    )
                    Spacer(Modifier.width(5.dp))
                }
                BannerColumn(
                    row = row,
                    isActive = row.profile.id == activeID,
                    daysSize = daysSize,
                    nameSize = if (rows.size >= 4) 12 else 14,
                    showDueDate = rows.size <= 2,
                    modifier = Modifier.weight(1f)
                ) { onSelect(row.profile.id) }
            }
        }
    }
}

@Composable
private fun BannerColumn(
    row: BannerRow,
    isActive: Boolean,
    daysSize: Int,
    nameSize: Int,
    showDueDate: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val pressedShape = RoundedCornerShape(14.dp)
    var m = modifier.fillMaxHeight()
    m = if (isActive) {
        m.graphicsLayer { scaleX = 0.97f; scaleY = 0.97f }
            .background(Color.Black.copy(alpha = 0.22f), pressedShape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent)
                ),
                pressedShape
            )
            .border(1.dp, Color.White.copy(alpha = 0.35f), pressedShape)
    } else {
        m
    }
    Column(
        modifier = m
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            row.profile.name,
            color = Color.White, fontSize = nameSize.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        val info = row.info
        if (info != null && info.isFinished) {
            Text(
                "已结束",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = (daysSize * 0.45).sp, fontWeight = FontWeight.Bold
            )
            Text(
                row.card?.name?.ifEmpty { "未命名卡片" } ?: "",
                color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        } else if (info != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    info.daysText,
                    color = Color.White, fontSize = daysSize.sp, fontWeight = FontWeight.Bold
                )
                if (info.daysRemaining != 0L) {
                    Text(
                        "天",
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                    )
                }
            }
            Text(
                row.card?.name?.ifEmpty { "未命名卡片" } ?: "",
                color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (info.daysRemaining < 0) {
                Text(
                    "已逾期",
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                )
            } else if (showDueDate) {
                Text(
                    "还款日 ${info.dueDate.format(DateFmt.monthDay)}",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 1
                )
            }
        } else {
            Text(
                "—",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = daysSize.sp, fontWeight = FontWeight.Bold
            )
            Text(
                "暂无卡片",
                color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp
            )
        }
    }
}
