import { Kysely, PostgresDialect } from 'kysely';
import pg from 'pg';
import type { Database } from './types.js';
import type { ProfileDatabase } from './profile-types.js';

const { Pool } = pg;

export type AppDatabase = Database & ProfileDatabase;
export type DatabaseClient = Kysely<AppDatabase>;

export function createDatabase(connectionString: string): DatabaseClient {
  const pool = new Pool({
    connectionString,
    max: 10,
    idleTimeoutMillis: 30_000,
    connectionTimeoutMillis: 5_000,
  });

  return new Kysely<AppDatabase>({
    dialect: new PostgresDialect({ pool }),
  });
}
