package com.xxyangyoulin.thsstockoverlay

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PlanListFragment : UiFragment() {
    private lateinit var store: StockPlanStore
    private lateinit var overlaySettingsStore: OverlaySettingsStore
    private var overlaySwitch: MaterialSwitch? = null
    private var overlayStatus: TextView? = null
    private var monitorPermissionSection: LinearLayout? = null
    private var monitorButton: MaterialButton? = null
    private var monitorStatus: TextView? = null

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshMonitorState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = StockPlanStore(requireContext())
        overlaySettingsStore = OverlaySettingsStore(requireContext())
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = buildScreen()

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            requireContext(),
            stateReceiver,
            IntentFilter(StockAccessibilityService.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        refreshMonitorState()
    }

    override fun onStop() {
        requireContext().unregisterReceiver(stateReceiver)
        super.onStop()
    }

    private fun buildScreen(): View {
        val root = screenRoot()
        root.addView(buildTopBar())

        val plans = store.getAll()
        val scrollContent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 4.dp, 18.dp, 32.dp)
            addView(buildSummary(plans))
            addView(buildMonitorCard())
            if (plans.isEmpty()) {
                addView(buildEmptyState())
            } else {
                plans.groupBy { it.planDate }.forEach { (date, group) ->
                    addView(dateHeader(date))
                    group.forEach { addView(planCard(it)) }
                }
            }
        }
        root.addView(ScrollView(requireContext()).apply {
            isFillViewport = true
            addView(scrollContent)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun buildTopBar() = MaterialToolbar(requireContext()).apply {
        title = "交易计划"
        setTitleTextAppearance(
            context,
            com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium
        )
        setPadding(6.dp, 4.dp, 4.dp, 0)
        menu.add(Menu.NONE, R.id.open_settings, Menu.NONE, "浮窗设置").apply {
            setIcon(R.drawable.ic_settings)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        menu.add(Menu.NONE, R.id.add_plan, Menu.NONE, "新建计划").apply {
            setIcon(R.drawable.ic_add)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.open_settings -> findNavController().navigate(R.id.overlaySettingsFragment)
                R.id.add_plan -> findNavController().navigate(R.id.planDetailFragment)
            }
            true
        }
    }

    private fun buildSummary(plans: List<StockPlan>) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        val today = LocalDate.now().toString()
        val tomorrow = LocalDate.now().plusDays(1).toString()
        addView(summaryItem("今日计划", plans.count { it.planDate == today }), summaryParams())
        addView(summaryItem("明日计划", plans.count { it.planDate == tomorrow }), summaryParams())
        addView(summaryItem("全部计划", plans.size), summaryParams(last = true))
    }

    private fun summaryParams(last: Boolean = false) =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            if (!last) marginEnd = 10.dp
        }

    private fun summaryItem(label: String, count: Int) = MaterialCardView(requireContext()).apply {
        radius = 8.dp.toFloat()
        cardElevation = 0f
        setCardBackgroundColor(0xFFF2F3F5.toInt())
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp, 12.dp, 12.dp, 12.dp)
            addView(TextView(context).apply {
                text = label
                textSize = 12f
                setTextColor(0xFF74777F.toInt())
            })
            addView(TextView(context).apply {
                text = count.toString()
                textSize = 22f
                setTextColor(0xFF1A1B1F.toInt())
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 4.dp, 0, 0)
            })
        })
    }

    private fun buildMonitorCard() = MaterialCardView(requireContext()).apply {
        radius = 8.dp.toFloat()
        cardElevation = 0f
        strokeWidth = 1.dp
        strokeColor = 0xFFE2E2E6.toInt()
        setCardBackgroundColor(0xFFFFFFFF.toInt())
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val overlayRow = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(16.dp, 12.dp, 10.dp, 12.dp)
        }
        overlayRow.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_notifications)
            imageTintList = android.content.res.ColorStateList.valueOf(0xFFC62828.toInt())
            contentDescription = null
        }, LinearLayout.LayoutParams(24.dp, 24.dp).apply { marginEnd = 12.dp })
        overlayRow.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = "显示计划浮窗"
                textSize = 15f
                setTextColor(0xFF1A1B1F.toInt())
                setTypeface(typeface, Typeface.BOLD)
            })
            overlayStatus = TextView(context).apply {
                textSize = 12f
                setTextColor(0xFF74777F.toInt())
                setPadding(0, 2.dp, 0, 0)
            }
            addView(overlayStatus)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        overlaySwitch = MaterialSwitch(context).apply {
            contentDescription = "显示计划浮窗"
            setOnCheckedChangeListener { _, enabled -> setOverlayEnabled(enabled) }
        }
        overlayRow.addView(overlaySwitch)
        content.addView(overlayRow)
        monitorPermissionSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        monitorPermissionSection?.addView(View(context).apply {
            setBackgroundColor(0xFFE8E8EC.toInt())
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp).apply {
            marginStart = 16.dp
            marginEnd = 16.dp
        })

        val permissionRow = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(16.dp, 8.dp, 10.dp, 8.dp)
        }
        monitorStatus = TextView(context).apply {
            textSize = 13f
            setTextColor(0xFF74777F.toInt())
        }
        permissionRow.addView(
            monitorStatus,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        monitorButton = MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            setOnClickListener { openAccessibilitySettings() }
        }
        permissionRow.addView(monitorButton, LinearLayout.LayoutParams(84.dp, 44.dp))
        monitorPermissionSection?.addView(permissionRow)
        content.addView(monitorPermissionSection)
        addView(content)
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 14.dp }
    }

    private fun buildEmptyState() = LinearLayout(requireContext()).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        orientation = LinearLayout.VERTICAL
        setPadding(24.dp, 72.dp, 24.dp, 24.dp)
        addView(TextView(context).apply {
            text = "还没有交易计划"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(0xFF1A1B1F.toInt())
            setTypeface(typeface, Typeface.BOLD)
        })
        addView(TextView(context).apply {
            text = "记录下一次操作，按计划执行"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFF74777F.toInt())
            setPadding(0, 6.dp, 0, 18.dp)
        })
        addView(MaterialButton(context).apply {
            text = "新建计划"
            setIconResource(R.drawable.ic_add)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            setOnClickListener { findNavController().navigate(R.id.planDetailFragment) }
        })
    }

    private fun planCard(plan: StockPlan): View = MaterialCardView(requireContext()).apply {
        radius = 8.dp.toFloat()
        cardElevation = 1.dp.toFloat()
        setCardBackgroundColor(0xFFFFFFFF.toInt())
        isClickable = true
        isFocusable = true
        foreground = selectableBackground()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 16.dp, 16.dp, 14.dp)
        }
        content.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(context).apply {
                text = plan.name
                textSize = 19f
                setTextColor(0xFF1A1B1F.toInt())
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = plan.code
                textSize = 13f
                setTextColor(0xFF74777F.toInt())
                setPadding(8.dp, 3.dp, 0, 0)
            })
        })
        content.addView(TextView(context).apply {
            text = plan.action
            textSize = 15f
            setTextColor(0xFF303036.toInt())
            setLineSpacing(0f, 1.25f)
            setPadding(0, 14.dp, 0, 0)
        })
        if (plan.reminder.isNotEmpty()) {
            content.addView(TextView(context).apply {
                text = "纪律提醒 · ${plan.reminder}"
                textSize = 13f
                setTextColor(0xFFC62828.toInt())
                setLineSpacing(0f, 1.2f)
                setPadding(0, 10.dp, 0, 0)
            })
        }
        content.addView(View(context).apply {
            setBackgroundColor(0xFFE8E8EC.toInt())
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp).apply {
            topMargin = 14.dp
            bottomMargin = 11.dp
        })
        content.addView(TextView(context).apply {
            text = planStatus(plan)
            textSize = 13f
            setTextColor(planStatusColor(plan))
        })
        addView(content)
        setOnClickListener {
            findNavController().navigate(R.id.planDetailFragment, Bundle().apply {
                putString(PlanDetailViewModel.ARG_STOCK_CODE, plan.code)
                putString(PlanDetailViewModel.ARG_STOCK_NAME, plan.name)
                putString(PlanDetailViewModel.ARG_PLAN_DATE, plan.planDate)
            })
        }
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 12.dp }
    }

    private fun dateHeader(value: String): TextView {
        val date = LocalDate.parse(value)
        val prefix = when (date) {
            LocalDate.now() -> "今天 · "
            LocalDate.now().plusDays(1) -> "明天 · "
            else -> ""
        }
        return TextView(requireContext()).apply {
            text = prefix + date.format(DateTimeFormatter.ofPattern("M月d日"))
            textSize = 14f
            setTextColor(0xFF74777F.toInt())
            setTypeface(typeface, Typeface.BOLD)
            setPadding(2.dp, 24.dp, 2.dp, 10.dp)
        }
    }

    private fun planStatus(plan: StockPlan): String {
        if (plan.status == PlanStatus.EXECUTED) return "✓ 已执行"
        if (plan.status == PlanStatus.CANCELLED) return "● 已作废"
        val date = LocalDate.parse(plan.planDate)
        return when {
            date.isBefore(LocalDate.now()) -> "● 已过期"
            date == LocalDate.now() -> "● 今日计划"
            date == LocalDate.now().plusDays(1) -> "● 明日计划"
            else -> "● 待执行"
        }
    }

    private fun planStatusColor(plan: StockPlan): Int {
        if (plan.status == PlanStatus.EXECUTED) return 0xFF16803D.toInt()
        if (plan.status == PlanStatus.CANCELLED) return 0xFF8A8D94.toInt()
        val date = LocalDate.parse(plan.planDate)
        return when {
            date.isBefore(LocalDate.now()) -> 0xFF8A8D94.toInt()
            date == LocalDate.now() -> 0xFFE09B00.toInt()
            else -> 0xFF16803D.toInt()
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun refreshMonitorState() {
        val enabled = isServiceEnabled()
        val overlayEnabled = overlaySettingsStore.isOverlayEnabled()
        overlaySwitch?.setOnCheckedChangeListener(null)
        overlaySwitch?.isChecked = overlayEnabled
        overlaySwitch?.isEnabled = enabled
        overlaySwitch?.setOnCheckedChangeListener { _, value -> setOverlayEnabled(value) }
        monitorPermissionSection?.visibility = if (enabled) View.GONE else View.VISIBLE
        overlayStatus?.text = when {
            !enabled -> "需先开启无障碍服务"
            overlayEnabled -> "进入股票详情页时自动显示"
            else -> "已暂停，当前不会显示浮窗"
        }
        monitorButton?.text = if (enabled) "管理" else "开启"
        monitorStatus?.text = if (enabled) "无障碍服务已授权" else "无障碍服务未授权"
    }

    private fun setOverlayEnabled(enabled: Boolean) {
        overlaySettingsStore.setOverlayEnabled(enabled)
        requireContext().sendBroadcast(
            Intent(StockAccessibilityService.ACTION_SETTINGS_CHANGED)
                .setPackage(requireContext().packageName)
        )
        refreshMonitorState()
    }

    private fun isServiceEnabled(): Boolean {
        val target = ComponentName(requireContext(), StockAccessibilityService::class.java)
        val manager = requireContext().getSystemService(AccessibilityManager::class.java)
        return manager.isEnabled && manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { service ->
                val info = service.resolveInfo.serviceInfo
                ComponentName(info.packageName, info.name) == target
            }
    }
}
