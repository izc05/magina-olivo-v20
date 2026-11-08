package com.isivoltpro.maginaolivo.domain.assistant

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import java.util.UUID

/**
 * Proveedor simulado (MOCK) de Mistral AI con especialización agrícola y consultas conversacionales ampliadas:
 * - Kilos recogidos por campaña.
 * - Gastos detallados por jornales y categorías.
 * - Fincas y parcelas de regadío.
 * - Trabajos pendientes en el cuaderno de campo.
 * - Desambiguación interactiva de fincas ante preguntas ambiguas.
 */
class SimulatedMistralInferenceProvider : AssistantInferenceProvider {
    override val providerId: String = "mistral-agricultural-mock-engine"

    override suspend fun generateResponse(
        request: AssistantInferenceRequest,
        toolsService: AssistantToolsService
    ): AppResult<AssistantInferenceResponse> {
        val prompt = request.userPrompt.trim()
        val context = request.securityContext

        // 1. Clasificación determinista previa al LLM (Barrera de Dominio Agrario)
        val classification = AgriculturalDomainClassifier.classify(prompt)
        if (!classification.isAgricultural) {
            return AppResult.Success(
                AssistantInferenceResponse(
                    text = classification.rejectionMessage
                        ?: "Mágina IA solo atiende consultas sobre agricultura, olivar y gestión de su explotación.",
                    toolsCalled = emptyList(),
                    promptTokens = 25,
                    completionTokens = 35,
                    isMock = true
                )
            )
        }

        val lower = prompt.lowercase()

        // 2. Ejecución determinista de Tool Calling respetando AssistantSecurityContext
        return try {
            when {
                // Desambiguación requerida ante pregunta ambigua sobre una finca no identificada
                (lower.contains("esta finca") || lower.contains("en la finca")) && (lower.contains("labor") || lower.contains("trabajo") || lower.contains("que tengo") || lower.contains("qué tengo")) -> {
                    if (!context.hasPermission(AssistantPermission.VIEW_FARMS)) {
                        return AppResult.Failure(AppError.Validation("security", "Permiso denegado: VIEW_FARMS requerido"))
                    }
                    val farmsResult = toolsService.getAuthorizedFarms(context)
                    when (farmsResult) {
                        is AppResult.Success -> {
                            val farms = farmsResult.value.first
                            val reply = if (farms.size > 1) {
                                val options = farms.joinToString { it.name }
                                "Tienes varias fincas registradas ($options). Por favor, indícame de qué finca en concreto deseas la información."
                            } else {
                                "Consultando información de tu explotación..."
                            }
                            AppResult.Success(
                                AssistantInferenceResponse(
                                    text = reply,
                                    toolsCalled = listOf("get_authorized_farms"),
                                    promptTokens = 100,
                                    completionTokens = 60,
                                    isMock = true
                                )
                            )
                        }
                        is AppResult.Failure -> AppResult.Failure(farmsResult.error)
                    }
                }

                // Consulta específica: Fincas de regadío
                lower.contains("regadío") || lower.contains("riego") -> {
                    if (!context.hasPermission(AssistantPermission.VIEW_FARMS)) {
                        return AppResult.Failure(AppError.Validation("security", "Permiso denegado: VIEW_FARMS requerido"))
                    }
                    val farmsResult = toolsService.getAuthorizedFarms(context)
                    when (farmsResult) {
                        is AppResult.Success -> {
                            val farms = farmsResult.value.first
                            val reply = if (farms.isEmpty()) {
                                "[DATO REGISTRADO]: No tienes fincas registradas en esta explotación."
                            } else {
                                val listStr = farms.joinToString(separator = "\n") { "- ${it.name} (${it.municipality ?: "Término no especificado"})" }
                                "[DATO REGISTRADO]: Tus fincas autorizadas con parcelas de regadío registradas son:\n$listStr\n(Fuente: ${farmsResult.value.second.origin})"
                            }
                            AppResult.Success(
                                AssistantInferenceResponse(
                                    text = reply,
                                    toolsCalled = listOf("get_authorized_farms"),
                                    promptTokens = 110,
                                    completionTokens = 70,
                                    isMock = true
                                )
                            )
                        }
                        is AppResult.Failure -> AppResult.Failure(farmsResult.error)
                    }
                }

                // Consulta específica: Trabajos o labores pendientes en cuaderno de campo
                lower.contains("trabajo") || lower.contains("pendiente") || lower.contains("labor") -> {
                    if (!context.hasPermission(AssistantPermission.VIEW_NOTEBOOK)) {
                        return AppResult.Failure(AppError.Validation("security", "Permiso denegado: VIEW_NOTEBOOK requerido"))
                    }
                    val notebookResult = toolsService.getNotebookActivities(context, null)
                    when (notebookResult) {
                        is AppResult.Success -> {
                            val activities = notebookResult.value.first
                            val reply = if (activities.isEmpty()) {
                                "[DATO REGISTRADO]: No tienes trabajos pendientes anotados en el cuaderno de campo."
                            } else {
                                val listStr = activities.joinToString(separator = "\n") {
                                    "- [${it.dateIso}] ${it.title} (${it.activityType})"
                                }
                                "[DATO REGISTRADO]: Tienes los siguientes trabajos anotados en el cuaderno de campo:\n$listStr"
                            }
                            AppResult.Success(
                                AssistantInferenceResponse(
                                    text = reply,
                                    toolsCalled = listOf("get_notebook_activities"),
                                    promptTokens = 115,
                                    completionTokens = 80,
                                    isMock = true
                                )
                            )
                        }
                        is AppResult.Failure -> AppResult.Failure(notebookResult.error)
                    }
                }

                // Consulta específica: Gastos en jornales o general
                lower.contains("jornal") -> {
                    if (!context.hasPermission(AssistantPermission.VIEW_EXPENSES)) {
                        return AppResult.Failure(AppError.Validation("security", "Permiso denegado: VIEW_EXPENSES requerido"))
                    }
                    val campaignId = context.selectedCampaignId ?: UUID.fromString("00000000-0000-0000-0000-000000000001")
                    val expenseResult = toolsService.getExpenseSummary(context, campaignId)
                    when (expenseResult) {
                        is AppResult.Success -> {
                            val exp = expenseResult.value.first
                            val labourEuros = exp.labourCostsCents / 100.0
                            val reply = "[CÁLCULO DETERMINISTA]: Has gastado un total de %.2f € en concepto de mano de obra y jornales para la campaña actual (Fuente: ${expenseResult.value.second.origin}).".format(labourEuros)
                            AppResult.Success(
                                AssistantInferenceResponse(
                                    text = reply,
                                    toolsCalled = listOf("get_expense_summary"),
                                    promptTokens = 90,
                                    completionTokens = 55,
                                    isMock = true
                                )
                            )
                        }
                        is AppResult.Failure -> AppResult.Failure(expenseResult.error)
                    }
                }

                // Consulta de fincas general con desambiguación si hay varias y pregunta ambigua
                lower.contains("finca") || lower.contains("olivares") -> {
                    if (!context.hasPermission(AssistantPermission.VIEW_FARMS)) {
                        return AppResult.Failure(AppError.Validation("security", "Permiso denegado: VIEW_FARMS requerido"))
                    }
                    val farmsResult = toolsService.getAuthorizedFarms(context)
                    when (farmsResult) {
                        is AppResult.Success -> {
                            val farms = farmsResult.value.first
                            val reply = when {
                                farms.isEmpty() -> "[DATO REGISTRADO]: No se encontraron fincas activas en su explotación autorizada."
                                lower.contains("esta finca") || lower.contains("en la finca") -> {
                                    // Desambiguación requerida ante pregunta ambigua sobre una finca no identificada
                                    val options = farms.joinToString { it.name }
                                    "Tienes varias fincas registradas ($options). Por favor, indícame de qué finca en concreto deseas la información."
                                }
                                else -> {
                                    val listStr = farms.joinToString(separator = "\n") {
                                        "- ${it.name} (${it.municipality ?: "Sin término"}) — ${it.oliveTreesCount ?: 0} olivos"
                                    }
                                    "[DATO REGISTRADO]: He consultado sus fincas autorizadas:\n$listStr\n(Fuente: ${farmsResult.value.second.origin})"
                                }
                            }
                            AppResult.Success(
                                AssistantInferenceResponse(
                                    text = reply,
                                    toolsCalled = listOf("get_authorized_farms"),
                                    promptTokens = 120,
                                    completionTokens = 85,
                                    isMock = true
                                )
                            )
                        }
                        is AppResult.Failure -> AppResult.Failure(farmsResult.error)
                    }
                }

                lower.contains("entrega") || lower.contains("kilo") || lower.contains("cosecha") -> {
                    if (!context.hasPermission(AssistantPermission.VIEW_DELIVERIES)) {
                        return AppResult.Failure(AppError.Validation("security", "Permiso denegado: VIEW_DELIVERIES requerido"))
                    }
                    val campaignId = context.selectedCampaignId ?: UUID.fromString("00000000-0000-0000-0000-000000000001")
                    val deliveryResult = toolsService.getDeliverySummary(context, campaignId)
                    when (deliveryResult) {
                        is AppResult.Success -> {
                            val summary = deliveryResult.value.first
                            val reply = "[DATO REGISTRADO & CÁLCULO]: Resumen de entregas de campaña:\n" +
                                    "- Total entregas: ${summary.totalDeliveries}\n" +
                                    "- Kilos netos totales recogidos: ${summary.totalKilos} kg\n" +
                                    "- Confirmados en almazara: ${summary.confirmedKilos} kg\n" +
                                    "- Pendientes de sincronización: ${summary.pendingSyncKilos} kg"
                            AppResult.Success(
                                AssistantInferenceResponse(
                                    text = reply,
                                    toolsCalled = listOf("get_delivery_summary"),
                                    promptTokens = 95,
                                    completionTokens = 65,
                                    isMock = true
                                )
                            )
                        }
                        is AppResult.Failure -> AppResult.Failure(deliveryResult.error)
                    }
                }

                lower.contains("repilo") || lower.contains("mosca") || lower.contains("fitosanitario") || lower.contains("tratamiento") -> {
                    val reply = "[RECOMENDACIÓN TÉCNICA AGRONÓMICA]: Para el control preventivo en olivar contra repilo (Spilocaea oleagina) o mosca del olivo (Bactrocera oleae), es clave monitorizar las condiciones de humedad relativa y temperatura en la comarca de Sierra Mágina antes de intervenir." +
                            AgriculturalSystemPrompt.OFFICIAL_DISCLAIMER_PHYTOSANITARY
                    AppResult.Success(
                        AssistantInferenceResponse(
                            text = reply,
                            toolsCalled = emptyList(),
                            promptTokens = 130,
                            completionTokens = 90,
                            isMock = true
                        )
                    )
                }

                lower.contains("tiempo") || lower.contains("clima") || lower.contains("lluvia") -> {
                    if (!context.hasPermission(AssistantPermission.VIEW_WEATHER)) {
                        return AppResult.Failure(AppError.Validation("security", "Permiso denegado: VIEW_WEATHER requerido"))
                    }
                    val weatherResult = toolsService.getWeatherForecast(context, null)
                    when (weatherResult) {
                        is AppResult.Success -> {
                            val weather = weatherResult.value.first
                            val reply = "[DATO METEOROLÓGICO OFICIAL - ${weather.provider}]: Previsión (${weather.municipality}):\n" +
                                    "- Temperaturas: Mín ${weather.tempMin}°C / Máx ${weather.tempMax}°C\n" +
                                    "- Probabilidad de lluvia: ${weather.rainProbabilityPercent}%\n" +
                                    "[RECOMENDACIÓN AGRONÓMICA]: ${weather.notice}"
                            AppResult.Success(
                                AssistantInferenceResponse(
                                    text = reply,
                                    toolsCalled = listOf("get_weather_forecast"),
                                    promptTokens = 85,
                                    completionTokens = 65,
                                    isMock = true
                                )
                            )
                        }
                        is AppResult.Failure -> AppResult.Failure(weatherResult.error)
                    }
                }

                lower.contains("gasto") || lower.contains("economía") || lower.contains("coste") -> {
                    if (!context.hasPermission(AssistantPermission.VIEW_EXPENSES)) {
                        return AppResult.Failure(AppError.Validation("security", "Permiso denegado: VIEW_EXPENSES requerido"))
                    }
                    val campaignId = context.selectedCampaignId ?: UUID.fromString("00000000-0000-0000-0000-000000000001")
                    val expenseResult = toolsService.getExpenseSummary(context, campaignId)
                    when (expenseResult) {
                        is AppResult.Success -> {
                            val exp = expenseResult.value.first
                            val totalEuros = exp.totalExpenseCents / 100.0
                            val reply = "[CÁLCULO DETERMINISTA]: Gastos registrados en campaña:\n" +
                                    "- Total acumulado: %.2f €\n".format(totalEuros) +
                                    "- Recolección: %.2f €\n".format(exp.harvestCostsCents / 100.0) +
                                    "- Maquinaria y carburante: %.2f €\n".format(exp.machineryCostsCents / 100.0) +
                                    "- Jornales: %.2f €".format(exp.labourCostsCents / 100.0)
                            AppResult.Success(
                                AssistantInferenceResponse(
                                    text = reply,
                                    toolsCalled = listOf("get_expense_summary"),
                                    promptTokens = 110,
                                    completionTokens = 75,
                                    isMock = true
                                )
                            )
                        }
                        is AppResult.Failure -> AppResult.Failure(expenseResult.error)
                    }
                }

                else -> {
                    // Consulta agraria general o saludo contextual
                    AppResult.Success(
                        AssistantInferenceResponse(
                            text = "Hola. Soy Mágina IA, tu asesor técnico agrícola especializado en olivar y cultivos de Sierra Mágina. Puedes consultarme por tus fincas, entregas en cooperativa, fitosanitarios, meteorología o gastos de explotación.",
                            toolsCalled = emptyList(),
                            promptTokens = 45,
                            completionTokens = 35,
                            isMock = true
                        )
                    )
                }
            }
        } catch (ex: Exception) {
            AppResult.Failure(AppError.Validation("inference", ex.message ?: "Error en inferencia simulada"))
        }
    }
}
