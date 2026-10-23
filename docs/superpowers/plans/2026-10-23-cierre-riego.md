# Cierre de riego 1.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Una sola línea productiva; no abrir implementaciones competidoras.

**Goal:** Riego tipado, reloj de inicio/aviso y cuota anual por finca.
**Architecture:** Extender ActivityDetail.Irrigation y el formulario existente; Organization para comunidad y Expense como único libro económico. Campos nuevos nullable y migraciones aditivas.
**Tech Stack:** Kotlin, Compose, Room, Supabase y QA existente.
**Spec:** docs/superpowers/specs/2026-10-23-cierre-1.0-perfil-riego-play.md

## Global Constraints
Conservar navegación, colores e historial. Guardar localmente primero. Cerrar G2 antes de ejecutar este bloque. Releer AGENTS.md y estado vigente sobre el SHA integrado. Sin candidata APK, despliegue persistente ni publicación automáticos.

## Review Focus
Cambio de cuenta sin filtración; caída de proceso/red sin perder cambios; migración conservando histórico; valores desconocidos sin inventar; permisos denegados y texto grande.

## Task 1: Formulario y persistencia
Modificar app/src/main/java/com/isivoltpro/maginaolivo/domain/activity/ActivityDetail.kt, ActivityDetailPatch.kt, feature/activities/ActivityDetailForm.kt y data/local/entity/ActivityDetailEntities.kt con sus mapeadores. Localizar formulario visual desde ActivityEditorPresetTest antes de editar.
Interfaces: conservar durationMinutes, volumeM3, sectorText, systemText y price; añadir referencias/campos nullable de comunidad, tipo de gotero y caudal con unidad explícita.
- [ ] Tests primero en TypedActivityDetailContractTest/ActivityEditorPresetTest: finca obligatoria, parcela de otra finca rechazada, sin campaña activa permitido, histórico sin campos nuevos legible.
- [ ] Auditar proveedor de Activity antes de duplicarlo; reutilizar Organization.
- [ ] Exponer comunidad, sector, sistema, gotero y L/h por gotero o caudal total. Confirmar semántica si el usuario quería volumen por evento; no confundir L y L/h.
- [ ] Mantener volumen desconocido si faltan datos. No calcular por olivos sin información.
- [ ] Implementar entradas Cuaderno > Riego y + sobre el mismo registro, CRUD/reinicio offline y migración desde versión integrada.
- [ ] Ejecutar gradle testDevDebugUnitTest, instrumentadas de Activity y RoomMigrationTest; commit.

## Task 2: Reloj y avisos
Modificar formulario existente, feature/agenda/AgendaScreens.kt, data/reminder/ReminderCoordinator.kt y AndroidReminderScheduler.kt, reutilizando picker común si existe.
Tests: MainReminderPersistenceTest, FirstReminderPermissionTest, AgendaReminderContractTest.
- [ ] Tests primero: seleccionar 07:05, cancelar sin mutar, editar sin duplicar aviso, reinicio, permiso denegado, cambio de hora y cambio de cuenta.
- [ ] Implementar reloj para inicio y aviso; persistir fecha/hora/zona de forma coherente con Agenda.
- [ ] Reprogramar/cancelar por ID canónico; no emitir aviso privado tras logout.
- [ ] Probar notificación offline en móvil físico y documentar límites del sistema; commit.

## Task 3: Cuota anual, gasto e intercambio
Reutilizar flujo/repositorio de Expense; extender ExpenseLedgerContractTest.
- [ ] Test primero: 1.200 EUR/año para 100 olivos = un gasto de 120.000 céntimos por finca/periodo, no 12 EUR por olivo ni un gasto por cada riego. Sin campaña permitido.
- [ ] Conservar periodo/concepto y organización; advertir coincidencias sin prohibir facturas adicionales legítimas.
- [ ] Verificar que diez riegos no multiplican cuota y enlazar factura no duplica dinero.
- [ ] Recuperar nuevos campos y gasto en segundo móvil. Si G2 solo soporta pesadas, marcar dependencia de nuevos despachadores como BLOQUEANTE.
- [ ] Gate RIEGO: CRUD, migración, avisos, conciliación y restore acreditados; commit.
