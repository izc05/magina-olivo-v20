package com.isivoltpro.maginaolivo.domain.activity

/**
 * #453: editing a work of the same type never erases what its form does not carry. The form
 * only ever sends the fields it shows; a stored value the form has no field for is history,
 * not a request to delete it. Changing the type is an explicit replacement and keeps nothing.
 */
object ActivityDetailPatch {
    fun keepingHidden(existing: ActivityDetail?, incoming: ActivityDetail?): ActivityDetail? = when {
        existing is ActivityDetail.Irrigation && incoming is ActivityDetail.Irrigation ->
            incoming.copy(price = incoming.price?.let { price -> existing.price?.let { price.keepingHidden(it) } ?: price })
        existing is ActivityDetail.Incident && incoming is ActivityDetail.Incident && incoming.state == existing.state ->
            incoming.copy(resolvedAt = incoming.resolvedAt ?: existing.resolvedAt)
        existing is ActivityDetail.Phytosanitary && incoming is ActivityDetail.Phytosanitary -> {
            val keepSnapshot = incoming.keepsProductSnapshot(existing)
            incoming.copy(
                operatorPersonId = incoming.operatorPersonId ?: existing.operatorPersonId,
                applicationMachineId = incoming.applicationMachineId ?: existing.applicationMachineId,
                serviceProviderOrganizationId = incoming.serviceProviderOrganizationId ?: existing.serviceProviderOrganizationId,
                productRegistrationNumber = incoming.productRegistrationNumber ?: existing.productRegistrationNumber.takeIf { keepSnapshot },
                productSource = incoming.productSource ?: existing.productSource.takeIf { keepSnapshot },
                productSourceVersion = incoming.productSourceVersion ?: existing.productSourceVersion.takeIf { keepSnapshot },
                productFetchedAt = incoming.productFetchedAt ?: existing.productFetchedAt.takeIf { keepSnapshot },
                authorizationContextSnapshot = incoming.authorizationContextSnapshot ?: existing.authorizationContextSnapshot.takeIf { keepSnapshot },
                pestProblemCode = incoming.pestProblemCode ?: existing.pestProblemCode,
                efficacyCode = incoming.efficacyCode ?: existing.efficacyCode,
                treatmentObservations = incoming.treatmentObservations ?: existing.treatmentObservations,
            )
        }
        else -> incoming
    }

    /** A legacy edit retains one historical block, never fragments from another product/selection. */
    private fun ActivityDetail.Phytosanitary.keepsProductSnapshot(stored: ActivityDetail.Phytosanitary): Boolean =
        productName == stored.productName && activeSubstance == stored.activeSubstance &&
            productRegistrationNumber == null && productSource == null && productSourceVersion == null &&
            productFetchedAt == null && authorizationContextSnapshot == null

    /**
     * The tariff form has no currency, notes or Gasto link: a historical price keeps its own
     * currency (never silently becomes EUR), its notes and its link to the real Expense.
     */
    private fun IrrigationPrice.keepingHidden(stored: IrrigationPrice) = copy(
        currency = stored.currency,
        linkedExpenseId = linkedExpenseId ?: stored.linkedExpenseId,
        notes = notes ?: stored.notes,
    )
}
