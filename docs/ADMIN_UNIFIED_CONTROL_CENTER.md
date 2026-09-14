# Mágina Olivo V20 — Admin unificado

## Objetivo

Mantener una única vista de gobierno para las superficies administrativas de V20 sin copiar lógica desde ramas funcionales ni crear enlaces a rutas que todavía no existen en la base coordinada.

La fuente visible es `/admin/modulos`.

## Regla de cierre

Un módulo nuevo que requiera gestión administrativa no se considera cerrado para V20 hasta que:

1. declare su superficie Admin;
2. aparezca en el directorio unificado;
3. tenga una ruta real antes de marcarse como `available`;
4. declare rama fuente y ruta objetivo cuando su Admin ya exista pero todavía no haya sido absorbido;
5. permanezca sin `href` mientras su rama funcional no haya sido absorbida;
6. mantenga su información técnica/comercial en su dominio original, evitando duplicar modelos o backends.

## Estados del directorio

### `available` — Disponible aquí

La superficie Admin existe dentro de la base de esta rama y puede enlazarse directamente. Debe declarar `href`.

### `implemented` — Implementado en rama

La superficie Admin real ya ha sido verificada en una rama funcional, pero ese código todavía no forma parte de esta base. Debe declarar `sourceBranch` y `targetHref`, pero no debe exponer `href` para evitar enlaces rotos.

## Cobertura auditada

El inventario actual registra **20 módulos de 20 con superficie administrativa identificada**:

- **13/20 disponibles** en esta rama;
- **7/20 implementados** en ramas funcionales y pendientes de absorción;
- **0/20 sin contrato Admin conocido**.

Tener cobertura 20/20 no significa que los 20 módulos estén ya integrados en esta rama. Separa deliberadamente dos preguntas: “¿existe Admin para este dominio?” y “¿está ya absorbido en la base coordinada?”.

## Disponibles en esta rama

- Operaciones — `/admin/operaciones`
- Analítica — `/admin/analitica`
- Gestión — `/admin/gestion`
- Campañas y planes — `/admin/campanas-planes`
- Agenda — `/admin/agenda`
- Trabajos — `/admin/trabajos`
- Documentos / OCR — `/admin/documentos`
- Profesional — `/admin/profesional`
- Fuentes — `/admin/fuentes`
- Territorio — `/admin/territorio`
- Multimedia — `/admin/media`
- Web / CMS — `/admin/web`
- Ayuntamientos — `/admin/ayuntamientos`

## Implementados en ramas funcionales

| Módulo | Rama fuente verificada | Ruta Admin objetivo |
| --- | --- | --- |
| Empresas | `feat/v20-business-directory` | `/admin/empresas` |
| Experiencias y reservas | `feat/v20-business-experiences` | `/admin/empresas/experiencias` |
| Mágina Pass | `feat/v20-business-magina-pass` | `/admin/empresas/magina-pass` |
| Rutas | `feat/v20-routes-explore` | `/admin/rutas` |
| Comunidad de rutas | `feat/v20-routes-explore` | `/admin/rutas/comunidad` |
| Patrocinios de rutas | `feat/v20-routes-explore` | `/admin/rutas/patrocinios` |
| Mágina Aventura | `feat/v20-routes-adventure` | `/admin/rutas/aventuras` |

Estas rutas objetivo son información de gobierno y no enlaces activos desde esta rama. Solo pasarán a `href` cuando el código funcional correspondiente haya sido absorbido y validado en la base común.

## Ayuntamientos

La base de esta rama es `feat/v20-municipalities-admin-navigation`, por lo que Ayuntamientos ya se considera disponible dentro de este frente. El directorio enlaza al centro municipal real y conserva la separación de sus editores especializados.

## Empresas, Pass y Experiencias

La auditoría confirma que estos tres dominios ya tienen superficies Admin reales en sus respectivas ramas. No deben reconstruirse dentro del Admin unificado. La integración correcta consiste en absorber primero su código funcional y después cambiar su estado a `available` con el `href` ya existente.

## Rutas, comunidad, patrocinios y Aventura

La auditoría confirma superficies Admin reales para Rutas, moderación comunitaria, patrocinios y Mágina Aventura. El centro unificado registra sus contratos pero no sustituye sus dominios ni adelanta enlaces antes de la absorción.

## Contrato automático

`scripts/check-admin-unified-control-center.mjs` comprueba como mínimo:

- presencia de los 20 módulos registrados;
- exactamente 13 módulos `available` y 7 `implemented` en esta fase;
- `href` real para las superficies ya disponibles;
- ausencia de `href` en módulos aún no absorbidos;
- rama fuente y ruta objetivo de los 7 módulos implementados externamente;
- rutas objetivo bajo `/admin/`;
- acceso al directorio desde `/admin`;
- acceso directo a Ayuntamientos;
- regla explícita que separa cobertura administrativa de absorción técnica.

El contrato forma parte de `pnpm check` y `pnpm check:fast`.

## Próxima absorción

Cuando una rama funcional esté lista para entrar en el Admin unificado:

1. integrar primero su código real en la base coordinada;
2. confirmar que su ruta Admin existe y compila;
3. cambiar su registro de `implemented` a `available`;
4. sustituir `sourceBranch` y `targetHref` por su `href` real;
5. ampliar el contrato si incorpora una nueva superficie crítica;
6. ejecutar typecheck, build y checks de dominio antes del handoff.

Este documento describe cobertura y contrato de integración; no sustituye el estado de CI. La rama solo debe considerarse validada cuando los checks reales del commit correspondiente hayan terminado correctamente.
