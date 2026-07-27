package com.foldclaw.domain.model

/**
 * 统一结果类型。所有工具、Provider 和执行器都返回它，避免异常控制流。
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Failure(val error: DomainError) : Result<Nothing>()

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Failure -> this
    }

    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): DomainError? = (this as? Failure)?.error
}

inline fun <T> success(data: T): Result<T> = Result.Success(data)
fun failure(error: DomainError): Result<Nothing> = Result.Failure(error)
