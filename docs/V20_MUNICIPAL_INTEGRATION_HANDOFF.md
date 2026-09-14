# V20 Ayuntamientos — handoff de integración coordinada

Fecha de cierre técnico: 2026-09-14.

Este documento entrega el bloque municipal de Mágina Olivo V20 a la rama coordinadora sin fusionar automáticamente nada y sin tocar `main`.

## Estado de cierre

El último HEAD funcional validado es:

`a9e250a9a6c54462dcffce24d4a42993618e90fb`

Rama: `feat/v20-municipalities-qa-16`  
PR: #106 — `V20 Ayuntamientos — QA final 16/16`

Matriz sobre ese mismo SHA:
- V20 municipalities directory #46 — verde;
- V20 environment contract #795 — verde;
- V20 foundation check #744 — verde;
- V20 platform admin check #642 — verde;
- V20 full candidate check #2976 — verde;
- V20 staging readiness #889 — verde;
- V20 beta browser E2E #1288 — verde.

## Cadena municipal exacta

Orden lógico de absorción, desde la rama coordinadora hacia el último delta:

1. `integrate/v20-beta-closure`
2. #83 `feat/v20-municipalities-directory`
3. #88 `feat/v20-municipalities-coverage`
4. #89 `feat/v20-municipalities-tourism`
5. #90 `feat/v20-municipalities-heritage-catalog`
6. #91 `feat/v20-municipalities-discovery-catalog`
7. #92 `feat/v20-municipalities-discovery-public-experience`
8. #94 `feat/v20-municipalities-public-landing`
9. #95 `feat/v20-municipalities-explore-directory`
10. #96 `feat/v20-municipalities-admin-control-center`
11. #97 `feat/v20-municipalities-editorial-control`
12. #98 `feat/v20-municipalities-content-editor`
13. #100 `feat/v20-municipalities-admin-navigation`
14. #101 `feat/v20-municipalities-seo-readiness`
15. #102 `feat/v20-municipalities-editorial-preview`
16. #103 `feat/v20-municipalities-audit-history`
17. #104 `feat/v20-municipalities-gap-matrix`
18. #106 `feat/v20-municipalities-qa-16`

No existe un PR municipal #99: #99 pertenece al frente de Rutas/GPS y queda fuera de esta cadena.

## Verificación de ancestry

Comprobación realizada el 14-09-2026 mediante comparación directa de refs:
- `integrate/v20-beta-closure` → #83: `behind_by=0`;
- #83 → #88: `behind_by=0`;
- #88 → #89: `behind_by=0`;
- #89 → #90: `behind_by=0`;
- #90 → #91: `behind_by=0`;
- #91 → #92: `behind_by=0`;
- #92 → #94: `behind_by=0`;
- #94 → #95: `behind_by=0`;
- #95 → #96: `behind_by=0`;
- #96 → #97: `behind_by=0`;
- #97 → #98: `behind_by=0`;
- #98 → #100: `behind_by=0`;
- #100 → #101: `behind_by=0`;
- #101 → #102: `behind_by=0`;
- #102 → #103: `behind_by=0`;
- #103 → #104: `behind_by=0`;
- #104 → #106: `behind_by=0`.

La raíz #83 está 28 commits por delante y 0 por detrás de `integrate/v20-beta-closure` en la comprobación de cierre.

## Qué entrega el bloque

El bloque municipal completo incluye:
- directorio canónico de 16 municipios y datos institucionales;
- 16 fichas públicas pre-renderizadas;
- cobertura municipal operativa;
- patrimonio, naturaleza y turismo con procedencia;
- catálogos oficiales importables;
- experiencia pública de descubrimiento;
- portada territorial y explorador municipal;
- centro de control Admin;
- control editorial de portada;
- editor completo de contenido municipal;
- contexto Admin persistente por `?municipio=`;
- SEO/Open Graph/Twitter por municipio;
- Preview borrador frente a resultado público real;
- historial municipal de solo lectura sobre auditoría corporativa;
- matriz accionable 16×9 de huecos;
- QA final 16/16 y guardas de solo lectura/privacidad.

## Importadores #90 y #91

Los catálogos patrimoniales/de descubrimiento están implementados y validados como herramientas de importación autenticada e idempotente.

**No se han ejecutado como parte de este handoff contra una base staging/live.**

No debe afirmarse que las 32 piezas están cargadas en una base real hasta que un administrador ejecute el importador en el entorno elegido y se compruebe el resultado.

## Frentes paralelos que NO se absorben aquí

Este handoff no incluye ni debe arrastrar por accidente:
- #105 `feat/v20-admin-unified-control-center`;
- #66 Admin transversal/Analítica/Fuentes;
- #80 `feat/v20-business-directory`;
- #82 Mágina Pass;
- #84 Experiencias/Reservas;
- #81 `feat/v20-routes-explore`;
- #87 `feat/v20-routes-adventure`;
- #93 cierre de catálogo de senderismo;
- #99 grabación GPS privada;
- #86 integración Rutas × Empresas × Territorio;
- ramas GIS, Catastro/SIGPAC, Weather/Radar o Mi Olivo que sigan abiertas.

Esos frentes tienen ciclos de integración propios. El hecho de que el bloque municipal los enlace conceptualmente no autoriza a fusionarlos aquí.

## Relación con #105 Admin unificado

#105 parte de `feat/v20-municipalities-admin-navigation` (#100), no del HEAD municipal final.

Por tanto:
- #105 se considera trabajo paralelo;
- este handoff no cambia la base de #105;
- no se cherry-pickea ni se recrea `/admin/modulos` dentro del bloque municipal;
- tras absorber la cadena municipal, el frente Admin unificado deberá reconciliarse de forma independiente con la rama coordinadora vigente.

## Secuencia de integración recomendada

1. Confirmar de nuevo que `integrate/v20-beta-closure` no se ha movido desde la comprobación de cierre.
2. Si no se ha movido, absorber la cadena municipal en orden de ancestry o, preferiblemente, integrar el último HEAD municipal conservando el historial completo.
3. No ejecutar los importadores #90/#91 durante la operación de merge.
4. Resolver únicamente conflictos que provengan de la rama coordinadora; no introducir Rutas, Empresas o Admin global para “arreglar” conflictos.
5. Ejecutar de nuevo la matriz global sobre el HEAD integrado resultante.
6. Mantener `main` intacto hasta que la coordinación Beta decida el cierre general.

## Criterio de aceptación tras absorción

El bloque se considera absorbido correctamente cuando el nuevo HEAD coordinador conserva:
- los 16 slugs municipales;
- migraciones 0061 y 0062 en la secuencia prevista;
- APIs pública/Admin municipales;
- todas las superficies `/ayuntamientos` y `/admin/ayuntamientos/**`;
- todos los contratos municipales, incluido `check-municipality-qa-16.mjs`;
- build/typecheck verde;
- Platform Admin, Candidate, Staging y Browser E2E verdes.

## Regla final

Este documento es un handoff, no una autorización de merge automático.

**No fusionar a `main`. No absorber #105/#66/Rutas/Empresas/GIS/Clima desde esta rama. No ejecutar importadores de contenido contra staging/live sin una acción explícita.**
