package com.isivoltpro.maginaolivo.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.accountlink.*
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class FarmLinkLocalState(val ledger: FarmLinkLedger? = null, val unreadable: Boolean = false)

class LocalFarmLinkRepository(
    private val context: Context,
    private val database: MaginaOlivoDatabase,
    private val account: AccountRepository,
    private val issuer: String,
    private val store: FarmLinkLedgerStore = AndroidFarmLinkLedger.forContext(context),
) : DeliveryWriteScope {
    private val mutable = MutableStateFlow(load())
    val state: StateFlow<FarmLinkLocalState> = mutable

    override suspend fun <T> withAccount(farmId: UUID, action: suspend (String?) -> T): T =
        account.withLocalWriteIdentity { userId -> withContext(Dispatchers.IO) {
            // The account mutex remains held through the local Room write; consent and
            // revocation use that same mutex before entering Room, preserving lock order.
            val bound = userId?.takeIf { isFarmConsented(farmId, it) }?.toString()
            action(bound)
        } }
    private fun load() = try { FarmLinkLocalState(store.read()) } catch (_: IOException) { FarmLinkLocalState(unreadable=true) }
    fun subject(userId: UUID) = "$issuer|$userId"
    fun accessAllowed(userId: UUID? = account.state.value.userId): Boolean {
        val value=mutable.value
        return !value.unreadable && (value.ledger == null || (userId != null && value.ledger.subject == subject(userId)))
    }
    private fun reloadAndRequire(userId: UUID): FarmLinkLedger? {
        mutable.value=load()
        if (!accessAllowed(userId)) throw IOException("Estas fincas están protegidas con otra cuenta o no se pudo comprobar su protección.")
        return mutable.value.ledger
    }
    suspend fun choices(): List<FarmChoice> = account.withLocalIdentity { userId -> withContext(Dispatchers.IO) {
        reloadAndRequire(userId); readChoices()
    } }

    suspend fun consent(expectedUserId: UUID, selected: List<FarmChoice>, accepted: Boolean): FarmLinkLedger = account.withLocalIdentity { userId -> withContext(Dispatchers.IO) {
        if (userId != expectedUserId) throw IOException("La cuenta ha cambiado. Revisa de nuevo la selección.")
        if (!accepted || selected.isEmpty() || selected.map { it.id }.distinct().size != selected.size) throw IOException("Selecciona alguna finca y confirma tu consentimiento.")
        if (issuer.isBlank()) throw IOException("La cuenta no está configurada.")
        val old=reloadAndRequire(userId)
        val pending=selected.filter { it.id !in old?.farmIds.orEmpty() }
        if (pending.isEmpty()) return@withContext checkNotNull(old)
        database.withTransaction {
            val actual=readChoices().associateBy { it.id }
            if (pending.any { actual[it.id] != it }) throw IOException("Las fincas han cambiado. Revisa de nuevo la selección.")
            // A fresh operation on each uncommitted attempt. Never reuse an old backup as current consent.
            val backupId=UUID.randomUUID()
            val receipt=LocalLinkBackup(context,database).capture(backupId)
            val manifest=File(receipt.directory,"manifest.json").readBytes()
            val entry=FarmLinkConsent(UUID.randomUUID(),subject(userId),pending.sortedBy { it.id.toString() },backupId,
                AndroidFarmLinkLedger.hash(manifest),Instant.now())
            try { store.append(entry).also { mutable.value=FarmLinkLocalState(it) } }
            catch (error: Exception) { mutable.value=load();throw error }
        }
    } }

    private fun readChoices(): List<FarmChoice> {
        val values=mutableListOf<FarmChoice>()
        database.openHelper.readableDatabase.query("SELECT id,workspace_id,name,version FROM farms WHERE deleted_at IS NULL AND status='ACTIVE' ORDER BY name,id").use { c ->
            while(c.moveToNext()) values.add(FarmChoice(UUID.fromString(c.getString(0)),UUID.fromString(c.getString(1)),c.getString(2),c.getLong(3)))
        }
        return values
    }

    /**
     * G2.3: Returns true if the farm has active G1b consent under the currently logged-in account.
     * Operations belonging to unconsented farms will remain local only and are not eligible for dispatch.
     */
    fun isFarmConsented(farmId: UUID, userId: UUID? = account.state.value.userId): Boolean {
        // A caller must not obtain A's consent by supplying A's old userId after
        // logout/account switch. The identity argument is a consistency assertion,
        // never an authorization override.
        if (userId == null || userId != account.state.value.userId) return false
        // Authorization must use the latest on-disk ledger, not a potentially stale StateFlow
        // snapshot held by another repository instance in this Android process.
        // A corrupt or unreadable ledger fails closed.
        mutable.value = load()
        if (!accessAllowed(userId)) return false
        val ledger = mutable.value.ledger ?: return false
        return farmId in ledger.activeFarmIds
    }

    /**
     * G2.3: Revokes local consent eligibility for a farm, transitioning its pending outbox operations
     * to BLOCKED status in Room so they cannot be dispatched.
     */
    suspend fun revokeConsentForFarm(farmId: UUID) = account.withLocalIdentity { userId -> withContext(Dispatchers.IO) {
        val old = reloadAndRequire(userId) ?: throw IOException("No hay consentimiento local para revocar.")
        if (farmId !in old.farmIds) throw IOException("La finca no tiene consentimiento para esta cuenta.")
        // Persist the denial first. If Room fails, a later dispatch must still recheck this
        // durable ledger; the operation remains blocked from authorization after process death.
        val next = store.revoke(FarmLinkRevocation(UUID.randomUUID(), subject(userId), farmId, Instant.now()))
        mutable.value = FarmLinkLocalState(next)
        database.withTransaction {
            database.syncOutboxDao().updateStatusForFarm(
                farmId = farmId,
                status = com.isivoltpro.maginaolivo.data.local.model.OutboxStatus.BLOCKED,
                nowEpochMillis = System.currentTimeMillis(),
            )
        }
    } }
}
