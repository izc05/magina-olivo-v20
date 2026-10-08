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
# Cursor de ejecución Codex — #695 / #693

2026-10-08. Producto/gates: #340/#338; este ledger solo registra evidencia y próximo trabajo. Base c4617f87. PR activa de documentación #694; no hay nueva implementación Android.

| Slice | Responsable | Estado / bloqueo | PR | CI / evidencia | Fecha | Siguiente |
| --- | --- | --- | --- | --- | --- | --- |
| 0.1 | Codex | BLOCKED resultado final aún en curso | #694, 1c6a3bcc antes de ampliar | foundation SUCCESS; gate3-emulator/gate3-evidence in_progress, headSha verificado; diff docs/qa/** | 08-10 | Reconsultar nuevo SHA tras publicar, no asumir verde |
| 0.2 | Codex | BLOCKED por 0.1 | #694 draft | No merge, no cambio a ready antes de CI | 08-10 | Ready para revisión si verde/sin conflictos |
| 0.3 | Codex/Claude | READY handoff documentado | #694 | ANDROID-CI-HANDOFF-680-689.md; comentario #693 enlazado | 08-10 | Claude corrige; Codex no mensaje automático a chat |
| 0.4 | Codex | READY documento de cursor | #694 ampliación | Este ledger; diff check | 08-10 | Mantener cada 2–3 slices |
| A1 | Codex | READY inventario, pendiente revisión | #694 ampliación | 23 filas A1–A23, evidencia/unknown/gate separados | 08-10 | A2/A3 guion y auditoría dirigida |
| A2/A3 | Codex/Claude | READY guion; ejecución pendiente | #694 ampliación | E2E-AGRICULTURAL-CLOSURE.md, pruebas existentes enlazadas | 08-10 | Claude ejecuta y registra build/resultados |
| A4/A5/A6 | Codex/Claude | READY evidencia dirigida; ejecución integral pendiente | #694 ampliación | MAPS-SERVICES-REPORTS-AUDIT.md; forecast 18/18; #669 integrado | 08-10 | Claude: móvil/mapa/lifecycle/PDF; Codex B1 |
| A7/A8 | Codex | READY resumen provisional; audit completo pendiente | #694 ampliación | RELEASE-GO-NOGO-1.0.md, evidencia actual | 08-10 | Actualizar tras A4–A6 y gates |
| B1 | Codex | READY inventario; integración condicionada | #694 ampliación | WEB-V3-BRANCH-INVENTORY.md; merge-tree exit0 sin integrar ni acreditar QA conjunta | 08-10 | B2–B5 evidencia del extremo #691 |
| B2/B3 | Codex | READY evidencia automatizada; manual parcial | #691 / #694 informe | Lint/types; Playwright 153 pass/9 skip; teclado/viewport/reduced-motion; Node24 local, Node22 CI | 08-10 | Lector de pantalla, zoom, orientación y aceptación |
| B4/B5 | Codex | READY build/SEO/manifest; BLOCKED Docker local | #552/#598/#691 / #694 | Build+Pages exit0; SEO 2 pass; PNG192/512; 792 anchors sin destino ausente | 08-10 | Docker con motor disponible, instalación PWA/cabeceras |
| B6/B7/B8 | Codex | READY inventario/revisión acotada; medición/editorial pendiente | #694 | WEB-V3-QA-2026-10-08.md; 15 imágenes/2320761 bytes; dos capturas inspeccionadas | 08-10 | B6 runtime, B8 contenido/externos, B9 paquete visual |

## CI disparada por documentación

Android CI y Gate3 tienen pull_request a main sin filtro de rutas, por lo que #694 docs-only ejecuta Android. Propuesta futura en PR compartida separada/revisada: clasificar diff y conservar Android para app/**, Gradle, recursos compartidos, workflows Android y contratos con efecto Android; tratar cambios solo editoriales docs/qa/** por comprobación documental. Si branch protection exige nombres de checks, no usar un filtro global que deje required checks eternamente pendientes: usar job de clasificación + resultado estable. Casos a probar: docs-only, app-only, Gradle, workflow, contrato compartido y diff mixto. No se modifican filtros ni se desactivan gates aquí.

## Cursor

HISTORICAL_NEXT=B6 runtime + B8 contenido/externos, luego B9 | base_sha=c4617f87 | web_tested_sha=76c1c05d | open_pr=694 | blockers=CI694 nuevo SHA pendiente; Docker local sin motor; Android680/689 rojos; V3-A no aprobado; Gate22/23 y beta física no acreditados.

A1–A3 aquí son entregas documentales, no flujos implementados/probados. Resumen A8 es provisional y no cierra gate de release.
