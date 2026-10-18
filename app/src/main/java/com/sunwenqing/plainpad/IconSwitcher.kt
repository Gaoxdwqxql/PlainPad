package com.sunwenqing.plainpad

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object IconSwitcher {

    private const val PKG = "com.sunwenqing.plainpad"

    // 主题色 id 映射到 Manifest 里的 activity-alias
    private val ALIASES = mapOf(
        "system" to "$PKG.icon.Purple",
        "purple" to "$PKG.icon.Purple",
        "blue" to "$PKG.icon.Blue",
        "indigo" to "$PKG.icon.Indigo",
        "teal" to "$PKG.icon.Teal",
        "green" to "$PKG.icon.Green",
        "orange" to "$PKG.icon.Orange",
        "red" to "$PKG.icon.Red",
        "pink" to "$PKG.icon.Pink"
    )

    fun applyIfNeeded(context: Context, colorId: String) {
        val target = ALIASES[colorId] ?: ALIASES["purple"]!!
        val pm = context.packageManager
        val all = ALIASES.values.toSet()

        val current = all.firstOrNull { isEnabled(pm, context, it) }
        if (current == target) return // 已经是目标图标，无需切换

        // 先启用目标
        setEnabled(pm, context, target, true)
        // 禁用其他的
        all.filter { it != target }.forEach { setEnabled(pm, context, it, false) }
    }

    private fun isEnabled(pm: PackageManager, context: Context, alias: String): Boolean {
        return try {
            pm.getComponentEnabledSetting(ComponentName(context, alias)) ==
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } catch (_: Exception) {
            false
        }
    }

    private fun setEnabled(pm: PackageManager, context: Context, alias: String, enabled: Boolean) {
        try {
            pm.setComponentEnabledSetting(
                ComponentName(context, alias),
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {
            // 忽略某些 ROM 的异常
        }
    }
}