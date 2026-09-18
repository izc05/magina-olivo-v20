# RC1.2 Screen Specification — Onboarding + Home

**Status:** IMPLEMENTATION REFERENCE  
**Baseline:** `RC1.2-BASELINE-2026-09-18`

## 1. Onboarding

Target: 5 screens. Concise, visual, skippable.

### O1 — Tu olivar
Message:
`Gestiona tus fincas y parcelas desde un solo lugar.`

Visual:
- olive grove / parcel image;
- simple farm + parcel hierarchy illustration.

CTA:
- Siguiente
- Saltar

### O2 — Registra trabajos
Message:
`Riego, tratamientos, abonado, poda, maquinaria y trabajos diarios.`

Visual:
- activity chips/icons;
- one clear work log card.

### O3 — Controla tu cosecha
Message:
`Fotografía tus vales, confirma los kilos y añade el rendimiento cuando llegue.`

Visual:
- ticket/photo → OCR → confirmed delivery;
- yield marked “Pendiente” first.

### O4 — Organiza lo que viene
Message:
`Programa riegos, cosecha, poda o cuadrillas y recibe avisos cuando los necesites.`

Visual:
- calendar;
- reminder;
- people/crew count.

### O5 — Todo tu olivar contigo
Message:
`Histórico, tiempo, radar, mercado del aceite y tu cooperativa de referencia.`

CTA:
`Empezar`

Rules:
- no network dependency for explanatory content;
- can be skipped;
- can be reopened from Help/About;
- do not ask for account/permissions inside illustration screens;
- no forced GPS;
- brand name remains configurable until Naming Gate closes.

## 2. Home hierarchy

Home answers, in this order:

1. What matters today?
2. How is my current campaign going?
3. What is coming next?
4. What weather is coming?
5. What is the reference oil market doing?
6. Is there a relevant notice from my cooperative?

External content never outranks farm operation.

## 3. Home top area

### Context header

Possible content:

```text
Buenos días

Finca Foralico
Bedmar, Jaén
```

If no current farm selected:

```text
Buenos días

Mi olivar
```

Location can come from:
- selected farm;
- user profile locality;
- manually selected weather location.

Do not require GPS.

## 4. Weather hero

Compact hero:

```text
22 °C
Parcialmente nublado
Lluvia próximas horas: 15 %
Viento: 11 km/h

[ Ver radar ]
```

Optional:
- weather alert chip;
- last update/source.

### Weather-responsive visual layer

Presentation states:

- CLEAR: warm light/sun glow;
- CLOUDY: subtle cloud layer;
- RAIN: light rain overlay;
- WIND: slight olive-leaf movement;
- FOG: subtle mist;
- STORM: restrained darker sky emphasis.

Rules:
- operational text always remains readable;
- no heavy particle engine;
- system reduced-motion respected;
- stale weather visibly marked;
- battery/performance tier can simplify effects;
- no random visual condition different from fetched weather.

## 5. My Olive Grove card

Primary operational card:

```text
Finca Foralico
Campaña 2026/27

12 parcelas
24,38 ha

5.300 kg entregados
22,43 % rendimiento medio

[ Ver campaña ]
```

If multiple farms:
- show current/most recently active;
- provide `Ver todas las fincas`.

No farm:
- strong empty state;
- `Añadir mi primera finca`.

## 6. Upcoming work

Show 1–3 next items.

Examples:

```text
Mañana · Riego
Los Llanos · Sector 4 · 08:00

22 sep · Tratamiento
La Hoya · Cobre

4 oct · Recolección
Finca Foralico · 6 personas
```

Actions:
- open item;
- mark completed;
- reschedule;
- create reminder.

## 7. Quick register

The centered bottom `+` remains the primary fast action.

Home may additionally expose a compact quick-action row:

- Trabajo
- Gasto
- Entrega
- Documento

Do not duplicate a giant action grid.

## 8. Oil market card

When source exists:

```text
Mercado del aceite

AOVE       3,xx €/kg
Virgen     3,xx €/kg
Lampante   3,xx €/kg

[ 1M ] [ 3M ] [ 1A ]
[ Ver evolución ]
```

Rules:
- shared time axis;
- source;
- last update;
- reference-market wording;
- not the exact price a cooperative will pay;
- unavailable categories are hidden, not fabricated.

## 9. Preferred cooperative

Compact card:

```text
Mi cooperativa

Cooperativa X

Aviso
Inicio de recepción de aceituna...

[ Ver aviso ]
```

Can include:
- one latest notice;
- one latest news item.

No preferred cooperative:
- optional setup CTA;
- Home still works normally.

## 10. Offline/stale behavior

When offline:

- farm/campaign/upcoming work still fully available;
- last-known weather may show `Actualizado hace ...`;
- radar requires connection and says so;
- oil market shows cached last-known value with timestamp;
- cooperative content shows cached item or hides gracefully.

Never show an external loading spinner that blocks Home.

## 11. Empty Home

New user:

```text
Tu olivar empieza aquí

Añade tu primera finca y empieza a registrar parcelas, trabajos y campañas.

[ Añadir finca ]
```

Secondary:
- onboarding tips;
- no fake sample metrics unless clearly marked as demo/tutorial.

## 12. Home reference screen acceptance

At common phone width, user should understand in under 5 seconds:

- current farm/campaign state;
- next work;
- weather;
- where to register something.

External information must be visually secondary to the agricultural core.
