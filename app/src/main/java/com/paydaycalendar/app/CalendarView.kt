package com.paydaycalendar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

/// 月历：红点=还款日，灰点=账单日。点任意一天看当天事项。
/// 整月事件只计算一次（remember），再分发给每个格子，与 iOS 优化版一致。
@Composable
fun CalendarView(cards: List<CreditCard>) {
    var displayedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf(LocalDate.now().dayOfMonth) }

    val evts = remember(displayedMonth, cards) { DateCalc.events(displayedMonth, cards) }
    val daysInMonth = displayedMonth.lengthOfMonth()
    val leadingBlanks = displayedMonth.atDay(1).dayOfWeek.value - 1 // 周一为每周第一天
    val todayDay = if (displayedMonth == YearMonth.now()) LocalDate.now().dayOfMonth else null
    val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                displayedMonth.format(DateFmt.monthTitle),
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                displayedMonth = displayedMonth.minusMonths(1)
                selectedDay = minOf(selectedDay, displayedMonth.lengthOfMonth())
            }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月")
            }
            IconButton(onClick = {
                displayedMonth = displayedMonth.plusMonths(1)
                selectedDay = minOf(selectedDay, displayedMonth.lengthOfMonth())
            }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "下个月")
            }
        }

        Row {
            weekdayLabels.forEach { w ->
                Text(
                    w,
                    fontSize = 12.sp, color = Color.Gray,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
            }
        }

        val cells = (List(leadingBlanks) { null } + (1..daysInMonth).map { it }).chunked(7)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            cells.forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { day ->
                        if (day == null) {
                            Box(Modifier.weight(1f).height(44.dp))
                        } else {
                            DayCell(
                                day = day,
                                dayEvents = evts[day] ?: emptyList(),
                                isToday = todayDay == day,
                                isSelected = selectedDay == day,
                                modifier = Modifier.weight(1f)
                            ) { selectedDay = day }
                        }
                    }
                    repeat(7 - week.size) { Box(Modifier.weight(1f).height(44.dp)) }
                }
            }
        }

        HorizontalDivider()

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${selectedDay} 日事项", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            val dayEvents = (evts[selectedDay] ?: emptyList())
                .sortedBy { if (it.kind == EventKind.DUE) 0 else 1 }
            if (dayEvents.isEmpty()) {
                Text("这一天没有账单日或还款日。", fontSize = 13.sp, color = Color.Gray)
            } else {
                dayEvents.forEach { ev ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (ev.kind == EventKind.DUE) CardPalette.primary(ev.card.themeIndex)
                                    else Color.Gray
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            ev.card.name.ifEmpty { "未命名卡片" },
                            fontSize = 13.sp, fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (ev.kind == EventKind.DUE) "还款日" else "账单日",
                            fontSize = 13.sp, color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    dayEvents: List<DayEvent>,
    isToday: Boolean,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val hasDue = dayEvents.any { it.kind == EventKind.DUE }
    val hasStatement = dayEvents.any { it.kind == EventKind.STATEMENT }
    Column(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) AccentColor.copy(alpha = 0.15f) else Color.Transparent)
            .clickable { onClick() }
            .padding(top = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            day.toString(),
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) AccentColor else Color.Black
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (hasDue) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(Color.Red))
            }
            if (hasStatement) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(Color.Gray))
            }
            if (!hasDue && !hasStatement) {
                Box(Modifier.size(5.dp))
            }
        }
    }
}
