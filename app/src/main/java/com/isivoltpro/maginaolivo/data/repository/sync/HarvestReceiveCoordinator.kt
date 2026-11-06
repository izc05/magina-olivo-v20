package com.isivoltpro.maginaolivo.data.repository.sync

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.RECEIVE_HOLD_REMOTE_REWRITTEN
import com.isivoltpro.maginaolivo.data.local.dao.ReceiptRecordOutcome
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.entity.SyncReceiveHoldEntity
import com.isivoltpro.maginaolivo.data.local.entity.SyncReceiveReceiptEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.local.recordReceiveEvidence
import com.isivoltpro.maginaolivo.data.repository.JornadaLedger
import com.isivoltpro.maginaolivo.data.repository.WorkspaceOwnerVerifier
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.domain.harvest.AUTO_DAY
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthFailure
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkLedger
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException

internal enum class HarvestReceiveOutcome {
    /** At least one Jornada was applied (holds may also have been recorded for others). */
    IMPORTED,
    /** Nothing differs from what this device already verified and holds: zero writes. */
    ALREADY_CURRENT,
    /** Nothing applied; every difference was retained with its cause in sync_receive_holds. */
    HELD,
    NOT_AUTHORIZED,
    CONSENT_REQUIRED,
    /** The local workspace, farm or campaign of the request is missing: nothing is read or written. */
    PARENT_MISSING,
    /** Network or an unknown outcome: nothing written; the same call can be repeated safely. */
    OFFLINE,
    /** Even a single day is over the contract limits: nothing written, never truncated. */
    TOO_LARGE,
    /** Out of contract (PROTOCOL): nothing written. */
    REJECTED,
    /**
     * R3-e-limit: this pass applied and/or retained what it read and more Jornadas remain after [HarvestReceiveReport.next];
     * the next pass of the series passes that cursor back. Never means data was dropped.
     */
    PARTIAL,
}

/**
 * R3-e-limit: one pass reads at most [maxBundles] bundles, and stops reading new groups once [maxBytes] of bundle bodies
 * were read. A group (Jornadas linked by a moved Pesada) is NEVER split: the first group of a pass is always read whole,
 * even over the limit (counted in [HarvestReceiveReport.oversizedGroups]), so every pass advances and no series loops.
 */
internal data class ReceiveLimit(val maxBundles: Int = 100, val maxBytes: Long = 1L shl 20) {
    init { require(maxBundles >= 1 && maxBytes >= 1) { "limits must be positive" } }
    companion object { val UNBOUNDED = ReceiveLimit(Int.MAX_VALUE, Long.MAX_VALUE) }
}

/**
 * R3-e-limit: where the next pass of a series continues (groups whose greatest Jornada id is after [after]). Bound to the
 * issuer, account, workspace, farm and campaign that produced it: any mismatch discards it and the series starts over.
 * Kept in memory by the caller, never persisted: losing it only costs re-reading what is still retained.
 */
internal data class ReceiveCursor(val issuer: String, val accountId: UUID, val workspaceId: UUID, val farmId: UUID,
    val campaignId: UUID, val after: UUID)

internal data class HarvestReceiveReport(
    val outcome: HarvestReceiveOutcome,
    val applied: List<UUID> = emptyList(),
    val held: List<SyncReceiveHoldEntity> = emptyList(),
    val bundlesRead: Int = 0,
    /** R3-e-limit: the cursor of the next pass, or null when this pass reached the end of the series. */
    val next: ReceiveCursor? = null,
    /** R3-e-limit: characters of bundle bodies read by this pass (UTF-8 JSON, ~bytes). */
    val bundleBytes: Long = 0,
    /** R3-e-limit: groups read whole although over the limit (never split). */
    val oversizedGroups: Int = 0,
)

/** Receive hold causes (design §5) decided by the receiver itself. */
internal object ReceiveCauses {
    const val NO_JORNADA = "NO_JORNADA"
    const val REMOTE_AHEAD = "REMOTE_AHEAD"
    const val LOCAL_PENDING = "LOCAL_PENDING"
    const val REMOTE_BEHIND = "REMOTE_BEHIND"
    const val PARENT_MISSING = "PARENT_MISSING"
    const val LOCAL_DEPENDENTS = "LOCAL_DEPENDENTS"
    const val REMOTE_MISSING = "REMOTE_MISSING"
    const val REMOTE_REWRITTEN = RECEIVE_HOLD_REMOTE_REWRITTEN
}

/**
 * #808 R3-c: receiver of Jornadas with all their Pesadas (design #834, D1-D10), over the R2 contract with the R3-b
 * parser and verifier. Explicit QA entry point: no composition root, scheduler or worker constructs it
 * (ArchitectureBoundaryTest).
 *
 * - Identity: the local account owns the local workspace; farm consent is current; inside withServerVerifiedIdentity the
 *   server confirms NOW that the session owns the workspace, and the commit runs only for that same verified account.
 * - Read (D1): the manifest of the farm and campaign (partitioned by dates on 54000, never truncated); only the Jornadas
 *   that differ from Room or from the receive receipts are read as bundles.
 * - Decide, then apply (D2): each Jornada is verified (R3-b) and decided unit by unit in memory; if ANY unit must be held,
 *   nothing of that Jornada is written (all or nothing) and the holds are recorded. Otherwise ONE Room transaction writes
 *   the Jornada, its Pesadas and parcels with their remote UUIDs and versions, derives an AUTO_DAY locally (never imported,
 *   D5), checks it against the server, and records the receive receipts. A MANUAL keeps its typed kilos (D6, J-D2).
 * - R3-e-fix: a Pesada the server lists under Y while this device links it to X moved X -> Y. X (same version, only its
 *   derived fingerprint moved) is read too, and X and Y are ONE group: decided together, applied in ONE transaction, every
 *   AUTO_DAY of the group derived after all writes and checked against the server. Any hold of a member applies nothing.
 * - R3-e-limit: a pass reads whole groups after its cursor up to [ReceiveLimit] (100 bundles / 1 MiB by default; at least
 *   one group, never split), applies them group by group as above and returns [HarvestReceiveOutcome.PARTIAL] with the next
 *   cursor while more remain. The manifest is always read whole. A cut loses at most the pass being read.
 * - Never an outbox operation, never a local deletion, never a resolved hold.
 */
internal class HarvestReceiveCoordinator(
    private val database: MaginaOlivoDatabase,
    private val account: AccountRepository,
    private val issuer: String,
    private val transport: DeliveryRpcTransport,
    private val ledger: (UUID) -> FarmLinkLedger?,
    private val ownerVerifier: WorkspaceOwnerVerifier?,
    private val idGenerator: IdGenerator,
    /**
     * #808 R3-d: the JWT issuer the R2 contract echoes (`auth.jwt()->>'iss'`, e.g. "<project>/auth/v1"). The device's
     * [issuer] (the project, as the consent ledger and the senders use it) keys receipts and holds; responses are checked
     * against this one. Passed explicitly by whoever wires the receiver; never derived by string rules here.
     */
    private val tokenIssuer: String = issuer,
    private val now: () -> Instant = Instant::now,
) {
    private class Offline : Exception()
    private class TooLarge : Exception()
    private class Unauthorized : Exception()
    private class Diverged(val bundle: HarvestBundle, val detail: String) : Exception()

    /** R3-e-fix: Jornadas linked by a Pesada that changed Jornada are one group, applied in ONE transaction. */
    private data class Read(val manifest: HarvestManifest, val groups: List<List<HarvestBundle>>, val next: ReceiveCursor?,
        val bytes: Long, val oversized: Int)

    /**
     * One pass. With the defaults a campaign larger than [limit] is received over several passes: each returns [PARTIAL]
     * and a [ReceiveCursor] until the last one (next == null). Every pass re-reads the full manifest (REMOTE_MISSING and
     * NO_JORNADA are always decided on all of it) and re-checks identity, ownership and consent.
     */
    suspend fun receive(workspaceId: UUID, farmId: UUID, campaignId: UUID, limit: ReceiveLimit = ReceiveLimit(),
        resumeAfter: ReceiveCursor? = null): HarvestReceiveReport {
        val verifier = ownerVerifier ?: return HarvestReceiveReport(HarvestReceiveOutcome.NOT_AUTHORIZED)
        if (issuer.isBlank() || tokenIssuer.isBlank()) return HarvestReceiveReport(HarvestReceiveOutcome.NOT_AUTHORIZED)
        val userId = account.state.value.userId ?: return HarvestReceiveReport(HarvestReceiveOutcome.NOT_AUTHORIZED)
        val workspace = database.workspaceDao().findById(workspaceId)
        if (workspace == null || workspace.metadata.deletedAt != null || workspace.ownerUserId != userId)
            return HarvestReceiveReport(HarvestReceiveOutcome.NOT_AUTHORIZED)
        val farm = database.farmDao().findById(farmId)
        val campaign = database.campaignDao().findById(campaignId)
        if (farm == null || farm.workspaceId != workspaceId || campaign == null || campaign.farmId != farmId)
            return HarvestReceiveReport(HarvestReceiveOutcome.PARENT_MISSING)
        if (!consented(userId, workspaceId, farmId)) return HarvestReceiveReport(HarvestReceiveOutcome.CONSENT_REQUIRED)
        val bounds = campaign.startDate to (campaign.endDate ?: maxOf(campaign.startDate, LocalDate.now()).plusYears(1))
        return try {
            account.withServerVerifiedIdentity(remote = { session ->
                if (session.userId != userId || !verifier.isCurrentOwner(session, workspaceId)) throw Unauthorized()
                // A cursor of another identity or scope is discarded: the series starts over (never skips, never leaks).
                val cursor = resumeAfter?.takeIf { it.issuer == issuer && it.accountId == session.userId && it.workspaceId == workspaceId &&
                    it.farmId == farmId && it.campaignId == campaignId }
                read(session, ManifestRequest(tokenIssuer, session.userId, workspaceId, farmId, campaignId), bounds, limit, cursor)
            }) { verified, read ->
                if (verified != userId || read.manifest.request.accountId != verified) HarvestReceiveReport(HarvestReceiveOutcome.NOT_AUTHORIZED)
                else commit(verified, read)
            }
        } catch (cancel: CancellationException) { throw cancel
        } catch (_: Unauthorized) { HarvestReceiveReport(HarvestReceiveOutcome.NOT_AUTHORIZED)
        } catch (_: Offline) { HarvestReceiveReport(HarvestReceiveOutcome.OFFLINE)
        } catch (_: TooLarge) { HarvestReceiveReport(HarvestReceiveOutcome.TOO_LARGE)
        } catch (_: HarvestReceiveProtocol) { HarvestReceiveReport(HarvestReceiveOutcome.REJECTED)
        } catch (failure: AuthFailure) {
            HarvestReceiveReport(if (failure.kind == AuthFailure.Kind.NETWORK) HarvestReceiveOutcome.OFFLINE else HarvestReceiveOutcome.NOT_AUTHORIZED)
        }
    }

    // ------------------------------------------------------------------------------------------------------- read

    private suspend fun read(session: AuthSession, request: ManifestRequest, bounds: Pair<LocalDate, LocalDate>, limit: ReceiveLimit,
        cursor: ReceiveCursor?): Read {
        val manifest = manifest(session, request, bounds)
        val inManifest = manifest.harvests.map { it.id }.toSet()
        // R3-e-fix: a Pesada the server lists under Y while this device still links it to X has moved X -> Y. X keeps its
        // version on the server (only its derived fingerprint moves), so it never "differs" by itself: it is read with Y.
        val moves = manifest.deliveries.mapNotNull { d ->
            val from = database.deliveryDao().findById(d.id)?.harvestId
            if (from != null && d.harvestId != null && from != d.harvestId && from in inManifest) from to d.harvestId else null
        }
        val sources = moves.map { it.first }.toSet()
        val wanted = manifest.harvests.filter { it.id in sources || differs(session.userId, it, manifest.deliveries.filter { d -> d.harvestId == it.id }) }
        // R3-e-limit: groups are formed BEFORE any bundle is read, so a group is never split across passes. Ordered by their
        // greatest Jornada id; the pass takes the groups after the cursor, whole, until the limit (always at least one).
        val after = cursor?.after?.toString() ?: ""
        val pending = groups(wanted.map { it.id }, moves).filter { it.last().toString() > after }
        val chosen = mutableListOf<List<HarvestBundle>>()
        var count = 0; var bytes = 0L; var oversized = 0
        for (group in pending) {
            if (chosen.isNotEmpty() && (count + group.size > limit.maxBundles || bytes >= limit.maxBytes)) break
            var groupBytes = 0L
            chosen += group.map { id ->
                val body = call(session, HarvestReceiveContract.BUNDLE_RPC, """{"p_harvest_id":"$id"}""")
                groupBytes += body.length
                HarvestReceiveParser.parseBundle(body, BundleRequest(tokenIssuer, session.userId, id, request.workspaceId, request.farmId, request.campaignId))
            }
            if (group.size > limit.maxBundles || groupBytes > limit.maxBytes) oversized++
            count += group.size; bytes += groupBytes
        }
        val next = if (pending.size > chosen.size) ReceiveCursor(issuer, session.userId, request.workspaceId, request.farmId,
            request.campaignId, chosen.last().last().harvest.id) else null
        return Read(manifest, chosen, next, bytes, oversized)
    }

    /** Connected components of the moves among the wanted Jornadas; each sorted, groups ordered by their greatest id. */
    private fun groups(ids: List<UUID>, moves: List<Pair<UUID, UUID>>): List<List<UUID>> {
        val root = ids.associateWith { it }.toMutableMap()
        fun find(id: UUID): UUID { var r = id; while (root.getValue(r) != r) r = root.getValue(r); return r }
        for ((from, to) in moves) if (from in root && to in root) {
            val (a, b) = listOf(find(from), find(to)).sortedBy { it.toString() }
            if (a != b) root[b] = a
        }
        return ids.groupBy { find(it) }.values.map { g -> g.sortedBy { it.toString() } }.sortedBy { it.last().toString() }
    }

    /** D1: the whole manifest, or (on 54000) the exact union of date halves, recursively; a single day over the limit is TOO_LARGE. */
    private suspend fun manifest(session: AuthSession, request: ManifestRequest, bounds: Pair<LocalDate, LocalDate>): HarvestManifest {
        val body = try {
            call(session, HarvestReceiveContract.MANIFEST_RPC, manifestBody(request))
        } catch (_: TooLarge) {
            // R3-d: the outer edges stay OPEN (null), so rows dated outside the local campaign's dates are never left out
            // of every partition; the campaign dates only place the cut. A closed single day still over the limit is TOO_LARGE.
            if (request.from != null && request.to != null && !request.from.isBefore(request.to)) throw TooLarge()
            val hi = request.to ?: maxOf(bounds.second, (request.from ?: bounds.first).plusYears(1))
            val lo = request.from ?: minOf(bounds.first, hi.minusYears(1))
            val mid = lo.plusDays((hi.toEpochDay() - lo.toEpochDay()) / 2)
            val low = manifest(session, request.copy(to = mid), bounds)
            val high = manifest(session, request.copy(from = mid.plusDays(1)), bounds)
            return HarvestManifest(request, low.harvests + high.harvests, low.deliveries + high.deliveries)
        }
        return HarvestReceiveParser.parseManifest(body, request)
    }

    private fun manifestBody(r: ManifestRequest) = buildString {
        append("""{"p_workspace_id":"${r.workspaceId}","p_farm_id":"${r.farmId}","p_campaign_id":"${r.campaignId}"""")
        append(""","p_from":${r.from?.let { "\"$it\"" } ?: "null"},"p_to":${r.to?.let { "\"$it\"" } ?: "null"}}""")
    }

    private suspend fun call(session: AuthSession, name: String, body: String): String {
        val response = try { transport.rpc(name, body, session.accessToken) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { throw Offline() }
        if (response.status in 200..299) return response.body
        if (response.status == 401 || response.status == 403) throw Unauthorized()
        // 54000: over a contract limit. Only the typed prefix is trusted.
        if (response.body.length <= 4096 && response.body.contains("\"54000\"") && response.body.contains("sync limit:")) throw TooLarge()
        if (response.status >= 500 || response.status == 408 || response.status == 429) throw Offline()
        throw HarvestReceiveProtocol("status ${response.status}")
    }

    /** True when the Jornada or any of its Pesadas is not exactly what this device already verified and holds. */
    private suspend fun differs(accountId: UUID, h: ManifestHarvest, deliveries: List<ManifestDelivery>): Boolean {
        val receipts = database.syncReceiveReceiptDao()
        val local = database.harvestDao().findById(h.id)
        if (local == null || local.metadata.remoteVersion != h.version || (local.metadata.deletedAt != null) != h.deleted) return true
        if (receipts.find(issuer, accountId.toString(), HARVEST, h.id)?.remoteVersion != h.version) return true
        return deliveries.any { d ->
            val row = database.deliveryDao().findById(d.id)
            row == null || row.metadata.remoteVersion != d.version || (row.metadata.deletedAt != null) != d.deleted ||
                row.harvestId != h.id || receipts.find(issuer, accountId.toString(), DELIVERY, d.id)?.remoteVersion != d.version
        }
    }

    // ----------------------------------------------------------------------------------------------------- commit

    private suspend fun commit(accountId: UUID, read: Read): HarvestReceiveReport {
        val account = accountId.toString()
        val holds = mutableListOf<SyncReceiveHoldEntity>()
        val applied = mutableListOf<UUID>()
        for (group in read.groups) {
            val result = applyGroup(account, group)
            if (result.isEmpty()) applied += group.map { it.harvest.id } else holds += result
        }
        // A Pesada without a Jornada has no bundle (R2 aclaración 1): never inserted, never given a Jornada.
        for (d in read.manifest.deliveries.filter { it.harvestId == null }) {
            holds += hold(account, DELIVERY, d.id, ReceiveCauses.NO_JORNADA, null, database.deliveryDao().findById(d.id)?.metadata?.remoteVersion,
                d.version, null, "partition_date=${d.partitionDate}")
        }
        // Confirmed locally but absent from the server's manifest: never deleted, retained.
        val request = read.manifest.request
        val remoteHarvests = read.manifest.harvests.map { it.id }.toSet()
        val remoteDeliveries = read.manifest.deliveries.map { it.id }.toSet()
        database.harvestDao().listRemoteForCampaign(request.farmId, request.campaignId).filter { it.id !in remoteHarvests }.forEach {
            holds += hold(account, HARVEST, it.id, ReceiveCauses.REMOTE_MISSING, it.id, it.metadata.remoteVersion, null, null, null)
        }
        database.deliveryDao().listRemoteForCampaign(request.farmId, request.campaignId).filter { it.id !in remoteDeliveries }.forEach {
            holds += hold(account, DELIVERY, it.id, ReceiveCauses.REMOTE_MISSING, it.harvestId, it.metadata.remoteVersion, null, null, null)
        }
        database.withTransaction { holds.forEach { database.syncReceiveHoldDao().observe(it) } }
        val outcome = when {
            read.next != null -> HarvestReceiveOutcome.PARTIAL
            applied.isNotEmpty() -> HarvestReceiveOutcome.IMPORTED
            holds.isNotEmpty() -> HarvestReceiveOutcome.HELD
            else -> HarvestReceiveOutcome.ALREADY_CURRENT
        }
        return HarvestReceiveReport(outcome, applied, holds, read.groups.sumOf { it.size }, read.next, read.bytes, read.oversized)
    }

    private sealed interface Decision {
        data object Insert : Decision
        data object Update : Decision
        data object Same : Decision
        data class Hold(val cause: String) : Decision
    }

    private class Plan(val bundle: HarvestBundle, val localDay: HarvestEntity?, val dayDecision: Decision,
        val decisions: Map<ReceivedDelivery, Decision>, val receipts: List<SyncReceiveReceiptEntity>)

    /**
     * Returns the holds of this group (one Jornada, or the Jornadas a moved Pesada links); empty means the whole group was
     * applied in ONE transaction. Any hold of any member means nothing of the group was written (all or nothing), only the
     * holds: a Pesada never leaves X without X being re-derived in the same transaction (R3-e-fix).
     */
    private suspend fun applyGroup(account: String, group: List<HarvestBundle>): List<SyncReceiveHoldEntity> {
        val unverified = group.flatMap { bundle ->
            val h = bundle.harvest
            val verdict = HarvestReceiveVerifier.verify(bundle)
            if (verdict.verified) emptyList() else verdict.holds.map { (cause, detail) ->
                hold(account, HARVEST, h.id, cause, h.id, database.harvestDao().findById(h.id)?.metadata?.remoteVersion, h.version,
                    bundle.head.headPayloadHash ?: bundle.head.ownFingerprint, detail)
            }
        }
        if (unverified.isNotEmpty()) return unverified
        val at = now()
        val holds = mutableListOf<SyncReceiveHoldEntity>()
        try {
            database.withTransaction {
                val plans = group.mapNotNull { plan(account, it, group, at, holds) }
                if (holds.isNotEmpty()) return@withTransaction
                // ---- every unit of every member is applicable: write the group as one unit
                for (p in plans) {
                    if (p.dayDecision != Decision.Same) writeHarvest(p.localDay, p.bundle.harvest, at)
                    for ((d, decision) in p.decisions) if (decision != Decision.Same) writeDelivery(d, at)
                }
                // Derived after ALL writes: the source of a moved Pesada no longer counts it, the destination does.
                for (p in plans) {
                    val h = p.bundle.harvest
                    if (h.dayOrigin != AUTO_DAY || h.deletedAt != null) continue
                    val derived = JornadaLedger(database, idGenerator).deriveForReceive(h.id, at)!!
                    val split = database.harvestDao().listParcels(h.id).sortedBy { it.parcelId.toString() }
                        .map { Triple(it.parcelId, it.weightGrams, it.allocationMode) }
                    if (derived.weightGrams != p.bundle.serverWeightGrams ||
                        split != p.bundle.serverParcels.map { Triple(it.parcelId, it.weightGrams, it.allocationMode) })
                        throw Diverged(p.bundle, "server=${p.bundle.serverWeightGrams};local=${derived.weightGrams}")
                }
                for (p in plans) for (r in p.receipts) check(database.recordReceiveEvidence(r) in setOf(ReceiptRecordOutcome.INSERTED,
                    ReceiptRecordOutcome.UPDATED, ReceiptRecordOutcome.UNCHANGED))
            }
        } catch (divergent: Diverged) {
            // Rolled back entirely: a derived day would not match the server. Nothing of the group was written.
            val h = divergent.bundle.harvest
            holds += hold(account, HARVEST, h.id, ReceiveHoldCauses.WEIGHT_DIVERGENT, h.id, null, h.version,
                divergent.bundle.head.derivedFingerprint, divergent.detail)
        }
        return holds
    }

    /** The decisions of one member, inside the group's transaction; null (with its holds added) when it cannot be applied. */
    private suspend fun plan(account: String, bundle: HarvestBundle, group: List<HarvestBundle>, at: Instant,
        holds: MutableList<SyncReceiveHoldEntity>): Plan? {
        val h = bundle.harvest
        val before = holds.size
        // D3: parents first. A Parcel of any Pesada must exist locally.
        val parcels = bundle.deliveries.flatMap { it.parcels }.map { it.parcelId }.toSet()
        val campaign = h.campaignId?.let { database.campaignDao().findById(it) }
        val inCampaign = h.campaignId?.let { id -> database.harvestDao().listCampaignParcels(id).map { it.parcelId }.toSet() }.orEmpty()
        if (campaign == null || campaign.metadata.remoteVersion == null ||
            parcels.any { database.parcelDao().findById(it) == null || it !in inCampaign }) {
            holds += hold(account, HARVEST, h.id, ReceiveCauses.PARENT_MISSING, h.id, null, h.version, null, null)
            return null
        }
        val localDay = database.harvestDao().findById(h.id)
        val dayDecision = decide(localDay?.metadata, open(SyncEntityType.HARVEST, h.id), h.version) { sameHarvest(localDay!!, h) }
        if (dayDecision is Decision.Hold) holds += hold(account, HARVEST, h.id, dayDecision.cause, h.id, localDay?.metadata?.remoteVersion,
            h.version, bundle.head.ownFingerprint, null)
        // A remote delete never removes what this device still records on that day (a Pesada the group moves away excepted).
        val leaving = group.filter { it !== bundle }.flatMap { other -> other.deliveries.map { it.id } }.toSet()
        if (h.deletedAt != null && localDay != null && localDay.metadata.deletedAt == null && hasDependents(localDay, bundle, leaving)) {
            holds += hold(account, HARVEST, h.id, ReceiveCauses.LOCAL_DEPENDENTS, h.id, localDay.metadata.remoteVersion, h.version, null, null)
        }
        val decisions = bundle.deliveries.associateWith { d ->
            val local = database.deliveryDao().findById(d.id)
            val decision = decide(local?.metadata, open(SyncEntityType.DELIVERY, d.id) || prepared(d.id), d.version) {
                sameDelivery(local!!, d)
            }
            if (decision is Decision.Hold) holds += hold(account, DELIVERY, d.id, decision.cause, h.id, local?.metadata?.remoteVersion,
                d.version, d.head?.payloadHash, null)
            decision
        }
        // Receive receipts must never go back nor be rewritten (R3-a correction).
        val receipts = receiptsFor(account, bundle, at)
        for (r in receipts) {
            val existing = database.syncReceiveReceiptDao().find(r.issuer, r.accountId, r.entityType, r.entityId) ?: continue
            if (r.remoteVersion < existing.remoteVersion) holds += hold(account, r.entityType, r.entityId, ReceiveCauses.REMOTE_BEHIND,
                h.id, existing.remoteVersion, r.remoteVersion, r.headPayloadHash, null)
            // Same version: only the derived fingerprint may move (R1-c); anything else is a rewrite.
            else if (r.remoteVersion == existing.remoteVersion &&
                existing.copy(derivedFingerprint = r.derivedFingerprint) != r.copy(receivedAt = existing.receivedAt))
                holds += hold(account, r.entityType, r.entityId, ReceiveCauses.REMOTE_REWRITTEN, h.id, existing.remoteVersion,
                    r.remoteVersion, r.headPayloadHash ?: r.ownFingerprint, "stored_hash=${existing.headPayloadHash ?: existing.ownFingerprint}")
        }
        return if (holds.size == before) Plan(bundle, localDay, dayDecision, decisions, receipts) else null
    }

    /** D7: clean = no open outbox operation, PREPARE or held receipt, not CONFLICT, and confirmed by the server once. */
    private suspend fun decide(local: LocalMetadata?, open: Boolean, incoming: Long, same: suspend () -> Boolean): Decision {
        if (local == null) return Decision.Insert
        val remote = local.remoteVersion
        if (open || local.syncStatus == SyncStatus.CONFLICT || remote == null)
            return Decision.Hold(if (remote != null && incoming > remote) ReceiveCauses.REMOTE_AHEAD else ReceiveCauses.LOCAL_PENDING)
        return when {
            incoming < remote -> Decision.Hold(ReceiveCauses.REMOTE_BEHIND)
            incoming == remote -> if (same()) Decision.Same else Decision.Hold(ReceiveCauses.REMOTE_REWRITTEN)
            else -> Decision.Update
        }
    }

    /** Any operation of the entity that has no settled outcome (codes that resolve without a request count as settled). */
    private suspend fun open(type: SyncEntityType, id: UUID): Boolean =
        database.syncOutboxDao().listForEntity(type, id).any { !settled(it) } ||
            (type == SyncEntityType.DELIVERY && database.deliveryHeldReceiptDao().countUnreconciled(id) > 0)

    private fun settled(op: SyncOutboxEntity) = op.status == OutboxStatus.CONFIRMED ||
        (op.status == OutboxStatus.BLOCKED && op.lastErrorCode in SETTLED_CODES)

    private suspend fun prepared(deliveryId: UUID) =
        database.deliveryDispatchPrepareDao().listForDelivery(deliveryId).any { it.settledAt == null }

    private suspend fun hasDependents(day: HarvestEntity, bundle: HarvestBundle, leaving: Set<UUID> = emptySet()): Boolean {
        val remoteDeleted = bundle.deliveries.filter { it.deletedAt != null }.map { it.id }.toSet()
        return day.collectionMethod != null || day.workerCount != null || day.machineryText != null || day.notes != null ||
            database.labourDao().listForHarvest(day.id).isNotEmpty() || database.equipmentDao().listForHarvest(day.id).isNotEmpty() ||
            database.expenseDao().listForHarvest(day.id).isNotEmpty() ||
            database.documentDao().countLiveForOwner(AttachmentOwnerType.HARVEST.name, day.id) > 0 ||
            database.deliveryDao().listLiveForHarvest(day.id).any { it.id !in remoteDeleted && it.id !in leaving }
    }

    private fun sameHarvest(local: HarvestEntity, h: ReceivedHarvest): Boolean {
        val manual = h.dayOrigin != AUTO_DAY
        return local.farmId == h.farmId && local.campaignId == h.campaignId && local.harvestDate == h.harvestDate &&
            local.dayOrigin == h.dayOrigin && local.destination == h.destination && local.notes == h.notes &&
            local.collectionMethod == h.collectionMethod && local.workerCount == h.workerCount && local.machineryText == h.machineryText &&
            (local.metadata.deletedAt != null) == (h.deletedAt != null) && (!manual || local.weightGrams == h.weightGrams)
    }

    private suspend fun sameDelivery(local: DeliveryEntity, d: ReceivedDelivery): Boolean {
        val incoming = HarvestReceiveVerifier.deliveryEntity(d, local.metadata.createdAt)
        val fields = local.copy(metadata = incoming.metadata) == incoming
        val localParcels = database.deliveryDao().listParcels(local.id).map { listOf(it.id, it.parcelId, it.campaignParcelId,
            it.parcelNameAtDelivery, it.weightGrams, it.allocationMode) }.toSet()
        val remoteParcels = d.parcels.map { listOf(it.id, it.parcelId, it.campaignParcelId, it.parcelNameAtDelivery, it.weightGrams,
            it.allocationMode) }.toSet()
        return fields && (local.metadata.deletedAt != null) == (d.deletedAt != null) && localParcels == remoteParcels
    }

    private fun synced(createdAt: Instant, updatedAt: Instant, deletedAt: Instant?, version: Long, at: Instant) =
        LocalMetadata(createdAt, updatedAt, deletedAt, version, SyncStatus.SYNCED, version, at)

    /** Remote UUID and version; an AUTO_DAY's kilos and split are derived afterwards (D5); a MANUAL keeps its typed kilos (D6). */
    private suspend fun writeHarvest(local: HarvestEntity?, h: ReceivedHarvest, at: Instant) {
        val auto = h.dayOrigin == AUTO_DAY
        database.harvestDao().upsert(HarvestEntity(id = h.id, workspaceId = h.workspaceId, campaignId = h.campaignId, farmId = h.farmId,
            harvestDate = h.harvestDate, weightGrams = if (auto) (local?.weightGrams ?: 0L) else h.weightGrams, destination = h.destination,
            notes = h.notes, collectionMethod = h.collectionMethod, workerCount = h.workerCount, machineryText = h.machineryText,
            metadata = synced(h.createdAt, h.updatedAt, h.deletedAt, h.version, at), dayOrigin = h.dayOrigin))
        // A deleted Jornada keeps no derived Parcels; a MANUAL's own split is never replaced from the server (H3).
        if (h.deletedAt != null && auto) database.harvestDao().deleteParcels(h.id)
    }

    /** The Pesada with its remote UUIDs and exactly its parcels (D7: the unit is the Pesada with all its parcels). */
    private suspend fun writeDelivery(d: ReceivedDelivery, at: Instant) {
        val createdAt = if (d.head?.payloadVersion == 3 && d.originCreatedAtExact != null) Instant.parse(d.originCreatedAtExact) else d.createdAt
        val row = HarvestReceiveVerifier.deliveryEntity(d, createdAt).let { it.copy(metadata = synced(createdAt, d.updatedAt, d.deletedAt, d.version, at)) }
        database.deliveryDao().upsert(row)
        val incoming = d.parcels.map { p ->
            DeliveryParcelEntity(id = p.id, workspaceId = p.workspaceId, deliveryId = p.deliveryId, parcelId = p.parcelId,
                campaignParcelId = p.campaignParcelId, parcelNameAtDelivery = p.parcelNameAtDelivery, weightGrams = p.weightGrams,
                allocationMode = p.allocationMode, metadata = synced(createdAt, d.updatedAt, null, d.version, at))
        }
        val stale = database.deliveryDao().listParcels(d.id).filter { old -> incoming.none { it.id == old.id } }.map { it.id }
        if (stale.isNotEmpty()) database.deliveryDao().deleteParcelsById(stale)
        if (incoming.isNotEmpty()) database.deliveryDao().upsertParcels(incoming)
    }

    private fun receiptsFor(account: String, bundle: HarvestBundle, at: Instant): List<SyncReceiveReceiptEntity> {
        val h = bundle.head
        val day = SyncReceiveReceiptEntity(issuer, account, HARVEST, bundle.harvest.id, bundle.harvest.id, h.version, h.deleted, h.headOperationId,
            h.headPayloadVersion, h.headPayloadHash, h.ownFingerprint, h.derivedFingerprint, 1, at.toEpochMilli())
        return listOf(day) + bundle.deliveries.map { d ->
            SyncReceiveReceiptEntity(issuer, account, DELIVERY, d.id, bundle.harvest.id, d.version, d.deletedAt != null, d.head?.operationId,
                d.head?.payloadVersion, d.head?.payloadHash, null, null, 1, at.toEpochMilli())
        }
    }

    private fun hold(account: String, type: String, id: UUID, cause: String, harvestId: UUID?, localVersion: Long?, remoteVersion: Long?,
        remoteHash: String?, detail: String?): SyncReceiveHoldEntity {
        val at = now().toEpochMilli()
        return SyncReceiveHoldEntity(issuer, account, type, id, cause, harvestId, localVersion, remoteVersion, remoteHash, detail, at, at)
    }

    private fun consented(userId: UUID, workspaceId: UUID, farmId: UUID): Boolean {
        val current = runCatching { ledger(userId) }.getOrNull() ?: return false
        if (current.subject != "$issuer|$userId" || farmId !in current.activeFarmIds || current.latestRevocation(farmId) != null) return false
        return current.consents.any { consent -> consent.farms.any { it.id == farmId && it.workspaceId == workspaceId } }
    }

    internal companion object {
        const val HARVEST = "HARVEST"
        const val DELIVERY = "DELIVERY"
        /** Terminal codes of operations that resolved without a request: they leave nothing open. */
        val SETTLED_CODES = setOf(HarvestCodes.RESOLVED_DERIVED_ONLY, HarvestCodes.RESOLVED_ADOPTED_NOOP, HarvestCodes.RESOLVED_NEVER_SENT,
            HarvestCodes.AUTO_DAY_REBASED, DeliveryDeleteCodes.RESOLVED_NEVER_SENT)
    }
}
