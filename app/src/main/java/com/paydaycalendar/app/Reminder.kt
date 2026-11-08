package com.paydaycalendar.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/// 到期提醒：开启后每天上午 9 点检查一次所有用户的卡：
/// 还剩 1 天、今天到期、已逾期的都会发一条汇总通知（点通知打开 App）。
/// 用 WorkManager 每日任务实现，重启手机也不丢。
object Reminder {
    const val PREF_KEY = "paydaycalendar.remindersEnabled"
    private const val STORE_KEY = "paydaycalendar.store.v2"
    private const val WORK_NAME = "payday-daily-reminder"
    private const val CHANNEL_ID = "payday.reminder"
    private const val NOTIF_ID = 1001

    private fun prefs(context: Context) =
        context.getSharedPreferences("paydaycalendar", Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(PREF_KEY, false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(PREF_KEY, on).apply()
        if (on) schedule(context) else cancel(context)
    }

    private fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(9, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /// 检查全部卡片，命中「还剩 1 天 / 今天到期 / 已逾期」的拼成一条通知发出
    fun notifyDueCards(context: Context) {
        val raw = prefs(context).getString(STORE_KEY, null) ?: return
        val data = try {
            PayDayData.fromJson(JSONObject(raw))
        } catch (_: Exception) {
            return
        }
        val today = LocalDate.now()
        val lines = mutableListOf<String>()
        val multi = data.profiles.size > 1
        for (profile in data.profiles) {
            for (card in data.cardsByProfile[profile.id].orEmpty()) {
                val info = DateCalc.cycleInfo(card, today)
                if (info.isFinished) continue
                val name = card.name.ifEmpty { "未命名卡片" }
                val who = if (multi) "${profile.name} · $name" else name
                when {
                    info.daysRemaining == 1L ->
                        lines.add("$who 明天到期（还款日 ${info.dueDate.format(DateFmt.monthDay)}）")
                    info.daysRemaining == 0L ->
                        lines.add("$who 今天到期")
                    info.daysRemaining < 0 ->
                        lines.add("$who 已逾期 ${-info.daysRemaining} 天")
                }
            }
        }
        if (lines.isEmpty()) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "还款提醒", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val summary = lines.first() + if (lines.size > 1) " 等 ${lines.size} 张卡待还款" else ""
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("还款提醒")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID, notif)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        if (Reminder.isEnabled(applicationContext)) {
            Reminder.notifyDueCards(applicationContext)
        }
        return Result.success()
    }
}
