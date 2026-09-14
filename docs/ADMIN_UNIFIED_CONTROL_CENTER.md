# Mágina Olivo V20 — Admin unificado

## Objetivo

Mantener una única vista de gobierno para las superficies administrativas de V20 sin copiar lógica desde ramas funcionales ni crear enlaces a rutas que todavía no existen en la base coordinada.

La fuente visible es `/admin/modulos`.

## Regla de cierre

Un módulo nuevo que requiera gestión administrativa no se considera cerrado para V20 hasta que:

1. declare su superficie Admin;
2. aparezca en el directorio unificado;
3. tenga una ruta real antes de marcarse como `available`;
4. permanezca sin `href` mientras su rama funcional no haya sido absorbida;
5. mantenga su información técnica/comercial en su dominio original, evitando duplicar modelos o backends.

## Estado de esta rama

### Disponible

- Operaciones
- Analítica
- Gestión
- Campañas y planes
- Agenda
- Trabajos
- Documentos / OCR
- Profesional
- Fuentes
- Territorio
- Multimedia
- Web / CMS
- Ayuntamientos

### En integración

- Empresas
- Experiencias y reservas
- Mágina Pass
- Rutas
- Comunidad de rutas
- Patrocinios de rutas
- Mágina Aventura

`En integración` no significa que el módulo no exista. Significa que su implementación vive todavía en una rama funcional independiente y esta rama no debe inventar una ruta, API o modelo para simular su absorción.

## Ayuntamientos

La base de esta rama es `feat/v20-municipalities-admin-navigation`, por lo que Ayuntamientos ya se considera disponible dentro de este frente. El directorio enlaza al centro municipal real y conserva la separación de sus editores especializados.

## Empresas, Pass y Experiencias

Estas superficies deben pasar a `available` únicamente después de absorber sus ramas funcionales correspondientes. En ese momento se añadirá el `href` real ya existente; no se reconstruirá aquí el módulo.

## Rutas y Aventura

Rutas mantiene su propio ciclo de publicación, moderación, comunidad y patrocinio. Aventura mantiene su propia evolución. El Admin unificado actúa como índice y contrato de integración, no como sustituto de esos dominios.

## Contrato automático

`scripts/check-admin-unified-control-center.mjs` comprueba como mínimo:

- presencia de los módulos críticos;
- rutas reales de las superficies ya disponibles;
- acceso al directorio desde `/admin`;
- acceso directo a Ayuntamientos;
- estado `integration` de los módulos todavía no absorbidos;
- ausencia de `href` en módulos pendientes para evitar enlaces rotos.

El contrato forma parte de `pnpm check` y `pnpm check:fast`.

## Próxima absorción

Cuando una rama funcional esté lista para entrar en el Admin unificado:

1. integrar primero su código real en la base coordinada;
2. confirmar que su ruta Admin existe y compila;
3. cambiar su registro de `integration` a `available`;
4. añadir su `href` real;
5. ampliar el contrato si incorpora una nueva superficie crítica;
6. ejecutar typecheck, build y checks de dominio antes del handoff.
