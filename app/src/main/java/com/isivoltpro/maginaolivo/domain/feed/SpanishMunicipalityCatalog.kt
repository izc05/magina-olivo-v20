package com.isivoltpro.maginaolivo.domain.feed

import java.text.Normalizer

/**
 * Official INE 2026 province or autonomous city (codes `01`..`52`).
 */
data class IneProvince(
    val code: String,
    val name: String,
)

/**
 * Official INE 2026 municipality entry (`ineCode` = 5 digits: 2-digit province + 3-digit municipality).
 */
data class IneMunicipality(
    val ineCode: String,
    val officialName: String,
    val displayName: String,
    val provinceCode: String,
    val provinceName: String,
) {
    fun toFeedLocation(): FeedLocation = FeedLocation(
        municipality = displayName,
        province = provinceName,
        ineCode = ineCode,
    )
}

/**
 * Offline directory of the official 2026 INE municipality catalog across all 50 provinces,
 * Ceuta and Melilla (`ine/ine_municipios_2026.tsv`).
 *
 * Supports accent-insensitive, case-insensitive, and article-insensitive search and resolution
 * (e.g. `"Carolina, La"` <-> `"La Carolina"`, `"Ejido, El"` <-> `"El Ejido"`, `"Bedmar"` -> `"Bedmar y Garcíez"`),
 * and disambiguates homonymous municipalities by province.
 */
object SpanishMunicipalityCatalog {

    private const val RESOURCE_PATH = "ine/ine_municipios_2026.tsv"

    private val TRAILING_ARTICLE_REGEX =
        Regex("^(.*),\\s*(el|la|los|las|l'|o|a|os|as|es|sa|ses)$", RegexOption.IGNORE_CASE)

    private val LEADING_ARTICLE_REGEX =
        Regex("^(el|la|los|las|l'|o|a|os|as|es|sa|ses)\\s+(.*)$", RegexOption.IGNORE_CASE)

    val provinces: List<IneProvince> = listOf(
        IneProvince("01", "Araba/Álava"),
        IneProvince("02", "Albacete"),
        IneProvince("03", "Alicante"),
        IneProvince("04", "Almería"),
        IneProvince("05", "Ávila"),
        IneProvince("06", "Badajoz"),
        IneProvince("07", "Illes Balears"),
        IneProvince("08", "Barcelona"),
        IneProvince("09", "Burgos"),
        IneProvince("10", "Cáceres"),
        IneProvince("11", "Cádiz"),
        IneProvince("12", "Castellón"),
        IneProvince("13", "Ciudad Real"),
        IneProvince("14", "Córdoba"),
        IneProvince("15", "A Coruña"),
        IneProvince("16", "Cuenca"),
        IneProvince("17", "Girona"),
        IneProvince("18", "Granada"),
        IneProvince("19", "Guadalajara"),
        IneProvince("20", "Gipuzkoa"),
        IneProvince("21", "Huelva"),
        IneProvince("22", "Huesca"),
        IneProvince("23", "Jaén"),
        IneProvince("24", "León"),
        IneProvince("25", "Lleida"),
        IneProvince("26", "La Rioja"),
        IneProvince("27", "Lugo"),
        IneProvince("28", "Madrid"),
        IneProvince("29", "Málaga"),
        IneProvince("30", "Murcia"),
        IneProvince("31", "Navarra"),
        IneProvince("32", "Ourense"),
        IneProvince("33", "Asturias"),
        IneProvince("34", "Palencia"),
        IneProvince("35", "Las Palmas"),
        IneProvince("36", "Pontevedra"),
        IneProvince("37", "Salamanca"),
        IneProvince("38", "Santa Cruz de Tenerife"),
        IneProvince("39", "Cantabria"),
        IneProvince("40", "Segovia"),
        IneProvince("41", "Sevilla"),
        IneProvince("42", "Soria"),
        IneProvince("43", "Tarragona"),
        IneProvince("44", "Teruel"),
        IneProvince("45", "Toledo"),
        IneProvince("46", "Valencia"),
        IneProvince("47", "Valladolid"),
        IneProvince("48", "Bizkaia"),
        IneProvince("49", "Zamora"),
        IneProvince("50", "Zaragoza"),
        IneProvince("51", "Ceuta"),
        IneProvince("52", "Melilla"),
    ).sortedBy { normalize(it.name) }

    private val provinceNameByCode: Map<String, String> = provinces.associate { it.code to it.name }

    private val provinceCodeAliases: Map<String, String> = buildMap {
        provinces.forEach { prov ->
            put(normalize(prov.name), prov.code)
            prov.name.split("/").map { normalize(it) }.filter { it.isNotEmpty() }.forEach { alias ->
                put(alias, prov.code)
            }
        }
        put("alava", "01")
        put("araba", "01")
        put("alacant", "03")
        put("alicante alacant", "03")
        put("baleares", "07")
        put("islas baleares", "07")
        put("balears illes", "07")
        put("castello", "12")
        put("castellon castello", "12")
        put("la coruna", "15")
        put("coruna", "15")
        put("coruna a", "15")
        put("gerona", "17")
        put("guipuzcoa", "20")
        put("lerida", "25")
        put("rioja", "26")
        put("rioja la", "26")
        put("nafarroa", "31")
        put("orense", "32")
        put("palmas las", "35")
        put("tenerife", "38")
        put("valencia valencia", "46")
        put("vizcaya", "48")
    }

    private data class IndexedMunicipality(
        val entry: IneMunicipality,
        val normalizedVariants: Set<String>,
        val normalizedCoreWithoutArticle: String,
        val normalizedSortKey: String,
    )

    private val indexedList: List<IndexedMunicipality> by lazy { loadCatalog() }
    private val byCode: Map<String, IneMunicipality> by lazy {
        indexedList.associate { it.entry.ineCode to it.entry }
    }
    private val byProvinceCode: Map<String, List<IndexedMunicipality>> by lazy {
        indexedList.groupBy { it.entry.provinceCode }
    }

    /** Total number of official municipalities loaded from the 2026 INE catalog. */
    val size: Int get() = indexedList.size

    /** All official municipalities in a given province (`01`..`52`). */
    fun municipalitiesInProvince(provinceCode: String): List<IneMunicipality> =
        byProvinceCode[provinceCode.trim()].orEmpty().map { it.entry }

    /** Exact lookup by 5-digit official INE municipality code (e.g. `"23902"`). */
    fun findByCode(ineCode: String?): IneMunicipality? {
        val code = ineCode?.trim() ?: return null
        if (code.length != 5 || !code.all(Char::isDigit)) return null
        return byCode[code]
    }

    /** Resolves a province name or 2-digit code to an [IneProvince], or `null` if unknown. */
    fun resolveProvince(province: String?): IneProvince? {
        val raw = province?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val code = if (raw.length == 2 && raw.all(Char::isDigit)) {
            raw
        } else {
            provinceCodeAliases[normalize(raw)]
        } ?: return null
        val name = provinceNameByCode[code] ?: return null
        return IneProvince(code, name)
    }

    /**
     * Resolves a municipality (by 5-digit `ineCode`, or by `municipality` and optional `province`)
     * when it matches a single unambiguous municipality in the official 2026 INE directory.
     *
     * Never guesses when multiple municipalities share the same name across provinces and no
     * province is given.
     */
    fun resolve(
        municipality: String?,
        province: String? = null,
        ineCode: String? = null,
    ): IneMunicipality? {
        findByCode(ineCode)?.let { return it }

        val wanted = municipality?.let(::normalize)?.takeIf { it.isNotEmpty() } ?: return null
        val wantedWithoutArticle = stripLeadingArticle(wanted)
        val provCode = resolveProvince(province)?.code
        // If a province was explicitly provided by the caller but is not a valid Spanish province, fail fast.
        if (!province.isNullOrBlank() && provCode == null) return null

        val pool = if (provCode != null) {
            byProvinceCode[provCode].orEmpty()
        } else {
            indexedList
        }

        // 1. Exact match against any normalized variant (official, inverted article, bilingual slash part)
        val exact = pool.filter { wanted in it.normalizedVariants }
        if (exact.size == 1) return exact.single().entry
        if (exact.size > 1) return null

        // 2. Match ignoring leading article ("Carolina" -> "La Carolina", when unambiguous in pool)
        if (wantedWithoutArticle.isNotEmpty()) {
            val articleMatch = pool.filter { it.normalizedCoreWithoutArticle == wantedWithoutArticle }
            if (articleMatch.size == 1) return articleMatch.single().entry
            if (articleMatch.size > 1) return null
        }

        // 3. Word-boundary prefix match ("Bedmar" -> "Bedmar y Garcíez", when unambiguous in pool)
        val prefix = pool.filter { candidate ->
            candidate.normalizedVariants.any { v -> v.startsWith("$wanted ") }
        }
        if (prefix.size == 1) return prefix.single().entry
        return null
    }

    /**
     * Offline search across the 2026 INE directory, optionally filtered by `provinceCode` (`01`..`52`).
     * Ignores accents, case, punctuation, and leading/trailing articles.
     */
    fun search(
        query: String = "",
        provinceCode: String? = null,
        limit: Int = 60,
    ): List<IneMunicipality> {
        val cleanProvince = provinceCode?.trim()?.takeIf { it.isNotEmpty() }
        val pool = if (cleanProvince != null) {
            byProvinceCode[cleanProvince].orEmpty()
        } else {
            indexedList
        }
        val q = normalize(query)
        if (q.isEmpty()) {
            return pool.asSequence().map { it.entry }.take(limit.coerceAtLeast(1)).toList()
        }
        val qCore = stripLeadingArticle(q)
        val qTokens = q.split(" ").filter { it.isNotEmpty() }

        return pool.asSequence()
            .mapNotNull { candidate ->
                val rank = matchRank(candidate, q, qCore, qTokens) ?: return@mapNotNull null
                rank to candidate
            }
            .sortedWith(
                compareBy<Pair<Int, IndexedMunicipality>> { it.first }
                    .thenBy { it.second.normalizedSortKey }
                    .thenBy { it.second.entry.provinceName },
            )
            .map { it.second.entry }
            .take(limit.coerceAtLeast(1))
            .toList()
    }

    private fun matchRank(
        candidate: IndexedMunicipality,
        q: String,
        qCore: String,
        qTokens: List<String>,
    ): Int? {
        if (candidate.entry.ineCode == q) return 0
        if (q in candidate.normalizedVariants) return 1
        if (qCore.isNotEmpty() && candidate.normalizedCoreWithoutArticle == qCore) return 2
        if (candidate.normalizedVariants.any { it.startsWith("$q ") }) return 3
        if (candidate.normalizedVariants.any { it.startsWith(q) }) return 4
        if (qCore.isNotEmpty() && candidate.normalizedCoreWithoutArticle.startsWith(qCore)) return 5
        if (candidate.normalizedVariants.any { variant ->
                qTokens.all { token -> variant.contains(token) }
            }
        ) {
            return 6
        }
        return null
    }

    /**
     * Converts INE inverted article format (`"Carolina, La"`, `"Ejido, El"`, `"Bollullos Par del Condado"`)
     * into natural display order (`"La Carolina"`, `"El Ejido"`).
     */
    internal fun toDisplayName(officialName: String): String {
        val trimmed = officialName.trim()
        val match = TRAILING_ARTICLE_REGEX.matchEntire(trimmed) ?: return trimmed
        val body = match.groupValues[1].trim()
        val article = match.groupValues[2].trim()
        return if (article.endsWith("'")) {
            "$article$body"
        } else {
            "$article $body"
        }
    }

    internal fun normalize(text: String): String =
        Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9ñ/ ]+"), " ")
            .replace("/", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun stripLeadingArticle(normalizedText: String): String {
        val match = LEADING_ARTICLE_REGEX.matchEntire(normalizedText) ?: return normalizedText
        return match.groupValues[2].trim()
    }

    private fun buildVariants(officialName: String, displayName: String): Set<String> = buildSet {
        add(normalize(officialName))
        add(normalize(displayName))
        officialName.split("/").map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
            val dispPart = toDisplayName(part)
            add(normalize(part))
            add(normalize(dispPart))
        }
    }

    private fun loadCatalog(): List<IndexedMunicipality> {
        val stream = SpanishMunicipalityCatalog::class.java.classLoader?.getResourceAsStream(RESOURCE_PATH)
            ?: ClassLoader.getSystemResourceAsStream(RESOURCE_PATH)
            ?: error("Missing $RESOURCE_PATH in resources")

        return stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) return@mapNotNull null
                val ineCode = line.substring(0, tab).trim()
                val officialName = line.substring(tab + 1).trim()
                if (ineCode.length != 5 || officialName.isEmpty()) return@mapNotNull null
                val provinceCode = ineCode.substring(0, 2)
                val provinceName = provinceNameByCode[provinceCode] ?: return@mapNotNull null
                val displayName = toDisplayName(officialName)
                val entry = IneMunicipality(
                    ineCode = ineCode,
                    officialName = officialName,
                    displayName = displayName,
                    provinceCode = provinceCode,
                    provinceName = provinceName,
                )
                val variants = buildVariants(officialName, displayName)
                val normDisplay = normalize(displayName)
                IndexedMunicipality(
                    entry = entry,
                    normalizedVariants = variants,
                    normalizedCoreWithoutArticle = stripLeadingArticle(normDisplay),
                    normalizedSortKey = normDisplay,
                )
            }.sortedWith(
                compareBy<IndexedMunicipality> { it.normalizedSortKey }
                    .thenBy { it.entry.provinceName },
            ).toList()
        }
    }
}

