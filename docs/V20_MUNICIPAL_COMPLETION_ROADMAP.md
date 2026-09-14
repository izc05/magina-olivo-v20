# V20 Ayuntamientos — roadmap de cierre

Este documento define el cierre del bloque municipal de Mágina Olivo V20 sobre la cadena que culmina en PR #100.

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

Estado: EN IMPLEMENTACIÓN en `feat/v20-municipalities-seo-readiness`.

Objetivos:
- metadata específica por municipio;
- título y descripción propios;
- Open Graph y Twitter Card;
- imagen social solo cuando procede de media real publicada;
- fallback determinista si la API no está disponible durante build;
- diagnóstico Admin 5/5: perfil principal, resumen, imagen, fuente/verificación y descubrimiento publicado.

Cierre:
- contrato específico verde;
- typecheck/build verde;
- 16 slugs siguen exportándose;
- ningún contenido inventado para rellenar metadata.

## Fase 2 — Preview editorial antes de publicar

Objetivos:
- vista previa desde Admin del hero, resumen y destacados;
- distinguir claramente borrador vs resultado público;
- acceso contextual con `?municipio=`;
- preflight visual con avisos de huecos, sin publicación automática.

Cierre:
- preview usa únicamente contenido real del CMS;
- responsive y accesible;
- no duplica el renderer público ni crea una fuente editorial paralela.

## Fase 3 — Historial y auditoría municipal legible

Objetivos:
- reutilizar auditoría existente;
- filtrar eventos por municipio y dominio municipal;
- mostrar quién cambió qué, cuándo y sobre qué entidad;
- accesos desde el contexto municipal.

Cierre:
- no crear un segundo sistema de auditoría;
- no exponer datos sensibles;
- permisos iguales al Admin existente.

Nota: antes de ejecutar esta fase se debe volver a comprobar colisión con PR #66 / Admin global.

## Fase 4 — Matriz de huecos de contenido

Objetivos:
- detectar por municipio huecos de perfil, media, procedencia, patrimonio, naturaleza, turismo, actualidad y economía;
- separar cobertura institucional de preparación editorial;
- enlaces directos al editor que resuelve cada hueco.

Cierre:
- todas las señales son binarias y derivadas de datos reales;
- ninguna puntuación subjetiva ni ranking de municipios.

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
