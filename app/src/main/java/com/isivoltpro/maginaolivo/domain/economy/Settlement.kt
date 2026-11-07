package com.isivoltpro.maginaolivo.domain.economy

import java.time.LocalDate
import java.util.UUID

/** CR-015 P5. An advance is money on account, never recognised income. */
enum class SettlementKind { ADVANCE, PROVISIONAL, FINAL }
enum class SettlementStatus { ACTIVE, CANCELLED }

/** Amounts are minor units of [currency]; net = gross − withholding − fees, all ≥ 0. */
data class Settlement(
    val id: UUID, val workspaceId: UUID, val farmId: UUID?, val cycleId: UUID?,
    val kind: SettlementKind, val cooperative: String, val documentDate: LocalDate, val currency: String,
    val grossMinor: Long, val withholdingMinor: Long, val feesMinor: Long, val netMinor: Long,
    val kilograms: Long? = null, val reference: String? = null, val replacesId: UUID? = null,
    val status: SettlementStatus = SettlementStatus.ACTIVE, val version: Long = 1,
) { val active get() = status == SettlementStatus.ACTIVE }

data class AdvanceApplication(val id: UUID, val advanceId: UUID, val settlementId: UUID, val amountMinor: Long,
    val active: Boolean = true)
data class SettlementPayment(val id: UUID, val settlementId: UUID, val date: LocalDate, val amountMinor: Long,
    val active: Boolean = true)

/** Workspace scope: farmId == null; farm scope: farmId; cycle scope: farmId + cycleId. */
data class EconomyScope(val workspaceId: UUID, val farmId: UUID? = null, val cycleId: UUID? = null)

data class EconomyLedger(
    val settlements: List<Settlement>, val applications: List<AdvanceApplication>, val payments: List<SettlementPayment>,
)

/** One currency, never converted. */
data class EconomyBalance(
    val currency: String,
    val recognisedGrossMinor: Long, val withholdingMinor: Long, val feesMinor: Long, val recognisedNetMinor: Long,
    val advancesUnappliedMinor: Long, val collectedMinor: Long, val pendingMinor: Long,
    val costsMinor: Long, val marginMinor: Long, val provisional: Boolean, val hasSettlements: Boolean,
)
