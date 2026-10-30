package com.isivoltpro.maginaolivo.domain.irrigation

import android.content.Context
import androidx.core.content.edit
import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

/**
 * Interface for persisting and retrieving farmer-configured irrigation profiles and punctual turns.
 * Operates purely locally without requiring server roundtrips or destructive Room schema migrations.
 */
interface FarmerIrrigationStore {
    fun getProfile(parcelId: UUID): ParcelIrrigationProfile?
    fun getAllProfiles(): Map<UUID, ParcelIrrigationProfile>
    fun saveProfile(profile: ParcelIrrigationProfile)
    fun removeProfile(parcelId: UUID)
    fun addPunctualTurn(turn: PunctualTurn)
    fun updatePunctualTurn(turn: PunctualTurn)
    fun deletePunctualTurn(parcelId: UUID, turnId: UUID)
    fun clear()

    /**
     * Sets the active identity/workspace scope partition without deleting data from other scopes.
     */
    fun setScope(scopeKey: String)

    /**
     * The currently active scope partition.
     */
    fun currentScope(): String

    /**
     * Retrieves all profiles persisted under a specific scope partition.
     */
    fun getProfilesForScope(scopeKey: String): Map<UUID, ParcelIrrigationProfile>

    /**
     * Explicitly links designated local/legacy parcel configurations to an authenticated user account
     * and workspace partition upon affirmative user authorization.
     *
     * Non-destructive: preserves original local and legacy keys intact.
     * Idempotent: safe to run multiple times without data corruption or duplicate writes.
     *
     * @return count of newly linked parcel profiles.
     */
    fun linkLocalProfilesToAccount(
        targetUserId: String,
        targetWorkspaceId: UUID,
        parcelIdsToLink: Set<UUID>,
        sourceWorkspaceId: UUID? = null,
    ): Int = 0

    @Deprecated("Superseded by explicit authorized linkLocalProfilesToAccount")
    fun claimLegacyProfiles(targetScope: String, claimantUserId: String): Int = 0
}

/**
 * In-memory implementation of [FarmerIrrigationStore] for fast unit and UI testing.
 * Preserves independent partitions across scope switches (A -> B -> A).
 */
class InMemoryFarmerIrrigationStore(initialScope: String = DEFAULT_SCOPE) : FarmerIrrigationStore {
    private var activeScope: String = initialScope
    private var legacyClaimant: String? = null
    private val scopedProfiles = ConcurrentHashMap<String, ConcurrentHashMap<UUID, ParcelIrrigationProfile>>()

    override fun linkLocalProfilesToAccount(
        targetUserId: String,
        targetWorkspaceId: UUID,
        parcelIdsToLink: Set<UUID>,
        sourceWorkspaceId: UUID?,
    ): Int {
        if (parcelIdsToLink.isEmpty()) return 0
        val targetScope = "${targetUserId}:::${targetWorkspaceId}"
        val targetMap = scopedProfiles.computeIfAbsent(targetScope) { ConcurrentHashMap() }
        var count = 0

        // Strict authorization boundary: only look inside the authorized source workspace local partition
        // (or default local scope if sourceWorkspaceId matches targetWorkspaceId / unassigned).
        // Never look across arbitrary local:::* partitions from other holdings.
        val allowedLocalScopes = buildList {
            val authorizedSourceWorkspace = sourceWorkspaceId ?: targetWorkspaceId
            add("local:::${authorizedSourceWorkspace}")
            add(DEFAULT_SCOPE)
            add("local:::default")
        }.distinct()

        val localMaps = allowedLocalScopes.mapNotNull { scopedProfiles[it] }

        for (parcelId in parcelIdsToLink) {
            if (!targetMap.containsKey(parcelId)) {
                val sourceProfile = localMaps.firstNotNullOfOrNull { it[parcelId] }
                if (sourceProfile != null) {
                    targetMap[parcelId] = sourceProfile
                    count++
                }
            }
        }
        return count
    }

    @Deprecated("Superseded by explicit authorized linkLocalProfilesToAccount")
    override fun claimLegacyProfiles(targetScope: String, claimantUserId: String): Int {
        if (legacyClaimant != null && legacyClaimant != claimantUserId) return 0
        legacyClaimant = claimantUserId
        val defaultMap = scopedProfiles[DEFAULT_SCOPE] ?: scopedProfiles["local:::default"] ?: scopedProfiles.entries.firstOrNull { it.key.startsWith("local:::") }?.value ?: return 0
        val targetMap = scopedProfiles.computeIfAbsent(targetScope) { ConcurrentHashMap() }
        var count = 0
        for ((k, v) in defaultMap) {
            if (!targetMap.containsKey(k)) {
                targetMap[k] = v
                count++
            }
        }
        return count
    }

    override fun setScope(scopeKey: String) {
        activeScope = scopeKey
    }

    override fun currentScope(): String = activeScope

    private fun currentMap(): ConcurrentHashMap<UUID, ParcelIrrigationProfile> {
        val map = scopedProfiles.computeIfAbsent(activeScope) { ConcurrentHashMap() }
        if (map.isEmpty() && (activeScope.startsWith("local:::") || activeScope == DEFAULT_SCOPE)) {
            val fallbackMap = scopedProfiles[DEFAULT_SCOPE] ?: scopedProfiles["local:::default"]
            if (fallbackMap != null && fallbackMap !== map) {
                map.putAll(fallbackMap)
            }
        }
        return map
    }

    override fun getProfile(parcelId: UUID): ParcelIrrigationProfile? = currentMap()[parcelId]

    override fun getAllProfiles(): Map<UUID, ParcelIrrigationProfile> = HashMap(currentMap())

    override fun getProfilesForScope(scopeKey: String): Map<UUID, ParcelIrrigationProfile> =
        HashMap(scopedProfiles[scopeKey] ?: emptyMap())

    override fun saveProfile(profile: ParcelIrrigationProfile) {
        currentMap()[profile.parcelId] = profile
    }

    override fun removeProfile(parcelId: UUID) {
        currentMap().remove(parcelId)
    }

    override fun addPunctualTurn(turn: PunctualTurn) {
        val map = currentMap()
        val current = map[turn.parcelId] ?: ParcelIrrigationProfile(parcelId = turn.parcelId)
        val updatedList = current.punctualTurns + turn
        map[turn.parcelId] = current.copy(punctualTurns = updatedList)
    }

    override fun updatePunctualTurn(turn: PunctualTurn) {
        val map = currentMap()
        val current = map[turn.parcelId] ?: return
        val updatedList = current.punctualTurns.map { if (it.id == turn.id) turn else it }
        map[turn.parcelId] = current.copy(punctualTurns = updatedList)
    }

    override fun deletePunctualTurn(parcelId: UUID, turnId: UUID) {
        val map = currentMap()
        val current = map[parcelId] ?: return
        val updatedList = current.punctualTurns.filterNot { it.id == turnId }
        map[parcelId] = current.copy(punctualTurns = updatedList)
    }

    override fun clear() {
        currentMap().clear()
    }

    companion object {
        const val DEFAULT_SCOPE = "default"
    }
}

/**
 * Android SharedPreferences backed implementation of [FarmerIrrigationStore] for offline persistence.
 *
 * Implements non-destructive scope partitioning (CR-004): switching between workspaces
 * or user accounts never wipes stored data, ensuring seamless recovery when returning (A -> B -> A).
 */
class AndroidFarmerIrrigationStore(
    context: Context,
    initialScope: String = DEFAULT_SCOPE,
) : FarmerIrrigationStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var activeScope: String = initialScope
    private val memoryCache = ConcurrentHashMap<UUID, ParcelIrrigationProfile>()

    init {
        loadScopeFromPrefs(initialScope)
    }

    override fun setScope(scopeKey: String) {
        if (activeScope != scopeKey) {
            activeScope = scopeKey
            loadScopeFromPrefs(scopeKey)
        }
    }

    override fun currentScope(): String = activeScope

    private fun loadScopeFromPrefs(scope: String) {
        memoryCache.clear()
        val prefix = scopedPrefix(scope)
        val allEntries = prefs.all
        for ((key, value) in allEntries) {
            if (key.startsWith(prefix) && value is String) {
                runCatching {
                    val json = JSONObject(value)
                    val profile = deserializeProfile(json)
                    memoryCache[profile.parcelId] = profile
                }
            } else if ((scope == DEFAULT_SCOPE || scope.startsWith("local:::")) &&
                (key.startsWith(KEY_PREFIX_PROFILE) && !key.startsWith("profile_scope_") && !key.startsWith("profile_backup_") ||
                 key.startsWith("profile_scope_default_")) && value is String
            ) {
                // Non-destructive fallback: load legacy un-scoped entries under default/local partition
                runCatching {
                    val json = JSONObject(value)
                    val profile = deserializeProfile(json)
                    memoryCache.putIfAbsent(profile.parcelId, profile)
                }
            }
        }
    }

    override fun getProfile(parcelId: UUID): ParcelIrrigationProfile? = memoryCache[parcelId]

    override fun getAllProfiles(): Map<UUID, ParcelIrrigationProfile> = HashMap(memoryCache)

    override fun getProfilesForScope(scopeKey: String): Map<UUID, ParcelIrrigationProfile> {
        val prefix = scopedPrefix(scopeKey)
        val result = mutableMapOf<UUID, ParcelIrrigationProfile>()
        val allEntries = prefs.all
        for ((key, value) in allEntries) {
            if (key.startsWith(prefix) && value is String) {
                runCatching {
                    val json = JSONObject(value)
                    val profile = deserializeProfile(json)
                    result[profile.parcelId] = profile
                }
            }
        }
        return result
    }

    override fun saveProfile(profile: ParcelIrrigationProfile) {
        memoryCache[profile.parcelId] = profile
        val serialized = serializeProfile(profile).toString()
        val scopedKey = scopedPrefix(activeScope) + profile.parcelId
        prefs.edit { putString(scopedKey, serialized) }
    }

    override fun removeProfile(parcelId: UUID) {
        memoryCache.remove(parcelId)
        val scopedKey = scopedPrefix(activeScope) + parcelId
        prefs.edit {
            remove(scopedKey)
            if (activeScope == DEFAULT_SCOPE || activeScope.startsWith("local:::")) {
                remove(KEY_PREFIX_PROFILE + parcelId)
            }
        }
    }

    /**
     * Explicit non-destructive migration of legacy un-scoped configurations to a designated verified scope.
     * Backs up legacy keys to `profile_backup_legacy_<parcelId>` and copies them to the target scope partition.
     * Original legacy keys are retained to prevent data loss.
     */
    fun migrateLegacyProfilesToScope(targetScope: String): Int {
        var count = 0
        val allEntries = prefs.all
        prefs.edit {
            for ((key, value) in allEntries) {
                if (key.startsWith(KEY_PREFIX_PROFILE) && !key.startsWith("profile_scope_") && !key.startsWith("profile_backup_") && value is String) {
                    val parcelIdStr = key.removePrefix(KEY_PREFIX_PROFILE)
                    val backupKey = "profile_backup_legacy_$parcelIdStr"
                    val targetScopedKey = scopedPrefix(targetScope) + parcelIdStr
                    if (!prefs.contains(backupKey)) {
                        putString(backupKey, value)
                    }
                    if (!prefs.contains(targetScopedKey)) {
                        putString(targetScopedKey, value)
                        count++
                    }
                }
            }
        }
        if (activeScope == targetScope) {
            loadScopeFromPrefs(targetScope)
        }
        return count
    }

    /**
     * Explicit, non-destructive linking of local/legacy parcel configurations to an authorized user account
     * and workspace partition upon affirmative user authorization.
     *
     * Conforms to strict authorization boundaries:
     * - Only searches within the explicitly authorized source workspace local partition (or target workspace / default legacy).
     * - Does NOT perform open wild-card searches across all `profile_scope_local:::` partitions to prevent cross-exploitation data leakage.
     * - Scopes backup keys per target user, target workspace and parcel to eliminate collision risks.
     * - Non-destructive: preserves original local and legacy keys intact.
     * - Idempotent: safe to run multiple times without data corruption or duplicate writes.
     *
     * @return count of newly linked parcel profiles.
     */
    override fun linkLocalProfilesToAccount(
        targetUserId: String,
        targetWorkspaceId: UUID,
        parcelIdsToLink: Set<UUID>,
        sourceWorkspaceId: UUID?,
    ): Int {
        if (parcelIdsToLink.isEmpty()) return 0
        val targetScope = scopeKey(targetUserId, targetWorkspaceId)
        val targetPrefix = scopedPrefix(targetScope)
        var count = 0

        val allEntries = prefs.all
        // Strictly bounded local prefixes: only authorized source workspace or fallback default
        val authorizedSourceWs = sourceWorkspaceId ?: targetWorkspaceId
        val localCandidateKeys = buildList {
            add(scopedPrefix(scopeKey(null, authorizedSourceWs)))
            if (authorizedSourceWs != targetWorkspaceId) {
                add(scopedPrefix(scopeKey(null, targetWorkspaceId)))
            }
            add(scopedPrefix(DEFAULT_SCOPE))
        }.distinct()

        prefs.edit {
            for (parcelId in parcelIdsToLink) {
                val parcelIdStr = parcelId.toString()
                val targetKey = targetPrefix + parcelIdStr

                var sourceJsonString: String? = null

                // 1. Search strictly in authorized source local scope candidates
                for (candidatePrefix in localCandidateKeys) {
                    val candidateKey = candidatePrefix + parcelIdStr
                    val candidateValue = allEntries[candidateKey]
                    if (candidateValue is String) {
                        sourceJsonString = candidateValue
                        break
                    }
                }

                // 2. Fallback to unassigned legacy un-scoped key if not found in authorized local workspace
                if (sourceJsonString == null) {
                    val legacyKey = KEY_PREFIX_PROFILE + parcelIdStr
                    val value = allEntries[legacyKey]
                    if (value is String) {
                        sourceJsonString = value
                    }
                }

                if (sourceJsonString != null) {
                    // Collision-free backup key namespaced by target user, target workspace and parcel ID
                    val backupKey = "profile_backup_link_${targetUserId}_${targetWorkspaceId}_$parcelIdStr"
                    if (!prefs.contains(backupKey)) {
                        putString(backupKey, sourceJsonString)
                    }

                    // Copy to target partition if not already present
                    if (!prefs.contains(targetKey)) {
                        putString(targetKey, sourceJsonString)
                        count++
                    }
                    // Non-destructive: Local and legacy keys are PRESERVED intact.
                }
            }
        }

        if (activeScope == targetScope) {
            loadScopeFromPrefs(targetScope)
        }
        return count
    }

    @Deprecated("Superseded by explicit authorized linkLocalProfilesToAccount")
    override fun claimLegacyProfiles(targetScope: String, claimantUserId: String): Int {
        val existingClaimant = prefs.getString(KEY_LEGACY_CLAIMANT, null)
        if (existingClaimant != null && existingClaimant != claimantUserId) {
            // Already claimed by a different user; do not copy to this user
            return 0
        }
        var count = 0
        val allEntries = prefs.all
        prefs.edit {
            putString(KEY_LEGACY_CLAIMANT, claimantUserId)
            for ((key, value) in allEntries) {
                if (key.startsWith(KEY_PREFIX_PROFILE) && !key.startsWith("profile_scope_") && !key.startsWith("profile_backup_") && value is String) {
                    val parcelIdStr = key.removePrefix(KEY_PREFIX_PROFILE)
                    val backupKey = "profile_backup_legacy_$parcelIdStr"
                    val targetScopedKey = scopedPrefix(targetScope) + parcelIdStr
                    if (!prefs.contains(backupKey)) {
                        putString(backupKey, value)
                    }
                    if (!prefs.contains(targetScopedKey)) {
                        putString(targetScopedKey, value)
                        count++
                    }
                    // Non-destructive: DO NOT delete legacy key
                }
            }
        }
        if (activeScope == targetScope) {
            loadScopeFromPrefs(targetScope)
        }
        return count
    }

    override fun addPunctualTurn(turn: PunctualTurn) {
        val current = memoryCache[turn.parcelId] ?: ParcelIrrigationProfile(parcelId = turn.parcelId)
        val updated = current.copy(punctualTurns = current.punctualTurns + turn)
        saveProfile(updated)
    }

    override fun updatePunctualTurn(turn: PunctualTurn) {
        val current = memoryCache[turn.parcelId] ?: return
        val updated = current.copy(punctualTurns = current.punctualTurns.map { if (it.id == turn.id) turn else it })
        saveProfile(updated)
    }

    override fun deletePunctualTurn(parcelId: UUID, turnId: UUID) {
        val current = memoryCache[parcelId] ?: return
        val updated = current.copy(punctualTurns = current.punctualTurns.filterNot { it.id == turnId })
        saveProfile(updated)
    }

    override fun clear() {
        // Non-destructive: only clears entries in the currently active scope
        val prefix = scopedPrefix(activeScope)
        val keysToRemove = prefs.all.keys.filter { it.startsWith(prefix) }
        prefs.edit {
            keysToRemove.forEach { remove(it) }
        }
        memoryCache.clear()
    }

    companion object {
        const val DEFAULT_SCOPE = "default"
        const val KEY_LEGACY_CLAIMANT = "legacy_profiles_claimed_by"
        private const val PREFS_NAME = "farmer_irrigation_configs"
        private const val KEY_PREFIX_PROFILE = "profile_"

        private fun scopedPrefix(scope: String): String = "profile_scope_${scope}_"

        /**
         * Generates a deterministic scope partition key combining user identity and workspace.
         */
        fun scopeKey(userId: String?, workspaceId: UUID?): String =
            "${userId?.trim() ?: "local"}:::${workspaceId?.toString() ?: "default"}"

        internal fun serializeProfile(profile: ParcelIrrigationProfile): JSONObject {
            val root = JSONObject()
            root.put("parcelId", profile.parcelId.toString())

            // Supply
            val supplyObj = JSONObject().apply {
                put("sourceType", profile.supply.sourceType.name)
                put("communityName", profile.supply.communityName ?: "")
                put("sector", profile.supply.sector ?: "")
                put("hydrant", profile.supply.hydrant ?: "")
                put("intakeOrValve", profile.supply.intakeOrValve ?: "")
            }
            root.put("supply", supplyObj)

            // Aforo
            val aforoObj = JSONObject().apply {
                put("measurementMode", profile.aforo.measurementMode.name)
                put("oliveTreeCount", profile.aforo.oliveTreeCount)
                put("emittersPerTree", profile.aforo.emittersPerTree)
                put("emitterFlowLph", profile.aforo.emitterFlowLph)
                put("emitterType", profile.aforo.emitterType.name)
                put("directFlowRate", profile.aforo.directFlowRate ?: -1.0)
                put("directFlowUnit", profile.aforo.directFlowUnit.name)
            }
            root.put("aforo", aforoObj)

            // Weekly slots
            val slotsArray = JSONArray()
            profile.weeklySlots.forEach { (day, slot) ->
                val slotObj = JSONObject().apply {
                    put("dayOfWeek", day.name)
                    put("startHour", slot.startTime.hour)
                    put("startMinute", slot.startTime.minute)
                    put("endHour", slot.endTime.hour)
                    put("endMinute", slot.endTime.minute)
                }
                slotsArray.put(slotObj)
            }
            root.put("weeklySlots", slotsArray)

            // Punctual turns
            val punctualArray = JSONArray()
            profile.punctualTurns.forEach { turn ->
                val pObj = JSONObject().apply {
                    put("id", turn.id.toString())
                    put("parcelId", turn.parcelId.toString())
                    put("date", turn.date.toString())
                    put("startHour", turn.timeSlot.startTime.hour)
                    put("startMinute", turn.timeSlot.startTime.minute)
                    put("endHour", turn.timeSlot.endTime.hour)
                    put("endMinute", turn.timeSlot.endTime.minute)
                    put("reason", turn.reason)
                    put("isCancelled", turn.isCancelled)
                }
                punctualArray.put(pObj)
            }
            root.put("punctualTurns", punctualArray)

            // Tariff
            val tariffObj = JSONObject().apply {
                put("tariffType", profile.tariff.tariffType.name)
                put("variablePricePerUnitMinor", profile.tariff.variablePricePerUnitMinor)
                put("annualCommunityQuotaMinor", profile.tariff.annualCommunityQuotaMinor)
                put("currency", profile.tariff.currency)
                if (profile.tariff.quotaIdentifier != null) {
                    put("quotaIdentifier", profile.tariff.quotaIdentifier)
                }
            }
            root.put("tariff", tariffObj)

            return root
        }

        internal fun deserializeProfile(json: JSONObject): ParcelIrrigationProfile {
            val parcelId = UUID.fromString(json.getString("parcelId"))

            val supplyObj = json.optJSONObject("supply")
            val supply = if (supplyObj != null) {
                WaterSupplyDetails(
                    sourceType = runCatching { WaterSourceType.valueOf(supplyObj.optString("sourceType")) }.getOrDefault(WaterSourceType.IRRIGATION_COMMUNITY),
                    communityName = supplyObj.optString("communityName").takeIf(String::isNotBlank),
                    sector = supplyObj.optString("sector").takeIf(String::isNotBlank),
                    hydrant = supplyObj.optString("hydrant").takeIf(String::isNotBlank),
                    intakeOrValve = supplyObj.optString("intakeOrValve").takeIf(String::isNotBlank),
                )
            } else WaterSupplyDetails()

            val aforoObj = json.optJSONObject("aforo")
            val aforo = if (aforoObj != null) {
                val directFlow = aforoObj.optDouble("directFlowRate", -1.0).takeIf { it >= 0.0 }
                AforoConfig(
                    measurementMode = runCatching { FlowMeasurementMode.valueOf(aforoObj.optString("measurementMode")) }.getOrDefault(FlowMeasurementMode.CALCULATED_FROM_EMITTERS),
                    oliveTreeCount = aforoObj.optInt("oliveTreeCount", 0),
                    emittersPerTree = aforoObj.optInt("emittersPerTree", 0),
                    emitterFlowLph = aforoObj.optDouble("emitterFlowLph", 0.0),
                    emitterType = runCatching { EmitterType.valueOf(aforoObj.optString("emitterType")) }.getOrDefault(EmitterType.INTEGRATED),
                    directFlowRate = directFlow,
                    directFlowUnit = runCatching { FlowUnit.valueOf(aforoObj.optString("directFlowUnit")) }.getOrDefault(FlowUnit.M3_PER_HOUR),
                )
            } else AforoConfig()

            val weeklySlots = mutableMapOf<DayOfWeek, ScheduledTimeSlot>()
            val slotsArray = json.optJSONArray("weeklySlots")
            if (slotsArray != null) {
                for (i in 0 until slotsArray.length()) {
                    val sObj = slotsArray.getJSONObject(i)
                    val day = runCatching { DayOfWeek.valueOf(sObj.getString("dayOfWeek")) }.getOrNull()
                    if (day != null) {
                        val startTime = LocalTime.of(sObj.optInt("startHour", 0), sObj.optInt("startMinute", 0))
                        val endTime = LocalTime.of(sObj.optInt("endHour", 0), sObj.optInt("endMinute", 0))
                        weeklySlots[day] = ScheduledTimeSlot(startTime, endTime)
                    }
                }
            }

            val punctualTurns = mutableListOf<PunctualTurn>()
            val punctualArray = json.optJSONArray("punctualTurns")
            if (punctualArray != null) {
                for (i in 0 until punctualArray.length()) {
                    val pObj = punctualArray.getJSONObject(i)
                    val id = runCatching { UUID.fromString(pObj.getString("id")) }.getOrDefault(UUID.randomUUID())
                    val pParcelId = runCatching { UUID.fromString(pObj.getString("parcelId")) }.getOrDefault(parcelId)
                    val date = runCatching { LocalDate.parse(pObj.getString("date")) }.getOrDefault(LocalDate.now())
                    val startTime = LocalTime.of(pObj.optInt("startHour", 0), pObj.optInt("startMinute", 0))
                    val endTime = LocalTime.of(pObj.optInt("endHour", 0), pObj.optInt("endMinute", 0))
                    val reason = pObj.optString("reason", "Turno extraordinario")
                    val isCancelled = pObj.optBoolean("isCancelled", false)

                    punctualTurns.add(
                        PunctualTurn(
                            id = id,
                            parcelId = pParcelId,
                            date = date,
                            timeSlot = ScheduledTimeSlot(startTime, endTime),
                            reason = reason,
                            isCancelled = isCancelled,
                        )
                    )
                }
            }

            val tariffObj = json.optJSONObject("tariff")
            val tariff = if (tariffObj != null) {
                WaterTariff(
                    tariffType = runCatching { WaterTariffType.valueOf(tariffObj.optString("tariffType")) }.getOrDefault(WaterTariffType.PER_M3),
                    variablePricePerUnitMinor = tariffObj.optLong("variablePricePerUnitMinor", 0L),
                    annualCommunityQuotaMinor = tariffObj.optLong("annualCommunityQuotaMinor", 0L),
                    currency = tariffObj.optString("currency", "EUR"),
                    quotaIdentifier = tariffObj.optString("quotaIdentifier").takeIf { it.isNotBlank() },
                )
            } else WaterTariff()

            return ParcelIrrigationProfile(
                parcelId = parcelId,
                supply = supply,
                aforo = aforo,
                weeklySlots = weeklySlots,
                punctualTurns = punctualTurns,
                tariff = tariff,
            )
        }
    }
}

