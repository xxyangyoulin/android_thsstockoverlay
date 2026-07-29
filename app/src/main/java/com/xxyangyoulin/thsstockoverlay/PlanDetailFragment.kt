package com.xxyangyoulin.thsstockoverlay

import android.app.DatePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.time.LocalDate

class PlanDetailFragment : UiFragment() {
    private val viewModel: PlanDetailViewModel by viewModels()

    private lateinit var title: TextView
    private lateinit var deleteButton: ImageButton
    private lateinit var codeInput: TextInputLayout
    private lateinit var nameInput: TextInputLayout
    private lateinit var actionInput: TextInputLayout
    private lateinit var reminderInput: TextInputLayout
    private lateinit var todayButton: MaterialButton
    private lateinit var tomorrowButton: MaterialButton
    private lateinit var otherDateButton: MaterialButton
    private lateinit var pendingButton: MaterialButton
    private lateinit var executedButton: MaterialButton
    private lateinit var cancelledButton: MaterialButton
    private lateinit var errorView: TextView
    private lateinit var existingPlansSection: LinearLayout
    private var renderedPlans: List<StockPlan>? = null

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val initial = viewModel.uiState.value
        val knownStock = initial.code.matches(Regex("[0-9]{6}")) && initial.name.isNotBlank()
        val root = screenRoot()
        root.addView(buildTopBar())

        val form = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 8.dp, 20.dp, 32.dp)
        }
        codeInput = textField("股票代码", initial.code).apply {
            editText?.inputType = InputType.TYPE_CLASS_NUMBER
        }
        nameInput = textField("股票名称", initial.name)
        if (!knownStock) {
            form.addView(codeInput)
            form.addView(nameInput)
        }
        form.addView(fieldLabel("计划日期"))
        form.addView(buildDateControls())
        form.addView(space(18.dp))
        form.addView(fieldLabel("计划状态"))
        form.addView(buildStatusControls())
        form.addView(space(18.dp))

        actionInput = textField("操作计划", initial.action, multiline = true)
        reminderInput = textField("纪律提醒（选填）", initial.reminder, multiline = true)
        form.addView(actionInput)
        form.addView(reminderInput)
        errorView = TextView(requireContext()).apply {
            setTextColor(0xFFC62828.toInt())
            textSize = 13f
            setPadding(4.dp, 0, 4.dp, 8.dp)
        }
        form.addView(errorView)
        existingPlansSection = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 24.dp, 0, 0)
        }
        form.addView(existingPlansSection)

        root.addView(ScrollView(requireContext()).apply { addView(form) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        root.addView(LinearLayout(requireContext()).apply {
            elevation = 8.dp.toFloat()
            setBackgroundColor(Color.WHITE)
            setPadding(20.dp, 8.dp, 20.dp, 10.dp)
            addView(MaterialButton(requireContext()).apply {
                text = "保存"
                setOnClickListener {
                    errorView.text = viewModel.save().orEmpty()
                    if (errorView.text.isEmpty()) {
                        notifyPlansChanged()
                        navigateUpOrFinish()
                    }
                }
            }, ViewGroup.LayoutParams.MATCH_PARENT, 52.dp)
        })

        bindInputs()
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun buildTopBar() = LinearLayout(requireContext()).apply {
        gravity = Gravity.CENTER_VERTICAL
        orientation = LinearLayout.HORIZONTAL
        setPadding(8.dp, 10.dp, 8.dp, 8.dp)
        addView(ImageButton(requireContext()).apply {
            setImageResource(R.drawable.ic_arrow_back)
            contentDescription = "返回"
            background = selectableBackground(borderless = true)
            setOnClickListener { navigateUpOrFinish() }
        }, LinearLayout.LayoutParams(48.dp, 48.dp))
        title = titleText("新建计划")
        addView(title, LinearLayout.LayoutParams(0, 56.dp, 1f))
        deleteButton = ImageButton(requireContext()).apply {
            setImageResource(R.drawable.ic_delete)
            contentDescription = "删除计划"
            background = selectableBackground(borderless = true)
            setOnClickListener {
                viewModel.delete()
                notifyPlansChanged()
                navigateUpOrFinish()
            }
        }
        addView(deleteButton, LinearLayout.LayoutParams(48.dp, 48.dp))
    }

    private fun buildDateControls() = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        isBaselineAligned = false
        todayButton = dateButton { viewModel.selectDate(LocalDate.now()) }
        tomorrowButton = dateButton { viewModel.selectDate(LocalDate.now().plusDays(1)) }
        otherDateButton = dateButton {
            val selected = viewModel.uiState.value.selectedDate
            DatePickerDialog(
                requireContext(),
                { _, year, month, day -> viewModel.selectDate(LocalDate.of(year, month + 1, day)) },
                selected.year,
                selected.monthValue - 1,
                selected.dayOfMonth
            ).show()
        }
        addView(todayButton, LinearLayout.LayoutParams(0, 62.dp, 1f).apply { marginEnd = 6.dp })
        addView(tomorrowButton, LinearLayout.LayoutParams(0, 62.dp, 1f).apply { marginEnd = 6.dp })
        addView(otherDateButton, LinearLayout.LayoutParams(0, 62.dp, 1f))
    }

    private fun dateButton(onClick: () -> Unit) = MaterialButton(requireContext()).apply {
        isAllCaps = false
        textSize = 13f
        setOnClickListener {
            errorView.text = ""
            onClick()
        }
    }

    private fun buildStatusControls() = MaterialButtonToggleGroup(requireContext()).apply {
        isSingleSelection = true
        isSelectionRequired = true
        pendingButton = statusButton("待执行", PlanStatus.PENDING)
        executedButton = statusButton("已执行", PlanStatus.EXECUTED)
        cancelledButton = statusButton("已作废", PlanStatus.CANCELLED)
        addView(pendingButton, LinearLayout.LayoutParams(0, 46.dp, 1f))
        addView(executedButton, LinearLayout.LayoutParams(0, 46.dp, 1f))
        addView(cancelledButton, LinearLayout.LayoutParams(0, 46.dp, 1f))
    }

    private fun statusButton(label: String, status: PlanStatus) = MaterialButton(
        requireContext(),
        null,
        com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        id = View.generateViewId()
        text = label
        isAllCaps = false
        setOnClickListener { viewModel.updateStatus(status) }
    }

    private fun bindInputs() {
        codeInput.editText?.doAfterTextChanged {
            errorView.text = ""
            viewModel.updateStock(
                it?.toString()?.trim().orEmpty(),
                nameInput.editText?.text?.toString()?.trim().orEmpty()
            )
        }
        nameInput.editText?.doAfterTextChanged {
            errorView.text = ""
            viewModel.updateStock(
                codeInput.editText?.text?.toString()?.trim().orEmpty(),
                it?.toString()?.trim().orEmpty()
            )
        }
        actionInput.editText?.doAfterTextChanged {
            errorView.text = ""
            viewModel.updateAction(it?.toString().orEmpty())
        }
        reminderInput.editText?.doAfterTextChanged {
            errorView.text = ""
            viewModel.updateReminder(it?.toString().orEmpty())
        }
    }

    private fun render(state: PlanDetailUiState) {
        title.text = if (state.code.matches(Regex("[0-9]{6}")) && state.name.isNotBlank()) {
            "${state.name}  ${state.code}"
        } else {
            "新建计划"
        }
        deleteButton.isVisible = state.editingPlanDate != null
        setInputText(codeInput, state.code)
        setInputText(nameInput, state.name)
        setInputText(actionInput, state.action)
        setInputText(reminderInput, state.reminder)
        renderDates(state.selectedDate)
        renderStatus(state.status)
        if (renderedPlans != state.existingPlans) {
            renderedPlans = state.existingPlans
            renderExistingPlans(state)
        }
    }

    private fun renderStatus(status: PlanStatus) {
        val selected = when (status) {
            PlanStatus.PENDING -> pendingButton
            PlanStatus.EXECUTED -> executedButton
            PlanStatus.CANCELLED -> cancelledButton
        }
        (selected.parent as MaterialButtonToggleGroup).check(selected.id)
    }

    private fun setInputText(input: TextInputLayout, value: String) {
        val editText = input.editText ?: return
        if (editText.text?.toString() != value) {
            editText.setText(value)
            editText.setSelection(value.length)
        }
    }

    private fun renderDates(selectedDate: LocalDate) {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        todayButton.text = "今天\n${today.monthValue}月${today.dayOfMonth}日"
        tomorrowButton.text = "明天\n${tomorrow.monthValue}月${tomorrow.dayOfMonth}日"
        otherDateButton.text = if (selectedDate != today && selectedDate != tomorrow) {
            "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日"
        } else {
            "选择日期"
        }
        listOf(todayButton to today, tomorrowButton to tomorrow, otherDateButton to selectedDate)
            .forEach { (button, date) ->
                val selected = selectedDate == date &&
                    (button != otherDateButton || selectedDate != today && selectedDate != tomorrow)
                button.backgroundTintList = ColorStateList.valueOf(
                    if (selected) 0xFFC62828.toInt() else 0xFFF0F0F0.toInt()
                )
                button.setTextColor(if (selected) Color.WHITE else 0xFF424242.toInt())
            }
    }

    private fun renderExistingPlans(state: PlanDetailUiState) {
        existingPlansSection.removeAllViews()
        if (!state.code.matches(Regex("[0-9]{6}"))) return
        existingPlansSection.addView(TextView(requireContext()).apply {
            text = "该股票已有计划"
            textSize = 17f
            setTextColor(Color.BLACK)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(4.dp, 0, 4.dp, 10.dp)
        })
        if (state.existingPlans.isEmpty()) {
            existingPlansSection.addView(TextView(requireContext()).apply {
                text = "暂无计划"
                textSize = 14f
                setTextColor(0xFF757575.toInt())
                setPadding(4.dp, 10.dp, 4.dp, 10.dp)
            })
            return
        }
        state.existingPlans.forEachIndexed { index, plan ->
            existingPlansSection.addView(existingPlanRow(plan))
            if (index < state.existingPlans.lastIndex) {
                existingPlansSection.addView(View(requireContext()).apply {
                    setBackgroundColor(0xFFE0E0E0.toInt())
                }, ViewGroup.LayoutParams.MATCH_PARENT, 1.dp)
            }
        }
    }

    private fun existingPlanRow(plan: StockPlan): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(4.dp, 12.dp, 4.dp, 12.dp)
        background = selectableBackground()
        isClickable = true
        isFocusable = true
        addView(TextView(requireContext()).apply {
            text = "${formatDate(LocalDate.parse(plan.planDate))} · ${plan.status.label}"
            textSize = 14f
            setTextColor(0xFF616161.toInt())
            setTypeface(typeface, Typeface.BOLD)
        })
        addView(TextView(requireContext()).apply {
            text = plan.action
            textSize = 15f
            setTextColor(0xFF303030.toInt())
            setPadding(0, 5.dp, 0, if (plan.reminder.isEmpty()) 0 else 3.dp)
        })
        if (plan.reminder.isNotEmpty()) {
            addView(TextView(requireContext()).apply {
                text = plan.reminder
                textSize = 13f
                setTextColor(0xFF757575.toInt())
            })
        }
        setOnClickListener { viewModel.loadPlan(plan) }
    }

    private fun notifyPlansChanged() {
        requireContext().sendBroadcast(
            Intent(StockAccessibilityService.ACTION_PLANS_CHANGED)
                .setPackage(requireContext().packageName)
        )
    }

    private fun navigateUpOrFinish() {
        if (!findNavController().navigateUp()) requireActivity().finish()
    }
}
