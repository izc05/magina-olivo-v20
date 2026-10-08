package com.isivoltpro.maginaolivo.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                schemaVersion2Statements.forEach(db::execSQL)
            }
        }

    val MIGRATION_2_3 =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE `campaigns` SET `status` = 'PREPARATION' WHERE `status` = 'PLANNED'")
                schemaVersion3Statements.forEach(db::execSQL)
            }
        }

    val MIGRATION_3_4 =
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                schemaVersion4Statements.forEach(db::execSQL)
            }
        }

    /**
     * Typed agronomic details (Phase 10).
     *
     * Purely additive: eight new child tables and their indices. No existing table is
     * rewritten, no column is dropped and no row is touched, so every Activity and every
     * `activity_parcels` relation created before this version survives untouched.
     */
    val MIGRATION_4_5 =
        object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                schemaVersion5Statements.forEach(db::execSQL)
            }
        }

    /**
     * Phase 12. `expenses` gains its Activity/Harvest/Delivery/supplier links plus the
     * DRAFT/POSTED status and the origin marker. SQLite cannot add a NOT NULL column
     * without a default, so the table is rebuilt and every existing row is carried over
     * as POSTED/MANUAL — the only kind of expense that could exist before this phase.
     * The five new tables are created empty.
     */
    val MIGRATION_5_6 =
        object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                schemaVersion6Statements.forEach(db::execSQL)
            }
        }

    /**
     * v7 — Harvest (Phase 13). `harvests` already existed; it gains its collection
     * fields as nullable columns, so every harvest row is kept as it was. Its origin
     * Parcels arrive in `harvest_parcels`, a child of the Harvest aggregate (D6).
     */
    val MIGRATION_6_7 =
        object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                schemaVersion7Statements.forEach(db::execSQL)
            }
        }

    /**
     * v8 — Deliveries (Phase 14). Three new tables, created empty: deliveries, their origin
     * Parcels, and the yield analyses that arrive later as separate records.
     */
    val MIGRATION_7_8 =
        object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                schemaVersion8Statements.forEach(db::execSQL)
            }
        }

    /** v9 — Machinery (Phase 15): machines and the machines each Activity used, created empty. */
    val MIGRATION_8_9 =
        object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                schemaVersion9Statements.forEach(db::execSQL)
            }
        }

    val MIGRATION_9_10 =
        object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                schemaVersion10Statements.forEach(db::execSQL)
            }
        }

    /** v11 — CR-004: optional grove description on each parcel; existing rows keep NULL ("—"). */
    val MIGRATION_10_11 =
        object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                schemaVersion11Statements.forEach(db::execSQL)
            }
        }

    /**
     * Phase 18: provenance of an imported Parcel (which land registry, when). Rows imported
     * from Catastro before this version are backfilled with their creation time, which is
     * when the confirmed import wrote them; manual rows stay NULL.
     */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE parcels ADD COLUMN source_provider TEXT")
            db.execSQL("ALTER TABLE parcels ADD COLUMN source_imported_at INTEGER")
            db.execSQL("UPDATE parcels SET source_provider = 'ES_CATASTRO', source_imported_at = created_at WHERE source = 'CATASTRO'")
        }
    }

    /**
     * Phase 19B: a Pesada (Delivery) may belong to one Jornada (Harvest) and keep the hour it
     * was weighed. Existing Deliveries stay unlinked, without an hour: nothing is guessed.
     */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE deliveries ADD COLUMN harvest_id TEXT")
            db.execSQL("ALTER TABLE deliveries ADD COLUMN delivery_time TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_deliveries_harvest_id` ON `deliveries` (`harvest_id`)")
        }
    }

    /** Phase 19D: reusable people and the labour (jornales) of each Jornada. No money here. */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `workers` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "$METADATA_COLUMNS, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_workers_workspace_id` ON `workers` (`workspace_id`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `harvest_labour` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `harvest_id` TEXT NOT NULL, " +
                    "`worker_id` TEXT, `worker_name` TEXT, `quantity` INTEGER NOT NULL, `unit` TEXT NOT NULL, `minutes` INTEGER, `notes` TEXT, " +
                    "$METADATA_COLUMNS, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                    "FOREIGN KEY(`harvest_id`) REFERENCES `harvests`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                    "FOREIGN KEY(`worker_id`) REFERENCES `workers`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_harvest_labour_harvest_id` ON `harvest_labour` (`harvest_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_harvest_labour_worker_id` ON `harvest_labour` (`worker_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_harvest_labour_workspace_id` ON `harvest_labour` (`workspace_id`)")
        }
    }

    /** Phase 19E: equipment used on each Jornada, by type and quantity or a registered Machine. */
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `harvest_equipment` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `harvest_id` TEXT NOT NULL, " +
                    "`type` TEXT NOT NULL, `label` TEXT, `quantity` INTEGER NOT NULL, `machine_id` TEXT, `notes` TEXT, " +
                    "$METADATA_COLUMNS, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                    "FOREIGN KEY(`harvest_id`) REFERENCES `harvests`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                    "FOREIGN KEY(`machine_id`) REFERENCES `machines`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_harvest_equipment_harvest_id` ON `harvest_equipment` (`harvest_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_harvest_equipment_machine_id` ON `harvest_equipment` (`machine_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_harvest_equipment_workspace_id` ON `harvest_equipment` (`workspace_id`)")
        }
    }

    /** Issue #254: where a Pesada's olives were picked (árbol/vuelo or suelo); older rows stay null. */
    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE deliveries ADD COLUMN harvest_origin TEXT")
        }
    }

    /**
     * CR-010 (A1, A4): marks the days the app creates for its Pesadas. A Jornada stored with 0 kg
     * can only be one opened before its first Pesada («Kg pendientes de pesada»; a hand-recorded
     * harvest is always > 0), so it is such a day: it has no typed kilos to lose. Every other
     * row keeps its kilos and stays a hand-recorded Jornada.
     */
    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE harvests ADD COLUMN day_origin TEXT")
            db.execSQL("UPDATE harvests SET day_origin = 'AUTO_DAY' WHERE weight_grams = 0")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_harvests_farm_id_campaign_id_harvest_date` " +
                    "ON `harvests` (`farm_id`, `campaign_id`, `harvest_date`)",
            )
        }
    }

    /** CR-010 (A3, A4): each Farm's optional recollection prices; an empty table, nothing guessed. */
    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recollection_rates` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `farm_id` TEXT NOT NULL, " +
                    "`currency` TEXT NOT NULL, `full_day_minor` INTEGER, `hourly_minor` INTEGER, `equipment_day_json` TEXT NOT NULL, " +
                    "$METADATA_COLUMNS, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recollection_rates_workspace_id` ON `recollection_rates` (`workspace_id`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recollection_rates_farm_id` ON `recollection_rates` (`farm_id`)")
        }
    }

    /** Phase 21A (Room v19): the farmer's municipality and preferred cooperative; an empty table. */
    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `profile_settings` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, " +
                    "`municipality` TEXT, `province` TEXT, `preferred_organization_id` TEXT, " +
                    "$METADATA_COLUMNS, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_profile_settings_workspace_id` ON `profile_settings` (`workspace_id`)")
        }
    }

    /** Phase 21B (Room v20): reminder preferences; reminders stay on, the day-before hour is 08:00. */
    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `profile_settings` ADD COLUMN `reminders_enabled` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `profile_settings` ADD COLUMN `previous_day_reminder_minute` INTEGER NOT NULL DEFAULT 480")
        }
    }

    /** CR-012: no assumed prices or debts are backfilled into historical labour. */
    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `harvest_labour` ADD COLUMN `applied_price_minor` INTEGER")
            db.execSQL("ALTER TABLE `harvest_labour` ADD COLUMN `applied_currency` TEXT")
            db.execSQL("ALTER TABLE `harvest_labour` ADD COLUMN `applied_price_date` TEXT")
            db.execSQL("ALTER TABLE `harvest_labour` ADD COLUMN `applied_basis` TEXT")
            db.execSQL("CREATE TABLE IF NOT EXISTS `labour_payments` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `worker_id` TEXT NOT NULL, `campaign_id` TEXT NOT NULL, `payment_date` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `note` TEXT, $METADATA_COLUMNS, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`worker_id`) REFERENCES `workers`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`campaign_id`) REFERENCES `campaigns`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_labour_payments_workspace_id` ON `labour_payments` (`workspace_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_labour_payments_worker_id` ON `labour_payments` (`worker_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_labour_payments_campaign_id_payment_date` ON `labour_payments` (`campaign_id`, `payment_date`)")
        }
    }

    /** CR-012 machinery: legacy use has no assumed historical price. */
    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `harvest_equipment` ADD COLUMN `applied_price_minor` INTEGER")
            db.execSQL("ALTER TABLE `harvest_equipment` ADD COLUMN `applied_currency` TEXT")
            db.execSQL("ALTER TABLE `harvest_equipment` ADD COLUMN `applied_price_date` TEXT")
        }
    }

    /** #547 / CUE V9: optional inclusive end date; legacy rows remain one-day records. */
    val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `activities` ADD COLUMN `activity_end_date` TEXT")
        }
    }

    /** #544 / CUE V9: reusable applicators/advisors and regulatory Machine resources. */
    val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `agronomic_people` (
                    `id` TEXT NOT NULL,
                    `workspace_id` TEXT NOT NULL,
                    `display_name` TEXT NOT NULL,
                    `given_name` TEXT,
                    `family_name` TEXT,
                    `tax_id` TEXT,
                    `is_advisor` INTEGER NOT NULL,
                    `source` TEXT NOT NULL,
                    `external_id` TEXT,
                    `source_version` TEXT,
                    `fetched_at` INTEGER,
                    `status` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `deleted_at` INTEGER,
                    `version` INTEGER NOT NULL,
                    `sync_status` TEXT NOT NULL,
                    `remote_version` INTEGER,
                    `last_synced_at` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_agronomic_people_workspace_id_status` ON `agronomic_people` (`workspace_id`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_agronomic_people_workspace_id_tax_id` ON `agronomic_people` (`workspace_id`, `tax_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_agronomic_people_workspace_id_external_id` ON `agronomic_people` (`workspace_id`, `external_id`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `agronomic_credentials` (
                    `id` TEXT NOT NULL,
                    `workspace_id` TEXT NOT NULL,
                    `person_id` TEXT NOT NULL,
                    `credential_type` TEXT NOT NULL,
                    `number` TEXT NOT NULL,
                    `category_code` TEXT,
                    `valid_from` TEXT,
                    `valid_until` TEXT,
                    `source` TEXT NOT NULL,
                    `external_id` TEXT,
                    `source_version` TEXT,
                    `fetched_at` INTEGER,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `deleted_at` INTEGER,
                    `version` INTEGER NOT NULL,
                    `sync_status` TEXT NOT NULL,
                    `remote_version` INTEGER,
                    `last_synced_at` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`person_id`) REFERENCES `agronomic_people`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_agronomic_credentials_person_id_valid_from` ON `agronomic_credentials` (`person_id`, `valid_from`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_agronomic_credentials_workspace_id` ON `agronomic_credentials` (`workspace_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_agronomic_credentials_workspace_id_external_id` ON `agronomic_credentials` (`workspace_id`, `external_id`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `phytosanitary_equipment_profiles` (
                    `machine_id` TEXT NOT NULL,
                    `workspace_id` TEXT NOT NULL,
                    `roma_registration` TEXT,
                    `census_reference` TEXT,
                    `acquisition_date` TEXT,
                    `regulatory_type_code` TEXT,
                    `source` TEXT NOT NULL,
                    `external_id` TEXT,
                    `source_version` TEXT,
                    `fetched_at` INTEGER,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `deleted_at` INTEGER,
                    `version` INTEGER NOT NULL,
                    `sync_status` TEXT NOT NULL,
                    `remote_version` INTEGER,
                    `last_synced_at` INTEGER,
                    PRIMARY KEY(`machine_id`),
                    FOREIGN KEY(`machine_id`) REFERENCES `machines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_phytosanitary_equipment_profiles_workspace_id` ON `phytosanitary_equipment_profiles` (`workspace_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_phytosanitary_equipment_profiles_workspace_id_external_id` ON `phytosanitary_equipment_profiles` (`workspace_id`, `external_id`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `phytosanitary_equipment_inspections` (
                    `id` TEXT NOT NULL,
                    `workspace_id` TEXT NOT NULL,
                    `machine_id` TEXT NOT NULL,
                    `inspection_date` TEXT NOT NULL,
                    `result_code` TEXT,
                    `certificate_reference` TEXT,
                    `source` TEXT NOT NULL,
                    `external_id` TEXT,
                    `source_version` TEXT,
                    `fetched_at` INTEGER,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `deleted_at` INTEGER,
                    `version` INTEGER NOT NULL,
                    `sync_status` TEXT NOT NULL,
                    `remote_version` INTEGER,
                    `last_synced_at` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`machine_id`) REFERENCES `machines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_phytosanitary_equipment_inspections_machine_id_inspection_date` ON `phytosanitary_equipment_inspections` (`machine_id`, `inspection_date`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_phytosanitary_equipment_inspections_workspace_id` ON `phytosanitary_equipment_inspections` (`workspace_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_phytosanitary_equipment_inspections_workspace_id_external_id` ON `phytosanitary_equipment_inspections` (`workspace_id`, `external_id`)")
        }
    }

    /** #677 / CUE v25: structured treatment refs + immutable regulatory snapshot fields. */
    val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `phytosanitary_details` ADD COLUMN `operator_person_id` TEXT")
            db.execSQL("ALTER TABLE `phytosanitary_details` ADD COLUMN `application_machine_id` TEXT")
            db.execSQL("ALTER TABLE `phytosanitary_details` ADD COLUMN `service_provider_organization_id` TEXT")
            db.execSQL("ALTER TABLE `phytosanitary_details` ADD COLUMN `product_registration_number` TEXT")
            db.execSQL("ALTER TABLE `phytosanitary_details` ADD COLUMN `product_source` TEXT")
            db.execSQL("ALTER TABLE `phytosanitary_details` ADD COLUMN `product_source_version` TEXT")
            db.execSQL("ALTER TABLE `phytosanitary_details` ADD COLUMN `product_fetched_at` INTEGER")
            db.execSQL("ALTER TABLE `phytosanitary_details` ADD COLUMN `authorization_context_snapshot` TEXT")
            db.execSQL("ALTER TABLE `phytosanitary_details` ADD COLUMN `pest_problem_code` TEXT")
            db.execSQL("ALTER TABLE `phytosanitary_details` ADD COLUMN `efficacy_code` TEXT")
            db.execSQL("ALTER TABLE `phytosanitary_details` ADD COLUMN `treatment_observations` TEXT")
        }
    }

    val all: Array<Migration> =
        arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
            MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25,
        )

    private val schemaVersion11Statements =
        arrayOf(
            "ALTER TABLE `parcels` ADD COLUMN `olive_tree_count` INTEGER",
            "ALTER TABLE `parcels` ADD COLUMN `variety` TEXT",
            "ALTER TABLE `parcels` ADD COLUMN `irrigation_system` TEXT",
            "ALTER TABLE `parcels` ADD COLUMN `irrigation_network` TEXT",
            "ALTER TABLE `parcels` ADD COLUMN `irrigation_sector` TEXT",
            "ALTER TABLE `parcels` ADD COLUMN `irrigation_days` TEXT",
        )

    private const val METADATA_COLUMNS =
        "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `version` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `remote_version` INTEGER, `last_synced_at` INTEGER"

    private val schemaVersion10Statements =
        arrayOf(
            "CREATE TABLE IF NOT EXISTS `activity_planning_details` (`activity_id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `planned_start_time` TEXT, `expected_duration_minutes` INTEGER, `expected_people_count` INTEGER, `provider_organization_id` TEXT, `crew_text` TEXT, $METADATA_COLUMNS, PRIMARY KEY(`activity_id`), FOREIGN KEY(`activity_id`) REFERENCES `activities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_activity_planning_details_workspace_id` ON `activity_planning_details` (`workspace_id`)",
            "CREATE TABLE IF NOT EXISTS `reminders` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `owner_type` TEXT NOT NULL, `owner_id` TEXT NOT NULL, `trigger_at` INTEGER NOT NULL, `kind` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `local_notification_id` INTEGER NOT NULL, `fired_at` INTEGER, $METADATA_COLUMNS, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            "CREATE INDEX IF NOT EXISTS `index_reminders_workspace_id` ON `reminders` (`workspace_id`)",
            "CREATE INDEX IF NOT EXISTS `index_reminders_owner_type_owner_id` ON `reminders` (`owner_type`, `owner_id`)",
            "CREATE INDEX IF NOT EXISTS `index_reminders_enabled_trigger_at` ON `reminders` (`enabled`, `trigger_at`)",
        )

    private val schemaVersion9Statements =
        arrayOf(
            "CREATE TABLE IF NOT EXISTS `machines` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `make` TEXT, `model` TEXT, `registration_or_serial` TEXT, `current_hours` REAL, `notes` TEXT, `status` TEXT NOT NULL, $METADATA_COLUMNS, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            "CREATE INDEX IF NOT EXISTS `index_machines_workspace_id_status` ON `machines` (`workspace_id`, `status`)",
            "CREATE TABLE IF NOT EXISTS `activity_machines` (`activity_id` TEXT NOT NULL, `machine_id` TEXT NOT NULL, `start_hours` REAL, `end_hours` REAL, `usage_hours` REAL, PRIMARY KEY(`activity_id`, `machine_id`), FOREIGN KEY(`activity_id`) REFERENCES `activities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`machine_id`) REFERENCES `machines`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            "CREATE INDEX IF NOT EXISTS `index_activity_machines_machine_id` ON `activity_machines` (`machine_id`)",
        )

    private val schemaVersion8Statements =
        arrayOf(
            "CREATE TABLE IF NOT EXISTS `deliveries` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `farm_id` TEXT NOT NULL, `campaign_id` TEXT NOT NULL, `delivery_date` TEXT NOT NULL, `destination_organization_id` TEXT, `destination_name` TEXT NOT NULL, `net_grams` INTEGER NOT NULL, `gross_grams` INTEGER, `tare_grams` INTEGER, `delivery_number` TEXT, `ticket_number` TEXT, `source` TEXT NOT NULL, `notes` TEXT, $METADATA_COLUMNS, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            "CREATE INDEX IF NOT EXISTS `index_deliveries_workspace_id_delivery_date` ON `deliveries` (`workspace_id`, `delivery_date`)",
            "CREATE INDEX IF NOT EXISTS `index_deliveries_campaign_id_delivery_date` ON `deliveries` (`campaign_id`, `delivery_date`)",
            "CREATE TABLE IF NOT EXISTS `delivery_parcels` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `delivery_id` TEXT NOT NULL, `parcel_id` TEXT NOT NULL, `campaign_parcel_id` TEXT, `parcel_name_at_delivery` TEXT NOT NULL, `weight_grams` INTEGER, `allocation_mode` TEXT NOT NULL, $METADATA_COLUMNS, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`delivery_id`) REFERENCES `deliveries`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`parcel_id`) REFERENCES `parcels`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_delivery_parcels_delivery_id_parcel_id` ON `delivery_parcels` (`delivery_id`, `parcel_id`)",
            "CREATE INDEX IF NOT EXISTS `index_delivery_parcels_workspace_id` ON `delivery_parcels` (`workspace_id`)",
            "CREATE INDEX IF NOT EXISTS `index_delivery_parcels_parcel_id` ON `delivery_parcels` (`parcel_id`)",
            "CREATE TABLE IF NOT EXISTS `delivery_yield_analyses` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `delivery_id` TEXT NOT NULL, `analysis_date` TEXT, `fat_yield_hundredths` INTEGER, `industrial_yield_hundredths` INTEGER, `source_attachment_id` TEXT, `notes` TEXT, $METADATA_COLUMNS, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`delivery_id`) REFERENCES `deliveries`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            "CREATE INDEX IF NOT EXISTS `index_delivery_yield_analyses_delivery_id` ON `delivery_yield_analyses` (`delivery_id`)",
            "CREATE INDEX IF NOT EXISTS `index_delivery_yield_analyses_workspace_id` ON `delivery_yield_analyses` (`workspace_id`)",
        )

    private val schemaVersion7Statements =
        arrayOf(
            "ALTER TABLE `harvests` ADD COLUMN `collection_method` TEXT",
            "ALTER TABLE `harvests` ADD COLUMN `worker_count` INTEGER",
            "ALTER TABLE `harvests` ADD COLUMN `machinery_text` TEXT",
            "CREATE TABLE IF NOT EXISTS `harvest_parcels` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `harvest_id` TEXT NOT NULL, `parcel_id` TEXT NOT NULL, `campaign_parcel_id` TEXT, `parcel_name_at_harvest` TEXT NOT NULL, `weight_grams` INTEGER, `allocation_mode` TEXT NOT NULL, $METADATA_COLUMNS, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`harvest_id`) REFERENCES `harvests`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`parcel_id`) REFERENCES `parcels`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_harvest_parcels_harvest_id_parcel_id` ON `harvest_parcels` (`harvest_id`, `parcel_id`)",
            "CREATE INDEX IF NOT EXISTS `index_harvest_parcels_workspace_id` ON `harvest_parcels` (`workspace_id`)",
            "CREATE INDEX IF NOT EXISTS `index_harvest_parcels_parcel_id` ON `harvest_parcels` (`parcel_id`)",
        )

    private val schemaVersion6Statements =
        arrayOf(
            "CREATE TABLE IF NOT EXISTS `expenses_new` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `campaign_id` TEXT, `farm_id` TEXT, `parcel_id` TEXT, `activity_id` TEXT, `harvest_id` TEXT, `delivery_id` TEXT, `supplier_organization_id` TEXT, `expense_date` TEXT NOT NULL, `concept` TEXT NOT NULL, `category` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `provider` TEXT, `notes` TEXT, `status` TEXT NOT NULL, `origin` TEXT NOT NULL, $METADATA_COLUMNS, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            "INSERT INTO `expenses_new` (`id`, `workspace_id`, `campaign_id`, `farm_id`, `parcel_id`, `expense_date`, `concept`, `category`, `amount_minor`, `currency`, `provider`, `notes`, `status`, `origin`, `created_at`, `updated_at`, `deleted_at`, `version`, `sync_status`, `remote_version`, `last_synced_at`) SELECT `id`, `workspace_id`, `campaign_id`, `farm_id`, `parcel_id`, `expense_date`, `concept`, `category`, `amount_minor`, `currency`, `provider`, `notes`, 'POSTED', 'MANUAL', `created_at`, `updated_at`, `deleted_at`, `version`, `sync_status`, `remote_version`, `last_synced_at` FROM `expenses`",
            "DROP TABLE `expenses`",
            "ALTER TABLE `expenses_new` RENAME TO `expenses`",
            "CREATE INDEX IF NOT EXISTS `index_expenses_workspace_id_expense_date` ON `expenses` (`workspace_id`, `expense_date`)",
            "CREATE INDEX IF NOT EXISTS `index_expenses_campaign_id_expense_date` ON `expenses` (`campaign_id`, `expense_date`)",
            "CREATE INDEX IF NOT EXISTS `index_expenses_farm_id_expense_date` ON `expenses` (`farm_id`, `expense_date`)",
            "CREATE INDEX IF NOT EXISTS `index_expenses_parcel_id_expense_date` ON `expenses` (`parcel_id`, `expense_date`)",
            "CREATE INDEX IF NOT EXISTS `index_expenses_activity_id` ON `expenses` (`activity_id`)",
            "CREATE INDEX IF NOT EXISTS `index_expenses_workspace_id_status` ON `expenses` (`workspace_id`, `status`)",
            "CREATE TABLE IF NOT EXISTS `agricultural_organizations` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `name` TEXT NOT NULL, `tax_id` TEXT, `municipality` TEXT, `province` TEXT, `address` TEXT, `phone` TEXT, `website` TEXT, `notes` TEXT, $METADATA_COLUMNS, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            "CREATE INDEX IF NOT EXISTS `index_agricultural_organizations_workspace_id` ON `agricultural_organizations` (`workspace_id`)",
            "CREATE TABLE IF NOT EXISTS `organization_roles` (`organization_id` TEXT NOT NULL, `role` TEXT NOT NULL, PRIMARY KEY(`organization_id`, `role`), FOREIGN KEY(`organization_id`) REFERENCES `agricultural_organizations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_organization_roles_role` ON `organization_roles` (`role`)",
            "CREATE TABLE IF NOT EXISTS `purchases` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `expense_id` TEXT NOT NULL, `supplier_organization_id` TEXT, `purchase_date` TEXT NOT NULL, `invoice_number` TEXT, `notes` TEXT, $METADATA_COLUMNS, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`expense_id`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_purchases_expense_id` ON `purchases` (`expense_id`)",
            "CREATE INDEX IF NOT EXISTS `index_purchases_workspace_id` ON `purchases` (`workspace_id`)",
            "CREATE TABLE IF NOT EXISTS `purchase_items` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `purchase_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `product_name` TEXT NOT NULL, `quantity` REAL, `unit` TEXT, `unit_price_minor` INTEGER, `line_total_minor` INTEGER, $METADATA_COLUMNS, PRIMARY KEY(`id`), FOREIGN KEY(`purchase_id`) REFERENCES `purchases`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_purchase_items_purchase_id` ON `purchase_items` (`purchase_id`)",
            "CREATE TABLE IF NOT EXISTS `document_ocr_extractions` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `attachment_id` TEXT NOT NULL, `document_type` TEXT NOT NULL, `owner_type` TEXT, `owner_id` TEXT, `engine` TEXT NOT NULL, `engine_version` TEXT, `raw_text` TEXT, `extracted_json` TEXT, `confidence_json` TEXT, `status` TEXT NOT NULL, `reviewed_at` INTEGER, `confirmed_at` INTEGER, $METADATA_COLUMNS, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            "CREATE INDEX IF NOT EXISTS `index_document_ocr_extractions_attachment_id` ON `document_ocr_extractions` (`attachment_id`)",
            "CREATE INDEX IF NOT EXISTS `index_document_ocr_extractions_workspace_id_status` ON `document_ocr_extractions` (`workspace_id`, `status`)",
        )

    private val schemaVersion5Statements =
        arrayOf(
            """CREATE TABLE IF NOT EXISTS `pruning_details` (`activity_id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `pruning_type` TEXT, `worker_count` INTEGER, `hours` REAL, `residue_management` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `version` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `remote_version` INTEGER, `last_synced_at` INTEGER, PRIMARY KEY(`activity_id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`activity_id`) REFERENCES `activities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
            """CREATE INDEX IF NOT EXISTS `index_pruning_details_workspace_id` ON `pruning_details` (`workspace_id`)""",
            """CREATE TABLE IF NOT EXISTS `fertilization_details` (`activity_id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `product_name` TEXT, `product_id` TEXT, `total_quantity` REAL, `unit` TEXT, `dose_value` REAL, `dose_unit` TEXT, `application_method` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `version` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `remote_version` INTEGER, `last_synced_at` INTEGER, PRIMARY KEY(`activity_id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`activity_id`) REFERENCES `activities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
            """CREATE INDEX IF NOT EXISTS `index_fertilization_details_workspace_id` ON `fertilization_details` (`workspace_id`)""",
            """CREATE TABLE IF NOT EXISTS `phytosanitary_details` (`activity_id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `product_name` TEXT, `product_id` TEXT, `active_substance` TEXT, `total_quantity` REAL, `unit` TEXT, `dose_value` REAL, `dose_unit` TEXT, `reason` TEXT, `equipment_text` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `version` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `remote_version` INTEGER, `last_synced_at` INTEGER, PRIMARY KEY(`activity_id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`activity_id`) REFERENCES `activities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
            """CREATE INDEX IF NOT EXISTS `index_phytosanitary_details_workspace_id` ON `phytosanitary_details` (`workspace_id`)""",
            """CREATE TABLE IF NOT EXISTS `soil_work_details` (`activity_id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `work_type` TEXT, `method` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `version` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `remote_version` INTEGER, `last_synced_at` INTEGER, PRIMARY KEY(`activity_id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`activity_id`) REFERENCES `activities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
            """CREATE INDEX IF NOT EXISTS `index_soil_work_details_workspace_id` ON `soil_work_details` (`workspace_id`)""",
            """CREATE TABLE IF NOT EXISTS `irrigation_details` (`activity_id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `duration_minutes` INTEGER, `volume_m3` REAL, `sector_text` TEXT, `system_text` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `version` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `remote_version` INTEGER, `last_synced_at` INTEGER, PRIMARY KEY(`activity_id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`activity_id`) REFERENCES `activities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
            """CREATE INDEX IF NOT EXISTS `index_irrigation_details_workspace_id` ON `irrigation_details` (`workspace_id`)""",
            """CREATE TABLE IF NOT EXISTS `irrigation_price_snapshots` (`activity_id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `pricing_basis` TEXT NOT NULL, `unit_price_minor` INTEGER, `quantity` REAL, `estimated_amount_minor` INTEGER, `currency` TEXT NOT NULL, `price_date` TEXT NOT NULL, `linked_expense_id` TEXT, `notes` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `version` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `remote_version` INTEGER, `last_synced_at` INTEGER, PRIMARY KEY(`activity_id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`activity_id`) REFERENCES `activities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
            """CREATE INDEX IF NOT EXISTS `index_irrigation_price_snapshots_workspace_id` ON `irrigation_price_snapshots` (`workspace_id`)""",
            """CREATE TABLE IF NOT EXISTS `maintenance_details` (`activity_id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `maintenance_type` TEXT, `asset_text` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `version` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `remote_version` INTEGER, `last_synced_at` INTEGER, PRIMARY KEY(`activity_id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`activity_id`) REFERENCES `activities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
            """CREATE INDEX IF NOT EXISTS `index_maintenance_details_workspace_id` ON `maintenance_details` (`workspace_id`)""",
            """CREATE TABLE IF NOT EXISTS `incident_details` (`activity_id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `category` TEXT, `severity` TEXT, `incident_status` TEXT NOT NULL, `location_geometry` TEXT, `action_taken` TEXT, `resolved_at` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `version` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `remote_version` INTEGER, `last_synced_at` INTEGER, PRIMARY KEY(`activity_id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`activity_id`) REFERENCES `activities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
            """CREATE INDEX IF NOT EXISTS `index_incident_details_workspace_id` ON `incident_details` (`workspace_id`)""",
        )

    private val schemaVersion4Statements =
        arrayOf(
            """CREATE TABLE IF NOT EXISTS `activity_parcels` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `activity_id` TEXT NOT NULL, `parcel_id` TEXT NOT NULL, `parcel_name_at_target` TEXT NOT NULL, `area_affected_m2` REAL, `notes` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `version` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `remote_version` INTEGER, `last_synced_at` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`activity_id`) REFERENCES `activities`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`parcel_id`) REFERENCES `parcels`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
            """CREATE UNIQUE INDEX IF NOT EXISTS `index_activity_parcels_activity_id_parcel_id` ON `activity_parcels` (`activity_id`, `parcel_id`)""",
            """CREATE INDEX IF NOT EXISTS `index_activity_parcels_workspace_id` ON `activity_parcels` (`workspace_id`)""",
            """CREATE INDEX IF NOT EXISTS `index_activity_parcels_parcel_id` ON `activity_parcels` (`parcel_id`)""",
        )

    private val schemaVersion3Statements =
        arrayOf(
            """CREATE TABLE IF NOT EXISTS `campaign_parcels` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `campaign_id` TEXT NOT NULL, `parcel_id` TEXT NOT NULL, `farm_id_at_start` TEXT NOT NULL, `farm_name_at_start` TEXT NOT NULL, `parcel_name_at_start` TEXT NOT NULL, `managed_area_m2_at_start` REAL, `cadastral_reference_at_start` TEXT, `geometry_geo_json_snapshot` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `version` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `remote_version` INTEGER, `last_synced_at` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`campaign_id`) REFERENCES `campaigns`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`parcel_id`) REFERENCES `parcels`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`farm_id_at_start`) REFERENCES `farms`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
            """CREATE UNIQUE INDEX IF NOT EXISTS `index_campaign_parcels_campaign_id_parcel_id` ON `campaign_parcels` (`campaign_id`, `parcel_id`)""",
            """CREATE INDEX IF NOT EXISTS `index_campaign_parcels_workspace_id` ON `campaign_parcels` (`workspace_id`)""",
            """CREATE INDEX IF NOT EXISTS `index_campaign_parcels_parcel_id` ON `campaign_parcels` (`parcel_id`)""",
            """CREATE INDEX IF NOT EXISTS `index_campaign_parcels_farm_id_at_start` ON `campaign_parcels` (`farm_id_at_start`)""",
        )

    private val schemaVersion2Statements =
        arrayOf(
            """CREATE TABLE IF NOT EXISTS `user_profiles` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `display_name` TEXT NOT NULL, `email` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `version` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `remote_version` INTEGER, `last_synced_at` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
            """CREATE UNIQUE INDEX IF NOT EXISTS `index_user_profiles_workspace_id` ON `user_profiles` (`workspace_id`)""",
            """CREATE TABLE IF NOT EXISTS `parcels` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `display_name` TEXT NOT NULL, `cadastral_reference` TEXT, `cadastral_polygon` TEXT, `cadastral_parcel` TEXT, `municipality` TEXT, `province` TEXT, `source` TEXT NOT NULL, `geometry_geo_json` TEXT, `cadastral_area_m2` REAL, `managed_area_m2` REAL, `notes` TEXT, `status` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `version` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `remote_version` INTEGER, `last_synced_at` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
            """CREATE INDEX IF NOT EXISTS `index_parcels_workspace_id_status` ON `parcels` (`workspace_id`, `status`)""",
            """CREATE INDEX IF NOT EXISTS `index_parcels_workspace_id_cadastral_reference` ON `parcels` (`workspace_id`, `cadastral_reference`)""",
            """CREATE TABLE IF NOT EXISTS `farm_parcel_memberships` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `farm_id` TEXT NOT NULL, `parcel_id` TEXT NOT NULL, `valid_from` INTEGER NOT NULL, `valid_until` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `version` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `remote_version` INTEGER, `last_synced_at` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`farm_id`) REFERENCES `farms`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`parcel_id`) REFERENCES `parcels`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
            """CREATE UNIQUE INDEX IF NOT EXISTS `index_farm_parcel_memberships_farm_id_parcel_id_valid_from` ON `farm_parcel_memberships` (`farm_id`, `parcel_id`, `valid_from`)""",
            """CREATE INDEX IF NOT EXISTS `index_farm_parcel_memberships_parcel_id_valid_until` ON `farm_parcel_memberships` (`parcel_id`, `valid_until`)""",
            """CREATE INDEX IF NOT EXISTS `index_farm_parcel_memberships_workspace_id` ON `farm_parcel_memberships` (`workspace_id`)""",
            """CREATE TABLE IF NOT EXISTS `campaigns` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `farm_id` TEXT NOT NULL, `name` TEXT NOT NULL, `start_date` TEXT NOT NULL, `end_date` TEXT, `status` TEXT NOT NULL, `notes` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `version` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `remote_version` INTEGER, `last_synced_at` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`farm_id`) REFERENCES `farms`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
            """CREATE INDEX IF NOT EXISTS `index_campaigns_workspace_id_status` ON `campaigns` (`workspace_id`, `status`)""",
            """CREATE INDEX IF NOT EXISTS `index_campaigns_farm_id_start_date` ON `campaigns` (`farm_id`, `start_date`)""",
            """CREATE TABLE IF NOT EXISTS `activities` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `campaign_id` TEXT, `farm_id` TEXT, `activity_date` TEXT NOT NULL, `type` TEXT NOT NULL, `status` TEXT NOT NULL, `description` TEXT NOT NULL, `product` TEXT, `quantity` REAL, `unit` TEXT, `cost_minor` INTEGER, `currency` TEXT, `notes` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `version` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `remote_version` INTEGER, `last_synced_at` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
            """CREATE INDEX IF NOT EXISTS `index_activities_workspace_id_activity_date` ON `activities` (`workspace_id`, `activity_date`)""",
            """CREATE INDEX IF NOT EXISTS `index_activities_campaign_id_activity_date` ON `activities` (`campaign_id`, `activity_date`)""",
            """CREATE INDEX IF NOT EXISTS `index_activities_farm_id_activity_date` ON `activities` (`farm_id`, `activity_date`)""",
            """CREATE TABLE IF NOT EXISTS `harvests` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `campaign_id` TEXT, `farm_id` TEXT, `harvest_date` TEXT NOT NULL, `weight_grams` INTEGER NOT NULL, `destination` TEXT, `notes` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `version` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `remote_version` INTEGER, `last_synced_at` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
            """CREATE INDEX IF NOT EXISTS `index_harvests_workspace_id_harvest_date` ON `harvests` (`workspace_id`, `harvest_date`)""",
            """CREATE INDEX IF NOT EXISTS `index_harvests_campaign_id_harvest_date` ON `harvests` (`campaign_id`, `harvest_date`)""",
            """CREATE INDEX IF NOT EXISTS `index_harvests_farm_id_harvest_date` ON `harvests` (`farm_id`, `harvest_date`)""",
            """CREATE TABLE IF NOT EXISTS `expenses` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `campaign_id` TEXT, `farm_id` TEXT, `parcel_id` TEXT, `expense_date` TEXT NOT NULL, `concept` TEXT NOT NULL, `category` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `provider` TEXT, `notes` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `version` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `remote_version` INTEGER, `last_synced_at` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
            """CREATE INDEX IF NOT EXISTS `index_expenses_workspace_id_expense_date` ON `expenses` (`workspace_id`, `expense_date`)""",
            """CREATE INDEX IF NOT EXISTS `index_expenses_campaign_id_expense_date` ON `expenses` (`campaign_id`, `expense_date`)""",
            """CREATE INDEX IF NOT EXISTS `index_expenses_farm_id_expense_date` ON `expenses` (`farm_id`, `expense_date`)""",
            """CREATE INDEX IF NOT EXISTS `index_expenses_parcel_id_expense_date` ON `expenses` (`parcel_id`, `expense_date`)""",
            """CREATE TABLE IF NOT EXISTS `documents` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `owner_type` TEXT NOT NULL, `owner_id` TEXT NOT NULL, `type` TEXT NOT NULL, `mime_type` TEXT NOT NULL, `display_name` TEXT NOT NULL, `file_size_bytes` INTEGER, `sha256` TEXT, `local_uri` TEXT NOT NULL, `remote_path` TEXT, `upload_status` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `version` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `remote_version` INTEGER, `last_synced_at` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
            """CREATE INDEX IF NOT EXISTS `index_documents_workspace_id_owner_type_owner_id` ON `documents` (`workspace_id`, `owner_type`, `owner_id`)""",
            """CREATE INDEX IF NOT EXISTS `index_documents_sha256` ON `documents` (`sha256`)""",
            """CREATE TABLE IF NOT EXISTS `weather_cache` (`cache_key` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `source` TEXT NOT NULL, `payload_json` TEXT NOT NULL, `fetched_at` INTEGER NOT NULL, `expires_at` INTEGER NOT NULL, PRIMARY KEY(`cache_key`))""",
            """CREATE INDEX IF NOT EXISTS `index_weather_cache_workspace_id_expires_at` ON `weather_cache` (`workspace_id`, `expires_at`)""",
            """CREATE TABLE IF NOT EXISTS `alerts` (`id` TEXT NOT NULL, `workspace_id` TEXT NOT NULL, `farm_id` TEXT, `parcel_id` TEXT, `kind` TEXT NOT NULL, `severity` TEXT NOT NULL, `title` TEXT NOT NULL, `message` TEXT NOT NULL, `starts_at` INTEGER NOT NULL, `ends_at` INTEGER, `acknowledged_at` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `version` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `remote_version` INTEGER, `last_synced_at` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`workspace_id`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
            """CREATE INDEX IF NOT EXISTS `index_alerts_workspace_id_starts_at` ON `alerts` (`workspace_id`, `starts_at`)""",
            """CREATE INDEX IF NOT EXISTS `index_alerts_farm_id_starts_at` ON `alerts` (`farm_id`, `starts_at`)""",
            """CREATE INDEX IF NOT EXISTS `index_alerts_parcel_id_starts_at` ON `alerts` (`parcel_id`, `starts_at`)""",
        )
}
