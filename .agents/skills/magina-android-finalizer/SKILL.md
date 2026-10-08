---
name: magina-android-finalizer
description: "Finalizar, auditar, corregir, probar y preparar la APK de Mágina Olivo Android (Kotlin, Jetpack Compose, Room, offline-first). Usar para errores de CI, pruebas de emulador, flujo Finca-Parcela-Campaña-Pesada-Jornales-Gastos, migraciones, datos, QA, Gates y release. No usar para retomar Web V3 sin autorización."
---

# Skill: cerrar Mágina Olivo Android sin perder datos

## Propósito y límites
Ayuda a **terminar el producto real**, no solo a generar código o pantallas. Opera sobre `izc05/magina-olivo-v20` y respeta el contrato de `AGENTS.md`. Este skill NO concede permiso para saltar Gates, fusionar PR sin validación, iniciar servicios externos ni reabrir la Web.

## 0. Leer y comprobar la verdad ANTES de tocar código
1. Leer `AGENTS.md`, `docs/00-master/RC1.2-PRODUCT-LOCK.md`, `docs/00-master/SINGLE-TRACK-EXECUTION.md`, `docs/00-master/CURRENT-STATE.md` y el Change Request aplicable.
2. Consultar el **estado actual**, no el título ni los comentarios antiguos, de issues/PR/commits/CI. Priorizar instrucciones del propietario **más recientes y aprobadas**. El issue [#696](https://github.com/izc05/magina-olivo-v20/issues/696) fija la prioridad Android 1.0 y pausa Web V3 desde 2026-10-08; comprobar si sigue vigente.
3. Identificar el `main` actual, PRs abiertos y fusionados, dependencias, responsable del slice y checks requeridos. No dar por pendiente una PR ya fusionada ni confundir `closed` con `merged`: verificar metadatos de PR.
4. Si `CURRENT-STATE.md` está desfasado respecto a PRs/issues, explicitar la discrepancia y proponer actualización mínima **basada en evidencia**. No inventar resultados.

## 1. Una sola línea de ejecución
- **Codex:** implementador principal de un único slice Android productivo a la vez, salvo reasignación explícita del propietario.
- **Claude u otro agente:** revisión, tests de apoyo, QA y documentación; no editar simultáneamente el mismo slice ni sobrescribir trabajo sin handoff.
- Trabajar en rama nueva desde `main` reciente (`fix/*` o `feat/*`); PR pequeña por problema. `main` permanece estable.
- No tocar `web/**` ni PRs Web V3 mientras continúe su pausa. No comenzar Auth/Sync/Backend productivos sin Gate y autorización correspondiente.
- Nunca ejecutar migraciones destructivas, reiniciar bases con datos reales ni eliminar fotos/adjuntos para hacer pasar tests.

## 2. Orden operativo de cierre
Seleccionar **solo el primer bloque no resuelto** en el tracker vigente:
1. **P0 estabilidad:** fallos de compilación Kotlin/tests, CI, emulador y regresiones; diagnosticar causa raíz, no deshabilitar asserts ni ocultar pruebas.
2. **P0 integridad agrícola:** Room, transacciones, migraciones, workspaces, historial, origen de kilos, costes, fotos y edición; priorizar riesgo de pérdida de datos.
3. **P1 flujo de extremo a extremo:** Finca → Parcela → Campaña activa → Pesadas/Jornada automática → Jornales/Maquinaria/Gastos → Rendimiento/Histórico.
4. **P1 UX y accesibilidad:** contexto de finca/campaña, navegación, acciones alcanzables en móvil, errores y estados sin conexión, textos y diseño bloqueado.
5. **Gate + APK:** recoger pruebas verificables, confirmar checks, construir APK DEV desde `main` integrado y preparar guion de prueba en móvil físico. Un build correcto NO equivale a Gate aceptado por el propietario.
6. **Solo después y con aprobación:** fases Backend/Auth/RLS → Sync → Admin → informes/PDF → QA → beta → publicación según roadmap y Gates.

## 3. Invariantes que ninguna corrección puede romper
- **Android Kotlin + Compose; Room es fuente local de verdad**; todas las acciones agrícolas críticas funcionan offline y persisten al reiniciar.
- `Finca → Parcela → Campaña` conserva identificadores, relación, snapshot histórico, archivado/restauración y aislamiento de usuario/workspace.
- Una Pesada manual conserva exactamente los kg introducidos, número de vale, destino, fecha, parcelas, origen suelo/árbol y foto opcional. **OCR aplazado** por CR-013; no reactivarlo para completar 1.0.
- No inventar kilos por parcela ni convertir valores desconocidos/`null` en cero. Proteger también las sumas ante overflow; jamás presentar totales negativos por desbordamiento.
- El ledger `Expense` contabilizado es la única fuente monetaria de coste; liquidar o anticipar pagos de jornales NO registra nuevamente un coste.
- Adjuntos y fotos deben sobrevivir actualizaciones, pruebas, reinicios y eliminación de otros fixtures. Los tests solo borran **sus** datos aislados.
- Mantener diseño, navegación, marcas y CR aprobados; no añadir tabs, rediseños ni cambios de esquema sin alcance y Gate autorizado.
- Datos Catastro/SIGPAC/Junta/meteorología/mercado: distinguir datos verificados de referencias o simulaciones; no afirmar integraciones reales sin prueba de conexión.

## 4. Ciclo de trabajo por PR
1. **Reproducir** defecto con pasos, commit SHA, dispositivo/API, datos y test fallido o registro. Clasificar P0/P1/P2 y causa probable.
2. **Proteger** con test de regresión para el fallo y al menos un caso normal. Para Room: migraciones desde versiones soportadas y conservación de datos/adjuntos. Para Compose: navegación, estados offline y semántica.
3. **Corregir mínimo**; comprobar efectos sobre otros módulos, no mover responsabilidades o cambiar contrato sin CR.
4. **Validar local** usando los comandos que exige el CI real. El `android-ci.yml` actual utiliza:

   ```bash
   gradle --no-daemon lintDevDebug testDevDebugUnitTest assembleDevDebug assembleStagingDebug assembleProductionDebug assembleDevDebugAndroidTest
   ```

   Para emulador instrumentado revisar `.github/workflows/android-ci.yml` y `.github/scripts/gate3-emulator-evidence.sh`. Si no se ha ejecutado el emulador, marcarlo `NO VERIFICADO`. No inventar métricas ni PASSES.
5. **PR acotada** desde `main`; comprobar diff, tests reales, checks remotos, cambios de Room y ausencia de `web/**`. Revisión independiente antes de integrar.
6. **Evidencia** en `docs/qa/` o `docs/06-testing/`, enlazando PR/issue/commit/logs. Actualizar `CURRENT-STATE.md` únicamente si hay una transición confirmada.
7. **Handoff** corto para Codex/Claude o propietario: qué está resuelto, qué queda bloqueado, quién implementa siguiente slice.

## 5. Matriz de aceptación obligatoria
Leer [references/android-closure-checklist.md](references/android-closure-checklist.md) antes de proponer una APK final o cerrar un Gate. Diferenciar tres etiquetas: `PASS VERIFICADO`, `FAIL` y `PENDIENTE/NO EJECUTADO`. Registrar emulador y móvil físico por separado.

## Formato de respuesta obligatorio
- **Ahora:** prioridad/issue, rama/PR, implementador y estado real.
- **Hecho:** cambios y casos cubiertos con evidencia vinculada.
- **Validación:** comandos, cantidades de tests, CI/emulador/móvil y SHA exactos; no atribuir PASS a comprobaciones no ejecutadas.
- **Siguiente:** UNA tarea prioritaria con criterio de aceptación claro.
- **Riesgos y alcance:** blockers, datos conservados, Gates pendientes, Web V3 sin tocar.
