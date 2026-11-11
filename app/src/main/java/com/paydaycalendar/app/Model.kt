package com.paydaycalendar.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/// 还款方式：与 iOS 版完全一致的三种。raw 是存档里用的字符串，与 iOS 版同名。
enum class DueMode(val raw: String, val label: String) {
    FIXED_DAY("fixedDay", "固定日期"),
    DAYS_AFTER("daysAfter", "账单日后天数"),
    ONE_TIME("oneTime", "单次");

    companion object {
        fun fromRaw(raw: String?): DueMode = entries.firstOrNull { it.raw == raw } ?: FIXED_DAY
    }
}

/// 一张卡。日期字段用 epochDay（LocalDate.toEpochDay）存储，简单可靠。
/// lastPaidDueDate：最近一次点「已还款」还清的那个还款日；没点过为 null。
/// manualPaidDueDate：手动模式下被点小框标记「已还」的那一期还款日；与 lastPaidDueDate
/// 是两套账——只做视觉记号，不推进周期；当前周期还款日变了它就自动对不上、标记失效。
/// oneTimeDueDate：单次模式保存时定锚的唯一还款日。
data class CreditCard(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val bank: String = "",
    val last4: String = "",
    val themeIndex: Int = 0,
    val statementDay: Int = 1,
    val dueMode: DueMode = DueMode.FIXED_DAY,
    val dueDay: Int = 1,
    val dueOffset: Int = 20,
    val createdAt: Long = System.currentTimeMillis(),
    val lastPaidDueDate: Long? = null,
    val oneTimeDays: Int = 30,
    val oneTimeDueDate: Long? = null,
    val manualPaidDueDate: Long? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("bank", bank)
        put("last4", last4)
        put("themeIndex", themeIndex)
        put("statementDay", statementDay)
        put("dueMode", dueMode.raw)
        put("dueDay", dueDay)
        put("dueOffset", dueOffset)
        put("createdAt", createdAt)
        put("lastPaidDueDate", lastPaidDueDate ?: JSONObject.NULL)
        put("oneTimeDays", oneTimeDays)
        put("oneTimeDueDate", oneTimeDueDate ?: JSONObject.NULL)
        put("manualPaidDueDate", manualPaidDueDate ?: JSONObject.NULL)
    }

    companion object {
        /// 宽容解码：字段缺失用默认值，升级不丢数据（与 iOS 版同一原则）
        fun fromJson(o: JSONObject): CreditCard = CreditCard(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", ""),
            bank = o.optString("bank", ""),
            last4 = o.optString("last4", ""),
            themeIndex = o.optInt("themeIndex", 0),
            statementDay = o.optInt("statementDay", 1),
            dueMode = DueMode.fromRaw(o.optString("dueMode", "fixedDay")),
            dueDay = o.optInt("dueDay", 1),
            dueOffset = o.optInt("dueOffset", 20),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            lastPaidDueDate = if (o.isNull("lastPaidDueDate")) null else o.optLong("lastPaidDueDate"),
            oneTimeDays = o.optInt("oneTimeDays", 30),
            oneTimeDueDate = if (o.isNull("oneTimeDueDate")) null else o.optLong("oneTimeDueDate"),
            manualPaidDueDate = if (o.isNull("manualPaidDueDate")) null else o.optLong("manualPaidDueDate")
        )
    }
}

/// 一个用户（资料完全独立）。最多 5 个。
data class UserProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): UserProfile = UserProfile(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "用户"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    }
}

/// 全部数据：用户列表 + 当前用户 + 每个用户的卡片。
data class PayDayData(
    val profiles: List<UserProfile> = emptyList(),
    val activeProfileID: String = "",
    val cardsByProfile: Map<String, List<CreditCard>> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("profiles", JSONArray().apply { profiles.forEach { put(it.toJson()) } })
        put("activeProfileID", activeProfileID)
        put("cardsByProfile", JSONObject().apply {
            cardsByProfile.forEach { (k, list) ->
                put(k, JSONArray().apply { list.forEach { c -> put(c.toJson()) } })
            }
        })
    }

    companion object {
        fun fromJson(o: JSONObject): PayDayData {
            val profiles = mutableListOf<UserProfile>()
            val pa = o.optJSONArray("profiles") ?: JSONArray()
            for (i in 0 until pa.length()) profiles.add(UserProfile.fromJson(pa.optJSONObject(i) ?: JSONObject()))
            val cards = mutableMapOf<String, List<CreditCard>>()
            val cm = o.optJSONObject("cardsByProfile") ?: JSONObject()
            val keys = cm.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val arr = cm.optJSONArray(k) ?: JSONArray()
                val list = mutableListOf<CreditCard>()
                for (i in 0 until arr.length()) list.add(CreditCard.fromJson(arr.optJSONObject(i) ?: JSONObject()))
                cards[k] = list
            }
            return PayDayData(profiles, o.optString("activeProfileID", ""), cards)
        }
    }
}
