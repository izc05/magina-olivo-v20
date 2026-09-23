package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.domain.expense.PurchaseLine
import com.isivoltpro.maginaolivo.domain.ocr.PurchaseProposal
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/** Stores what the parser proposed as `extracted_json`, exactly as proposed. */
interface ProposalCodec {
    fun encode(proposal: PurchaseProposal): String

    fun decode(json: String): PurchaseProposal?
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

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) getLong(key) else null
}
