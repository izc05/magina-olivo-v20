# Mágina Olivo — RC1 Change Control

This file defines how the approved RC1 baseline may change.

## Default rule

The RC1 baseline is frozen. Do not change architecture, core scope, phase order or historical data rules as part of a normal feature PR.

## What counts as a structural change

A Change Request is mandatory when a proposal changes one or more of:

- Android-first delivery model;
- offline-first behavior;
- Room/local-source-of-truth strategy;
- synchronization model;
- Supabase/PostgreSQL/PostGIS backend contract;
- Catastro integration model;
- farm/parcel/campaign/activity core hierarchy;
- campaign snapshots/history guarantees;
- RC1 feature scope;
- primary navigation;
- phase ordering or Gate definitions;
- identifier/version/delete strategy;
- module dependency direction.

## Change Request template

```md
# CR-XXX — Title

## Motivation
Why is the baseline no longer sufficient?

## Proposed change
Exact behavior/architecture to change.

## Affected baseline sections
List them explicitly.

## Affected phases
Which roadmap phases/Gates change?

## Data impact
Schema, migrations, history, compatibility and recovery consequences.

## Offline/sync impact
Describe effects on local persistence, outbox and conflict handling.

## UX impact
Describe navigation/workflow changes.

## Risks
Data loss, migration, security, performance, schedule.

## Alternatives considered
Include the option of keeping the current baseline.

## Decision
APPROVED / REJECTED

## New baseline version
Only if approved: RC1.x
```

## Versioning

- `RC1` — current approved baseline.
- `RC1.1`, `RC1.2`, ... — structural revisions before first release.
- Editorial corrections that do not change meaning do not require a new baseline version.

## Agent rule

Codex, Antigravity or any other agent must not infer permission to redesign the project from a broad prompt such as "improve Mágina Olivo".

For implementation work, the agent must receive:

1. RC1 baseline;
2. applicable specification;
3. exact phase;
4. branch/worktree;
5. Gate/acceptance criteria.

If an implementation task conflicts with the baseline, stop the task and raise a Change Request instead of silently changing architecture.

## New ideas

New ideas that are useful but not required for the current phase go to `POST-RC1-BACKLOG.md` or to the roadmap's later modules. They do not interrupt the active Gate.
