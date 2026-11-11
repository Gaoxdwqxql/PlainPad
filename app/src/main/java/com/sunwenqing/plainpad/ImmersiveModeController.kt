package com.sunwenqing.plainpad

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sunwenqing.plainpad.databinding.ActivityMainBinding
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 沉浸阅读控制器。
 *
 * 升级后的行为：
 * - 隐藏顶栏、标签栏、状态栏、系统栏、行号与快速滚动条
 * - 左侧/右侧点按翻页，中间点按呼出/隐藏控制条
 * - 左右滑动翻页，上下拖动直接滚动正文（大文件模式同样支持）
 * - 音量键翻页
 * - 底部细进度条 + 百分比/行号，控制条 4 秒后自动隐藏
 * - 阅读期间关闭编辑与光标，防止误触；保持屏幕常亮
 * - 退出时恢复行号、当前行高亮与滚动条
 */
class ImmersiveModeController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val prefs: Prefs,
    private val canShowLineNumbers: () -> Boolean,
    private val isLargeFileMode: () -> Boolean
) {

    var isImmersive: Boolean = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var controlsVisible = false
    private var hideControlsTask: Runnable? = null
    private var savedLineHighlight = true

    // 手势方向锁定，避免斜着滑时同时翻页和滚动
    private var gestureAxis = AXIS_UNKNOWN
    private var pendingX = 0f
    private var pendingY = 0f
    private var horizontalAccum = 0f
    private var horizontalPaged = false
    private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop.toFloat()
    private val minFlingVelocity = ViewConfiguration.get(activity).scaledMinimumFlingVelocity

    private val gestureDetector = GestureDetector(
        activity,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                resetGesture()
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (gestureAxis == AXIS_UNKNOWN) {
                    pendingX += distanceX
                    pendingY += distanceY
                    if (abs(pendingX) < touchSlop && abs(pendingY) < touchSlop) return true
                    gestureAxis =
                        if (abs(pendingX) > abs(pendingY)) AXIS_HORIZONTAL else AXIS_VERTICAL
                    if (gestureAxis == AXIS_VERTICAL) {
                        scrollContent(pendingY.toInt())
                        pendingY = 0f
                    } else {
                        horizontalAccum = pendingX
                        pendingX = 0f
                    }
                    return true
                }

                if (gestureAxis == AXIS_VERTICAL) {
                    scrollContent(distanceY.toInt())
                } else if (!horizontalPaged) {
                    horizontalAccum += distanceX
                    val threshold = binding.immersiveTouchLayer.width * 0.18f
                    if (abs(horizontalAccum) >= threshold) {
                        page(if (horizontalAccum < 0) 1 else -1)
                        horizontalPaged = true
                    }
                }
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                // 横向甩动翻页；纵向甩动交给拖动滚动，不额外翻页
                if (!horizontalPaged &&
                    abs(velocityX) > minFlingVelocity &&
                    abs(velocityX) > abs(velocityY)
                ) {
                    page(if (velocityX < 0) 1 else -1)
                    horizontalPaged = true
                    return true
                }
                return false
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                handleTap(e.x)
                return true
            }
        }
    )

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (!isImmersive) return
            updateProgress()
            handler.postDelayed(this, 300L)
        }
    }

    init {
        setupTouchLayer()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchLayer() {
        binding.immersiveTouchLayer.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                resetGesture()
            }
            true
        }
    }

    private fun resetGesture() {
        gestureAxis = AXIS_UNKNOWN
        pendingX = 0f
        pendingY = 0f
        horizontalAccum = 0f
        horizontalPaged = false
    }

    fun apply(on: Boolean) {
        if (on == isImmersive) return
        isImmersive = on
        if (on) enter() else exit()
    }

    fun pageUp() = page(-1)

    fun pageDown() = page(1)

    /** Activity 进入后台时停掉进度刷新，回来再恢复 */
    fun onHostPause() {
        handler.removeCallbacks(progressRunnable)
    }

    fun onHostResume() {
        if (isImmersive) startProgressUpdates()
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        hideControlsTask = null
    }

    // ---------------- 进入 / 退出 ----------------

    private fun enter() {
        savedLineHighlight = binding.editor.currentLineHighlightEnabled
        binding.editor.currentLineHighlightEnabled = false
        binding.editor.isInteractionDisabled = true
        binding.editor.clearFocus()
        hideKeyboard()

        binding.statusBarFill.visibility = View.GONE
        binding.navigationBarFill.visibility = View.GONE
        binding.topBar.visibility = View.GONE
        binding.tabScroll.visibility = View.GONE
        binding.statusBar.visibility = View.GONE
        binding.tvLineNumbers.visibility = View.GONE
        binding.fastScrollBar.visibility = View.GONE
        binding.immersiveLayer.visibility = View.VISIBLE

        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setSystemBarsHidden(true)

        controlsVisible = false
        showControls(autoHide = true)
        startProgressUpdates()
        binding.root.post { updateProgress() }
    }

    private fun exit() {
        handler.removeCallbacks(progressRunnable)
        hideControlsTask?.let { handler.removeCallbacks(it) }
        hideControlsTask = null

        binding.editor.isInteractionDisabled = false
        binding.editor.currentLineHighlightEnabled = savedLineHighlight
        binding.immersiveControls.animate().cancel()
        binding.immersiveControls.visibility = View.VISIBLE
        binding.immersiveControls.alpha = 1f
        binding.immersiveLayer.visibility = View.GONE
        val fillLp = binding.immersiveProgressFill.layoutParams
        fillLp.width = 0
        binding.immersiveProgressFill.layoutParams = fillLp

        binding.statusBarFill.visibility = View.VISIBLE
        binding.navigationBarFill.visibility = View.VISIBLE
        binding.topBar.visibility = View.VISIBLE
        binding.tabScroll.visibility = View.VISIBLE
        binding.statusBar.visibility = View.VISIBLE
        binding.fastScrollBar.visibility = View.VISIBLE
        binding.tvLineNumbers.visibility =
            if (canShowLineNumbers()) View.VISIBLE else View.GONE

        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setSystemBarsHidden(false)
    }

    private fun setSystemBarsHidden(hidden: Boolean) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (hidden) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            ThemeColorHelper.applySystemBars(activity)
        }
    }

    private fun hideKeyboard() {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
    }

    // ---------------- 手势 ----------------

    private fun handleTap(x: Float) {
        val width = binding.immersiveTouchLayer.width
        if (width <= 0) return
        when {
            x < width * 0.3f -> page(-1)
            x > width * 0.7f -> page(1)
            else -> if (controlsVisible) hideControls() else showControls(autoHide = true)
        }
    }

    private fun page(direction: Int) {
        if (!isImmersive) return
        val distance = if (isLargeFileMode()) {
            binding.largeTextView.getVisibleHeight()
        } else {
            val editor = binding.editor
            (editor.height - editor.paddingTop - editor.paddingBottom).coerceAtLeast(1)
        }
        val step = (distance * 0.9f).toInt().coerceAtLeast(1)
        scrollContent(direction * step)
        showControls(autoHide = true)
    }

    private fun scrollContent(dy: Int) {
        if (!isImmersive || dy == 0) return
        if (isLargeFileMode()) {
            val large = binding.largeTextView
            val target = (large.getCurrentScrollY() + dy).coerceIn(0, large.getMaxScrollY())
            large.scrollToY(target)
        } else {
            val editor = binding.editor
            val layout = editor.layout ?: return
            val viewport =
                (editor.height - editor.paddingTop - editor.paddingBottom).coerceAtLeast(1)
            val max = (layout.height - viewport).coerceAtLeast(0)
            editor.scrollTo(0, (editor.scrollY + dy).coerceIn(0, max))
        }
        updateProgress()
    }

    // ---------------- 控制条与进度 ----------------

    private fun showControls(autoHide: Boolean) {
        controlsVisible = true
        hideControlsTask?.let { handler.removeCallbacks(it) }
        binding.immersiveControls.animate().cancel()
        binding.immersiveControls.visibility = View.VISIBLE
        binding.immersiveControls.alpha = 1f
        if (autoHide) {
            hideControlsTask = Runnable { hideControls() }
            handler.postDelayed(hideControlsTask!!, 4000L)
        }
    }

    private fun hideControls() {
        if (!isImmersive || !controlsVisible) return
        controlsVisible = false
        hideControlsTask?.let { handler.removeCallbacks(it) }
        hideControlsTask = null
        if (prefs.einkMode) {
            binding.immersiveControls.visibility = View.GONE
        } else {
            binding.immersiveControls.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction {
                    if (!controlsVisible) binding.immersiveControls.visibility = View.GONE
                }
                .start()
        }
    }

    private fun startProgressUpdates() {
        handler.removeCallbacks(progressRunnable)
        handler.post(progressRunnable)
    }

    private fun updateProgress() {
        if (!isImmersive) return

        val total: Int
        val current: Int
        val fraction: Float

        if (isLargeFileMode()) {
            val large = binding.largeTextView
            total = large.getLineCount()
            current = if (total <= 0) 0 else large.getFirstVisibleLine() + 1
            val max = large.getMaxScrollY()
            fraction = when {
                total <= 0 -> 0f
                max <= 0 -> 1f
                else -> large.getCurrentScrollY().toFloat() / max
            }
        } else {
            val editor = binding.editor
            val layout = editor.layout ?: return
            total = layout.lineCount
            val viewport =
                (editor.height - editor.paddingTop - editor.paddingBottom).coerceAtLeast(1)
            val max = (layout.height - viewport).coerceAtLeast(0)
            current = layout.getLineForVertical(
                (editor.scrollY + editor.paddingTop).coerceAtLeast(0)
            ) + 1
            fraction = if (max <= 0) 1f else editor.scrollY.toFloat() / max
        }

        val percent = (fraction * 100f).roundToInt().coerceIn(0, 100)
        binding.tvImmersiveProgress.text = activity.getString(
            R.string.immersive_progress,
            percent,
            formatNumber(current),
            formatNumber(total)
        )

        val trackWidth = binding.immersiveProgressTrack.width
        if (trackWidth > 0) {
            val lp = binding.immersiveProgressFill.layoutParams
            lp.width = (trackWidth * fraction).toInt().coerceIn(0, trackWidth)
            binding.immersiveProgressFill.layoutParams = lp
        }
    }

    private fun formatNumber(value: Int): String =
        String.format(Locale.US, "%,d", value)

    private companion object {
        const val AXIS_UNKNOWN = 0
        const val AXIS_VERTICAL = 1
        const val AXIS_HORIZONTAL = 2
    }
}
