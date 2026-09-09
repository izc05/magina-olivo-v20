import { Kysely, PostgresDialect } from 'kysely';
import pg from 'pg';
import type { Database } from './types.js';

const { Pool } = pg;

export type DatabaseClient = Kysely<Database>;

export function createDatabase(connectionString: string): DatabaseClient {
  const pool = new Pool({
    connectionString,
    max: 10,
    idleTimeoutMillis: 30_000,
    connectionTimeoutMillis: 5_000,
  });

  return new Kysely<Database>({
    dialect: new PostgresDialect({ pool }),
  });
}
