# CR-004 — Design v3 from the owner's mockups

## Motivation
On 2026-09-23 the owner supplied five screens (Inicio/Mi Campo, Fincas y parcelas, finca,
parcela, alerta de riego) and asked that the app keep that design. They sharpen the approved
CR-003 language (photography, editorial serif, icon cards) and show grove data the app does
not store yet.

## Decision (owner, 2026-09-23)

| Topic | Decision |
|---|---|
| Visual language | **APPROVED.** Photographic headers bleeding to the top with rounded lower corners and a dark fade; serif titles on the photo; white stat strips/tiles with icon badges; section cards with icon + serif title; tabs on Parcel detail. |
| Primary navigation | **REJECTED — keep the frozen shell** `Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil`. The mockups' `Mi Campo · Campaña · Más` bar is not adopted. |
| Parcel grove description | **APPROVED — Room v11.** Optional `olive_tree_count`, `variety`, `irrigation_system`, `irrigation_network`, `irrigation_sector`, `irrigation_days` on `parcels`. |
| "Crear alerta de riego" (push to every irrigator of a sector) | **DEFERRED.** Needs server accounts and the separate private Admin web surface already approved; planned with the backend/sync phases, not built in the Android app now. |
| "Mágina recomienda", "Revisar mosca", sanitary status | **NOT SHOWN** until a real, dated source exists (truth rule). |
| Olive counts, kg, prices in the mockups | Illustrative only. The app shows the farmer's own data, "—" when unknown and "≥ N" when only part of the parcels carry a count. |

## Affected baseline sections
`VISUAL_DESIGN_LOCK.md` (composition of Inicio, Mi Olivar, Farm and Parcel detail);
`DATA-MODEL` parcel entity (new optional columns). Navigation, hierarchy, offline-first,
sync and Catastro contracts are unchanged.

## Affected phases
Design work lands on `feat/design-v3`, on top of the Phase 16 + UI polish chain; no Gate
changes. Irrigation alerts join the backend phase backlog.

## Data impact
`MIGRATION_10_11`: six nullable `ALTER TABLE parcels ADD COLUMN`. Existing rows keep NULL;
no version bump, no outbox intent from the migration. Edits travel with the normal Parcel
UPDATE intent. Covered by `RoomMigrationTest.migration10To11…` and
`OfflineFirstFarmRepositoryTest.groveDescriptionRoundTripsAndNonsenseIsRefused`.

## Offline/sync impact
None beyond the extra Parcel fields in the existing UPDATE payload.

## UX impact
Same routes and roots. Parcel detail gains tiles, a Riego card and Actividad/Datos/Documentos
tabs; the parcel editor asks for the grove description (all optional; tapping the chosen
irrigation option again clears it, so "not told" never becomes "secano").

## Risks
Decorative bundled photograph could be read as the farmer's land: it is used only when the
farmer has no cover photo, and the photo headers never label it as a place.

## Alternatives considered
Adopting the mockups' navigation (rejected by the owner); storing grove data in notes
(rejected: not queryable, no truthful totals).

## Decision
APPROVED as scoped above.
