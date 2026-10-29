# Sistema de Riego 1.0 — Pantalla y Componentes de Interfaz (B.2)

**Fecha:** 29 de octubre de 2026  
**Autor:** Antigravity (Ingeniero Android / UI & Arquitectura)  
**Rama:** `feat/antigravity-riego-ui`  
**Base:** `0ee406246` (Rama `feat/antigravity-riego`, PR #783)  
**Estado:** Tarea B.2 Completada  

---

## 1. Alcance y Arquitectura de la Interfaz

La tarea B.2 implementa la interfaz visual completa de **Riego 1.0** para Mágina Olivo (`feature/irrigation`), conectada de forma determinista con el dominio implementado en B.1 y respetando escrupulosamente el sistema de diseño crema y verde olivo (`MoColors`, `MoSurfaceTokens`, `MoShape`).

### 1.1 `IrrigationScreen` y `IrrigationRoute`
- **Punto de navegación modular:** `IrrigationRoute` inyecta de forma limpia `ParcelRepository`, `ActivityRepository` y `FarmRepository` mediante `viewModelFactory` con `collectAsStateWithLifecycle()`.
- **Top Bar / Header:**
  - Botón de retroceso (`MoIcons.ChevronLeft`).
  - Título *"Riego"* y nombre de la finca activa.
  - Botón de acción directa `+ Registrar riego` (`MoPrimaryButton`).
- **Navegación por pestañas:** 3 pestañas principales compactas (`MoShape.pill`):
  1. **Resumen y turnos:**
     - Tarjetas métricas (`MoMetricCard`):
       - Volumen registrado en campaña ($\text{m}^3$) y conteo de riegos ejecutados.
       - Horas acumuladas de riego y minutos totales.
       - Coste variable estimado de agua (€), excluyendo cuotas fijas de comunidad.
     - Badges de distribución de parcelas por sistema agronómico (Goteo, Aspersión, Secano).
     - **Calendario Semanal de Turnos:**
       - Selector interactivo de Lunes a Domingo con indicador de puntos para días con turno.
       - Tarjetas para cada parcela con turno (`TurnItemCard`): nombre, comunidad de regantes, sector y número de olivos.
       - Estado vacío específico cuando un día no tiene turnos programados.
       - Cuadro explicativo: *"Días habituales configurados por el agricultor. No constituyen turnos oficiales ni agua efectivamente entregada."*
  2. **Instalaciones y parcelas:**
     - **Calculadora agronómica interactiva de dotación:**
       - Entrada de olivos, goteros/olivo, caudal unitario (L/h) y duración en minutos.
       - Tarjeta de resultado en verde olivo destacando: caudal horario de la instalación ($\text{m}^3/\text{h}$), volumen teórico total ($\text{m}^3$ y L) y dosis neta por olivo ($\text{L/árbol}$).
     - **Listado de parcelas en regadío:** Cada parcela muestra su sistema, comunidad, sector y botón *"Simular dosis"* para cargar sus datos directamente en la calculadora.
     - Estado vacío específico si la finca no tiene parcelas en regadío.
  3. **Historial de riegos:**
     - Tarjetas de actividades completadas (`ActivityType.IRRIGATION`): fecha legible, parcela afectada, duración, volumen en $\text{m}^3$ y coste de agua.
     - Click en la tarjeta para navegar al detalle de la labor en el Cuaderno.
     - Estado vacío específico con botón de acción *"Registrar riego"* si aún no se han registrado riegos.

---

## 2. Pruebas y Evidencias

### 2.1 Pruebas Unitarias JVM (`IrrigationViewModelTest` y Dominio)
- `IrrigationViewModelTest`: 6 tests PASS
- `WateringDoseCalculatorTest`: 6 tests PASS
- `WeeklyIrrigationCalendarTest`: 4 tests PASS
- `IrrigationSummaryTest`: 4 tests PASS
- `IrrigationTurnsTest`: 5 tests PASS
- **Total Unitarias:** **25 de 25 PASS (100%)**.

### 2.2 Pruebas Instrumentadas en Emulador API 35 (`IrrigationScreenTest`)
Ejecutadas sobre el dispositivo dedicado `emulator-5554` (`MaginaOlivo_Antigravity_API35`):
- `screenWithData_displaysMetricsAndWeeklyTurns`: PASS
- `emptyState_displaysCleanEmptyStateMessages`: PASS
- `tabNavigation_triggersTabSelection`: PASS
- `calculatorSection_displaysResultsBoxWhenDosePresent`: PASS
- `headerButtons_triggerCallbacks`: PASS
- **Total Instrumentadas:** **5 de 5 PASS (100%)**.

### 2.3 Captura de Pantalla
- Guardada en `docs/screenshots/irrigation_screen_api35.png`.
