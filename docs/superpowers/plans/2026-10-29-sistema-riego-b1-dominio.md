# Sistema de Riego 1.0 — Especificación Técnica y Dominio (B.1)

**Fecha:** 29 de octubre de 2026  
**Autor:** Antigravity (Ingeniero Android / Arquitectura)  
**Rama:** `feat/antigravity-riego`  
**Base:** `3224ac97489537c5b1b1812dab52df133de5b9d3` (`v20/main`)  
**Estado:** Tarea B.1 Completada (19/19 tests unitarios aprobados)  

---

## 1. Alcance y Arquitectura de Dominio

La tarea B.1 dota al módulo de riego de un núcleo agronómico determinista, inmutable y desacoplado de dependencias externas o de persistencia directa en Room/Supabase, respetando las directrices de `AGENTS.md` (reglas 7, 13 y 14) y la coordinación con Codex y Claude.

Se compone de tres modelos de cálculo puro:

### 1.1 `WateringDoseCalculator`
- **Modelo:** `DripInstallation(oliveTreeCount, emittersPerTree, emitterFlowLph, emitterType)`
- **Emisores:** `EmitterType` (`PRESSURE_COMPENSATING`, `TURBULENT`, `INTEGRATED`, `OTHER`).
- **Cálculo de Caudal:** Caudal horario en L/h y $\text{m}^3/\text{h}$ con redondeo a 2 decimales determinista (`HALF_UP`).
- **Cálculo de Dotación Teórica:**
  $$\text{Volumen Teórico (L)} = \text{olivos} \times \text{goterosPorOlivo} \times \text{caudalLph} \times \frac{\text{duracionMinutos}}{60}$$
  $$\text{Dosis (L/olivo)} = \frac{\text{Volumen Teórico (L)}}{\text{olivos}}$$
- **Cálculo Inverso:** `estimatedDurationMinutes(installation, targetLitersPerTree)` para determinar el tiempo requerido para alcanzar una dotación objetivo en L/olivo.
- **Validaciones:** Comprobación estricta de no-negatividad y manejo determinista de 0 olivos/goteros evitando divisiones por cero.

### 1.2 `WeeklyIrrigationCalendar`
- **Modelo:** `WeeklyPlan`, `DaySchedule(dayOfWeek, turns)` y `ScheduledTurnItem(parcelId, parcelName, sector, communityOrNetwork, system, oliveTreeCount)`.
- **Planificación:** 7 días completos de lunes a domingo.
- **Filtrado:** Solo parcelas activas no archivadas (`archivedAt == null`), con sistema de riego no nulo y distinto de `DRYLAND`, y con días asignados en `agronomy.irrigationDays`.
- **Agrupación y Ordenación:** Agrupación por comunidad de regantes dentro de cada día (`turnsByCommunity`) y ordenación determinista: comunidad $\rightarrow$ sector $\rightarrow$ nombre de parcela.

### 1.3 `IrrigationSummary`
- **Modelo:** `IrrigationSummary`, `IrrigationRealStats`, `IrrigationTypeBreakdown` y `CommunityWaterTariffFee`.
- **Desglose Agronómico:** Parcelas, olivos y superficie agrupadas por sistema (`DRIP`, `SPRINKLER`, `OTHER`, `DRYLAND`, `null`).
- **Mediciones Reales vs Teóricas:** Suma estricta de volumen en $\text{m}^3$, duración y costes derivados únicamente de actividades completadas (`ActivityStatus.COMPLETED` de tipo `ActivityType.IRRIGATION`). Las actividades planificadas o canceladas no alteran el volumen consumido.
- **Cuotas de Comunidad de Regantes:** `CommunityWaterTariffFee` modela cuotas fijas anuales (en céntimos de euro), manteniéndolas nítidamente separadas de los costes variables de campaña.

---

## 2. Pruebas Unitarias Ejecutadas

- `WateringDoseCalculatorTest`: 6 tests PASS
- `WeeklyIrrigationCalendarTest`: 4 tests PASS
- `IrrigationSummaryTest`: 4 tests PASS
- `IrrigationTurnsTest`: 5 tests PASS
- **Total:** 19 tests superados al 100% (0 fallos, 0 errores).
