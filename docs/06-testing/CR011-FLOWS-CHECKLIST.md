# CR-011 — Verificación de los 16 flujos (bloque E)

Fuente: plan del propietario «Mágina Olivo — Plan de simplificación y pulido UX», §28-E y §30.

Cada flujo indica su evidencia automática (CI: `foundation`, `gate3-emulator`, `gate3-evidence`)
y si queda pendiente del recorrido en dispositivo. «Dispositivo» significa comprobación manual del
propietario sobre el APK que incluye CR-011, junto con el Gate CR-010.

| # | Flujo | Evidencia automática | Dispositivo |
|---|---|---|---|
| 1 | Inicio → Mi Campo → Finca → Cuaderno | `AppNavigationTest.aFarmsCuadernoIsTheOneCuadernoOnThatFarm` | Sí (visual) |
| 2 | Cuaderno → Trabajo | `AppNavigationTest.cuadernoWorkActionOpensTheFlowDirectly`, `registerTodayShowsInTheDiaryAndSurvivesARestart` | Sí (visual) |
| 3 | Cuaderno → Riego | `NotebookHomeScreenTest` (seis acciones); `ActivityEditorPresetTest` (riego preseleccionado); `AppNavigationTest.theActivityEditorShowsOnlyTheTypedBlockOfTheChosenType` (bloque de riego) | Sí |
| 4 | Cuaderno → Tratamiento | `NotebookHomeScreenTest` (seis acciones); `TypedActivityDetailContractTest` y `ActivityDetailFormTest` (datos fitosanitarios) | Sí |
| 5 | Cuaderno → Pesada | `AppNavigationTest.cuadernoPesadaAndGastoOpenTheirScreensOnThatFarm` (formulario abierto en la finca) | Sí (visual) |
| 6 | Pesada → OCR → revisar → guardar | `RecollectionFlowContractTest` (pesada con foto del vale); el OCR nunca guarda sin confirmar | **Sí, obligatorio** (cámara/archivo real) |
| 7 | Pesada crea el día de recolección | `JornadaPesadasContractTest`, `RecollectionFlowContractTest` | Sí (visual) |
| 8 | Segunda pesada del mismo día | `JornadaPesadasContractTest.threePesadasOfOneDayToTwoCooperativesSurviveRestartAsOneTruthfulJornada` | Sí (visual) |
| 9 | Cuaderno → Jornal | `AppNavigationTest.cuadernoJornalOpensTodaysDayOnceAndItSurvivesARestart` (abre el día de hoy, lo reutiliza, sobrevive al reinicio) | Sí (visual) |
| 10 | Uso de maquinaria en trabajo/día | `EquipmentScreenTest`, `EquipmentContractTest`, `MachineryContractTest` | Sí |
| 11 | Cuaderno → Gasto → manual | `AppNavigationTest.cuadernoPesadaAndGastoOpenTheirScreensOnThatFarm`; `ExpenseLedgerContractTest` | Sí (visual) |
| 12 | Cuaderno → Gasto → ticket/factura | `AttachmentContractTest`; revisión OCR con confirmación humana | **Sí, obligatorio** (cámara/archivo real) |
| 13 | Cerrar y volver a abrir | `recreate()` en `AppNavigationTest` (varios); `JornadaPesadasContractTest` (reinicio de Room) | **Sí, obligatorio** (cierre real del proceso) |
| 14 | Modo avión | Diseño offline-first: escritura local en Room, sin red en el camino de guardado | **Sí, obligatorio** (no se desactiva la red del emulador compartido de CI) |
| 15 | Campaña → cerrar | `AppNavigationTest.farmParcelCampaignLifecyclePersistsAcrossRecreation`; `CampaignLifecycleContractTest` | Sí (visual) |
| 16 | Rendimiento posterior | `JornadaPesadasContractTest.aYieldAddedDaysLaterChangesOnlyTheYieldRecordAndTheDerivedMetrics`; `PesadaSearchScreenTest` | Sí (visual) |

## Duplicados retirados (CR-011)

- Segundo «Cuaderno» dentro de cada finca → un solo Cuaderno contextualizado (#298).
- «Registrar hoy» + selector repetido → seis acciones directas (#298).
- `QuickAddSheet` → eliminado con sus tests (#298).
- «Documento» como acción de primer nivel → dentro de Gasto/Pesada/Trabajo (#298).
- «Abrir jornada de hoy» → el día se crea solo (#299).
- «Registrar o planificar» → Cuaderno «Registrar trabajo» / Avisos «Planificar trabajo» (#299).

## Conservado

Todas las funciones y datos: sistema de adjuntos y OCR, gestión de máquinas (Perfil → Mis
máquinas), `HarvestRepository.openJornada` (usado por Cuaderno → Jornal), planificación con
recordatorios, cálculos de kilos, rendimientos, costes y campañas. Sin cambios de Room.
