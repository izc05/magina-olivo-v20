## Estado vigente — #696, 8 de octubre de 2026 (15:15 UTC)

Prioridad: Android; Codex ejecuta una sola tarea productiva cada vez, Claude apoya revisión/pruebas. **Web V3 y Web-1 pausadas** hasta orden expresa. #696 sustituye el reparto/cursor anteriores.

| PR | HEAD validado | Merge en main | Validación final |
| --- | --- | --- | --- |
| #689 | 6ad5848a | 8618a721 | 514 unitarios; 671 instrumentados CI; tres checks SUCCESS |
| #680 | 73f79a02 | cda14003 | 517 unitarios; 676 instrumentados CI; offline196; tres checks SUCCESS |
| #684 | d30e3ec4 | 690e2195 | 520 unitarios; parser extremo y persistencia UI API35; tres checks SUCCESS |
| #692 | 4157013d | c2556e4e | 524 unitarios; 677 instrumentados CI; offline196; tres checks SUCCESS |

Main integrado: `c2556e4e96787faea7fde015a850b91404f9c0f2`. Los informes ANDROID-689-FIX, ANDROID-680-FIX, ANDROID-684-INTEGRATION y ANDROID-692-INTEGRATION de esta carpeta y los comentarios DONE en #696 conservan ramas, commits, pruebas y gates. Ninguna de estas cuatro PR sigue bloqueada por los errores descritos en la auditoría inicial. En #680 la causa confirmada fue la selección del workspace en fixtures; no se relajó ownership productivo. Además se corrigió la mezcla de snapshots regulatorios y se probó migración soportada 1–24→25.

NEXT vigente: ejecutar recorrido agrícola sobre este main, preparar candidata APK y evidencia de actualización sin borrar datos; aceptación física por el propietario pendiente (Gate21). La cámara virtual y tests no acreditan móvil físico. Exportación PDF pendiente Fase25; Gates22–28 no abiertos. #694 conserva evidencia histórica, no ejecuta Web ni acepta release.

### Auditoría histórica conservada

**Todo lo que sigue describe el snapshot inicial c4617f87 / af1384ab, anterior a #696.** Sus fallos CI, PRs pendientes, asignaciones y próximos pasos son históricos y quedan sustituidos por el estado vigente de arriba. Sus resultados Web se conservan como evidencia previa y no autorizan reanudarla. La existencia de tests o guiones en aquel snapshot no acredita recorrido completo.
# Mágina Olivo 1.0 — GO/NO-GO provisional

2026-10-08, main c4617f87, #693/#695. **NO-GO para release/beta declarada validada.** Este resumen se apoya en la matriz de cierre; no autoriza integración ni amplía producto.

## Cinco blockers principales

1. **Nube/recuperación aún pendientes frente #340:** ayuda actual indica sin cuenta/copia cloud; Gate22/23 no acreditados. Necesario Auth/RLS A/B/Storage, replay idempotente y restore en segundo teléfono. Responsable Claude/backend.
2. **CI #689 bloquea compilación:** helper delivery inexistente y Long? en tests; reparar sin convertir desconocidos a cero, repetir suite y gates. Responsable Claude.
3. **CI #680 bloquea tratamiento v25:** 24/194 fallos instrumentados, workspaceId/context_mismatch; diagnosticar seed/contexto antes de tocar validaciones y repetir offline/gates. Responsable Claude.
4. **WEB-0/WEB-1 sin gate final:** #595 K01–K12 conceptuales, no aprobación V3-A; Home estática no es motor cinematográfico. WEB-1 requiere Auth/Sync reales; consolidación #591 desde main después de aprobación. Responsable Codex/propietario/backend.
5. **Aceptación integral/release sin evidencia:** dataset grande, PDF conciliado, upgrade sin desinstalar, privacidad/Store y varios días físicos no acreditados. Responsables Claude/propietario/backend; Gates25–28.

## Secuencia realista

Claude repara gates Android y termina Gate21 → backend/Auth Gate22 → Sync/restore Gate23 → servicios/Admin/soporte/privacy Gate24 → informes Gate25 → WEB-1 read-only sobre gates reales → QA automática y volúmenes Gate26 → beta física Gate27 → firma/Store Gate28. Codex puede avanzar QA web y contratos PREP independientes; no despliegue CUE ni aceptación oficial por documentación.

## Condición de GO a beta física

Build/SHA firmado para la prueba, gates requeridos verdes, P0 de integridad/aislamiento resueltos, guion y datos ficticios identificados, upgrade no destructivo y alcance de recuperación comunicado. Propietario registra teléfono/Android/red/fecha y aceptación por recorrido, incluyendo campo sin cobertura/reinicio/fotos. Firma de aceptación: **pendiente**. GO beta no equivale a GO Play Store.

PR #692 y #684 tienen CI verde, pero no cierran flujos integrales por sí solas. MAPA/IUWS #688/#690 están integrados como PREP: no catálogo desplegado, conexión Junta ni conformidad acreditada. [Matriz](MAGINA-OLIVO-1.0-CLOSURE-MATRIX.md), [handoff CI](ANDROID-CI-HANDOFF-680-689.md), [guion](E2E-AGRICULTURAL-CLOSURE.md), [cursor](CODEX-EXECUTION-LEDGER.md).
