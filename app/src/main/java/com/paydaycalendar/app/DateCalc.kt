package com.paydaycalendar.app

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/// 账单/还款周期的纯计算层，与 iOS 版 DateCalc 逻辑逐行对应。
/// 规则：每月某日若超过当月天数，自动按当月最后一天算。
object DateCalc {

    /// 以 ref 所在月为基准、偏移 offsetMonths 个月的那一月里，「每月 day 日」的日期
    fun dayDate(day: Int, monthOffset: Int, from: LocalDate): LocalDate {
        val ym = YearMonth.from(from).plusMonths(monthOffset.toLong())
        return ym.atDay(day.coerceIn(1, ym.lengthOfMonth()))
    }

    /// 不晚于 date 的最近一个账单日
    fun statementOnOrBefore(card: CreditCard, date: LocalDate): LocalDate {
        val thisMonth = dayDate(card.statementDay, 0, date)
        return if (!thisMonth.isAfter(date)) thisMonth else dayDate(card.statementDay, -1, date)
    }

    /// 晚于 date 的下一个账单日
    fun statementAfter(card: CreditCard, date: LocalDate): LocalDate {
        val thisMonth = dayDate(card.statementDay, 0, date)
        return if (thisMonth.isAfter(date)) thisMonth else dayDate(card.statementDay, 1, date)
    }

    /// 账单日 statement 所在周期的还款日
    fun dueDateForStatement(statement: LocalDate, card: CreditCard): LocalDate = when (card.dueMode) {
        DueMode.DAYS_AFTER -> statement.plusDays(card.dueOffset.toLong())
        DueMode.ONE_TIME -> statement.plusDays(card.oneTimeDays.toLong())
        DueMode.FIXED_DAY -> {
            val sameMonth = dayDate(card.dueDay, 0, statement)
            if (sameMonth.isAfter(statement)) sameMonth else dayDate(card.dueDay, 1, statement)
        }
    }

    /// 单次模式定锚：从 ref 当天起，加上所填天数，就是那个唯一的还款日
    fun oneTimeDue(days: Int, from: LocalDate): LocalDate = from.plusDays(days.toLong())

    /// 当前周期信息。周期只由「已还款」标记推进，不会到期自动滚到下一期：
    /// 没点已还款时，到期后 daysRemaining 变负数、进度条保持 0（红色）。
    fun cycleInfo(card: CreditCard, today: LocalDate = LocalDate.now()): CycleInfo {
        // 单次模式：只有一个还款日，点过「已还款」即结束、永不进入下一周期
        if (card.dueMode == DueMode.ONE_TIME) {
            val paidEpoch = card.lastPaidDueDate
            if (paidEpoch != null) {
                val dueDay = LocalDate.ofEpochDay(paidEpoch)
                return CycleInfo(
                    statementDate = dueDay.minusDays(card.oneTimeDays.toLong()),
                    dueDate = dueDay, daysRemaining = 0, progress = 0.0, isFinished = true
                )
            }
            val dueDay = card.oneTimeDueDate?.let { LocalDate.ofEpochDay(it) }
                ?: oneTimeDue(card.oneTimeDays, today)
            val remaining = ChronoUnit.DAYS.between(today, dueDay)
            val total = maxOf(card.oneTimeDays, 1).toDouble()
            val progress = if (remaining <= 0) 0.0 else minOf(remaining.toDouble() / total, 1.0)
            return CycleInfo(
                statementDate = dueDay.minusDays(card.oneTimeDays.toLong()),
                dueDate = dueDay, daysRemaining = remaining, progress = progress
            )
        }

        val statement: LocalDate
        val due: LocalDate
        val paidEpoch = card.lastPaidDueDate
        if (paidEpoch != null) {
            // 已还清 paid 那一期，当前周期是它之后的第一个还款周期
            val paidDay = LocalDate.ofEpochDay(paidEpoch)
            var s = statementAfter(card, statementOnOrBefore(card, paidDay))
            var d = dueDateForStatement(s, card)
            var guard = 0
            while (!d.isAfter(paidDay) && guard < 24) {
                s = statementAfter(card, s)
                d = dueDateForStatement(s, card)
                guard++
            }
            statement = s
            due = d
        } else {
            statement = statementOnOrBefore(card, today)
            due = dueDateForStatement(statement, card)
        }
        val remaining = ChronoUnit.DAYS.between(today, due)
        // 若下个账单日还没到（刚点完「已还款」、新周期还没正式开始），
        // 总时长从还清日算到还款日，进度条从还款那一刻起就均匀变短；
        // 否则 remaining 会大于周期总长、被钳成满格（与 iOS 版同一个 bug 的修复）。
        val total = if (paidEpoch != null && today.isBefore(statement)) {
            ChronoUnit.DAYS.between(LocalDate.ofEpochDay(paidEpoch), due)
        } else {
            ChronoUnit.DAYS.between(statement, due)
        }
        val progress = if (total <= 0) {
            if (remaining > 0) 1.0 else 0.0
        } else {
            (remaining.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
        }
        return CycleInfo(statement, due, remaining, progress)
    }

    /// 显示月份内每一天的事件：[day: [事件]]。单次卡只标它那一个还款日，不按月循环。
    fun events(month: YearMonth, cards: List<CreditCard>): Map<Int, List<DayEvent>> {
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        val result = mutableMapOf<Int, MutableList<DayEvent>>()
        fun add(card: CreditCard, kind: EventKind, date: LocalDate) {
            if (date.isBefore(first) || date.isAfter(last)) return
            result.getOrPut(date.dayOfMonth) { mutableListOf() }.add(DayEvent(card, kind, date))
        }
        for (card in cards) {
            if (card.dueMode == DueMode.ONE_TIME) {
                card.oneTimeDueDate?.let { add(card, EventKind.DUE, LocalDate.ofEpochDay(it)) }
                continue
            }
            val st = dayDate(card.statementDay, 0, first)
            add(card, EventKind.STATEMENT, st)
            val prevStatement = dayDate(card.statementDay, -1, first)
            for (s in listOf(prevStatement, st)) {
                add(card, EventKind.DUE, dueDateForStatement(s, card))
            }
        }
        return result
    }
}

data class CycleInfo(
    val statementDate: LocalDate,
    val dueDate: LocalDate,
    /// 距还款日的天数：正数是倒计时，0 是还款当天，负数是逾期天数
    val daysRemaining: Long,
    /// 进度条的「剩余量」0...1：越接近还款日越少，到期为 0
    val progress: Double,
    /// 单次模式且已点「已还款」：这张卡已结束
    val isFinished: Boolean = false
) {
    val daysText: String get() = if (daysRemaining == 0L) "今天" else daysRemaining.toString()
}

enum class EventKind { STATEMENT, DUE }

data class DayEvent(val card: CreditCard, val kind: EventKind, val date: LocalDate)

/// 全局共享的日期格式化器（只建一次）
object DateFmt {
    val monthDay: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    val long: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
    val monthTitle: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)
}
