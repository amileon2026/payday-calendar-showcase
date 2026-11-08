package com.paydaycalendar.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.time.LocalDate

/// 顶部横幅的一行：某个用户最近的一张卡。
data class BannerRow(val profile: UserProfile, val card: CreditCard?, val info: CycleInfo?)

/// 数据层：保存在本机 SharedPreferences（JSON），不联网、不收集。与 iOS 版行为一致。
class CardStore(context: Context) {
    private val prefs = context.getSharedPreferences("paydaycalendar", Context.MODE_PRIVATE)

    var data by mutableStateOf(PayDayData())
        private set

    // 极简模式开关。注意：不要再另写 setMinimalMode() 函数——委托属性的 setter
    // 与同名函数会生成相同的 JVM 签名，云端构建曾因此报 Platform declaration clash；
    // 赋值后在 commitMinimal() 落盘由调用方负责（见 MainActivity 切换处）。
    var minimalMode by mutableStateOf(prefs.getBoolean(KEY_MINIMAL, false))

    init { load() }

    companion object {
        const val MAX_PROFILES = 5
        private const val KEY_STORE = "paydaycalendar.store.v2"
        private const val KEY_MINIMAL = "paydaycalendar.minimalMode"
    }

    /// 极简模式赋值后调用：把当前值写入 SharedPreferences 持久化
    fun persistMinimalMode() {
        prefs.edit().putBoolean(KEY_MINIMAL, minimalMode).apply()
    }

    // MARK: - 用户

    val profiles: List<UserProfile> get() = data.profiles

    val activeProfile: UserProfile
        get() = data.profiles.firstOrNull { it.id == data.activeProfileID }
            ?: data.profiles.firstOrNull()
            ?: UserProfile(name = "用户 1")

    fun switchProfile(id: String) {
        if (data.profiles.any { it.id == id }) commit(data.copy(activeProfileID = id))
    }

    fun addProfile(): UserProfile? {
        if (data.profiles.size >= MAX_PROFILES) return null
        val p = UserProfile(name = "用户 ${data.profiles.size + 1}")
        commit(
            data.copy(
                profiles = data.profiles + p,
                cardsByProfile = data.cardsByProfile + (p.id to emptyList())
            )
        )
        return p
    }

    fun renameProfile(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        commit(data.copy(profiles = data.profiles.map { if (it.id == id) it.copy(name = trimmed) else it }))
    }

    fun deleteProfile(id: String) {
        if (data.profiles.size <= 1) return
        val newProfiles = data.profiles.filter { it.id != id }
        if (newProfiles.size == data.profiles.size) return
        val newActive = if (data.activeProfileID == id) newProfiles[0].id else data.activeProfileID
        commit(
            data.copy(
                profiles = newProfiles,
                activeProfileID = newActive,
                cardsByProfile = data.cardsByProfile - id
            )
        )
    }

    fun cardCount(profileID: String): Int = data.cardsByProfile[profileID]?.size ?: 0

    // MARK: - 卡片（当前用户）

    val cards: List<CreditCard> get() = data.cardsByProfile[activeProfile.id] ?: emptyList()

    fun cardsFor(profileID: String): List<CreditCard> = data.cardsByProfile[profileID] ?: emptyList()

    private fun commitCards(key: String, list: List<CreditCard>) {
        commit(data.copy(cardsByProfile = data.cardsByProfile + (key to list)))
    }

    fun add(card: CreditCard) {
        val k = activeProfile.id
        commitCards(k, cards + card)
    }

    fun update(card: CreditCard) {
        val k = activeProfile.id
        commitCards(k, cards.map { if (it.id == card.id) card else it })
    }

    fun deleteCard(id: String) {
        val k = activeProfile.id
        commitCards(k, cards.filter { it.id != id })
    }

    /// 点「已还款」：把当前周期的还款日标记为已还清，周期随即推进到下一期（单次卡则结束）。
    fun markPaid(card: CreditCard) {
        val info = DateCalc.cycleInfo(card)
        update(card.copy(lastPaidDueDate = info.dueDate.toEpochDay()))
    }

    /// 每张卡的周期信息只算一次，再按紧迫度排序（已结束的单次卡沉底）。
    fun sortedCardsWithInfo(list: List<CreditCard> = cards): List<Pair<CreditCard, CycleInfo>> {
        val today = LocalDate.now()
        return list
            .map { it to DateCalc.cycleInfo(it, today) }
            .sortedWith(
                compareBy(
                    { if (it.second.isFinished) Long.MAX_VALUE else it.second.daysRemaining },
                    { it.first.createdAt }
                )
            )
    }

    /// 横幅数据：每个用户一行（该用户最近还款的卡），顺序与用户列表一致。
    fun bannerRows(): List<BannerRow> = data.profiles.map { profile ->
        val nearest = sortedCardsWithInfo(cardsFor(profile.id)).firstOrNull()
        BannerRow(profile, nearest?.first, nearest?.second)
    }

    // MARK: - 持久化

    private fun commit(newData: PayDayData) {
        data = newData
        prefs.edit().putString(KEY_STORE, newData.toJson().toString()).apply()
    }

    private fun load() {
        val raw = prefs.getString(KEY_STORE, null)
        if (raw != null) {
            try {
                val decoded = PayDayData.fromJson(JSONObject(raw))
                if (decoded.profiles.isNotEmpty()) {
                    data = decoded
                    return
                }
            } catch (_: Exception) {
                // 存档损坏时落到默认数据，不崩溃
            }
        }
        val p = UserProfile(name = "用户 1")
        data = PayDayData(
            profiles = listOf(p),
            activeProfileID = p.id,
            cardsByProfile = mapOf(p.id to emptyList())
        )
    }
}
