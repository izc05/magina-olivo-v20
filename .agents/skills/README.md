# Herramientas de agentes — Mágina Olivo Android

Este directorio reúne **Skills especializados para Codex y agentes compatibles**. Son instrucciones y recursos de apoyo; no modifican por sí mismos código, APK o GitHub Actions.

| Skill | Cuándo usarlo | Fuente |
| --- | --- | --- |
| `gh-fix-ci` | Analizar fallos reales de checks de GitHub Actions y localizar la línea del error | OpenAI |
| `systematic-debugging` | Reproducir el error y determinar causa raíz antes de editar | Superpowers |
| `test-driven-development` | Desarrollar una regresión reproducible y cambiar solo lo necesario | Superpowers |
| `testing-setup` | Auditar/mejorar la infraestructura de pruebas Android, Compose, Room y E2E | Google Android |
| `verification-before-completion` | Exigir evidencia nueva de compilación, tests y Gate antes de declarar PASS | Superpowers |

## Cómo combinarlos para Mágina Olivo
- **CI falla:** `gh-fix-ci` → `systematic-debugging` → `test-driven-development` (para código/regresiones) → `verification-before-completion`.
- **Faltan pruebas reales:** `testing-setup` para analizar los huecos existentes; después implementar solo el slice aprobado, con `verification-before-completion`.
- **Antes de APK o integración:** comprobar `AGENTS.md`, `docs/00-master/CURRENT-STATE.md`, la prioridad más reciente del issue #696, logs de GitHub y aceptación en móvil cuando aplique. También está preparado el skill específico `magina-android-finalizer` de la PR #701; usarlo cuando esté en la rama de trabajo.

## Restricciones que prevalecen sobre los Skills externos
1. **AGENTS.md, Product Lock, Gates e instrucciones recientes aprobadas del propietario tienen prioridad.** Un Skill externo no autoriza por sí mismo nuevas dependencias Hilt/Gradle, cambios de Room, navegación, diseño, UI, backend, permisos, OCR, ni saltarse Gates.
2. Una sola línea productiva Android con Codex como ejecutor mientras #696 esté vigente; Claude/revisor en paralelo sin editar el mismo slice. No reactivar Web V3 sin orden.
3. **Nunca** borrar datos, limpiar directorios compartidos de fotos, debilitar validación de workspace, alterar kilos desconocidos (`null`) o duplicar costes contables para que los tests pasen.
4. `testing-setup` debe comenzar auditando los tests reales (`app/src/test`, `app/src/androidTest`, `.github/workflows/android-ci.yml`). Sus propuestas de instalar frameworks son opcionales y requieren autorización por issue/PR; no sustituir automáticamente el stack existente.
5. `gh-fix-ci` requiere `gh` autenticado y Python en el entorno de ejecución. Su script solo se ejecuta si el agente dispone de permisos y necesita ese diagnóstico. Si no puede ejecutar CLI, consultar logs/checks mediante herramientas de GitHub equivalentes y registrar el límite.
6. Nunca presentar documentación como test ejecutado ni CI verde como aceptación en móvil real.

## Origen y licencias — versiones fijadas
- **Google Android Skills**: <https://github.com/android/skills/tree/42dc2270e96032bd860bb94511e440aa00a43125/testing/testing-setup> — SHA `42dc2270e96032bd860bb94511e440aa00a43125` — Apache-2.0 (licencia en `testing-setup/LICENSE.txt`).
- **OpenAI Skills**: <https://github.com/openai/skills/tree/49f948faa9258a0c61caceaf225e179651397431/skills/.curated/gh-fix-ci> — SHA `49f948faa9258a0c61caceaf225e179651397431` — Apache-2.0 (licencia en `gh-fix-ci/LICENSE.txt`).
- **Superpowers (Jesse Vincent)**: <https://github.com/obra/superpowers/tree/8ca22dba9a94f28898bbce59f2537ff4d87c747d/skills> — SHA `8ca22dba9a94f28898bbce59f2537ff4d87c747d` — MIT (copia de licencia dentro de cada Skill).

Los Skills se han copiado de esas revisiones concretas con referencias necesarias. Revisar y actualizar **por PR**; no confiar automáticamente en Skills de fuentes desconocidas ni ejecutar scripts de terceros sin inspección previa.
