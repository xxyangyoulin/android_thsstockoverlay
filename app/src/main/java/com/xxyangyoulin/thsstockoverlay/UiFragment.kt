package com.xxyangyoulin.thsstockoverlay

import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.time.LocalDate
import java.time.format.DateTimeFormatter

abstract class UiFragment : Fragment() {
    protected fun screenRoot() = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xFFFAFAFA.toInt())
    }

    protected fun titleText(value: String) = TextView(requireContext()).apply {
        text = value
        textSize = 22f
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(Color.BLACK)
        setTypeface(typeface, Typeface.BOLD)
    }

    protected fun fieldLabel(value: String) = TextView(requireContext()).apply {
        text = value
        textSize = 12f
        setTextColor(0xFF616161.toInt())
        setPadding(4.dp, 0, 4.dp, 4.dp)
    }

    protected fun textField(label: String, value: String, multiline: Boolean = false) =
        TextInputLayout(requireContext()).apply {
            hint = label
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding(0, 0, 0, 12.dp)
            addView(TextInputEditText(context).apply {
                setText(value)
                if (multiline) {
                    minLines = 3
                    gravity = Gravity.TOP
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                }
            })
        }

    protected fun selectableBackground(borderless: Boolean = false): android.graphics.drawable.Drawable? {
        val attribute = if (borderless) android.R.attr.selectableItemBackgroundBorderless
        else android.R.attr.selectableItemBackground
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attribute, typedValue, true)
        return ContextCompat.getDrawable(requireContext(), typedValue.resourceId)
    }

    protected fun formatDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))

    protected fun space(height: Int) = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(1, height)
    }

    protected val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
