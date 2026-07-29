package com.xxyangyoulin.thsstockoverlay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

enum class PlanStatus(val label: String) {
    PENDING("待执行"),
    EXECUTED("已执行"),
    CANCELLED("已作废")
}

data class StockPlan(
    val code: String,
    val name: String,
    val planDate: String,
    val action: String,
    val reminder: String,
    val updatedAt: Long,
    val status: PlanStatus = PlanStatus.PENDING
)

class StockPlanStore(context: Context, preferencesName: String = PREFS) {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun getAll(): List<StockPlan> {
        val array = JSONArray(preferences.getString(KEY_PLANS, "[]"))
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    StockPlan(
                        code = item.getString("code"),
                        name = item.getString("name"),
                        planDate = item.getString("planDate"),
                        action = item.getString("action"),
                        reminder = item.optString("reminder"),
                        updatedAt = item.getLong("updatedAt"),
                        status = PlanStatus.valueOf(
                            item.optString("status", PlanStatus.PENDING.name)
                        )
                    )
                )
            }
        }.sortedWith(compareByDescending<StockPlan> { it.planDate }.thenByDescending { it.updatedAt })
    }

    fun find(code: String, planDate: String): StockPlan? =
        getAll().firstOrNull { it.code == code && it.planDate == planDate }

    fun findRelevant(code: String, today: LocalDate = LocalDate.now()): StockPlan? {
        val plans = getAll().filter {
            it.code == code &&
                it.status == PlanStatus.PENDING &&
                LocalDate.parse(it.planDate) >= today
        }
        return plans.firstOrNull { it.planDate == today.toString() }
            ?: plans.minByOrNull { it.planDate }
    }

    fun save(plan: StockPlan) {
        val plans = getAll()
            .filterNot { it.code == plan.code && it.planDate == plan.planDate }
            .plus(plan)
        write(plans)
    }

    fun delete(code: String, planDate: String) {
        write(getAll().filterNot { it.code == code && it.planDate == planDate })
    }

    private fun write(plans: List<StockPlan>) {
        val array = JSONArray()
        plans.forEach { plan ->
            array.put(
                JSONObject()
                    .put("code", plan.code)
                    .put("name", plan.name)
                    .put("planDate", plan.planDate)
                    .put("action", plan.action)
                    .put("reminder", plan.reminder)
                    .put("updatedAt", plan.updatedAt)
                    .put("status", plan.status.name)
            )
        }
        preferences.edit().putString(KEY_PLANS, array.toString()).apply()
    }

    companion object {
        private const val PREFS = "stock_plans"
        private const val KEY_PLANS = "plans"
    }
}
