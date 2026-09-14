# Historial municipal

Ruta: `/admin/ayuntamientos/historial?municipio=<slug>`.

## Objetivo

Hacer legible la trazabilidad municipal sin crear un segundo sistema de auditoría ni ampliar el backend.

## Fuente de verdad

La pantalla reutiliza exclusivamente:
- `adminApi.audit()` para los eventos corporativos;
- `adminApi.content()` para resolver las piezas CMS vinculadas al municipio;
- `adminApi.territoryCatalog()` para la identidad municipal canónica.

## Correlación

Un evento se considera municipal cuando:
1. `target_id` coincide con el ID canónico del municipio;
2. `target_id` coincide con una pieza CMS actualmente vinculada mediante `content_json.municipality_id`;
3. la metadata de auditoría contiene el ID o slug canónico del municipio.

La correlación no usa coincidencias libres por nombre del pueblo.

## Privacidad y alcance

La vista es de solo lectura.

No solicita el directorio de usuarios ni muestra email o nombre personal del administrador. El actor se presenta como rol + identificador abreviado cuando existe.

La metadata completa del evento no se renderiza. Solo se permiten etiquetas no sensibles y útiles (`type`, `slug`, `status`, `name`). Esto evita exponer accidentalmente datos presentes en auditorías de otras superficies.

## Ventana histórica

La pantalla trabaja con el conjunto que devuelve `/api/v1/admin/audit`. Si no aparecen eventos no se interpreta como ausencia histórica absoluta: puede deberse a la ventana/retención disponible en la auditoría corporativa.

## Arquitectura

- sin tablas nuevas;
- sin endpoint nuevo;
- sin escritura;
- sin duplicar `admin_audit_log`;
- mantiene `?municipio=<slug>` y el workspace municipal compartido.
