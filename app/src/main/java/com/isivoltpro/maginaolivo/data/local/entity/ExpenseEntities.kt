package com.isivoltpro.maginaolivo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** `DATA-MODEL-RC1.1-ADDENDUM` §3: one organization, many roles, never duplicated. */
@Entity(
    tableName = "agricultural_organizations",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index(value = ["workspace_id"])],
)
data class AgriculturalOrganizationEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    val name: String,
    @ColumnInfo(name = "tax_id") val taxId: String? = null,
    val municipality: String? = null,
    val province: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val notes: String? = null,
    @Embedded val metadata: LocalMetadata,
)

/** Roles are children of the organization aggregate and are replaced with it. */
@Entity(
    tableName = "organization_roles",
    primaryKeys = ["organization_id", "role"],
    foreignKeys = [
        ForeignKey(
            entity = AgriculturalOrganizationEntity::class,
            parentColumns = ["id"],
            childColumns = ["organization_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["role"])],
)
data class OrganizationRoleEntity(
    @ColumnInfo(name = "organization_id") val organizationId: UUID,
    val role: String,
)

/** `DATA-MODEL-RC1.1-ADDENDUM` §8: what was bought. Money stays in the linked Expense. */
@Entity(
    tableName = "purchases",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expense_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["expense_id"], unique = true),
        Index(value = ["workspace_id"]),
    ],
)
data class PurchaseEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "expense_id") val expenseId: UUID,
    @ColumnInfo(name = "supplier_organization_id") val supplierOrganizationId: UUID? = null,
    @ColumnInfo(name = "purchase_date") val purchaseDate: LocalDate,
    @ColumnInfo(name = "invoice_number") val invoiceNumber: String? = null,
    val notes: String? = null,
    @Embedded val metadata: LocalMetadata,
)

@Entity(
    tableName = "purchase_items",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchase_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["purchase_id"])],
)
data class PurchaseItemEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "purchase_id") val purchaseId: UUID,
    val position: Int,
    @ColumnInfo(name = "product_name") val productName: String,
    val quantity: Double? = null,
    val unit: String? = null,
    @ColumnInfo(name = "unit_price_minor") val unitPriceMinor: Long? = null,
    @ColumnInfo(name = "line_total_minor") val lineTotalMinor: Long? = null,
    @Embedded val metadata: LocalMetadata,
)

/**
 * `DATA-MODEL-RC1.2-ADDENDUM` §3: one OCR model for every document type. The attachment is
 * the immutable source; the extraction is assistive data that never becomes money by itself.
 */
@Entity(
    tableName = "document_ocr_extractions",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["attachment_id"]),
        Index(value = ["workspace_id", "status"]),
    ],
)
data class DocumentOcrExtractionEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
    @ColumnInfo(name = "attachment_id") val attachmentId: UUID,
    @ColumnInfo(name = "document_type") val documentType: String,
    @ColumnInfo(name = "owner_type") val ownerType: String? = null,
    @ColumnInfo(name = "owner_id") val ownerId: UUID? = null,
    val engine: String,
    @ColumnInfo(name = "engine_version") val engineVersion: String? = null,
    @ColumnInfo(name = "raw_text") val rawText: String? = null,
    @ColumnInfo(name = "extracted_json") val extractedJson: String? = null,
    @ColumnInfo(name = "confidence_json") val confidenceJson: String? = null,
    val status: String,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: Instant? = null,
    @ColumnInfo(name = "confirmed_at") val confirmedAt: Instant? = null,
    @Embedded val metadata: LocalMetadata,
)
