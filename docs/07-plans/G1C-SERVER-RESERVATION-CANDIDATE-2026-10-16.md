# G1c server reservation candidate — NOT DEPLOYABLE

Only `supabase/proposals/g1c-reservations.sql` and an isolated in-memory PostgreSQL
test are supplied. No migration, Supabase deployment, HTTP request, JWT issuance,
agricultural import, owner adoption, or synchronization is authorized by this candidate.

## Draft protocol

`POST /rest/v1/rpc/reserve_local_farm_g1c` takes `p_request` with the eight Kotlin
camelCase fields (`payloadVersion`, `operationId`, `farmId`, `localWorkspaceId`,
`consentId`, `consentTextVersion`, `manifestSha256`, `aggregateVersion`) and
`p_remote_workspace` UUID. Extra actor/hash fields are rejected. Actor authority
comes exclusively from `auth.uid()`; issuer comes from gateway-verified JWT
`iss`. The DEV-only candidate requires the exact literal
`https://zubpicdibqbmewxeurhm.supabase.co/auth/v1` and returns the exact project
base URL; a different project requires a separately reviewed issuer configuration. SQL fixture
claims are synthetic, trusted test inputs, not proof of JWT verification.
SQL additionally requires role `authenticated`, subject equal to `auth.uid()`,
future numeric `exp`, and a session not marked anonymous. A Supabase anonymous
Auth session is rejected even if it owns the requested workspace. These claim
checks depend on the gateway preventing callers from supplying trusted SQL context.

Successful response is a flat object of seven strings: `issuer`, `actorId`,
`operationId`, `farmId`, `payloadHash`, `remoteWorkspaceId`, `receiptId`.
Authorization failure uses SQLSTATE 42501; malformed requests 22023; all operation,
farm, and legacy collisions use 23505 with the same `reservation conflict` message.
Native PostgREST mapping (including authenticated 403 and conflict 409) remains a gate.

Hash matches Kotlin v1: ordered fields `magina-reservation`, `1`, operation UUID,
farm UUID, local workspace UUID, consent UUID, consent version, lowercase manifest
SHA256, aggregate version. Each UTF-8 field has a four-byte big-endian byte length;
PostgreSQL `int4send` plus built-in `sha256(bytea)` avoids extension dependence.

## Authorization and invariants

The effective remote workspace must exist, have no tombstone, and be owned by the
current actor. This is rechecked on replay. A local Room workspace UUID carries no
remote authority. Reservations live in a new non-exposed `magina_g1c_private`
schema with RLS and no direct client table grants or policies. The public invoker
wrapper calls one narrowly granted private definer function with an empty search
path. This avoids granting schema usage to existing G0 private helpers.

Unique `(actor_id, operation_id)` and unique `farm_id` enforce stable replay and
cross-account exclusion. UPDATE/DELETE triggers reject receipt changes, including
privileged ordinary SQL. Exact replay returns the saved receipt; changed request,
workspace, or issuer conflicts. Existing `public.farms.id`, including a legacy
NULL-workspace row, always conflicts without rewriting any legacy data.

Reservation RPC writes only its reservation row. The candidate farm BEFORE INSERT
guard and RPC acquire the same transaction advisory lock for the farm UUID. The
guard checks reserved UUIDs only: actor, creator, workspace, and any supplied owner
must match the receipt. Unreserved existing G0 creation paths remain available.
An authorized later farm INSERT is still the existing G0 path, not reservation
consumption/import authorization. Nothing copies agricultural data or creates grants.

## Evidence and blocking gates

Node v24.13.1, pinned `@electric-sql/pglite` 0.2.17 (`npm ci --ignore-scripts`).
`node --test tests/g1c_reservations_isolated.test.mjs`: 6/6 passed after observing
red for the missing RPC. Tests apply the existing fixture and five G0 migrations,
then only this proposal. They cover replay/hash, absent/foreign/deleted authority,
malformed/extra fields, SQL grants/RLS, replay revocation, cross-account UUID and
operation conflicts, immutable receipt, legacy preservation, and direct farm theft.
Red/green also covers anonymous/wrong-role/expired claims and subject mismatch.
G0 principal immutability is explicitly tested for UPDATE of an existing farm ID
to a reserved UUID and for workspace/creator/owner changes on a reserved farm.
The existing G0 security workflow invokes `npm test --prefix supabase`; its test
script now includes this isolated candidate suite. Complete local `npm test`:
59/59 passed, no skips or failures, on the same pinned Node/PGlite versions.

PGlite exercises real SQL and sequential advisory-lock availability, but one
in-memory connection cannot prove PostgreSQL concurrency. Before any deploy:

- Independently review SQL, grants, trigger compatibility, and client protocol.
- Run real PostgreSQL concurrent reservation/reservation and reservation/INSERT
  races, including two accounts, same operation with different payload, and rollback.
  Default PostgREST READ COMMITTED isolation is the current design assumption;
  stronger isolation needs explicit validation or rejection before shipping.
- Verify native Auth JWT signature/issuer handling, wrong-project JWT rejection,
  exposed schema configuration, actual PostgREST status mapping, and receipt secrecy.
- Verify consent proof and supported consent-text versions on the server. This
  candidate only checks positive version and binds its value into the payload
  hash: unknown version 999 is currently accepted. It does not verify consent
  evidence, manifest contents, or a source aggregate, and cannot authorize import.
- The literal HTTPS DEV issuer excludes native loopback HTTP JWTs. The native
  fixture signs synthetic HTTPS claims with a disposable key; a real confirmed
  DEV session must still prove its literal `iss` before persistent deployment.
- Recheck effective workspace authorization and deletion/revocation in real sessions.
- Review a migration generated through the approved deployment workflow separately.

Current Supabase docs consulted 2026-10-16: [functions](https://supabase.com/docs/guides/database/functions),
[RLS](https://supabase.com/docs/guides/database/postgres/row-level-security),
and [changelog](https://supabase.com/changelog). Current advice requires narrow
EXECUTE grants, explicit auth checks, and a pinned search path for definer functions.

## Read-only review of the Kotlin journal and draft transport

The journal commits the immutable request before sending and keeps network I/O
outside its file lock. Receipt validation and late-actor recheck occur before the
new snapshot commits. Transport matches this draft protocol, validates all receipt
identity/hash/workspace fields, redacts wrapper strings, and has no installed HTTP
wire or application consumer. No Kotlin changes were made in this SQL review.
No additional blocking defect was found for the isolated preparation scope.

Activation remains blocked on Android filesystem validation (atomic replacement,
file/directory durability and actual process termination), real concurrent
process/thread locking tests, and a controlled app-private canonical path factory.
The existing JVM tests reopen instances and simulate precommit failure; they do
not prove OS-process termination or power-loss recovery. Alternate path aliases
must not produce distinct adjacent lock files for the same underlying journal.
No journal status alone grants access: terminal RESERVED_REMOTE records remain
bound to their original actor/scope, and a future consumer must revalidate visible
actor before presenting or using a receipt. The 1000-record/1MiB limits also need
an explicit retention/recovery policy before repeated production use.
