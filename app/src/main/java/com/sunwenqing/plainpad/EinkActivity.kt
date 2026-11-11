package com.sunwenqing.plainpad

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity

/**
 * 所有界面的基类：开启墨水屏模式时先合并黑白主题，再在 setContentView 之后
 * 统一去掉卡片/按钮的圆角与阴影。
 *
 * 主题必须在 super.onCreate 之前合并，否则 AppCompat 已经按旧主题初始化过一部分控件；
 * 而圆角/阴影是布局里写死的属性，只能等视图树创建完再改。
 */
open class EinkActivity : AppCompatActivity() {

    private var eink = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // 在 super.onCreate 之前读取，onCreate 里再去改主题就晚了
        eink = Prefs(this).einkMode
        if (eink) {
            theme.applyStyle(R.style.ThemeOverlay_PlainPad_Eink, true)
        }
        super.onCreate(savedInstanceState)
        if (eink) {
            window.decorView.setBackgroundColor(Color.WHITE)
        }
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        styleContentIfEink()
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        styleContentIfEink()
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        styleContentIfEink()
    }

    private fun styleContentIfEink() {
        if (!eink) return
        EinkStyler.apply(findViewById(android.R.id.content))
    }
}
