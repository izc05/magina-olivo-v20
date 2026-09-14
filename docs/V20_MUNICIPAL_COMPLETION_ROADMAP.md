# V20 Ayuntamientos — roadmap de cierre

Este documento define el cierre del bloque municipal de Mágina Olivo V20 sobre la cadena municipal apilada.

Principio: cada fase debe ser integrable, auditable y no invadir Rutas, Empresas, GIS/Clima ni el Admin global.

## Estado de partida

Completado antes de este roadmap:
- directorio canónico de 16 municipios;
- fichas públicas municipales;
- cobertura institucional;
- patrimonio/naturaleza/turismo verificable;
- catálogo importable oficial;
- experiencia pública de descubrimiento;
- portada pública premium;
- explorador territorial;
- centro de control Admin;
- control editorial de portada;
- editor completo de contenido municipal;
- navegación Admin persistente por municipio.

## Fase 1 — SEO y preparación editorial

Estado: COMPLETADA en PR #101 / `feat/v20-municipalities-seo-readiness`.

Incluye metadata por municipio, Open Graph/Twitter, media real, fallback seguro y diagnóstico editorial 5/5. Matriz CI completa verde sobre el mismo HEAD.

## Fase 2 — Preview editorial antes de publicar

Estado: COMPLETADA en PR #102 / `feat/v20-municipalities-editorial-preview`.

Incluye comparación `Borrador editorial` vs `Resultado público actual`, estados CMS visibles, máximo tres imprescindibles, preflight 5/5, contexto municipal y pantalla de solo lectura. Matriz CI completa verde sobre el mismo HEAD.

## Fase 3 — Historial y auditoría municipal legible

Estado: IMPLEMENTADA en PR #103 / `feat/v20-municipalities-audit-history`.

Incluye reutilización de `adminApi.audit()`, correlación por identidad canónica/targets CMS, actor abreviado, metadata bruta oculta, solo lectura y contexto municipal. No crea backend paralelo ni consulta el directorio de usuarios.

## Fase 4 — Matriz de huecos de contenido

Estado: IMPLEMENTADA en PR #104 / `feat/v20-municipalities-gap-matrix`.

Incluye matriz alfabética 16×9, señales binarias de perfil/resumen/hero/procedencia/patrimonio/naturaleza/turismo/actualidad/economía, ventanas de publicación, filtros y CTAs directos. Sin puntuaciones subjetivas ni ranking.

## Fase 5 — QA municipal final 16/16

Estado: EN VALIDACIÓN en `feat/v20-municipalities-qa-16`.

Objetivos:
- validar exactamente los 16 slugs canónicos;
- comprobar generación estática y metadata por slug;
- verificar secciones públicas, estados vacíos y fuente municipal canónica;
- verificar el workspace Admin completo;
- confirmar contexto `?municipio=` en todas las herramientas;
- asegurar que Preview, Historial y Huecos siguen siendo de solo lectura;
- ejecutar todos los contratos municipales históricos más el agregador final 16/16;
- superar typecheck, build, Admin, Candidate, Staging y Browser E2E sobre el mismo HEAD.

Cierre:
- contrato `scripts/check-municipality-qa-16.mjs` verde;
- workflow municipal verde;
- matriz global de 7 gates verde sobre el mismo commit.

## Fase 6 — Handoff de integración

Estado: PENDIENTE tras cerrar la Fase 5.

Objetivos:
- verificar cadena de bases desde #83 hasta el último PR municipal;
- comprobar `behind_by=0` respecto a su padre inmediato;
- documentar orden de absorción;
- revalidar colisiones con Rutas, Empresas, GIS/Clima y Admin global;
- preparar handoff hacia `integrate/v20-beta-closure`.

Reglas:
- no merge automático;
- no tocar `main`;
- no ejecutar importadores #90/#91 contra staging/live durante el handoff;
- no absorber ramas ajenas al bloque municipal.

## Definición de terminado del bloque municipal

El bloque municipal se considera funcionalmente cerrado cuando:
1. los 16 municipios tienen identidad canónica y ficha pública;
2. Admin puede editar institución, contenido, portada, patrimonio y actualidad sin tocar código;
3. el municipio seleccionado persiste entre herramientas;
4. cada ficha tiene metadata propia y diagnóstico editorial;
5. existen preview, trazabilidad y matriz objetiva de huecos;
6. las 16 fichas pasan QA público/Admin;
7. la cadena queda lista para integración coordinada sin invadir otros módulos.
