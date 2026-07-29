package com.xxyangyoulin.thsstockoverlay

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.commitNow
import androidx.navigation.fragment.NavHostFragment

class MainActivity : AppCompatActivity() {
    private lateinit var navHost: NavHostFragment
    private val handler = Handler(Looper.getMainLooper())
    private val imeBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            WindowInsetsControllerCompat(window, window.decorView)
                .hide(WindowInsetsCompat.Type.ime())
            window.decorView.findFocus()?.clearFocus()
            isEnabled = false
        }
    }
    private val disableImeBackCallback = Runnable {
        val insets = ViewCompat.getRootWindowInsets(window.decorView)
        if (insets?.isVisible(WindowInsetsCompat.Type.ime()) != true) {
            imeBackCallback.isEnabled = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        onBackPressedDispatcher.addCallback(this, imeBackCallback)

        val containerId = R.id.nav_host
        val container = FrameLayout(this).apply {
            id = containerId
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
                    handler.removeCallbacks(disableImeBackCallback)
                    imeBackCallback.isEnabled = true
                } else if (imeBackCallback.isEnabled) {
                    handler.removeCallbacks(disableImeBackCallback)
                    handler.postDelayed(disableImeBackCallback, IME_CALLBACK_RELEASE_DELAY_MS)
                }
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        setContentView(container)

        if (savedInstanceState == null) {
            navHost = NavHostFragment()
            supportFragmentManager.commitNow {
                replace(containerId, navHost)
                setPrimaryNavigationFragment(navHost)
            }
            setNavigationGraph(intent)
        } else {
            navHost = supportFragmentManager.findFragmentById(containerId) as NavHostFragment
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setNavigationGraph(intent)
    }

    private fun setNavigationGraph(intent: Intent) {
        val controller = navHost.navController
        val graph = controller.navInflater.inflate(R.navigation.main_navigation)
        val openDetail = intent.getBooleanExtra(EXTRA_OPEN_DETAIL, false)
        if (openDetail) graph.setStartDestination(R.id.planDetailFragment)
        controller.setGraph(graph, if (openDetail) detailArguments(intent) else null)
    }

    private fun detailArguments(intent: Intent) = Bundle().apply {
        putString(PlanDetailViewModel.ARG_STOCK_CODE, intent.getStringExtra(EXTRA_STOCK_CODE).orEmpty())
        putString(PlanDetailViewModel.ARG_STOCK_NAME, intent.getStringExtra(EXTRA_STOCK_NAME).orEmpty())
        putBoolean(PlanDetailViewModel.ARG_RELEVANT_PLAN, true)
    }

    companion object {
        private const val IME_CALLBACK_RELEASE_DELAY_MS = 500L
        const val EXTRA_OPEN_DETAIL = "open_detail"
        const val EXTRA_FROM_OVERLAY = "from_overlay"
        const val EXTRA_STOCK_NAME = "stock_name"
        const val EXTRA_STOCK_CODE = "stock_code"
    }
}
