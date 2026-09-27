# Issue #254 — Campaña/recolección simplificada (Pesadas, jornadas, jornales, vale)

Owner: Claude (carried from Codex, owner OK 2026-09-27). Source of truth: Issue #246 §4B and
Issue #254. One small PR per slice; no destructive migration; old data stays visible.

| Slice | Issue block | Content | Room | Status |
|---|---|---|---|---|
| — | CODEX-1 | Pesada as the single productive action; Jornada/Pesada vocabulary (Codex) + preset form (#253) | — | PR #261 |
| 254-A | CODEX-3 core | Pesada origin **Árbol/vuelo · Suelo** required in the form; shown in Pesadas, Jornada and Cuaderno; ticket photo already attached to the Pesada | **v16**: `deliveries.harvest_origin` (nullable; older Pesadas "Sin indicar") | PR #262 |
| 254-B | CODEX-2 | Recolección: 2×2 (kg pesados, nº pesadas, rendimiento medio ponderado, gastos), "+ Nueva pesada", history by Jornada | — | PR #263 (Cuaderno Recolección tab: 2×2 kg · pesadas · rendimiento medio ponderado · gastos de recolección; "+ Nueva pesada" primary; history by day unchanged) |
| 254-C | CODEX-4 | Jornada: kg = sum of its Pesadas, compact Pesada list with origin/almazara/rendimiento/vale/photo, complete without locking | — | PR #264 (merged): kg already = sum of Pesadas (19B writer); each row now shows hora · origen · vale · rendimiento (or "pendiente"), the Jornada summary its kilo-weighted yield. Photo stays on the Pesada detail; a Jornada never locks (only a closed Campaign is read-only) |
| 254-D | CODEX-5 | Jornales per worker (stable ID, quick picker, default 1, totals per person/campaign/farm/dates). Workers already have a stable ID (v14); "nombre y apellidos" is one field | none | PR #268 (merged): per-worker totals in the campaign summary (stable worker id, renamed person stays one row; bare counts shown apart as «Sin nombre»); name + surnames stay one field (owner default) |
| 254-E | CODEX-6/7 | Remove visual duplicates; E2E "Campanil · 2.390 kg · Árbol/Vuelo · Bedmarense · vale · foto → Jornada → 5 jornales → gasto → totales" | — | IN PROGRESS: a Jornada's own Pesadas/costs no longer repeated in Diario/Recolección (its row shows their yield); E2E contract test `RecollectionFlowContractTest` |

OCR of the ticket stays out of scope (extension point only).
