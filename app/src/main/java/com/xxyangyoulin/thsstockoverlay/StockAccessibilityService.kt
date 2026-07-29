package com.xxyangyoulin.thsstockoverlay

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs

class StockAccessibilityService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private lateinit var planStore: StockPlanStore
    private lateinit var settingsStore: OverlaySettingsStore
    private var overlaySettings = OverlaySettings()
    private var contentOverlay: LinearLayout? = null
    private var handleOverlay: FrameLayout? = null
    private var contentParams: WindowManager.LayoutParams? = null
    private var handleParams: WindowManager.LayoutParams? = null
    private var nameView: TextView? = null
    private var actionView: TextView? = null
    private var reminderView: TextView? = null
    private var lastInfo: StockInfo? = null
    private var currentInfo: StockInfo? = null
    private var handleX = 0
    private var handleY = 0
    private var resolvingScreenshot = false
    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val plansChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SETTINGS_CHANGED) {
                overlaySettings = settingsStore.get()
                if (!settingsStore.isOverlayEnabled()) {
                    removeOverlayImmediately()
                    return
                }
                applyOverlayStyle()
                measureContentOverlay()
                applyPosition(edgeX(handleX + HANDLE_SIZE_DP.dp / 2 >= resources.displayMetrics.widthPixels / 2), handleY)
                inspectCurrentWindow()
            } else {
                currentInfo?.let(::updateOverlayContent)
            }
        }
    }

    override fun onServiceConnected() {
        windowManager = getSystemService(WindowManager::class.java)
        planStore = StockPlanStore(this)
        settingsStore = OverlaySettingsStore(this)
        overlaySettings = settingsStore.get()
        val filter = IntentFilter(ACTION_PLANS_CHANGED).apply {
            addAction(ACTION_SETTINGS_CHANGED)
        }
        ContextCompat.registerReceiver(
            this,
            plansChangedReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isRunning = true
        sendState()
        inspectCurrentWindow()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        inspectCurrentWindow()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        removeOverlayImmediately()
        unregisterReceiver(plansChangedReceiver)
        textRecognizer.close()
        isRunning = false
        sendState()
        super.onDestroy()
    }

    private fun inspectCurrentWindow() {
        if (!settingsStore.isOverlayEnabled()) {
            hideOverlay()
            return
        }
        val root = rootInActiveWindow ?: run {
            hideOverlay()
            return
        }
        try {
            if (root.packageName?.toString() != THS_PACKAGE) {
                hideOverlay()
                return
            }
            val quoteRoots = root.findAccessibilityNodeInfosByViewId("$THS_PACKAGE:id/quotes_rootview")
            val titleNodes = root.findAccessibilityNodeInfosByViewId("$THS_PACKAGE:id/navi_title_text")
            val name = titleNodes.firstOrNull()?.text?.toString()?.trim().orEmpty()
            val isQuotePage = quoteRoots.isNotEmpty()
            quoteRoots.recycleAll()
            titleNodes.recycleAll()
            if (name.isEmpty() || !isQuotePage) {
                hideOverlay()
                return
            }

            val texts = ArrayList<String>()
            collectTexts(root, texts)
            val preferences = getSharedPreferences(PREFS, MODE_PRIVATE)
            val parsedCode = StockTextParser.findCode(name, texts)
                ?: preferences.getString("$KEY_CODE_CACHE_PREFIX$name", null)
            val info = StockInfo(name, parsedCode)
            if (parsedCode != null && planStore.findRelevant(parsedCode) == null) {
                hideOverlay()
                return
            }
            currentInfo = info
            showOverlay(info)
            if (parsedCode == null) resolveCodeFromScreenshot(info)
            if (info != lastInfo) {
                lastInfo = info
                preferences.edit()
                    .putString(KEY_NAME, info.name)
                    .putString(KEY_CODE, info.code)
                    .apply()
                sendState()
            }
        } finally {
            root.recycle()
        }
    }

    private fun resolveCodeFromScreenshot(info: StockInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || resolvingScreenshot) return
        resolvingScreenshot = true
        takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val hardwareBuffer = screenshot.hardwareBuffer
                    val source = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                    hardwareBuffer.close()
                    if (source == null) {
                        resolvingScreenshot = false
                        return
                    }
                    val left = (source.width * 0.2f).toInt()
                    val top = (source.height * 0.04f).toInt()
                    val width = (source.width * 0.6f).toInt()
                    val height = (source.height * 0.09f).toInt()
                    val title = Bitmap.createBitmap(source, left, top, width, height)
                    source.recycle()
                    textRecognizer.process(InputImage.fromBitmap(title, 0))
                        .addOnSuccessListener { result ->
                            val code = StockTextParser.findStandaloneCode(
                                result.textBlocks.flatMap { block -> block.lines.map { it.text } }
                            )
                            if (code != null && currentInfo?.name == info.name) {
                                val resolved = StockInfo(info.name, code)
                                currentInfo = resolved
                                lastInfo = resolved
                                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                    .putString(KEY_NAME, resolved.name)
                                    .putString(KEY_CODE, resolved.code)
                                    .putString("$KEY_CODE_CACHE_PREFIX${resolved.name}", resolved.code)
                                    .apply()
                                updateOverlayContent(resolved)
                                sendState()
                            }
                        }
                        .addOnCompleteListener {
                            title.recycle()
                            resolvingScreenshot = false
                        }
                }

                override fun onFailure(errorCode: Int) {
                    resolvingScreenshot = false
                }
            }
        )
    }

    private fun collectTexts(node: AccessibilityNodeInfo, output: MutableList<String>) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(output::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(output::add)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectTexts(child, output)
            child.recycle()
        }
    }

    private fun showOverlay(info: StockInfo) {
        if (contentOverlay == null) {
            createOverlay(info)
        } else {
            updateOverlayContent(info)
        }
    }

    private fun createOverlay(info: StockInfo) {
        nameView = TextView(this).apply {
            setTypeface(typeface, Typeface.BOLD)
        }
        actionView = TextView(this).apply {
        }
        reminderView = TextView(this).apply {
        }
        contentOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(nameView)
            addView(actionView)
            addView(reminderView)
        }
        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFE0A21A.toInt())
                setStroke(2.dp, Color.WHITE)
            }
        }
        handleOverlay = FrameLayout(this).apply {
            contentDescription = "打开当前股票计划"
            setPadding(15.dp, 15.dp, 15.dp, 15.dp)
            addView(dot, FrameLayout.LayoutParams(18.dp, 18.dp, Gravity.CENTER))
            setOnTouchListener(HandleTouchListener())
        }

        applyOverlayStyle()
        updateOverlayContent(info)
        measureContentOverlay()

        val position = getSharedPreferences(PREFS, MODE_PRIVATE)
        val right = position.getBoolean(KEY_FLOAT_RIGHT, true)
        handleY = position.getInt(KEY_FLOAT_Y, 72.dp)
        handleX = edgeX(right)
        val screenHeight = resources.displayMetrics.heightPixels
        handleY = handleY.coerceIn(32.dp, screenHeight - HANDLE_SIZE_DP.dp - 32.dp)
        val contentX = contentX(handleX, right)
        contentParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = contentX
            y = handleY
        }
        handleParams = WindowManager.LayoutParams(
            HANDLE_SIZE_DP.dp,
            HANDLE_SIZE_DP.dp,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = handleX
            y = handleY
        }
        windowManager.addView(contentOverlay, contentParams)
        windowManager.addView(handleOverlay, handleParams)
    }

    private fun updateOverlayContent(info: StockInfo) {
        val plan = info.code?.let(planStore::findRelevant)
        if (info.code != null && plan == null) {
            removeOverlayImmediately()
            return
        }
        nameView?.text = listOfNotNull(info.name, info.code).joinToString("  ")
        actionView?.apply {
            text = plan?.action.orEmpty()
            visibility = if (plan == null) View.GONE else View.VISIBLE
        }
        reminderView?.apply {
            text = plan?.reminder.orEmpty()
            visibility = if (plan?.reminder.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        if (contentOverlay?.isAttachedToWindow == true) {
            measureContentOverlay()
            applyPosition(handleX, handleY)
        }
    }

    private fun applyOverlayStyle() {
        val scale = overlaySettings.scalePercent / 100f
        val horizontalPadding = (16 * scale).toInt().dp
        val verticalPadding = (10 * scale).toInt().dp
        val maxTextWidth = minOf(
            (250 * scale).toInt().dp,
            resources.displayMetrics.widthPixels - HANDLE_SIZE_DP.dp - horizontalPadding * 2 - overlaySettings.edgeMarginDp.dp * 2
        )
        nameView?.apply {
            setTextColor(overlaySettings.textColor)
            textSize = overlaySettings.fontSizeSp.toFloat()
            maxWidth = maxTextWidth
        }
        actionView?.apply {
            setTextColor(overlaySettings.textColor)
            textSize = overlaySettings.fontSizeSp.toFloat()
            maxWidth = maxTextWidth
            setPadding(0, (6 * scale).toInt().dp, 0, 0)
        }
        reminderView?.apply {
            setTextColor(overlaySettings.textColor)
            alpha = 0.75f
            textSize = maxOf(8, overlaySettings.fontSizeSp - 2).toFloat()
            maxWidth = maxTextWidth
            setPadding(0, (4 * scale).toInt().dp, 0, 0)
        }
        contentOverlay?.apply {
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            background = GradientDrawable().apply {
                val alpha = overlaySettings.opacityPercent * 255 / 100
                setColor(Color.argb(
                    alpha,
                    Color.red(overlaySettings.backgroundColor),
                    Color.green(overlaySettings.backgroundColor),
                    Color.blue(overlaySettings.backgroundColor)
                ))
                cornerRadius = (7 * scale).toInt().dp.toFloat()
                setStroke(1.dp, 0x40FFFFFF)
            }
        }
    }

    private fun measureContentOverlay() {
        contentOverlay?.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
    }

    private fun applyPosition(newHandleX: Int, newHandleY: Int) {
        val handle = handleOverlay ?: return
        val content = contentOverlay ?: return
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val margin = overlaySettings.edgeMarginDp.dp
        val minY = 32.dp
        val maxY = screenHeight - handle.height - 32.dp
        handleX = newHandleX.coerceIn(margin, screenWidth - handle.width - margin)
        handleY = newHandleY.coerceIn(minY, maxY)
        val onRight = handleX + handle.width / 2 >= screenWidth / 2
        val contentWidth = content.measuredWidth
        val proposedContentX = if (onRight) handleX - contentWidth else handleX + handle.width
        val contentX = proposedContentX.coerceIn(margin, screenWidth - contentWidth - margin)

        handleParams?.let {
            it.x = handleX
            it.y = handleY
            windowManager.updateViewLayout(handle, it)
        }
        contentParams?.let {
            it.x = contentX
            it.y = handleY
            windowManager.updateViewLayout(content, it)
        }
    }

    private fun snapToEdge() {
        val right = handleX + HANDLE_SIZE_DP.dp / 2 >= resources.displayMetrics.widthPixels / 2
        applyPosition(edgeX(right), handleY)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_FLOAT_RIGHT, right)
            .putInt(KEY_FLOAT_Y, handleY)
            .apply()
    }

    private fun edgeX(right: Boolean): Int {
        val margin = overlaySettings.edgeMarginDp.dp
        return if (right) resources.displayMetrics.widthPixels - HANDLE_SIZE_DP.dp - margin else margin
    }

    private fun contentX(handleX: Int, right: Boolean): Int {
        val margin = overlaySettings.edgeMarginDp.dp
        val width = contentOverlay?.measuredWidth ?: 0
        val proposed = if (right) handleX - width else handleX + HANDLE_SIZE_DP.dp
        return proposed.coerceIn(margin, resources.displayMetrics.widthPixels - width - margin)
    }

    private fun openCurrentPlan() {
        val info = currentInfo ?: return
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_DETAIL, true)
                putExtra(MainActivity.EXTRA_FROM_OVERLAY, true)
                putExtra(MainActivity.EXTRA_STOCK_NAME, info.name)
                putExtra(MainActivity.EXTRA_STOCK_CODE, info.code)
            }
        )
    }

    private fun hideOverlay() {
        removeOverlayImmediately()
    }

    private fun removeOverlayImmediately() {
        contentOverlay?.takeIf { it.isAttachedToWindow }?.let(windowManager::removeViewImmediate)
        handleOverlay?.takeIf { it.isAttachedToWindow }?.let(windowManager::removeViewImmediate)
        contentOverlay = null
        handleOverlay = null
        contentParams = null
        handleParams = null
        nameView = null
        actionView = null
        reminderView = null
        currentInfo = null
    }

    private fun sendState() {
        sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
    }

    private inner class HandleTouchListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragged = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = handleX
                    startY = handleY
                    dragged = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (abs(dx) > 6.dp || abs(dy) > 6.dp) dragged = true
                    if (dragged) applyPosition(startX + dx.toInt(), startY + dy.toInt())
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragged) snapToEdge() else openCurrentPlan()
                    return true
                }
            }
            return false
        }
    }

    private fun List<AccessibilityNodeInfo>.recycleAll() = forEach { it.recycle() }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_STATE_CHANGED = "com.xxyangyoulin.thsstockoverlay.STATE_CHANGED"
        const val ACTION_PLANS_CHANGED = "com.xxyangyoulin.thsstockoverlay.PLANS_CHANGED"
        const val ACTION_SETTINGS_CHANGED = "com.xxyangyoulin.thsstockoverlay.SETTINGS_CHANGED"
        const val PREFS = "monitor_state"
        const val KEY_NAME = "stock_name"
        const val KEY_CODE = "stock_code"
        private const val KEY_FLOAT_RIGHT = "float_right"
        private const val KEY_FLOAT_Y = "float_y"
        private const val KEY_CODE_CACHE_PREFIX = "stock_code_"
        private const val THS_PACKAGE = "com.hexin.plat.android"
        private const val HANDLE_SIZE_DP = 48

        @Volatile
        var isRunning = false
            private set
    }
}
