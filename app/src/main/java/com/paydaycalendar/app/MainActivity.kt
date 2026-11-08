package com.paydaycalendar.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = CardStore(applicationContext)
        setContent {
            PayDayApp(store)
        }
    }
}

/// 主界面：手机版单列布局为基准，逻辑与排版与 iOS 最终版一致。
@Composable
fun PayDayApp(store: CardStore) {
    var formSession by remember { mutableStateOf<FormSession?>(null) }
    var showUsers by remember { mutableStateOf(false) }
    var showDonate by remember { mutableStateOf(false) }

    // 到期提醒开关：Android 13+ 首次开启要先拿到通知权限，拿不到就提示去系统设置开
    val context = LocalContext.current
    var remindersEnabled by remember { mutableStateOf(Reminder.isEnabled(context)) }
    var showReminderDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            remindersEnabled = true
            Reminder.setEnabled(context, true)
        } else {
            showReminderDenied = true
        }
    }
    fun toggleReminders() {
        if (remindersEnabled) {
            remindersEnabled = false
            Reminder.setEnabled(context, false)
        } else if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            remindersEnabled = true
            Reminder.setEnabled(context, true)
        }
    }

    MaterialTheme(colorScheme = lightColorScheme()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PageBackground)
        ) {
            Column(Modifier.statusBarsPadding()) {
                TopBar(
                    remindersEnabled = remindersEnabled,
                    onToggleReminders = { toggleReminders() },
                    onUsers = { showUsers = true },
                    onAdd = { formSession = FormSession(CreditCard(), isNew = true) },
                    onDonate = { showDonate = true }
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        NearestBanner(
                            rows = store.bannerRows(),
                            activeID = store.activeProfile.id
                        ) { store.switchProfile(it) }
                    }
                    if (store.cards.isEmpty()) {
                        item {
                            EmptyState {
                                formSession = FormSession(CreditCard(), isNew = true)
                            }
                        }
                    } else {
                        item {
                            CardsHeader(
                                title = "${store.activeProfile.name}的卡片",
                                minimal = store.minimalMode
                            ) { store.minimalMode = !store.minimalMode; store.persistMinimalMode() }
                        }
                        items(store.sortedCardsWithInfo(), key = { it.first.id }) { pair ->
                            CardRow(
                                card = pair.first,
                                info = pair.second,
                                minimal = store.minimalMode,
                                onPaid = { store.markPaid(pair.first) },
                                onClick = { formSession = FormSession(pair.first, isNew = false) }
                            )
                        }
                        item {
                            CalendarView(cards = store.cards)
                        }
                    }
                }
            }

            formSession?.let { session ->
                CardFormDialog(store = store, session = session) { formSession = null }
            }
            if (showUsers) {
                UserManagerDialog(store = store) { showUsers = false }
            }
            if (showDonate) {
                DonateDialog { showDonate = false }
            }
            if (showReminderDenied) {
                AlertDialog(
                    onDismissRequest = { showReminderDenied = false },
                    title = { Text("未获得通知权限") },
                    text = { Text("请到系统「设置 → 应用 → 还款记录 → 通知」里允许通知，再回来点铃铛打开提醒。") },
                    confirmButton = {
                        TextButton(onClick = { showReminderDenied = false }) { Text("知道了") }
                    }
                )
            }
        }
    }
}

/// 顶栏：标题居中，右上角是提醒铃铛、用户切换与添加按钮（与标题同一行，与 iOS 版一致）
@Composable
private fun TopBar(
    remindersEnabled: Boolean,
    onToggleReminders: () -> Unit,
    onUsers: () -> Unit,
    onAdd: () -> Unit,
    onDonate: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // 左上角作者标识：圆形小头像 + 「打赏作者」小字，轻量融入顶栏，点击弹出收款码
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(50))
                .clickable { onDonate() }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.author_logo),
                contentDescription = "作者标识",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.size(5.dp))
            Text("打赏作者", fontSize = 10.sp, color = Color.Gray)
        }
        Text(
            "还款记录",
            fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center)
        )
        Row(modifier = Modifier.align(Alignment.CenterEnd)) {
            IconButton(onClick = onToggleReminders) {
                Icon(
                    if (remindersEnabled) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                    contentDescription = "到期提醒开关",
                    tint = if (remindersEnabled) AccentColor else Color.Gray,
                    modifier = Modifier.size(26.dp)
                )
            }
            IconButton(onClick = onUsers) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = "切换用户",
                    tint = AccentColor,
                    modifier = Modifier.size(26.dp)
                )
            }
            IconButton(onClick = onAdd) {
                Icon(
                    Icons.Filled.AddCircle,
                    contentDescription = "添加卡片",
                    tint = AccentColor,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

/// 打赏弹窗：白卡圆角，上下依次展示作者的支付宝、微信收款码原图（码面未作任何处理）；内容可滚动
@Composable
private fun DonateDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.author_logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.size(8.dp))
            Text("打赏作者", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "觉得好用，就请作者喝杯咖啡吧",
                fontSize = 12.sp, color = Color.Gray,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(Modifier.size(12.dp))
            Image(
                painter = painterResource(R.drawable.donate_alipay),
                contentDescription = "支付宝收款码",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.size(12.dp))
            Image(
                painter = painterResource(R.drawable.donate_wechat),
                contentDescription = "微信收款码",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.size(6.dp))
            TextButton(onClick = onDismiss) {
                Text("关闭", fontSize = 14.sp)
            }
        }
    }
}

/// 卡片区标题行：左侧当前用户，右侧普通/极简切换（选择会记住）
@Composable
private fun CardsHeader(title: String, minimal: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable { onToggle() }
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(if (minimal) "极简" else "普通", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            Icons.Filled.CreditCard,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(52.dp)
        )
        Text("还没有卡片", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "添加你的第一张信用卡，填上账单日和还款日，这里会自动算出还剩几天还款。",
            fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center
        )
        Button(onClick = onAdd) {
            Text("添加卡片")
        }
    }
}
