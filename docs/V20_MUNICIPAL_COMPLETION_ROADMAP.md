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

Estado: IMPLEMENTADA en PR #103 / `feat/v20-municipalities-audit-history`; validación CI en curso.

Incluye reutilización de `adminApi.audit()`, correlación por identidad canónica/targets CMS, actor abreviado, metadata bruta oculta, solo lectura y contexto municipal. No crea backend paralelo ni consulta el directorio de usuarios.

## Fase 4 — Matriz de huecos de contenido

Estado: EN IMPLEMENTACIÓN en `feat/v20-municipalities-gap-matrix`.

Objetivos:
- matriz alfabética de los 16 municipios;
- nueve señales binarias: perfil, resumen, hero, procedencia, patrimonio, naturaleza, turismo, actualidad y economía;
- respetar estado y ventanas de publicación;
- filtro por área y solo huecos;
- enlace directo a la herramienta que resuelve cada ausencia;
- ninguna nota subjetiva ni ranking.

Cierre:
- solo lectura;
- fuentes canónicas existentes;
- contrato específico y matriz CI verdes.

## Fase 5 — QA municipal final 16/16

Objetivos:
- smoke de las 16 fichas públicas;
- metadata por slug;
- enlaces Admin contextuales;
- responsive móvil/escritorio;
- accesibilidad;
- enlaces oficiales y procedencia presentes cuando existen;
- estados vacíos correctos.

Cierre:
- contrato municipal + workspace + candidate + Admin + staging + Browser E2E verdes sobre el mismo HEAD.

## Fase 6 — Handoff de integración

Objetivos:
- verificar cadena de bases desde #83 hasta el último PR municipal;
- comprobar `behind_by=0` respecto a su padre inmediato;
- documentar orden de absorción;
- preparar handoff hacia `integrate/v20-beta-closure`.

Reglas:
- no merge automático;
- no tocar `main`;
- revalidar colisiones con Rutas, Empresas y Admin global antes de la absorción coordinada.

## Definición de terminado del bloque municipal

El bloque municipal se considera funcionalmente cerrado cuando:
1. los 16 municipios tienen identidad canónica y ficha pública;
2. Admin puede editar institución, contenido, portada, patrimonio y actualidad sin tocar código;
3. el municipio seleccionado persiste entre herramientas;
4. cada ficha tiene metadata propia y diagnóstico editorial;
5. existen preview, trazabilidad y matriz objetiva de huecos;
6. las 16 fichas pasan QA público/Admin;
7. la cadena queda lista para integración coordinada sin invadir otros módulos.
