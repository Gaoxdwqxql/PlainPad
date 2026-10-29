package com.sunwenqing.plainpad

import android.app.Application
import android.os.Process

class PlainPadApp : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CrashLogger.record(this, thread, throwable)
            if (previous != null) {
                // 交还系统默认处理：弹崩溃对话框并结束进程
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }
}
