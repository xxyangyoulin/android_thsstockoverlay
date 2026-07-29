package com.xxyangyoulin.thsstockoverlay

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.navigation.fragment.findNavController

class OverlaySettingsFragment : UiFragment() {
    private lateinit var settingsStore: OverlaySettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = OverlaySettingsStore(requireContext())
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = screenRoot()
        val topBar = LinearLayout(requireContext()).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(8.dp, 10.dp, 20.dp, 8.dp)
            addView(ImageButton(requireContext()).apply {
                setImageResource(R.drawable.ic_arrow_back)
                contentDescription = "返回"
                background = selectableBackground(borderless = true)
                setOnClickListener { findNavController().navigateUp() }
            }, LinearLayout.LayoutParams(48.dp, 48.dp))
            addView(titleText("浮窗设置"), LinearLayout.LayoutParams(0, 56.dp, 1f))
        }
        root.addView(topBar)

        var settings = settingsStore.get()
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 8.dp, 20.dp, 32.dp)
        }
        fun save(updated: OverlaySettings) {
            settings = updated
            settingsStore.save(updated)
            requireContext().sendBroadcast(
                Intent(StockAccessibilityService.ACTION_SETTINGS_CHANGED)
                    .setPackage(requireContext().packageName)
            )
        }
        content.addView(settingSlider("浮窗大小", settings.scalePercent, 70, 150, "%") {
            save(settings.copy(scalePercent = it))
        })
        content.addView(settingSlider("左右边距", settings.edgeMarginDp, 0, 32, "dp") {
            save(settings.copy(edgeMarginDp = it))
        })
        content.addView(settingSlider("字体大小", settings.fontSizeSp, 8, 18, "sp") {
            save(settings.copy(fontSizeSp = it))
        })
        content.addView(settingSlider("透明度", settings.opacityPercent, 30, 100, "%") {
            save(settings.copy(opacityPercent = it))
        })
        content.addView(colorSetting(
            "字体颜色",
            listOf(Color.WHITE, 0xFFFFE082.toInt(), 0xFFA5D6A7.toInt(), 0xFF90CAF9.toInt(), Color.BLACK),
            settings.textColor
        ) { save(settings.copy(textColor = it)) })
        content.addView(colorSetting(
            "浮窗颜色",
            listOf(0xFF212121.toInt(), 0xFF37474F.toInt(), 0xFF1B5E20.toInt(), 0xFF0D47A1.toInt(), 0xFFB71C1C.toInt()),
            settings.backgroundColor
        ) { save(settings.copy(backgroundColor = it)) })
        root.addView(ScrollView(requireContext()).apply { addView(content) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        return root
    }

    private fun settingSlider(
        title: String,
        value: Int,
        minimum: Int,
        maximum: Int,
        suffix: String,
        onChanged: (Int) -> Unit
    ): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 10.dp, 0, 14.dp)
        val label = TextView(requireContext()).apply {
            text = "$title  $value$suffix"
            textSize = 15f
            setTextColor(Color.BLACK)
        }
        addView(label)
        addView(SeekBar(requireContext()).apply {
            min = minimum
            max = maximum
            progress = value
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    label.text = "$title  $progress$suffix"
                    if (fromUser) onChanged(progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun colorSetting(
        title: String,
        colors: List<Int>,
        selected: Int,
        onChanged: (Int) -> Unit
    ): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 10.dp, 0, 18.dp)
        addView(TextView(requireContext()).apply {
            text = title
            textSize = 15f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 10.dp)
        })
        val swatches = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val views = mutableListOf<View>()
        fun refresh(active: Int) {
            views.forEachIndexed { index, view ->
                view.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colors[index])
                    setStroke(if (colors[index] == active) 3.dp else 1.dp, 0xFF757575.toInt())
                }
            }
        }
        colors.forEach { color ->
            val swatch = View(requireContext()).apply {
                contentDescription = "$title ${String.format("#%06X", color and 0xFFFFFF)}"
                setOnClickListener {
                    refresh(color)
                    onChanged(color)
                }
            }
            views.add(swatch)
            swatches.addView(swatch, LinearLayout.LayoutParams(44.dp, 44.dp).apply {
                marginEnd = 12.dp
            })
        }
        refresh(selected)
        addView(swatches)
    }
}
