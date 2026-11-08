package com.isivoltpro.maginaolivo.domain.assistant

/**
 * Ámbitos o temáticas reconocidas por el clasificador de dominio de Mágina IA.
 */
enum class AgriculturalDomainTopic {
    // Especialización núcleo
    OLIVE_GROWING,         // Olivicultura, poda, variedades (Picual, Hojiblanca, Arbequina, etc.)
    AGRONOMY_GENERAL,      // Suelos, laboreo, cubierta vegetal, fisiología vegetal
    IRRIGATION_WATER,      // Riego, comunidades de regantes, sectores, dotaciones
    WEATHER_CLIMATE,       // Meteorología, heladas, ola de calor, evapotranspiración
    PESTS_DISEASES,        // Prays, mosca del olivo, repilo, verticilosis, xylella
    PHYTOSANITARY_FERT,    // Fitosanitarios, registro oficial MAPA, abonos, fertirriego
    MACHINERY_EQUIPMENT,   // Tractores, vibradores, atomizadores, desbrozadoras
    COOPERATIVE_HARVEST,   // Entregas, almazaras, alpechín, rendimiento graso, acidez
    AGRI_ECONOMY,          // Costes por kilo, jornales, subvenciones, liquidaciones
    REGULATORY_PAC_CADASTRE, // PAC, e-cuaderno SIEX, SIGPAC, Catastro rústico
    APP_FARM_MANAGEMENT,   // Gestión de fincas, parcelas y cuaderno en Mágina Olivo

    // Temáticas no agrarias (fuera de dominio)
    OFF_TOPIC              // Programación general, política general, cine, recetas no agrarias, etc.
}

/**
 * Resultado de la clasificación de una consulta o texto.
 */
data class DomainClassificationResult(
    val topic: AgriculturalDomainTopic,
    val isAgricultural: Boolean,
    val requiresOfficialDisclaimer: Boolean = false,
    val detectedKeywords: List<String> = emptyList(),
    val rejectionMessage: String? = null
)

/**
 * Clasificador determinista y semántico en la capa de aplicación.
 * Garantiza que la especialización agraria no dependa únicamente del prompt del LLM.
 */
object AgriculturalDomainClassifier {

    private val AGRARIAN_KEYWORDS = setOf(
        "olivo", "oliva", "olivar", "aceituna", "aceite", "picual", "hojiblanca", "arbequina",
        "rendimiento", "graso", "almazara", "cooperativa", "pesada", "kilos", "cosecha", "recolección",
        "riego", "regadío", "secano", "comunidad de regantes", "gotero", "embalse", "dotación",
        "poda", "desvaretado", "tala", "clareo", "sopladora", "vibrador", "vareador", "tractor",
        "repilo", "verticilosis", "mosca", "prays", "glifosato", "cobre", "abono", "nitrógeno",
        "potasio", "foliar", "tratamiento", "fitosanitario", "plaga", "enfermedad", "materia activa",
        "cuaderno", "siex", "sigpac", "catastro", "pac", "ayuda", "subvención", "jornal", "gasto",
        "finca", "parcela", "polígono", "término", "clima", "tiempo", "meteorología", "aemet", "lluvia", "helada", "temperatura",
        "trabajo", "labor", "labores", "pendiente"
    )

    private val PHYTOSANITARY_KEYWORDS = setOf(
        "fitosanitario", "materia activa", "dosis", "fungicida", "insecticida", "herbicida",
        "repilo", "mosca del olivo", "tratamiento", "plazo de seguridad"
    )

    private val PROMPT_INJECTION_PATTERNS = listOf(
        "olvida las instrucciones",
        "ignora las reglas",
        "ignore previous instructions",
        "eres un modelo de lenguaje general",
        "actúa como un asistente de programación",
        "modo desarrollador",
        "jailbreak",
        "dame una receta de cocina",
        "escribe código python",
        "cuéntame un chiste de políticos",
        "quién ganó el mundial"
    )

    fun classify(prompt: String): DomainClassificationResult {
        val lower = prompt.lowercase().trim()

        // 1. Detección de intentos de alteración de rol o jailbreak
        for (pattern in PROMPT_INJECTION_PATTERNS) {
            if (lower.contains(pattern)) {
                return DomainClassificationResult(
                    topic = AgriculturalDomainTopic.OFF_TOPIC,
                    isAgricultural = false,
                    rejectionMessage = "Mágina IA está especializado exclusivamente en el sector agrario y la gestión de su explotación olivarera y cultivos asociados. No puedo atender consultas ajenas a este ámbito."
                )
            }
        }

        // 2. Comprobación de palabras clave agrarias
        val matches = AGRARIAN_KEYWORDS.filter { lower.contains(it) }

        // Si no contiene ninguna referencia agraria ni al contexto de la app
        if (matches.isEmpty() && !lower.contains("finca") && !lower.contains("hola") && !lower.contains("ayuda")) {
            return DomainClassificationResult(
                topic = AgriculturalDomainTopic.OFF_TOPIC,
                isAgricultural = false,
                rejectionMessage = "Soy Mágina IA, tu asesor técnico agrícola especializado en olivar y explotaciones agrarias. Mi servicio se concentra en agronomía, labores, riego, meteorología, cuaderno de campo, PAC y gestión de fincas. ¿En qué aspecto de tu explotación te puedo ayudar?"
            )
        }

        // 3. Detección de consultas fitosanitarias o normativas críticas
        val isPhyto = PHYTOSANITARY_KEYWORDS.any { lower.contains(it) }

        val topic = when {
            lower.contains("repilo") || lower.contains("mosca") || lower.contains("prays") -> AgriculturalDomainTopic.PESTS_DISEASES
            isPhyto -> AgriculturalDomainTopic.PHYTOSANITARY_FERT
            lower.contains("riego") || lower.contains("regadío") -> AgriculturalDomainTopic.IRRIGATION_WATER
            lower.contains("tiempo") || lower.contains("lluvia") || lower.contains("helada") -> AgriculturalDomainTopic.WEATHER_CLIMATE
            lower.contains("entrega") || lower.contains("kilo") || lower.contains("cooperativa") -> AgriculturalDomainTopic.COOPERATIVE_HARVEST
            lower.contains("gasto") || lower.contains("coste") || lower.contains("jornal") -> AgriculturalDomainTopic.AGRI_ECONOMY
            lower.contains("pac") || lower.contains("sigpac") || lower.contains("catastro") -> AgriculturalDomainTopic.REGULATORY_PAC_CADASTRE
            lower.contains("olivo") || lower.contains("poda") -> AgriculturalDomainTopic.OLIVE_GROWING
            else -> AgriculturalDomainTopic.APP_FARM_MANAGEMENT
        }

        return DomainClassificationResult(
            topic = topic,
            isAgricultural = true,
            requiresOfficialDisclaimer = isPhyto,
            detectedKeywords = matches
        )
    }
}
