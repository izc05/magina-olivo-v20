# Sierra Mágina hiking catalog — coverage audit

Audit date: 2026-09-13

## Scope and rule of truth

This audit treats catalog completeness as an auditable product property, not as a visual claim.

A route is not considered publishable only because a name, description or third-party track exists. Technical route data must be sourced. A route with a real track is still not publishable until that track has been validated against its authoritative source and the application geometry rules.

Community routes are a separate dynamic layer and never count toward official completeness.

## Current coverage

### Territory

- Target municipalities: 16
- Municipalities audited against public/official sources: 16/16
- Municipalities represented by at least one canonical named route record: 15/16
- Municipality audited without a named official hiking route located: Larva

Larva is intentionally not filled with an invented route. Official tourism material confirms hiking-compatible natural areas, but this audit did not locate a named official pedestrian route with its own technical sheet/track.

### Parque Natural official core

Current minimum official core: 17 signposted trails documented from Junta de Andalucía / Ventana del Visitante.

The baseline includes Hoyalinos and Umbría de los Corzos. Pico Cabañas is excluded because its official page belongs to Parque Natural Sierras de Cazorla, Segura y Las Villas, not Sierra Mágina.

### Diputación / Sendero Intermodal

Official hiking network R1–R9 is catalogued separately so complete tourism itineraries do not overwrite or duplicate the Junta trail entities they may reuse.

Known current network:

- R1 Cueva de la Graja – Los Caracoles – Pinar de Cánava
- R2 Albanchez de Mágina – Cima del Aznaitín
- R3 Columpio Gigante de Torres – Barranco del Arroyuelo – Portillo de la Zarzadilla
- R4 Área Recreativa Fuenmayor – Albanchez de Mágina
- R5 Área Recreativa El Peralejo – Área Recreativa Fuenmayor
- R6 Área Recreativa El Peralejo – Pico Mágina – Miramundos
- R7 Barranco del Gargantón
- R8 Área Recreativa Río Cuadros – Caño del Aguadero – Bélmez de la Moraleda
- R9 Piedra del Palo

### Homologated network

Federation evidence currently identifies these Sierra Mágina homologated routes/sections:

- GR-7 through Sierra Mágina, including the relevant Jódar–Bedmar–Albanchez–Torres–Cambil–Cárcheles stages.
- PR-A 350 Caño del Aguadero.
- SL-A 135 Fuenmayor.

GR-7 carries a maintenance warning in the federation source: no maintenance has been notified since 1998 and signage is described as scarce/deteriorated. Homologation and current maintenance condition must therefore remain separate fields.

## Canonical catalog metrics at this checkpoint

- Canonical named route records: 30
- Parque Natural official core: 17/17
- Additional official comarca route records: 13
- Municipality source audit: 16/16
- Canonical municipality route representation: 15/16
- Canonical records with technical documentation marked complete: 11/30
- Canonical records with a real track located: 15/30
- Canonical tracks validated: 0/30
- Canonical routes publishable under the strict policy: 0/30
- Intermodal routes catalogued: 9/9
- Intermodal tracks explicitly located in source during this audit: 6/9
- Homologated route records: 3
- Homologated records with track availability reported by federation: 3/3

These numbers are deliberately conservative. “Found” is not the same as “validated”.

## Closure gates

Senderismo must not be called complete until all applicable gates are satisfied:

1. Keep 16/16 municipality source audit green.
2. Keep the current Junta official-core baseline complete and detect changes over time.
3. Keep R1–R9 complete and reconciled with canonical trail entities.
4. Keep GR/PR/SL federation layer explicit, including homologation and maintenance state.
5. Download authoritative KML/KMZ/GPX sources where available and validate geometry, CRS, route length and endpoint plausibility.
6. Complete missing technical fields only from authoritative or clearly attributed sources.
7. Reconcile duplicates and route variants instead of creating visually duplicated cards.
8. Persist validated tracks into the route_tracks model and derive elevation only from an approved methodology/source.
9. Expose source, verification date, operational warning and track-validation state in Admin.
10. Pass repository checks/CI and browser/API tests before handoff.

## Safety / publication rule

No route is promoted to publishable while `track_validated=false`. Operational closures or warnings are independent of publication state and must remain visible to users.
