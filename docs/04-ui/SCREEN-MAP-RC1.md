# Olive Farm App — Screen Map RC1.2

**Status:** Phase 0.6 draft
**Navigation root:** `Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil`
**Normative override:** `docs/00-master/RC1.2-PRODUCT-LOCK.md`. Public brand TBD.

This document defines purpose, entry/exit and required states for every RC1 screen. UI implementation must not invent new top-level navigation without a baseline Change Request.

# 1. Global navigation model

```text
APP
├── Auth flow (only when required)
└── Main shell
    ├── Inicio
    ├── Mi Olivar
    ├── Registrar (+)
    ├── Calendario
    └── Perfil
```

Global rules:

- Android back must return predictably through the stack.
- Bottom navigation keeps root destinations stable.
- Deep screens do not add new bottom tabs.
- `Registrar (+)` can be opened globally or contextually.
- `Mi Olivar` is the root for farm → parcel → campaign navigation.
- `Calendario` surfaces planned work/reminders.
- `Perfil` contains account, municipality, preferred cooperative, preferences and sync/help surfaces.
- destructive/closing actions require appropriate confirmation, not every ordinary save.

---

# 2. Launch/Auth

## S00 — Onboarding

Purpose: explain the product in 4–6 concise visual screens on first run.

Narrative: farms/parcels → field work → harvest/delivery and invoice OCR → reminders → weather/market/cooperative/history.

Rules:

- skippable;
- no network dependency for the explanatory screens;
- can be reopened from Help/About;
- does not replace authentication or workspace setup.


## S01 — Splash/Bootstrap

Purpose: restore local app/session state and route without unnecessary blocking.

States:

- local bootstrap;
- authenticated local session;
- first run/no session;
- schema migration in progress;
- fatal migration/startup error.

Exit:

- S02 Login;
- S05 Main/Inicio.

Offline rule: previously usable local workspace must not be blocked merely because remote refresh is unavailable, subject to final auth-token security behavior.

## S02 — Login

Fields:

- email;
- password.

Actions:

- Entrar;
- Crear cuenta;
- Recuperar contraseña.

States:

- idle;
- validating;
- signing in;
- wrong credentials;
- network unavailable;
- server error.

## S03 — Crear cuenta

Minimum fields:

- email;
- password;
- confirm password;
- legal acceptance when applicable.

Exit:

- workspace bootstrap;
- Inicio.

## S04 — Recuperar contraseña

Email request + clear success state.

---

# 3. Main shell

## S05 — Inicio

Purpose: operational summary and fastest path to record work.

Sections:

1. active campaign card;
2. farms/parcels/managed area;
3. prominent `+ Registrar`;
4. campaign metrics with real data only;
5. farm shortcuts;
6. recent activity;
7. compact sync status when attention is needed.

Empty first-run state:

`Aún no tienes fincas` + `Crear mi primera finca`.

Contextual RC1.2 blocks may include weather/radar, olive-oil reference market and preferred-cooperative notices/news. They must be secondary, cached/optional and never block field workflows. No unrelated ads/general portal feed.

---

# 4. Farms

## S10 — Mis Fincas

Content:

- search/filter when useful;
- farm cards;
- area/parcel count;
- mini map/visual identity where efficient;
- `+ Nueva finca`.

States:

- empty;
- populated;
- archived filter optional;
- local loading;
- sync warning non-blocking.

Tap farm → S12.

## S11 — Nueva Finca

Required:

- name.

Optional:

- municipality;
- province;
- description/notes;
- cover photo later.

Primary action: `Crear finca`.

Success exits to S12 and encourages adding parcels.

## S12 — Detalle Finca

Header:

- name;
- current total managed area;
- parcel count;
- map containing owned parcels.

Primary action: `Registrar`.

Internal tabs/sections:

- Resumen;
- Parcelas;
- Campaña;
- Documentos.

Actions:

- Editar;
- Añadir parcela;
- Abrir mapa full-screen;
- Archivar through secondary menu.

## S13 — Editar Finca

Edits current metadata only. Historical campaign snapshot values are not rewritten.

## S14 — Mapa Finca

Full map:

- all active parcel geometries;
- selected parcel highlight;
- fit bounds;
- user position action when permission/useful;
- quick parcel sheet/card.

Tap parcel → quick card → S23.

## S15 — Parcelas de Finca

List/grid alternative to the map, always available.

Each row:

- operational name;
- area;
- cadastral identifier summary.

---

# 5. Add parcel / Catastro

## S20 — Añadir Parcela — Método

Options:

1. Buscar en Catastro (mapa);
2. Referencia catastral;
3. Importar GML.

Future methods may appear without changing downstream confirmation flow.

## S21 — Mapa Catastro

Layers:

- base map;
- cadastral WMS discovery layer;
- candidate highlight;
- existing owned parcels.

Actions:

- search place/reference shortcut;
- my location;
- tap parcel/candidate;
- confirm candidate.

States:

- provider loading;
- provider unavailable;
- multiple candidates;
- no parcel found;
- selected candidate.

Saved owned parcels remain visible from local data even when provider fails.

## S21B — Buscar Referencia

Input cadastral reference.

Result → S22.

Validation distinguishes malformed reference from provider no-result.

## S21C — Importar GML

Android document picker → parse/validate → S22.

## S22 — Confirmar Parcela

Show:

- polygon preview;
- cadastral identity;
- source surface where available;
- municipality;
- source/provenance;
- selected destination farm;
- editable operational name;
- managed area override optional.

Duplicate warning if needed.

Primary: `Añadir a mi finca`.

## S23 — Detalle Parcela

Map-first header.

Show:

- display name;
- area;
- cadastral polygon/parcel/reference;
- farm;
- active campaign;
- quick campaign metrics where valid;
- latest activity;
- `+ Registrar`;
- timeline preview.

Secondary sections:

- Información;
- Histórico;
- Documentos;
- Perfil del olivar where exposed.

## S24 — Editar Parcela

Editable operational fields:

- display name;
- managed area;
- notes;
- agronomic profile subset.

Cadastral source fields are displayed separately; source refresh is not an ordinary edit.

## S25 — Histórico Parcela

Full chronological timeline filtered by campaign/type/date.

Entries can include:

- activity;
- incident;
- harvest;
- relevant attachment/event.

Tap → appropriate detail screen.

## S26 — Documentos Parcela

Attachment list + add file/photo action.

---

# 6. Campaigns

## S30 — Campaña Root

If one active campaign exists, open its dashboard.

Otherwise show:

- current/preparation campaigns;
- historical campaigns;
- `Nueva campaña`.

## S31 — Nueva Campaña

Fields:

- name (suggested from season years);
- season start/end years;
- optional dates;
- notes;
- select participating farms/parcels.

Before activate: preview included parcel count/area.

## S32 — Dashboard Campaña

Header:

- name;
- state;
- managed snapshot area;
- number parcels.

Metrics:

- work count;
- expenses;
- harvested kg;
- kg/ha when mathematically valid;
- deliveries/yield where data exists.

Sections/actions:

- `Registrar`;
- activity;
- farms/parcels;
- harvest;
- expenses;
- deliveries;
- documents;
- report;
- state transition/close.

## S33 — Actividad Campaña

Chronological list with filters:

- date;
- type;
- farm;
- parcel.

## S34 — Parcelas Campaña

Shows historical snapshot names/areas where appropriate.

## S35 — Cerrar Campaña

Pre-close checks:

- pending local drafts;
- unsynced records warning (does not necessarily prohibit local close unless contract requires it);
- campaign summary.

Require explicit confirmation.

Result: `CLOSED` and protected history.

## S36 — Histórico Campañas

List past campaigns with summary metrics.

Tap → read-focused S32 variant.

---

# 7. Register hub

## S40 — ¿Qué quieres registrar?

Primary families:

- Observación;
- Poda;
- Abonado;
- Tratamiento;
- Suelo;
- Riego;
- Mantenimiento;
- Incidencia;
- Cosecha;
- Gasto;
- Otro.

Context banner if parcel/farm/campaign already preselected.

Use recent/favorite templates later without changing the base list semantics.

---

# 8. Common activity form behavior

Typed forms S41–S48 share:

1. Date.
2. Campaign (normally active/defaulted).
3. Farm/parcel selection.
4. Type-specific fields.
5. Cost when appropriate.
6. Notes.
7. Photos/documents.
8. Save.

Common states:

- new;
- editing;
- recovered draft;
- validation error;
- saved locally pending sync;
- saved + synced later.

Multi-parcel selector clearly shows all selected parcels.

## S41 — Observación

Minimal fast record:

- date;
- parcel(s);
- note/title;
- optional photo.

## S42 — Poda

- pruning type;
- workers;
- hours;
- machinery;
- residue handling;
- cost;
- notes/photos.

## S43 — Abonado

- product;
- total quantity/unit;
- dose/unit;
- method;
- cost;
- notes/photos.

## S44 — Tratamiento

- product;
- active substance optional;
- quantity/unit;
- dose/unit;
- reason;
- equipment;
- cost;
- notes/photos.

## S45 — Suelo

- work type;
- method;
- machinery;
- hours/cost where useful;
- notes/photos.

## S46 — Riego

- duration;
- volume if known;
- sector text;
- system;
- cost/notes.

## S47 — Mantenimiento

- maintenance type;
- asset/location text;
- description;
- cost;
- photos.

## S48 — Incidencia

- category;
- severity;
- parcel/location;
- description;
- photos;
- status;
- action taken.

## S49 — Otro

Generic but structured enough to remain searchable:

- date;
- parcel(s);
- title;
- description;
- cost optional;
- attachments.

---

# 9. Activity detail/edit

## S50 — Detalle Actuación

Read view:

- type/date;
- target parcels;
- typed details;
- notes;
- attachments;
- linked expense/cost where relevant;
- sync state only if useful.

Actions:

- Editar;
- Duplicar as new later/template future;
- soft delete from overflow with confirmation.

## S51 — Editar Actuación

Uses same typed form component with existing data.

---

# 10. Expenses

## S60 — Gastos

Campaign-oriented list with:

- total;
- filters;
- category breakdown only where useful;
- `+ Gasto`.

## S61 — Nuevo/Editar Gasto

- date;
- amount/currency;
- category;
- description;
- supplier text;
- optional farm/parcel/activity/harvest/delivery relation;
- invoice/ticket attachment.

## S62 — Detalle Gasto

Read/edit/delete entry point.

---

# 11. Harvest

## S70 — Cosechas

List by date with total kg and parcel context.

## S71 — Nueva/Editar Cosecha

- date;
- parcel(s);
- total kg;
- exact per-parcel allocation when known;
- explicit `No conozco el reparto exacto` option;
- collection method;
- workers;
- machinery;
- notes/attachments.

Validation never invents per-parcel distribution.

## S72 — Detalle Cosecha

Show total and allocation mode clearly.

---

# 12. Deliveries

## S80 — Entregas

List by date/destination with campaign delivered total.

## S81 — Nueva/Editar Entrega

- date;
- destination/almázara;
- kg;
- delivery/albarán/ticket numbers;
- fat yield;
- industrial yield;
- notes;
- ticket/photo/PDF.

## S82 — Detalle Entrega

Read/edit/delete entry point.

---

# 13. Attachments

## S90 — Documentos

Generic workspace/campaign document browser accessible through Más, without replacing contextual farm/parcel document sections.

Filters:

- owner/context;
- type;
- date.

## S91 — Visor Documento/Foto

Preview where platform supports it; otherwise open through Android intent.

Actions:

- share;
- rename metadata if allowed;
- delete link/attachment with confirmation.

---

# 14. Reports

## S100 — Informes

Options:

- Campaña;
- Finca;
- Parcela.

Require/select context.

## S101 — Previsualizar Informe

Sections reflect the chosen report and data availability.

Actions:

- Generar PDF;
- Compartir;
- Guardar.

Show generation errors without losing underlying domain data.

---

# 15. More / settings

## S110 — Más

RC1 entries:

- Documentos;
- Sincronización;
- Perfil/Cuenta;
- Ajustes;
- Acerca de / fuentes/licencias.

Future Mi Campo resources appear here or in a dedicated secondary hub without changing bottom navigation.

## S111 — Sincronización

Show understandable operational info:

- last successful sync;
- pending count;
- failed/conflict count;
- `Sincronizar ahora`;
- individual attention list only for failures/conflicts.

Do not expose raw stack traces/XML.

## S112 — Perfil/Cuenta

- account email;
- workspace identity;
- logout;
- recovery/security links as applicable.

## S113 — Ajustes

Keep small for RC1. Only settings with real product value.

## S114 — Acerca de / Legal / Data sources

Include app version, privacy/legal documents and Catastro/source attribution/licence references where required.

---

# 16. Contextual quick flows

## Parcel → quick work

```text
S23 Parcel Detail
→ + Registrar
→ S40 with parcel preselected
→ typed form
→ save local
→ S23 or S50
```

## Farm → add parcel

```text
S12 Farm
→ Añadir parcela
→ S20
→ Catastro/ref/GML
→ S22 confirm
→ S12/S23
```

## Campaign → harvest

```text
S32 Campaign
→ Registrar
→ Cosecha
→ S71
→ local save
→ campaign metrics update from Room
```

## Incident follow-up

```text
S48 create incident
→ S50 incident detail
→ edit status/action
→ resolved
→ parcel timeline preserves full current event metadata
```

---

# 17. Empty/error/offline design rules

Every collection/detail screen must define:

- content state;
- empty state with useful next action;
- local DB loading/migration state where necessary;
- non-blocking sync pending state;
- provider/network error only where external data is truly required;
- permission-denied state when camera/location/doc access was requested.

Avoid generic full-screen spinners for data already available locally.

---

# 18. Phase 0.6 Gate

- [ ] every RC1 entity has a create/read/edit path where required;
- [ ] destructive operations are reachable but not prominent;
- [ ] all primary flows return to a sensible destination;
- [ ] bottom navigation is stable;
- [ ] contextual Register preselection is defined;
- [ ] Catastro failure does not hide owned parcels;
- [ ] harvest and delivery have separate flows;
- [ ] offline saved state is representable in UI;
- [ ] conflicts/sync errors have a destination under Perfil / synchronization support;
- [ ] future Mi Campo modules can be added without another primary tab;
- [ ] historical campaign access uses the same conceptual dashboard in protected/read-focused mode;
- [ ] contextual weather/market/preferred-cooperative Home blocks remain secondary and there is no unrelated public portal feed;
