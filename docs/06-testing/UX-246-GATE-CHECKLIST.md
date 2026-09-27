# Issue #246 / CR-007 Gate Checklist — Mi Cuaderno como centro operativo

Status: **PENDING DEVICE CHECK** (UX-G). Automated evidence is below; the reorganisation is not
closed until the owner runs the device checks and reports the result (Issue #246 §9: "No
declarar la reorganización terminada sin CI verde y validación física").

## Slices merged

| Slice | PR | Room | CI on merged head |
|---|---|---|---|
| UX-A — Audit | (Issue comment, approved) | — | — |
| UX-B — Navigation shell | #248 | — | green |
| UX-C — Mi Cuaderno shell | #249 | — | green |
| UX-D — Registrar hoy | #250 | — | green |
| UX-E — Diario + Fitosanitario · Gastos · Campaña | #251 | — | green |
| UX-F — Registrar from Mi Campo | #252 | — | green |
| UX-G — QA (this checklist + tests) | this PR | — | pending |

No Room schema change in the whole reorganisation (still v15); no migration.

## Automated evidence (CI: unit + instrumented on emulator)

| Criterion (Issue #246) | Test evidence |
|---|---|
| Bottom bar Inicio · Mi Campo · Cuaderno · Avisos · Perfil; every root reachable and selected | `AppNavigationTest.allFrozenRootsAreReachableAndSelected`, `AppDestinationTest` |
| Old routes (`register`, `calendar`) keep their owning root (Cuaderno, Avisos) | `AppDestinationTest.nestedRoutesKeepTheirOwningRootSelected` |
| Reminder notification opens its work over Avisos | not covered by CI — device check D10 |
| Cuaderno: context, Registrar hoy, nine quick actions, four tabs | `NotebookHomeScreenTest` |
| Registrar hoy → ¿Qué has hecho hoy? → existing form, type preset, today, context shown | `RegisterTodaySheetTest`, `AppNavigationTest.cuadernoRegisterTodayOpensTheChoicesBeforeTheFlow` |
| One record, one home (Registrar writes the same Activity the Farm shows) | `AppNavigationTest.registrarPlusCreatesARealActivityOnTheSelectedFarm` |
| **Success criterion:** register → visible at once in Diario → still there after a cold reopen, same active Farm | `AppNavigationTest.registerTodayShowsInTheDiaryAndSurvivesARestart` |
| Diario / Fitosanitario / Gastos / Campaña are projections; nothing invented or counted twice | `NotebookViewsTest`, `CampaignNotebookTest` |
| Registrar from a Farm / Parcel opens Cuaderno → Registrar hoy with that context | `AppNavigationTest.registerFromAFarmOpensCuadernoRegisterTodayWithThatFarm`, `ParcelScreensTest.registerOnAParcelHandsItsFarmAndNameToCuaderno` |
| Accessibility: icon + text, every control a labelled button | `NotebookHomeScreenTest.quickActionsAndTabsAreLabelledControls` |
| Offline: Cuaderno reads only Room; no network boundary in notebook code | `ArchitectureBoundaryTest` (network only under `data/remote/…`) |

## Device checks (owner) — to run on the APK of the merged `main`

Report device model and Android version with the result.

| # | Check | Expected |
|---|---|---|
| D1 | Open the app → bottom bar | Inicio · Mi Campo · **Cuaderno** (centre) · Avisos · Perfil |
| D2 | Cuaderno → Registrar hoy → Riego | Irrigation form, today's date, Farm · Campaña visible before saving |
| D3 | Save it | "Actuación guardada en este dispositivo"; back in Cuaderno the Diario shows it at once |
| D4 | Tratamiento with product, dose and parcel | Fitosanitario shows it; missing data listed as "Falta: …" |
| D5 | Gasto (e.g. gasoil) | Gastos total and "Maquinaria" group include it once |
| D6 | Mi Campo → a Farm → "Registrar en esta finca" | Cuaderno opens with that Farm and "¿Qué has hecho hoy?" |
| D7 | Mi Campo → a Parcel → "Registrar en esta parcela" → Trabajo | The Parcel is already ticked in the form |
| D8 | **Airplane mode**, repeat D2–D3 | Works the same; nothing waits for signal |
| D9 | Close the app from recents and reopen (cold start) | Same active Farm in Cuaderno; records still there |
| D10 | Avisos | Agenda (overdue, today, next days); a reminder notification opens its work over Avisos |
| D11 | Large font (Ajustes → Pantalla → Tamaño de fuente máximo) | Cuaderno readable; buttons and chips still tappable, no text cut |
| D12 | TalkBack on Cuaderno | Each quick action and tab is read by its name |

## Known limits (not blocking this gate)

- Fitosanitario: no dedicated applicator field nor document link yet; the legal record model is
  closed separately (Issue #246 §4). Crew from planning is shown when present.
- Diario, Fitosanitario, Gastos and Campaña need a Campaign; without one the Cuaderno says so and
  offers "Ir a Campañas".
