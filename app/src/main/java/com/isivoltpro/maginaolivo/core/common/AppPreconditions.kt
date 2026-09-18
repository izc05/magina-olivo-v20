package com.isivoltpro.maginaolivo.core.common

object AppPreconditions {
    fun nonBlank(
        value: String,
        field: String,
    ): AppResult<String> {
        val normalized = value.trim()
        return if (normalized.isEmpty()) {
            AppResult.Failure(
                AppError.Validation(
                    field = field,
                    code = "required",
                ),
            )
        } else {
            AppResult.Success(normalized)
        }
    }
}
