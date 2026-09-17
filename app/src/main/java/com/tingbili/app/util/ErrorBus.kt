package com.tingbili.app.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 全局错误事件总线：UI 各处可发"轻量错误"消息（Snackbar 显示），
 * 错误可附带 retry 回调（点击 Snackbar action 触发）。
 *
 * 用 SharedFlow 而非 StateFlow：避免重复消费（如退出重进页面）
 * 也避免同一条错误被多个监听器消费后异常累积。
 */
object ErrorBus {
    data class Error(
        val message: String,
        val retry: (() -> Unit)? = null,
        val retryLabel: String = "重试"
    )

    private val _errors = MutableSharedFlow<Error>(extraBufferCapacity = 8)
    val errors: SharedFlow<Error> = _errors.asSharedFlow()

    fun post(message: String, retry: (() -> Unit)? = null, retryLabel: String = "重试") {
        _errors.tryEmit(Error(message, retry, retryLabel))
    }

    fun postFrom(throwable: Throwable, retry: (() -> Unit)? = null) {
        post(throwable.message ?: throwable.javaClass.simpleName, retry)
    }
}