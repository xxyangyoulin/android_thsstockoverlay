package com.xxyangyoulin.thsstockoverlay

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

data class PlanDetailUiState(
    val code: String,
    val name: String,
    val selectedDate: LocalDate,
    val action: String,
    val reminder: String,
    val status: PlanStatus,
    val editingPlanDate: String?,
    val existingPlans: List<StockPlan>
)

class PlanDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val store = savedStateHandle.get<String>(ARG_STORE_NAME)
        ?.let { StockPlanStore(application, it) }
        ?: StockPlanStore(application)
    private val initialCode = savedStateHandle.get<String>(ARG_STOCK_CODE).orEmpty()
    private val initialName = savedStateHandle.get<String>(ARG_STOCK_NAME).orEmpty()
    private val initialPlan = when {
        savedStateHandle.get<Boolean>(ARG_RELEVANT_PLAN) == true -> store.findRelevant(initialCode)
        savedStateHandle.get<String>(ARG_PLAN_DATE) != null ->
            store.find(initialCode, savedStateHandle.get<String>(ARG_PLAN_DATE).orEmpty())
        else -> null
    }
    private val initialDate = initialPlan?.planDate?.let(LocalDate::parse)
        ?: savedStateHandle.get<String>(ARG_PLAN_DATE)?.let(LocalDate::parse)
        ?: LocalDate.now()

    private val _uiState = MutableStateFlow(stateFor(initialPlan, initialCode, initialName, initialDate))
    val uiState = _uiState.asStateFlow()

    fun updateStock(code: String, name: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            code = code,
            name = name,
            existingPlans = plansFor(code)
        )
    }

    fun updateAction(value: String) {
        _uiState.value = _uiState.value.copy(action = value)
    }

    fun updateReminder(value: String) {
        _uiState.value = _uiState.value.copy(reminder = value)
    }

    fun updateStatus(value: PlanStatus) {
        _uiState.value = _uiState.value.copy(status = value)
    }

    fun selectDate(date: LocalDate) {
        val current = _uiState.value
        val existing = current.code.takeIf { it.matches(Regex("[0-9]{6}")) }
            ?.let { store.find(it, date.toString()) }
        _uiState.value = when {
            existing != null -> stateFor(existing, existing.code, existing.name, date)
            current.editingPlanDate != null -> stateFor(null, current.code, current.name, date)
            else -> current.copy(selectedDate = date)
        }
    }

    fun loadPlan(plan: StockPlan) {
        _uiState.value = stateFor(plan, plan.code, plan.name, LocalDate.parse(plan.planDate))
    }

    fun save(): String? {
        val state = _uiState.value
        val error = when {
            !state.code.matches(Regex("[0-9]{6}")) -> "请输入六位股票代码"
            state.name.isBlank() -> "请输入股票名称"
            state.action.isBlank() -> "请输入操作计划"
            store.find(state.code, state.selectedDate.toString())?.planDate != state.editingPlanDate ->
                "该股票当天已有计划"
            else -> null
        }
        if (error != null) return error
        store.save(
            StockPlan(
                code = state.code,
                name = state.name,
                planDate = state.selectedDate.toString(),
                action = state.action.trim(),
                reminder = state.reminder.trim(),
                updatedAt = System.currentTimeMillis(),
                status = state.status
            )
        )
        return null
    }

    fun delete() {
        val state = _uiState.value
        state.editingPlanDate?.let { store.delete(state.code, it) }
    }

    private fun stateFor(
        plan: StockPlan?,
        code: String,
        name: String,
        date: LocalDate
    ) = PlanDetailUiState(
        code = plan?.code ?: code,
        name = plan?.name ?: name,
        selectedDate = date,
        action = plan?.action.orEmpty(),
        reminder = plan?.reminder.orEmpty(),
        status = plan?.status ?: PlanStatus.PENDING,
        editingPlanDate = plan?.planDate,
        existingPlans = plansFor(plan?.code ?: code)
    )

    private fun plansFor(code: String): List<StockPlan> =
        store.getAll().filter { it.code == code }

    companion object {
        const val ARG_STOCK_CODE = "stock_code"
        const val ARG_STOCK_NAME = "stock_name"
        const val ARG_PLAN_DATE = "plan_date"
        const val ARG_RELEVANT_PLAN = "relevant_plan"
        internal const val ARG_STORE_NAME = "store_name"
    }
}
