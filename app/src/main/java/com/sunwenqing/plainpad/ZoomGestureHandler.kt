package com.sunwenqing.plainpad

import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt

/**
 * 双指缩放：基于两指初始距离比例，实时回调预览，抬手时一次性提交。
 * 与 ScaleGestureDetector 不同，此实现不会因为系统重启手势而闪烁。
 */
class ZoomGestureHandler(
    private val initialFontSizeProvider: () -> Float,
    private val onPreview: (Float) -> Unit,
    private val onCommit: (Int) -> Unit,
    private val minSp: Float = 10f,
    private val maxSp: Float = 40f
) {

    private var isZooming = false
    private var initialDistance = 0f
    private var initialFontSize = 0f
    private var latestFontSize = 0f

    fun onTouch(@Suppress("UNUSED_PARAMETER") v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    isZooming = true
                    initialDistance = distance(event)
                    initialFontSize = initialFontSizeProvider()
                    latestFontSize = initialFontSize
                }
                return isZooming
            }

            MotionEvent.ACTION_MOVE -> {
                if (isZooming && event.pointerCount >= 2 && initialDistance > 1f) {
                    val scale = distance(event) / initialDistance
                    val newSize = (initialFontSize * scale).coerceIn(minSp, maxSp)
                    latestFontSize = newSize
                    onPreview(newSize)
                    return true
                }
                return false
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (isZooming && event.pointerCount <= 2) commit()
                return isZooming
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isZooming) commit()
                return isZooming
            }
        }
        return false
    }

    private fun commit() {
        val finalSize = latestFontSize.toInt().coerceIn(minSp.toInt(), maxSp.toInt())
        onCommit(finalSize)
        isZooming = false
        initialDistance = 0f
    }

    private fun distance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return sqrt(dx * dx + dy * dy)
    }
}