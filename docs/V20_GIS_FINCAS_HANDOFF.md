# V20 GIS Fincas — handoff P0/P1

## Estado

Frente funcional cerrado en `agent/gis-fincas` y preparado para absorción en `integrate/v20-beta-closure`.

La entidad visible sigue siendo **Finca**. Catastro y SIGPAC son capas técnicas de localización/verificación; no sustituyen el modelo de Finca.

## Contrato funcional cerrado

- Alta de Finca con selector GIS real antes de confirmar el guardado.
- Catastro: consulta por referencia y búsqueda por área/bbox.
- SIGPAC: consulta por ID de recinto y búsqueda por área/bbox.
- Previsualización del límite seleccionado en el mapa existente.
- Confirmación explícita de si el límite seleccionado pasa a ser la geometría canónica.
- Persistencia de referencias en `field_land_refs`.
- Persistencia de geometría canónica, superficie, origen, estado y fecha de comprobación en `fields`.
- Guardado válido de Finca sin geometría con estado `unlocated`.
- Alta idempotente: si el vínculo GIS falla tras crear la Finca, el reintento reutiliza la misma Finca.
- Lectura individual y edición de Finca mediante API.
- Edición en `/mi-campo/fincas/editar?fieldId=...`.
- Recuperación de geometría y referencias al volver a editar.
- Sustitución explícita de geometría canónica, incluida Catastro → SIGPAC.
- Un fallo de Catastro/SIGPAC no borra ni degrada la geometría canónica existente.
- Acceso al editor GIS desde la ficha de Finca.

## API y persistencia

- `GET /api/v1/fields/:fieldId`
- `PATCH /api/v1/fields/:fieldId`
- `GET /api/v1/fields/:fieldId/map-context`
- `GET /api/v1/fields/:fieldId/land-references`
- `POST /api/v1/fields/:fieldId/land-references/catastro`
- `POST /api/v1/fields/:fieldId/land-references/sigpac`

Invariantes que deben conservarse durante la absorción:

1. `fields.geometry` es la geometría canónica de la Finca.
2. `field_land_refs` conserva las referencias y geometrías técnicas consultadas/vinculadas.
3. `geometry_source` indica el origen de la geometría canónica (`catastro`, `sigpac`, etc.).
4. Una Finca sin geometría permanece operativa con `geometry_status = 'unlocated'`.
5. Los errores upstream se muestran como error recuperable y no mutan el estado territorial previo.
6. Catastro/SIGPAC no deben convertirse en la entidad visible principal de la interfaz.

## Pruebas cerradas

El workflow `V20 GIS finca selector check` valida:

- typecheck/build de contratos, API y web;
- adaptadores GIS offline;
- PostgreSQL 17 + PostGIS 3.5 y migraciones;
- `gis-smoke`;
- `map-context-smoke`;
- `gis-finca-flow-smoke`;
- Playwright determinista para selector, alta, edición, persistencia, recuperación, `unlocated`, sustitución Catastro → SIGPAC y fallo de proveedor.

La suite GIS de navegador usa `E2E_GIS_FIXTURES=true`; la suite Beta general no depende de la disponibilidad externa de Catastro/SIGPAC.

Además se actualizan los journeys transversales de Beta que crean una Finca para respetar el nuevo contrato de alta (`Guardar sin límites` cuando no se selecciona una geometría).

## Validación final del handoff

HEAD funcional validado antes del cierre: `7ebf75ac7192d6d42ad5119b0154feba7b2d74b6`.

Sobre ese HEAD quedaron verdes:

- V20 environment contract;
- V20 foundation;
- V20 full candidate check;
- V20 GIS finca selector check;
- V20 beta browser E2E;
- V20 staging readiness (validación transversal automática; este frente no modificó staging compartido).

El PR de handoff es `#61`, con base `integrate/v20-beta-closure`. Está preparado para revisión/absorción y no debe fusionarse en `main` desde este frente.

## Fuera de alcance respetado

No se ha realizado rediseño visual general. No se ha trabajado funcionalmente en clima/radar, Admin, Foundation ni staging compartido. No se ha fusionado `main` ni se ha ejecutado la absorción del PR desde esta rama.
