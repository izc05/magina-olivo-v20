# Phase 21 Gate Checklist — Perfil (+ candidate APK for CR-012, CR-013 and #345)

Status: **OPEN — waiting for the owner's physical-device check.** Prepared 2026-10-03.

**Gate 21:** preferences persist offline and account-sensitive operations are protected (no cloud
account is offered before Phase 22; «Cuenta» stays honest).

The same candidate APK also carries the owner-device acceptance of CR-012 Slice 4, CR-013
(Pesada manual + foto, OCR aplazado) and #345 (lluvia/radar/refresco), merged after the last
device run. One device session covers all of them.

## Candidate APK

- Build: **Android CI run #761** on `main` `908b5023` (artifact `magina-olivo-dev-debug`,
  `app-dev-debug.apk`; Perfil → Acerca de shows «compilación 761»).
- Includes: Phase 21A/21B/21C (#307, #308, #311), CR-012 Slices 1–4 (#310, #313, #314, #341),
  CR-013 (#343), #345 (#348).
- Install over the previous DEV build (same signing); local data is kept.

## Merged slices

| Item | PR | Room | Status |
|---|---|---|---|
| 21A — Mi perfil: municipio + cooperativa | #307 | v19 | merged |
| 21B — Avisos: interruptor + hora del día anterior | #308 | v20 | merged |
| 21C — Ayuda, privacidad y uso sin cobertura | #311 | — | merged |
| CR-012 Slice 4 — costes de recogida y coste/kg | #341 | — | merged |
| CR-013 — Pesada manual + foto; OCR aplazado | #343 | — | merged |
| #345 — lluvia en Inicio, refresco 1 h, radar | #348 | — | merged |

## Automated evidence (CI, fixtures only)

All of `foundation`, `gate3-emulator` and `gate3-evidence` were green on every merged head. The relevant
suites are `ProfileSettingsContractTest`, `MyProfileScreenTest`, `ReminderPreferencesContractTest`,
Room migrations 18→19 and 19→20, `ReleaseNotesTest`, `RecollectionChoiceTest/UiTest`,
`ExpenseLedgerContractTest`, `ManualPesadaReceiptTest/UiTest`, `DeliveryContractTest`, `RainLineTest`,
`HomeWeatherRefreshTest`, `WeatherFeedContractTest`, `HomeFeedsScreenTest`, `WeatherWeekScreenTest` and
`DesignContrastTest`.

## Owner device checks (☐ → ☑)

### Perfil (Gate 21)
- ☐ P1 Perfil → «Tu municipio»: elegir municipio; cerrar y reabrir la app → se conserva.
- ☐ P2 «Tu cooperativa»: elegir una existente o crear una; Inicio muestra su nombre.
- ☐ P3 Modo avión: cambiar municipio/cooperativa → se guarda; al reabrir sigue.
- ☐ P4 Avisos: apagar → no suena ningún aviso planificado; encender y elegir 08:00 → el aviso del día anterior suena a esa hora.
- ☐ P5 Ayuda: «Qué hay de nuevo», «Privacidad y datos», «Usar la app sin cobertura» se abren y se leen a 360 dp / letra grande.
- ☐ P6 «Cuenta y sincronización» no ofrece cuenta ni nube (llega en Fase 22).

### Pesada manual (CR-013)
- ☐ M1 Nueva pesada con kg y sin foto ni nº de vale → se guarda.
- ☐ M2 Nueva pesada con «Añadir foto del recibo» (cámara y archivo) → la foto aparece en el detalle de esa pesada; los kg no cambian.
- ☐ M3 «Guardar y añadir otra» → la segunda pesada no arrastra la foto.
- ☐ M4 No aparecen «Leer vale», «Añadir vale y leer datos» ni «Ticket o factura».

### Costes de recogida (CR-012)
- ☐ C1 Cuaderno → Gasto con recolección activa → «Gasto de recogida · Campaña …» marcado; cambiar a «Gasto general» → no cuenta en coste/kg.
- ☐ C2 Resumen de campaña: coste/kg coherente con gastos confirmados ÷ kg de pesadas.

### Tiempo y radar (#345)
- ☐ T1 Inicio muestra «Prob. lluvia X %» cuando la fuente es AEMET; con MET Norway no aparece «0 %».
- ☐ T2 Volver a Inicio tras más de 1 h (o al día siguiente) → «Actualizado hace …» se renueva.
- ☐ T3 El tiempo → «Actualizar»; en modo avión avisa y conserva el dato con su antigüedad.
- ☐ T4 «Radar de lluvia» se distingue (azul agua, icono) y se lee a 360 dp / letra grande.

## Decision

Pending owner device run. On PASS: Gate 21 closes and Phase 22 (Cuenta + Supabase Auth/backend/RLS/Storage)
may start with owner project access. WEB-0 (#346) stays design/preparation only.
