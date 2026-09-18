package com.isivoltpro.maginaolivo.core.common

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>

    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    inline fun <R> fold(
        onSuccess: (T) -> R,
        onFailure: (AppError) -> R,
    ): R = when (this) {
        is Success -> onSuccess(value)
        is Failure -> onFailure(error)
    }
}
