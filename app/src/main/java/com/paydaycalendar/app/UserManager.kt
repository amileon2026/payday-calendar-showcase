package com.paydaycalendar.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/// 用户切换与管理：点圆圈切换用户，名字可直接改，可添加（最多 5 个）或删除用户。
/// 每个用户的数据完全独立，删除用户会连同其卡片一并删掉。
@Composable
fun UserManagerDialog(store: CardStore, onDismiss: () -> Unit) {
    var pendingDelete by remember { mutableStateOf<UserProfile?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "切换用户",
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("完成") }
                }

                store.profiles.forEach { profile ->
                    ProfileRow(
                        store = store,
                        profile = profile,
                        showDelete = store.profiles.size > 1,
                        onSwitch = {
                            store.switchProfile(profile.id)
                            onDismiss()
                        },
                        onDelete = { pendingDelete = profile }
                    )
                }

                Text(
                    "点圆圈切换用户；名字可以直接修改。每个用户的数据完全独立，最多 ${CardStore.MAX_PROFILES} 个用户。",
                    fontSize = 12.sp, color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                if (store.profiles.size < CardStore.MAX_PROFILES) {
                    TextButton(onClick = {
                        store.addProfile()?.let { store.switchProfile(it.id) }
                        onDismiss()
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("添加用户")
                    }
                }
            }
        }
    }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这个用户？") },
            text = {
                Text("「${profile.name}」的 ${store.cardCount(profile.id)} 张卡会一并删除，删了就没了。")
            },
            confirmButton = {
                TextButton(onClick = {
                    store.deleteProfile(profile.id)
                    pendingDelete = null
                }) {
                    Text("删除 ${profile.name}", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ProfileRow(
    store: CardStore,
    profile: UserProfile,
    showDelete: Boolean,
    onSwitch: () -> Unit,
    onDelete: () -> Unit
) {
    val isActive = profile.id == store.activeProfile.id
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconButton(onClick = onSwitch) {
            Icon(
                if (isActive) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = "切换到该用户",
                tint = if (isActive) AccentColor else Color.Gray
            )
        }
        BasicTextField(
            value = name,
            onValueChange = {
                name = it
                store.renameProfile(profile.id, it)
            },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 16.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
            ),
            modifier = Modifier.weight(1f)
        )
        Text("${store.cardCount(profile.id)} 张卡", fontSize = 12.sp, color = Color.Gray)
        if (showDelete) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除用户", tint = Color.Red)
            }
        }
        Spacer(Modifier.padding(0.dp))
    }
}
