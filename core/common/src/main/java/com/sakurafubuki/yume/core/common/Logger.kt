package com.sakurafubuki.yume.core.common

import android.util.Log

object Logger {

    @Volatile
    var isDebug: Boolean = true

    fun d(tag: String, message: String) {
        if (isDebug) Log.d(tag, message)
    }

    fun d(tag: String, message: String, tr: Throwable) {
        if (isDebug) Log.d(tag, message, tr)
    }

    fun i(tag: String, message: String) {
        if (isDebug) Log.i(tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    fun w(tag: String, message: String, tr: Throwable) {
        Log.w(tag, message, tr)
    }

    fun e(tag: String, message: String) {
        Log.e(tag, message)
    }

    fun e(tag: String, message: String, tr: Throwable) {
        Log.e(tag, message, tr)
    }
}
