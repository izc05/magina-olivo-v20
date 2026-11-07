package com.isivoltpro.maginaolivo.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Based on the verified P1 v34. Empty sidecars only; no historical UPDATE/DELETE/INSERT SELECT. */
val AGRICULTURAL_CYCLE_MIGRATION_34_35 = object : Migration(34,35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS agricultural_cycles (id TEXT NOT NULL, workspace_id TEXT NOT NULL, farm_id TEXT NOT NULL, label TEXT NOT NULL, start_date TEXT NOT NULL, effective_end_date TEXT, closed_at INTEGER, no_recollection INTEGER NOT NULL, version INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(workspace_id) REFERENCES workspaces(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(farm_id) REFERENCES farms(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_agricultural_cycles_workspace_id ON agricultural_cycles(workspace_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_agricultural_cycles_farm_id_start_date ON agricultural_cycles(farm_id,start_date)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS agricultural_cycle_farm_state (farm_id TEXT NOT NULL, revision INTEGER NOT NULL, PRIMARY KEY(farm_id), FOREIGN KEY(farm_id) REFERENCES farms(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        for ((table,key,parent) in listOf(
            Triple("agricultural_cycle_activities","activity_id","activities"),
            Triple("agricultural_cycle_expenses","expense_id","expenses"),
            Triple("agricultural_cycle_recollections","campaign_id","campaigns"),
        )) {
            db.execSQL("CREATE TABLE IF NOT EXISTS $table ($key TEXT NOT NULL, cycle_id TEXT NOT NULL, PRIMARY KEY($key), FOREIGN KEY(cycle_id) REFERENCES agricultural_cycles(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY($key) REFERENCES $parent(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_${table}_cycle_id ON $table(cycle_id)")
        }
        db.execSQL("""CREATE TABLE IF NOT EXISTS agricultural_cycle_operations (id TEXT NOT NULL, cycle_id TEXT NOT NULL, owner_id TEXT, fingerprint TEXT NOT NULL, recorded_at INTEGER NOT NULL, reason TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(cycle_id) REFERENCES agricultural_cycles(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_agricultural_cycle_operations_cycle_id ON agricultural_cycle_operations(cycle_id)")
    }
}
