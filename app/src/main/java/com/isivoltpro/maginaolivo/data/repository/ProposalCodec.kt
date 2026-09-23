package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.domain.expense.PurchaseLine
import com.isivoltpro.maginaolivo.domain.ocr.DeliveryTicketProposal
import com.isivoltpro.maginaolivo.domain.ocr.PurchaseProposal
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/** Stores what the parser proposed as `extracted_json`, exactly as proposed. */
interface ProposalCodec {
    fun encode(proposal: PurchaseProposal): String

    fun decode(json: String): PurchaseProposal?

    fun encodeDelivery(proposal: DeliveryTicketProposal): String

    fun decodeDelivery(json: String): DeliveryTicketProposal?
}

class JsonProposalCodec : ProposalCodec {
    override fun encode(proposal: PurchaseProposal): String =
        JSONObject().apply {
            put("schema", "purchase_proposal_v1")
            putOpt("supplier_name", proposal.supplierName)
            putOpt("supplier_tax_id", proposal.supplierTaxId)
            putOpt("invoice_number", proposal.invoiceNumber)
            putOpt("invoice_date", proposal.invoiceDate?.toString())
            putOpt("subtotal_minor", proposal.subtotalMinor)
            putOpt("tax_minor", proposal.taxMinor)
            putOpt("total_minor", proposal.totalMinor)
            putOpt("currency", proposal.currency)
            put(
                "items",
                JSONArray().apply {
                    proposal.lines.forEach { line ->
                        put(
                            JSONObject().apply {
                                put("description", line.productName)
                                putOpt("quantity", line.quantity)
                                putOpt("unit", line.unit)
                                putOpt("unit_price_minor", line.unitPriceMinor)
                                putOpt("line_total_minor", line.lineTotalMinor)
                            },
                        )
                    }
                },
            )
        }.toString()

    override fun decode(json: String): PurchaseProposal? = runCatching {
        val root = JSONObject(json)
        if (root.optString("schema") == "delivery_ticket_v1") return@runCatching null
        val items = root.optJSONArray("items") ?: JSONArray()
        PurchaseProposal(
            supplierName = root.optStringOrNull("supplier_name"),
            supplierTaxId = root.optStringOrNull("supplier_tax_id"),
            invoiceNumber = root.optStringOrNull("invoice_number"),
            invoiceDate = root.optStringOrNull("invoice_date")?.let(LocalDate::parse),
            subtotalMinor = root.optLongOrNull("subtotal_minor"),
            taxMinor = root.optLongOrNull("tax_minor"),
            totalMinor = root.optLongOrNull("total_minor"),
            currency = root.optStringOrNull("currency"),
            lines = (0 until items.length()).map { index ->
                val item = items.getJSONObject(index)
                PurchaseLine(
                    productName = item.optString("description"),
                    quantity = if (item.has("quantity")) item.getDouble("quantity") else null,
                    unit = item.optStringOrNull("unit"),
                    unitPriceMinor = item.optLongOrNull("unit_price_minor"),
                    lineTotalMinor = item.optLongOrNull("line_total_minor"),
                )
            },
        )
    }.getOrNull()

    override fun encodeDelivery(proposal: DeliveryTicketProposal): String =
        JSONObject().apply {
            put("schema", "delivery_ticket_v1")
            putOpt("organization_name", proposal.organizationName)
            putOpt("ticket_number", proposal.ticketNumber)
            putOpt("delivery_date", proposal.deliveryDate?.toString())
            putOpt("gross_grams", proposal.grossGrams)
            putOpt("tare_grams", proposal.tareGrams)
            putOpt("net_grams", proposal.netGrams)
            putOpt("member_reference", proposal.memberReference)
            putOpt("vehicle_reference", proposal.vehicleReference)
        }.toString()

    override fun decodeDelivery(json: String): DeliveryTicketProposal? = runCatching {
        val root = JSONObject(json)
        if (root.optString("schema") != "delivery_ticket_v1") return@runCatching null
        DeliveryTicketProposal(
            organizationName = root.optStringOrNull("organization_name"),
            ticketNumber = root.optStringOrNull("ticket_number"),
            deliveryDate = root.optStringOrNull("delivery_date")?.let(LocalDate::parse),
            grossGrams = root.optLongOrNull("gross_grams"),
            tareGrams = root.optLongOrNull("tare_grams"),
            netGrams = root.optLongOrNull("net_grams"),
            memberReference = root.optStringOrNull("member_reference"),
            vehicleReference = root.optStringOrNull("vehicle_reference"),
        )
    }.getOrNull()

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) getLong(key) else null
}
