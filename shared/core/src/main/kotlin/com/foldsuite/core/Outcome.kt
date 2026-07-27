package com.foldsuite.core

/**
 * 跨产品可用的轻量结果类型。禁止在此模块放入任何具体产品业务模型。
 */
sealed class Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>()
    data class Err(val message: String, val cause: Throwable? = null) : Outcome<Nothing>()

    inline fun <R> map(transform: (T) -> R): Outcome<R> = when (this) {
        is Ok -> Ok(transform(value))
        is Err -> this
    }

    fun getOrNull(): T? = (this as? Ok)?.value
}
