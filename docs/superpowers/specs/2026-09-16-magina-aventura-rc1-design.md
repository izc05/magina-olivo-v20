# Mágina Aventura V20 — RC1 closure design

Date: 2026-09-16
Status: Approved design, pre-implementation
Release branch: `release/v20-magina-aventura-rc1`
Base branch: `integrate/v20-beta-closure`

## 1. Purpose

Mágina Aventura V20 is now in release-closure mode. The goal of RC1 is not to add another broad generation of features, but to consolidate the existing specialist work into one coherent, testable, deployable release candidate focused on the hiking/adventure product.

RC1 must make the following journey feel like one product rather than a collection of stacked experiments:

`Inicio Aventura → Explorar rutas → Ficha de ruta → Preparar aventura → Aventura en curso → Checkpoints/retos → Finalización → Mi Aventura → Colecciones/insignias → Comunidad → Perfil`

The release must use real, auditable route, weather, GPS and user data. Safety and source truth always override gamification.

## 2. Product boundary

RC1 is specifically **Mágina Aventura**. Shared V20 infrastructure may remain in the repository, but closure decisions for this release are evaluated against the adventure product.

In scope:

- premium Adventure landing and exploration experience;
- real hiking route catalogue and route details;
- authoritative route-source and geometry handling;
- live foreground GPS recording and telemetry;
- validated track display and next-checkpoint logic;
- preparation/safety/weather experience;
- Weather Engine and premium weather visuals;
- levels 1–10, XP, badges, expeditions and collections;
- kilometres conquered and explored-territory progression;
- community content with real/approved data only;
- profile/adventure summary;
- super-admin operational surfaces required to manage Adventure content;
- mobile-first responsive UX and desktop composition;
- offline/poor-connectivity states where the web architecture can support them safely;
- release, staging and field-validation documentation.

Out of scope for RC1 unless required by an Adventure dependency:

- broad Mi Campo/agricultural expansion;
- new commercial subsystems unrelated to Adventure;
- speculative social feeds or synthetic engagement;
- new crypto/wallet/investment semantics;
- unvalidated scientific nowcasting or precipitation conversions;
- arbitrary new route data without authoritative provenance.

## 3. Release branch strategy

The only RC1 integration branch is:

`release/v20-magina-aventura-rc1`

It is created from the current `integrate/v20-beta-closure` candidate and must not modify `main`.

Specialist branches remain sources of validated deltas, not competing release trunks.

Planned absorption order:

1. `feat/v20-routes-adventure-premium-mobile` — PR #142, premium Adventure trunk.
2. `feat/v20-bedmar-adventure-vertical` — PR #147, real Bedmar GPS vertical.
3. `feat/v20-adventure-weather-visuals` — PR #152, premium weather rendering.
4. `feat/v20-route-geometry-ingestion-v1` — PR #153, port the useful geometry-ingestion delta onto the current RC line rather than merging its older ancestry blindly.
5. `feat/v20-routes-catalog-completion` — PR #93, absorb only route-catalogue/source-truth pieces not already present.
6. `feat/v20-ui-ux-premium` — PR #138, absorb only the Adventure-relevant transversal shell/tokens/navigation work after specialist Adventure UI is present, avoiding duplicate implementations.

Every absorption must be small, reviewable and followed by the relevant gates before proceeding.

## 4. Functional target

### 4.1 Inicio Aventura

The Adventure home must establish Sierra Mágina identity, show meaningful real context and route users toward real actions. It should include the premium visual hero, route/adventure calls to action and real user/adventure summary where authenticated.

No fake statistics, fake users, fake route activity or fabricated weather may be used to fill empty states.

### 4.2 Route catalogue and detail

The route catalogue must present only route facts supported by audited sources or validated geometry. Route detail must keep these concerns separate:

- editorial/publication state;
- geometry-validation state;
- operational/safety state;
- technical source provenance.

A valid geometry does not automatically make a route safe or publishable. Temporary closure or current safety information has priority over points, XP or challenge mechanics.

Route details should expose, when genuinely available:

- distance;
- estimated duration;
- difficulty;
- ascent/descent or elevation data;
- track/map;
- official/authoritative source;
- safety/closure information;
- checkpoints/discoveries;
- weather preparation;
- GPX/KML-related actions where allowed by the existing product contract.

### 4.3 Official geometry ingestion

Geometry ingestion must normalize supported KML/GML/KMZ inputs into canonical track geometry, calculate documented basic metadata and version source + geometry in PostGIS.

A new geometry version must not become active merely because parsing succeeded. The system must preserve discrepancy review and activation controls.

If an official remote source cannot be accessed reliably, the system may record the failure and support an authorized manual-official upload path. It must never silently substitute a community track and label it official.

### 4.4 Adventure preparation

Before starting, the product must show a compact preflight view with the information actually available for that route and current location/context:

- route status and key safety notices;
- weather state;
- GPS permission/readiness;
- expected track/checkpoint availability;
- connectivity/offline warning where relevant;
- a clear start action.

If required data is unavailable, the user must see an explicit designed state rather than a false green status.

### 4.5 Adventure in progress

The live screen must use one foreground GPS watcher/recording source of truth. No second competing geolocation watcher is introduced for HUD or map convenience.

The live experience includes:

- current position on the route map;
- map recentering;
- GPS accuracy/readiness state;
- recorded distance;
- ascent/elevation information when supported by the existing recorder contract;
- next checkpoint/discovery determined from route progression/proximity rules;
- validated route track;
- route safety context;
- dynamic weather HUD and visual atmosphere;
- reduced-motion support;
- designed GPS-denied, GPS-degraded, stale-weather and connectivity states.

Track/safety behaviour always wins over gamification effects.

### 4.6 Completion and progression

A completed valid activity may feed the existing Adventure progression contracts. RC1 preserves the established distinction between historical progress and spendable/reward concepts from other V20 modules; Adventure itself must not present XP as money or an investment asset.

Adventure progression includes the existing concepts where backed by real persisted data:

- levels 1–10;
- XP;
- badges/achievements;
- expeditions;
- kilometres conquered;
- explored territory;
- collections: Flora, Fauna, Patrimonio, Olivar, Tradiciones and Paisaje.

Completion must be idempotent and must not grant duplicate progress because of retries, refreshes or intermittent connectivity.

### 4.7 Community

Community surfaces may show real reviews, photos and approved notices. Empty states must remain empty/designed; synthetic activity is prohibited.

Moderation/admin rules remain authoritative for public visibility.

### 4.8 Admin

RC1 must expose or preserve the Adventure administration capabilities necessary to operate the release, including the routes/adventure content and governance surfaces already implemented in specialist/admin branches.

The admin target is operational reliability rather than a new redesign. The final transversal UI pass may improve visual consistency, but must not weaken permissions, auditability or super-admin protections.

## 5. Weather architecture

Weather uses the existing Weather Engine contracts rather than introducing a second weather source stack.

The shared weather state may combine:

- Open-Meteo by route/current coordinates for general conditions;
- existing AEMET/radar territorial/official information;
- explicit freshness/loading/error/stale/no-data states.

Premium Weather Visuals operate as a renderer of the normalized weather model. They must not mutate the scientific/source state.

Supported visual effects may include rain, snow, fog, cloud, wind, olive leaves, dust, lightning and day-phase tinting, subject to:

- reduced motion;
- performance tiers;
- accessibility/contrast;
- no invented weather;
- official warnings overriding decorative presentation.

## 6. Offline and background boundary

RC1 must be explicit about the limitations of a browser/PWA hiking experience.

Software scope:

- robust foreground GPS behaviour;
- cached/available route experience where existing architecture permits it;
- designed offline/poor-network states;
- no data loss on normal transient network failures where the recorder already supports recovery.

Field validation still required before public claims:

- real GPS accuracy on Sierra Mágina routes;
- battery consumption;
- behaviour when the screen locks;
- true background tracking behaviour;
- no/poor coverage behaviour.

If browser platform limits prevent reliable background tracking, RC1 must document that limitation rather than pretending native-equivalent behaviour. Native/Capacitor work is a separate decision unless already implemented and verified in-repo.

## 7. Visual system

The Adventure specialist UI is authoritative for Adventure-specific experience. The transversal UI branch may provide shared shell, tokens and navigation after specialist features are present.

Visual goals:

- Sierra Mágina identity: mountain, stone, olive landscape and natural light;
- premium but practical outdoor-app character;
- mobile first without treating desktop as stretched mobile;
- strong map readability;
- clear safety hierarchy;
- restrained motion outside contextual weather/adventure effects;
- no emoji as primary product iconography;
- consistent topbar/bottom navigation/shell patterns where compatible.

Required viewport QA:

`360 / 390 / 430 / 768 / 1024 / 1280 / 1440 / 1920`

Required interaction/accessibility properties:

- no horizontal overflow;
- primary touch targets >= 44 px;
- visible keyboard focus;
- meaningful accessible names/labels;
- contrast appropriate to outdoor/map contexts;
- `prefers-reduced-motion` respected;
- designed loading, empty, error, stale, offline, session and GPS-permission states.

## 8. Data integrity and safety rules

Non-negotiable rules:

1. Never invent route facts, tracks, weather, safety notices, users or community activity.
2. Authoritative source and geometry validation are distinct states.
3. Valid geometry does not imply a currently safe/open route.
4. Operational closure overrides Adventure start/gamification.
5. Community tracks cannot silently become official tracks.
6. GPS data comes from one recording source of truth during an activity.
7. Completion/reward/progress operations must remain retry-safe/idempotent.
8. Weather visuals render normalized state; they do not invent meteorology.
9. No new feature is accepted into RC1 while its required gates are red.

## 9. Testing and release gates

RC1 is considered software-complete only when the intended release functionality exists on one SHA and the relevant matrix is green on that SHA.

At minimum, the final gate set must cover:

- TypeScript/typecheck;
- production build;
- Full Candidate checks;
- Browser E2E;
- Adventure-specific tests;
- Activity/GPS tests;
- Routes/catalogue/source-truth tests;
- Weather Engine tests;
- Weather Visuals tests where present;
- geometry-ingestion tests;
- environment contract;
- staging readiness;
- lockfile/foundation/runtime guards affected by the integration;
- responsive/accessibility visual QA.

A green specialist branch is evidence for absorption, but it is not sufficient for release. The integrated RC1 SHA must be revalidated.

## 10. Real staging

RC1 must ultimately be deployed to a real staging environment before external Beta. Passing a staging-readiness workflow is not the same as having deployed staging.

The release process must verify the actual dependencies used by Adventure, including as applicable:

- PostgreSQL/PostGIS and migrations;
- application/web/API/worker deployment;
- route geometry storage/data;
- weather/radar access from the deployed host;
- media/storage required by community/content;
- authentication;
- HTTPS;
- backup/restore path for persistent data;
- smoke journeys on the deployed URL.

Secrets are configured in the deployment environment, never committed or requested in chat.

## 11. Field-validation gate

After software RC1 is green and deployed, perform a real route test in Sierra Mágina, with Bedmar as the initial vertical when operationally appropriate.

Field checklist:

- acquisition and stability of GPS;
- practical positional accuracy;
- route-centering usability;
- checkpoint progression/proximity;
- recorded distance plausibility;
- ascent/elevation plausibility;
- weather + GPS coexistence;
- battery impact;
- screen-lock/background behaviour;
- temporary loss of connectivity;
- recovery/resume;
- completion and progression sync;
- safety/closure presentation.

Field results must be documented separately from software CI. A software-green RC does not imply field-green.

## 12. Definition of Done

Mágina Aventura V20 RC1 is complete when all of the following are true:

- one release branch contains the intended Adventure feature set;
- premium Adventure, Bedmar live GPS, weather visuals, route geometry ingestion and route catalogue truth are consolidated without duplicate subsystems;
- route safety/source rules remain intact;
- the end-to-end Adventure journey works with real data;
- levels/XP/badges/collections and completion work from real persisted activity;
- community contains no fabricated activity;
- required Adventure admin operations are available and permission-safe;
- mobile/tablet/desktop UX meets the viewport/accessibility requirements;
- all required automated gates are green on the same RC SHA;
- real staging is deployed and smoke-tested;
- physical field validation is completed or any remaining platform limitation is explicitly documented and accepted;
- `main` has not been used as a shortcut during integration.

Only after this definition is satisfied should RC1 be considered ready to promote toward an external Beta/release path.

## 13. Implementation principle

From this point forward, the default answer to a new idea is **not yet** unless it closes a documented RC1 gap, fixes a defect, or is required for safe operation. The objective is to finish, integrate, validate and ship Mágina Aventura V20 rather than expand its surface area indefinitely.
