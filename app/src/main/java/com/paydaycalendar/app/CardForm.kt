package com.paydaycalendar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate

data class FormSession(val card: CreditCard, val isNew: Boolean)

/// 单次模式定锚：新建、或单次天数改动时，以保存当天为起点重新算定那个唯一的还款日；
/// 只改名字颜色等不动它；已结束（点过已还款）的单次卡不重算。与 iOS 版一致。
fun anchored(card: CreditCard, original: CreditCard): CreditCard {
    if (card.dueMode != DueMode.ONE_TIME || card.lastPaidDueDate != null) return card
    val inputsChanged = original.dueMode != DueMode.ONE_TIME || original.oneTimeDays != card.oneTimeDays
    return if (inputsChanged || card.oneTimeDueDate == null) {
        card.copy(oneTimeDueDate = DateCalc.oneTimeDue(card.oneTimeDays, LocalDate.now()).toEpochDay())
    } else {
        card
    }
}

/// 添加/编辑卡片：全屏弹窗。数字既可点开直接输入，也可按加减微调。
@Composable
fun CardFormDialog(store: CardStore, session: FormSession, onDismiss: () -> Unit) {
    var draft by remember(session) { mutableStateOf(session.card) }
    var showDelete by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = PageBackground) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Text(
                        if (session.isNew) "添加卡片" else "编辑卡片",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = {
                            val card = anchored(draft, session.card)
                            if (session.isNew) store.add(card) else store.update(card)
                            onDismiss()
                        },
                        enabled = draft.name.isNotBlank()
                    ) {
                        Text("保存", fontWeight = FontWeight.Bold)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        FormSection("卡片信息") {
                            OutlinedTextField(
                                value = draft.name,
                                onValueChange = { draft = draft.copy(name = it) },
                                label = { Text("名称，如：招行经典白") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    unfocusedContainerColor = Color.White,
                                    focusedContainerColor = Color.White
                                )
                            )
                            OutlinedTextField(
                                value = draft.bank,
                                onValueChange = { draft = draft.copy(bank = it) },
                                label = { Text("银行（可选）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    unfocusedContainerColor = Color.White,
                                    focusedContainerColor = Color.White
                                )
                            )
                            OutlinedTextField(
                                value = draft.last4,
                                onValueChange = { v ->
                                    draft = draft.copy(last4 = v.filter { it.isDigit() }.take(4))
                                },
                                label = { Text("尾号（可选）") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    unfocusedContainerColor = Color.White,
                                    focusedContainerColor = Color.White
                                )
                            )
                        }
                    }

                    item {
                        FormSection("卡面颜色") {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(6),
                                modifier = Modifier.height(128.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                userScrollEnabled = false
                            ) {
                                items(CardPalette.gradients.size) { idx ->
                                    Box(
                                        modifier = Modifier
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(CardPalette.brush(idx))
                                            .clickable { draft = draft.copy(themeIndex = idx) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (draft.themeIndex == idx) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = "已选",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Column {
                            FormSection("日期") {
                                if (draft.dueMode != DueMode.ONE_TIME) {
                                    NumberStepperRow(
                                        label = "账单日",
                                        value = draft.statementDay,
                                        range = 1..31, prefix = "每月", suffix = "日"
                                    ) { draft = draft.copy(statementDay = it) }
                                }
                                SegmentedControl(
                                    options = DueMode.entries.map { it.label },
                                    selected = draft.dueMode.ordinal
                                ) { draft = draft.copy(dueMode = DueMode.entries[it]) }
                                when (draft.dueMode) {
                                    DueMode.FIXED_DAY -> NumberStepperRow(
                                        label = "还款日",
                                        value = draft.dueDay,
                                        range = 1..31, prefix = "每月", suffix = "日"
                                    ) { draft = draft.copy(dueDay = it) }

                                    DueMode.DAYS_AFTER -> NumberStepperRow(
                                        label = "免息期",
                                        value = draft.dueOffset,
                                        range = 1..60, prefix = "账单日后", suffix = "天"
                                    ) { draft = draft.copy(dueOffset = it) }

                                    DueMode.ONE_TIME -> NumberStepperRow(
                                        label = "单次天数",
                                        value = draft.oneTimeDays,
                                        range = 1..36500, prefix = "", suffix = "天"
                                    ) { draft = draft.copy(oneTimeDays = it) }
                                }
                            }
                            Text(
                                if (draft.dueMode == DueMode.ONE_TIME) {
                                    "单次模式只填一个天数（1–36500 天）：从保存当天起算，这天就是唯一的还款日；到期点「已还款」后即结束，不再进入下个周期。"
                                } else {
                                    "设为 31 日时，短月份自动按当月最后一天计算。数字可以直接点中间输入，也可以按加减微调。"
                                },
                                fontSize = 12.sp, color = Color.Gray,
                                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                            )
                        }
                    }

                    item {
                        val info = DateCalc.cycleInfo(anchored(draft, session.card))
                        FormSection("预览") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(
                                        if (draft.dueMode == DueMode.ONE_TIME) "还款日（单次）" else "下个还款日",
                                        fontSize = 12.sp, color = Color.Gray
                                    )
                                    Text(
                                        info.dueDate.format(DateFmt.long),
                                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("还剩", fontSize = 12.sp, color = Color.Gray)
                                    if (info.isFinished) {
                                        Text(
                                            "已结束",
                                            fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                                            color = Color.Gray
                                        )
                                    } else {
                                        Text(
                                            if (info.daysRemaining == 0L) "今天到期" else "${info.daysRemaining} 天",
                                            fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                                            color = urgencyColor(info)
                                        )
                                    }
                                }
                            }
                            PreviewProgress(info)
                        }
                    }

                    if (!session.isNew) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White
                            ) {
                                TextButton(
                                    onClick = { showDelete = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("删除这张卡", color = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("确定删除这张卡？") },
            confirmButton = {
                TextButton(onClick = {
                    store.deleteCard(draft.id)
                    showDelete = false
                    onDismiss()
                }) { Text("删除", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            }
        )
    }
}

/// 表单分区：灰字小标题 + 白色圆角卡片
@Composable
fun FormSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            fontSize = 13.sp, color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
            }
        }
    }
}

/// 三段切换（自绘，避免实验 API）：固定日期 / 账单日后天数 / 单次
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(FieldBackground)
            .padding(2.dp)
    ) {
        options.forEachIndexed { i, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (i == selected) Color.White else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

/// 数字行：中间的数字可以直接点开输入，也可以按右侧加减微调；输入超出范围会自动夹回。
@Composable
fun NumberStepperRow(
    label: String,
    value: Int,
    range: IntRange,
    prefix: String,
    suffix: String,
    onChange: (Int) -> Unit
) {
    var text by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!focused) text = value.toString() }

    fun commit() {
        val n = text.trim().toIntOrNull()
        if (n != null) onChange(n.coerceIn(range.first, range.last))
        text = (n?.coerceIn(range.first, range.last) ?: value).toString()
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        if (prefix.isNotEmpty()) {
            Text(prefix, color = Color.Gray, fontSize = 16.sp)
            Spacer(Modifier.width(6.dp))
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(fontSize = 16.sp, textAlign = TextAlign.Center),
            modifier = Modifier
                .width(54.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(FieldBackground)
                .onFocusChanged { st ->
                    focused = st.isFocused
                    if (!st.isFocused) commit()
                }
                .padding(vertical = 6.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(suffix, color = Color.Gray, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(FieldBackground),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onChange((value - 1).coerceIn(range.first, range.last)) },
                contentAlignment = Alignment.Center
            ) {
                Text("−", fontSize = 20.sp)
            }
            Box(
                Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(Color.Gray.copy(alpha = 0.3f))
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onChange((value + 1).coerceIn(range.first, range.last)) },
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontSize = 20.sp)
            }
        }
    }
}

/// 预览区进度条：已结束灰、到期红、其余蓝色（与 iOS 预览同口径）
@Composable
private fun PreviewProgress(info: CycleInfo) {
    val color = when {
        info.isFinished -> Color.Gray
        info.daysRemaining <= 0 -> Color.Red
        else -> AccentColor
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFE5E5EA))
    ) {
        if (info.progress > 0) {
            Box(
                modifier = Modifier
                    .width(maxWidth * info.progress.toFloat())
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}
