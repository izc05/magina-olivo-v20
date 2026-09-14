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

Estado: COMPLETADA funcionalmente en PR #103 / `feat/v20-municipalities-audit-history` y cubierta por el QA final #106.

Incluye reutilización de `adminApi.audit()`, correlación por identidad canónica/targets CMS, actor abreviado, metadata bruta oculta, solo lectura y contexto municipal. No crea backend paralelo ni consulta el directorio de usuarios.

## Fase 4 — Matriz de huecos de contenido

Estado: COMPLETADA funcionalmente en PR #104 / `feat/v20-municipalities-gap-matrix` y cubierta por el QA final #106.

Incluye matriz alfabética 16×9, señales binarias de perfil/resumen/hero/procedencia/patrimonio/naturaleza/turismo/actualidad/economía, ventanas de publicación, filtros y CTAs directos. Sin puntuaciones subjetivas ni ranking.

## Fase 5 — QA municipal final 16/16

Estado: COMPLETADA en PR #106 / `feat/v20-municipalities-qa-16`.

HEAD validado: `a9e250a9a6c54462dcffce24d4a42993618e90fb`.

Cierre validado:
- exactamente 16 slugs canónicos;
- generación estática y metadata por slug;
- secciones públicas y estados vacíos;
- workspace Admin completo;
- contexto `?municipio=`;
- Preview, Historial y Huecos protegidos como solo lectura;
- todos los contratos municipales históricos + agregador final 16/16;
- V20 municipalities directory #46 verde;
- V20 environment contract #795 verde;
- V20 foundation check #744 verde;
- V20 platform admin check #642 verde;
- V20 full candidate check #2976 verde;
- V20 staging readiness #889 verde;
- V20 beta browser E2E #1288 verde.

## Fase 6 — Handoff de integración

Estado: COMPLETADA EN ESTA RAMA mediante `docs/V20_MUNICIPAL_INTEGRATION_HANDOFF.md` y `scripts/check-municipality-integration-handoff.mjs`.

Verificaciones realizadas:
- cadena municipal #83 → #88 → #89 → #90 → #91 → #92 → #94 → #95 → #96 → #97 → #98 → #100 → #101 → #102 → #103 → #104 → #106;
- cada hijo está `behind_by=0` respecto a su padre inmediato;
- #83 está `behind_by=0` respecto a `integrate/v20-beta-closure`;
- orden de absorción documentado;
- #105 Admin unificado identificado como trabajo paralelo;
- #66, Rutas, Empresas, GIS/Clima y demás frentes paralelos excluidos del handoff;
- importadores #90/#91 explícitamente no ejecutados contra staging/live;
- `main` permanece fuera de alcance;
- no se autoriza merge automático.

Cierre de esta fase:
- contrato de handoff conectado al workflow municipal;
- handoff final debe pasar CI sobre el HEAD exacto antes de considerarse listo para absorción coordinada.

## Definición de terminado del bloque municipal

El bloque municipal se considera funcionalmente cerrado cuando:
1. los 16 municipios tienen identidad canónica y ficha pública;
2. Admin puede editar institución, contenido, portada, patrimonio y actualidad sin tocar código;
3. el municipio seleccionado persiste entre herramientas;
4. cada ficha tiene metadata propia y diagnóstico editorial;
5. existen preview, trazabilidad y matriz objetiva de huecos;
6. las 16 fichas pasan QA público/Admin;
7. la cadena queda lista para integración coordinada sin invadir otros módulos.

Todos estos puntos están implementados. La última condición pendiente de cada ejecución es que el HEAD del handoff conserve CI verde antes de su absorción coordinada.
