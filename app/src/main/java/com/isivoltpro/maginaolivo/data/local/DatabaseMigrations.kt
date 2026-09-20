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

    val all: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

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
