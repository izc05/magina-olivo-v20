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

NEXT=B6 runtime + B8 contenido/externos, luego B9 | base_sha=c4617f87 | web_tested_sha=76c1c05d | open_pr=694 | blockers=CI694 nuevo SHA pendiente; Docker local sin motor; Android680/689 rojos; V3-A no aprobado; Gate22/23 y beta física no acreditados.

A1–A3 aquí son entregas documentales, no flujos implementados/probados. Resumen A8 es provisional y no cierra gate de release.
