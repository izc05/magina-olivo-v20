# G1c native ephemeral test preparation — 2026-10-16

Phase: G1c preparation only. Branch `feat/g1c-reservation-contract`, PR #759.
The reservation SQL remains `supabase/proposals/g1c-reservations.sql`.
This preparation adds no persistent migration, deployment, synchronization, G2,
merge or candidate APK.

## Execution status at preparation checkpoint

**Local checkpoint: native NOT EXECUTED. Superseded by the isolated GitHub execution below.** Docker CLI is installed,
but `docker info --format '{{.ServerVersion}}'` failed because the
`dockerDesktopLinuxEngine` named pipe does not exist. Supabase CLI was also absent
from the inspected PowerShell PATH. This document is preparation, not Gate PASS.

Completed local checks: `node --check supabase/tests/g1c-native/reservations.test.mjs`
passed; `node --test supabase/tests/g1c-native/safety.test.mjs` passed (1 test).
These checks prove JavaScript syntax and target validation only. They do not prove
native SQL concurrency, JWT validation, PostgREST status mapping, or runner startup.

## Run in a disposable local environment

Prerequisites: Bash, Node with built-in fetch, Supabase CLI, and a running Docker
Linux engine. Inspect the pinned/installed CLI version and its help before use.
From the repository root:

```bash
bash supabase/tests/g1c-native/run.sh
```

The runner creates a fresh temporary Supabase project with a task-specific random
timestamp/PID name. It refuses an existing database container, accepts no remote
database URL, does not link a project, and binds the test to the generated local
configuration and loopback API status. Cleanup targets only that newly created
project with `stop --no-backup`; it never stops all stacks. Ordinary failures use
the EXIT cleanup trap. Forced host termination can leave this stack running;
remove only its printed/project-specific containers after inspecting them.

Raw status (including local keys) stays in temporary storage and is removed during
cleanup. Do not archive status/start logs or tokens. Capture the Node test report,
CLI version and Docker image versions separately as evidence. A failed startup
or any failed assertion is a failed native run, never a skip or a PASS.

The fixture creates two real GoTrue email/password users, applies the existing G0
baseline DDL and candidate proposal inside the fresh database without migration
history writes, then prepares native PostgreSQL and HTTP assertions. Local GoTrue emits HTTP issuer tokens, which the unchanged proposal deliberately rejects. The harness explicitly asserts that rejection, then signs synthetic HTTPS-issuer fixtures with the disposable gateway secret, retaining real local user/session claims. Positive HTTP and SQL concurrency fixtures use that synthetic issuer. This tests gateway signature handling with signed fixtures; it does not prove native HTTPS GoTrue token issuance. The harness enables anonymous
Auth only in this temporary project's generated configuration. No synthetic
`auth.uid()` implementation is used. Schema reload is polled and bounded.

## Prepared assertions

| Surface | Prepared native evidence |
| --- | --- |
| Reservation / reservation | Two distinct `psql` connections; observe winner after reservation while its transaction remains open; observe loser waiting on a PostgreSQL lock; commit and compare exact saved seven-field receipt; one durable row. |
| Same operation / different farm | Concurrent unique-key collision produces generic conflict and only one receipt. |
| Cross-account collision | Same farm, different actors/operations: loser receives generic conflict; foreign receipt is absent; direct private-table access is denied. |
| Reservation / farm INSERT | Both commit orderings: reservation first denies a foreign INSERT; INSERT first preserves existing owner and prevents a reservation. Assertions check resulting rows. |
| Gateway replay | Gateway-verified synthetic HTTPS-issuer fixture returns the exact same HTTP response and receipt; raw local HTTP-issuer GoTrue token is denied; deleting workspace authority denies replay. |
| Wrong project | JWT signed with unrelated ephemeral secret and a foreign issuer is rejected with 401. This proves signature rejection for an unrelated project, not an issuer allowlist for tokens signed by a trusted key. |
| Expired JWT | Locally signed, hour-expired JWT rejected with 401, outside PostgREST clock-skew tolerance. |
| Anonymous | Legacy anon API token denied with 401; claims from a real anonymous Auth session, re-signed with the synthetic HTTPS issuer and owning its own synthetic workspace, denied with 403. |
| Foreign HTTP collision | B receives generic 409 without A's receipt, followed by A's authority-revoked replay receiving 403. |

SQL connections set trusted fixture claims only to isolate concurrency testing.
Those calls are explicitly not JWT evidence; only the HTTP cases exercise gateway
signature verification. Direct authenticated INSERT fixtures are synthetic rows in
the disposable database, not synchronization or agricultural imports.

## Remaining native gate

Execute and repair the harness against an available native stack, record exact
Supabase CLI/container/PostgreSQL versions, and archive a sanitized passing test
report before making any native validation claim. Current CLI status must expose
the local legacy JWT secret for the expired-token fixture; if that contract changes,
the test fails closed and requires a reviewed native signing fixture adaptation.
Modern asymmetric signing deployments still need deployment-specific validation;
this local legacy-HS256 fixture must not be generalized as evidence for them.

This harness currently prepares the clean baseline only. Legacy schema/storage
compatibility remains covered by the separate G0 suite and is not re-proven here.
No production security or deployment approval follows from this preparation.

References consulted: [Supabase changelog](https://supabase.com/changelog),
[PostgREST authentication](https://docs.postgrest.org/en/v12/references/auth.html).
PostgREST validates signatures and standard temporal/audience claims; project
issuer enforcement must not be inferred from a well-formed `iss` value alone.

Independent review caught two preparation defects before publication: raw loopback issuers could never pass the existing SQL, and node:test timeout options were in the wrong argument position. The fixtures and timeout placement were corrected; production SQL was unchanged. Native execution remains NOT EXECUTED. Real HTTPS GoTrue issuance and asymmetric signing remain separate gates.

## Native CI evidence — executed after preparation

GitHub run `38961135139`, exact source HEAD `ef8161884106f3f2e5885f56fcd2b67e80036fc1`, job `g1c-native-reservation`: SUCCESS. Artifact `g1c-native-reservation/tests.txt` records PostgreSQL17.11, Supabase CLI2.120.0 and five tests passed, zero failures/skips, in16.97s after ephemeral stack startup. The three existing G0 security jobs also passed on that SHA; they remain separate evidence.

All five prepared assertions executed: reservation/reservation exact replay; concurrent same-operation/different-farm conflict; cross-account generic conflict/private-table denial; reservation/INSERT in both commit orderings; gateway signature/expiry/anonymous/revocation and HTTP replay. The race harness observed backend sleep and a competing lock wait before comparing outcomes. Raw local HTTP-issuer tokens were denied; positive gateway fixtures used the documented synthetic HTTPS issuer and ephemeral signing secret. Real HTTPS GoTrue issuance and asymmetric-key deployment validation remain pending.

The runner created and stopped only a new disposable project, with SQL applied as test fixture DDL inside its fresh database. The proposal remains outside migrations; no persistent deployment, agricultural import, application consumer or G2 was activated. A later documentation-only HEAD must obtain its own CI; this run does not automatically validate another SHA. The durable remote routing, supported consent versions/evidence and native Android filesystem gates also remain open before integration.