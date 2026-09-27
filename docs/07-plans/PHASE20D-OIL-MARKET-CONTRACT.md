# Phase 20D — Oil market source + API contract

Status: **READY FOR IMPLEMENTATION** (owner direction 2026-09-27).

This document exists so Claude/Codex can implement and audit Phase 20D even when their local
network cannot reach the public source websites.

## Product rule

Do **not** calculate one synthetic "market price" by averaging sources with different methodologies.
Each observation keeps its source, geography, category and period. The UI may overlay series for
comparison, but must label them.

Primary user categories:
- AOVE — Aceite de oliva virgen extra
- AOV — Aceite de oliva virgen
- AOL — Aceite de oliva lampante

Canonical display/storage unit: **EUR_PER_KG**.
Keep the source's original value + unit in the normalized record for traceability.

## Source 1 — Junta de Andalucía (PRIMARY for Andalucía)

Official source:
`https://www.juntadeandalucia.es/agriculturaypesca/observatorio/servlet/FrontController?action=UltimosPrecios&posicion=2291332&producto=33000&subsector=33`

Meaning on the page: latest validated prices in **Almazara o Bodega**.

Verified sample, week 38 (2026-09-14 to 2026-09-20):
- AOVE / VIRGEN-EXTRA: **3.46 EUR/kg**
- AOV / VIRGEN: **3.31 EUR/kg**
- AOL / LAMPANTE (1 g): **3.15 EUR/kg**

The same page exposes multiple previous weeks and is therefore suitable for short historical charts.
The Junta's published campaign reports explicitly state that reproduction of data/graphs is permitted
when the source is cited. Production ingestion must still document the chosen stable access path.

## Source 2 — MAPA (OFFICIAL comparison for Spain / CCAA / representative markets)

Landing page:
`https://www.mapa.gob.es/es/agricultura/temas/producciones-agricolas/aceite-oliva-y-aceituna-mesa/evolucion_precios_ao_vegetales`

Current campaign publishes a weekly PDF per week. Verified sample:
Week 38/2026 (2026-09-14 to 2026-09-20), national prices:
- AOVE: **346.89 EUR/100kg** => **3.4689 EUR/kg**
- AOV: **320.71 EUR/100kg** => **3.2071 EUR/kg**
- AOL: **303.38 EUR/100kg** => **3.0338 EUR/kg**

MAPA also publishes CCAA and representative-market prices, including Jaén in the detailed tables.
The bulletin legal notice allows reuse when source and update date are cited.

Do not parse PDF inside the Android app. If PDF remains the only supported source path, parse it
server-side and expose a normalized JSON contract.

## Source 3 — European Commission DG AGRI (OFFICIAL EU comparison)

Landing page:
`https://agriculture.ec.europa.eu/data-and-analysis/markets/price-data/price-monitoring-sector/olive-oil_en`

The Commission publishes **Weekly price developments: olive oil** as XLSX. Verified listing date
during this plan update: 2026-09-17.

Use this source for EU/international comparison and campaign history, not as the default Home price.
Parse XLSX server-side and keep country/source labels.

## Source 4 — POOLred (NOT APPROVED FOR AUTOMATED INGESTION)

Useful transaction benchmark and local-market reference, including the three main categories.
Do not automate, redistribute or store production data from POOLred without an explicit licence or
agreement from Fundación del Olivar. Manual cross-check only until that happens.

## Other publications

Olimerca / COAG / AgroCLM and similar sources may be used for manual validation and context.
They are not a production source of truth unless reuse permission is confirmed.

## Recommended architecture

Follow the existing Phase 20 weather boundary:

`public official source -> Supabase Edge Function oil-market -> normalized JSON -> Android cache/repository -> UI`

No public website parsing in Compose/UI code.

Suggested Edge Function endpoints/operations:

- `oil-market { operation: "latest", geography: "andalucia" }`
- `oil-market { operation: "series", geography: "andalucia", weeks: 12 }`
- future: `geography: "spain" | "eu:ES" | "eu:IT" | ...`

The Edge Function may have separate adapters:
- `JuntaOilPriceSource`
- `MapaOilPriceSource`
- `EuOilPriceSource`

A source failure must not fail the whole response when another explicitly requested series can still
be returned.

## Normalized observation contract

Example:

```json
{
  "sourceId": "junta-andalucia-observatorio",
  "sourceName": "Observatorio de Precios y Mercados - Junta de Andalucía",
  "geography": {
    "level": "AUTONOMOUS_COMMUNITY",
    "code": "ES-AN",
    "name": "Andalucía"
  },
  "category": "AOVE",
  "periodStart": "2026-09-14",
  "periodEnd": "2026-09-20",
  "valueEurPerKg": 3.46,
  "originalValue": 3.46,
  "originalUnit": "EUR_PER_KG",
  "marketStage": "ALMAZARA_OR_BODEGA",
  "fetchedAt": "<ISO-8601>",
  "sourceUpdatedAt": null
}
```

MAPA example keeps:
- `originalValue: 346.89`
- `originalUnit: EUR_PER_100KG`
- `valueEurPerKg: 3.4689`

Never round the stored normalized value merely for UI formatting.

## API response proposal

```json
{
  "provider": "oil-market",
  "fetchedAt": "<ISO-8601>",
  "series": [
    {
      "sourceId": "junta-andalucia-observatorio",
      "geography": "ES-AN",
      "category": "AOVE",
      "observations": []
    }
  ]
}
```

The UI must not assume all series contain the same weeks.

## Cache / stale rules

- Weekly prices are not real-time.
- A newly published weekly observation remains **fresh** until the expected next weekly publication
  window has passed plus a safety margin.
- If refresh fails, keep the latest observation but display its week/date explicitly and mark it
  stale when appropriate.
- Never label a cached old value as "precio actual".
- Missing categories/weeks remain missing; do not interpolate as observed data.

## UI scope for 20D

### Home card
Show the latest **Junta Andalucía** official reference:
- AOVE
- Virgen
- Lampante
- week/date
- source
- stale/unavailable state

CTA: `Ver mercado`.

### Mercado del aceite
Initial filters:
- 12 semanas
- Campaña
- 1 año (only when enough official data is available)

Series:
- AOVE
- Virgen
- Lampante

Source/geography selector:
- Andalucía
- España
- Europa (comparison view)

Future only after licensed local source:
- Jaén
- Picual / local market views

## Offline behaviour

- Home and chart load cached observations immediately.
- Offline mode never attempts to present radar-style live semantics; prices are weekly observations.
- Display the source period and cache age.
- All farm/Cuaderno functionality remains independent of market data.

## Fixtures required before production network code

Create source fixtures for:
1. Junta week 38 sample above.
2. Junta missing category / "--".
3. MAPA week 38 national sample above.
4. MAPA representative-market sample (Jaén) if implemented.
5. EU XLSX normalized sample.
6. malformed/changed upstream document.
7. upstream timeout/5xx.

Tests must verify:
- original unit conversion is correct;
- AOVE/AOV/AOL mapping is strict;
- missing data stays missing;
- stale cache survives source failure;
- one source cannot silently impersonate another;
- source + period are always visible in UI.

## Gate 20D

PASS only when:
1. no invented/interpolated observed prices;
2. latest Junta values render with source/week;
3. offline cache is honest;
4. 12-week series renders gaps correctly;
5. source switch never merges methodologies into one line;
6. source/parser failure leaves the rest of Mágina Olivo fully usable;
7. fixture tests + Android tests + CI are green.
