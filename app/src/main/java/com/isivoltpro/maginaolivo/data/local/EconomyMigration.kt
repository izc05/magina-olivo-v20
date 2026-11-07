package com.isivoltpro.maginaolivo.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** CR-015 P5: three empty local-economy tables. No existing row is read, rewritten or copied. */
val ECONOMY_MIGRATION_36_37 = object : Migration(36,37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS cooperative_settlements (id TEXT NOT NULL, workspace_id TEXT NOT NULL, farm_id TEXT, cycle_id TEXT, kind TEXT NOT NULL, cooperative TEXT NOT NULL, document_date TEXT NOT NULL, currency TEXT NOT NULL, gross_minor INTEGER NOT NULL, withholding_minor INTEGER NOT NULL, fees_minor INTEGER NOT NULL, net_minor INTEGER NOT NULL, kilograms INTEGER, reference TEXT, replaces_id TEXT, status TEXT NOT NULL, version INTEGER NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(workspace_id) REFERENCES workspaces(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(farm_id) REFERENCES farms(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(cycle_id) REFERENCES agricultural_cycles(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(replaces_id) REFERENCES cooperative_settlements(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        for (column in listOf("workspace_id","farm_id","cycle_id","replaces_id"))
            db.execSQL("CREATE INDEX IF NOT EXISTS index_cooperative_settlements_$column ON cooperative_settlements($column)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS settlement_advance_applications (id TEXT NOT NULL, advance_id TEXT NOT NULL, settlement_id TEXT NOT NULL, amount_minor INTEGER NOT NULL, active INTEGER NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(advance_id) REFERENCES cooperative_settlements(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(settlement_id) REFERENCES cooperative_settlements(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_settlement_advance_applications_advance_id ON settlement_advance_applications(advance_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_settlement_advance_applications_settlement_id ON settlement_advance_applications(settlement_id)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS settlement_payments (id TEXT NOT NULL, settlement_id TEXT NOT NULL, payment_date TEXT NOT NULL, amount_minor INTEGER NOT NULL, active INTEGER NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(settlement_id) REFERENCES cooperative_settlements(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_settlement_payments_settlement_id ON settlement_payments(settlement_id)")
    }
}
