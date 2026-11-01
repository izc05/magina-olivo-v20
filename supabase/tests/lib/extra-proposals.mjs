// Opt-in regression hook: MAGINA_EXTRA_PROPOSALS="a.sql,b.sql" appends reviewed proposals from
// supabase/proposals after a suite's own schema. Empty or unset keeps every suite unchanged.
import { readFileSync } from 'node:fs';

const names = () => (process.env.MAGINA_EXTRA_PROPOSALS ?? '').split(',').map(name => name.trim()).filter(Boolean);

export function extraProposals() {
  return names().map(name => {
    if (!/^[a-z0-9-]+\.sql$/.test(name)) throw new Error(`Invalid extra proposal name: ${name}`);
    return readFileSync(new URL(`../../proposals/${name}`, import.meta.url), 'utf8');
  }).join('\n');
}

/** True when the G2.6 membership hardening is part of the schema under test. */
export const membershipsHardened = () => names().includes('g26-fpm-manage-hardening.sql');

/** True when the G2.6 PARCEL mutation contract (which revokes direct parcel writes) is under test. */
export const parcelWritesClosed = () => names().includes('g26-parcel-mutation-v1.sql');
