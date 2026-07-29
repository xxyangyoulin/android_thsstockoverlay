package com.xxyangyoulin.thsstockoverlay

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PlanDetailViewModelTest {
    private val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val preferencesName = "stock_plans_test"
    private val store = StockPlanStore(application, preferencesName)

    @Before
    fun setUp() {
        application.getSharedPreferences(preferencesName, android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        application.getSharedPreferences(preferencesName, android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun savingTomorrowKeepsTodayPlan() {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        store.save(plan(today, "今天计划"))
        val viewModel = PlanDetailViewModel(
            application,
            SavedStateHandle(mapOf(
                PlanDetailViewModel.ARG_STOCK_CODE to "002115",
                PlanDetailViewModel.ARG_STOCK_NAME to "三维通信",
                PlanDetailViewModel.ARG_PLAN_DATE to today.toString(),
                PlanDetailViewModel.ARG_STORE_NAME to preferencesName
            ))
        )

        viewModel.selectDate(tomorrow)
        assertNull(viewModel.uiState.value.editingPlanDate)
        assertEquals(tomorrow, viewModel.uiState.value.selectedDate)
        viewModel.updateAction("明天计划")
        assertNull(viewModel.save())

        assertNotNull(store.find("002115", today.toString()))
        assertNotNull(store.find("002115", tomorrow.toString()))
    }

    @Test
    fun relevantPlanExcludesExecutedAndCancelledPlans() {
        val today = LocalDate.now()
        store.save(plan(today, "今天计划").copy(status = PlanStatus.EXECUTED))
        store.save(plan(today.plusDays(1), "明天计划").copy(status = PlanStatus.CANCELLED))
        store.save(plan(today.plusDays(2), "后天计划"))

        assertEquals(today.plusDays(2).toString(), store.findRelevant("002115")?.planDate)
    }

    private fun plan(date: LocalDate, action: String) = StockPlan(
        code = "002115",
        name = "三维通信",
        planDate = date.toString(),
        action = action,
        reminder = "",
        updatedAt = System.currentTimeMillis()
    )
}
