package com.isivoltpro.maginaolivo.core.common

sealed interface AppError {
    data class Validation(
        val field: String? = null,
        val code: String,
        val cause: Throwable? = null,
    ) : AppError

    data class NotFound(
        val resource: String,
        val cause: Throwable? = null,
    ) : AppError

    data class Conflict(
        val resource: String? = null,
        val cause: Throwable? = null,
    ) : AppError

    data class Permission(
        val operation: String? = null,
        val cause: Throwable? = null,
    ) : AppError

    data class Offline(
        val cause: Throwable? = null,
    ) : AppError

    data class Storage(
        val operation: String? = null,
        val cause: Throwable? = null,
    ) : AppError

    data class Remote(
        val operation: String? = null,
        val cause: Throwable? = null,
    ) : AppError

    data class Unknown(
        val cause: Throwable? = null,
    ) : AppError
}
