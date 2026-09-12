# V20 GIS Fincas — handoff P0/P1

Rama propietaria: `agent/gis-fincas`

Destino previsto de absorción: `integrate/v20-beta-closure`

## Alcance cerrado en esta rama

Este frente mantiene **Finca** como entidad visible y usa Catastro/SIGPAC únicamente como capas técnicas de localización y verificación.

### Alta de finca

1. El usuario introduce los datos básicos de la finca.
2. Puede guardar la finca sin geometría (`geometry_status = unlocated`) y completar los límites después.
3. Puede consultar un límite real por referencia catastral o ID de recinto SIGPAC.
4. Puede usar geolocalización del navegador para pedir candidatos por `bbox` sin guardar la posición del dispositivo.
5. El límite seleccionado se previsualiza sobre el mapa existente.
6. El usuario confirma explícitamente si ese límite debe convertirse en geometría canónica de la finca.
7. La referencia técnica se persiste en `field_land_refs` y, si se confirma, la geometría se guarda en `fields.geometry` con `geometry_source`, `geometry_status`, `geometry_checked_at` y `calculated_area_ha`.
8. Si el proveedor GIS falla después de crear la finca, la finca creada se conserva y el reintento reutiliza el mismo `fieldId`; no se crea una finca duplicada.

### Edición y recuperación

- Nueva ruta: `/mi-campo/fincas/editar?fieldId=...`.
- `GET /api/v1/fields/:fieldId` recupera los datos editables de la finca.
- `PATCH /api/v1/fields/:fieldId` actualiza los datos básicos sin alterar la geometría canónica.
- `GET /api/v1/fields/:fieldId/map-context` sigue siendo la fuente de verdad para geometría y referencias técnicas.
- Al volver a editar, la geometría canónica y las referencias persistidas se recuperan desde PostgreSQL/PostGIS.
- Es posible vincular otra referencia y sustituir la geometría canónica de forma explícita.
- Un fallo posterior del proveedor no borra ni degrada una geometría canónica ya verificada.

## Estados funcionales cubiertos

- Finca nueva sin geometría.
- Finca guardada sin geometría.
- Catastro seleccionado y persistido.
- SIGPAC seleccionado y persistido.
- Geometría canónica verificada y recuperada al reabrir edición.
- Sustitución explícita de geometría Catastro → SIGPAC.
- Error de proveedor antes de seleccionar un límite.
- Error de proveedor después de existir una geometría verificada, sin corrupción del estado anterior.
- Reintento tras fallo de vínculo sin duplicación de finca.

## Pruebas

### API

`apps/api/src/testing/gis-finca-flow-smoke.ts` cubre el ciclo:

`crear finca unlocated → leer → map-context vacío → consultar Catastro → vincular Catastro → recuperar geometría → editar finca → vincular SIGPAC → recuperar sustitución → provocar fallo de Catastro → comprobar que SIGPAC sigue canónico → crear otra finca sin geometría`.

Se mantienen además los smokes GIS ya existentes (`gis-smoke` y `map-context-smoke`).

### Navegador

`e2e/gis-finca.spec.ts` ejecuta con proveedores GIS deterministas:

- alta con Catastro real a nivel de contrato;
- persistencia de geometría canónica;
- recuperación al editar y tras recarga;
- edición de datos de finca;
- sustitución por SIGPAC;
- finca guardada sin geometría;
- error de proveedor recuperable.

La suite determinista se activa solo con `E2E_GIS_FIXTURES=true`. El E2E Beta general no queda acoplado a disponibilidad externa de Catastro/SIGPAC.

`e2e/beta-farmer.spec.ts` se actualiza para comprobar el nuevo flujo honesto de alta: el agricultor puede guardar sin límites y volver después al editor GIS real.

## CI

`.github/workflows/gis-check.yml` valida en la rama:

- typecheck/build de contratos, API y web;
- adaptadores GIS offline;
- migraciones completas sobre PostgreSQL 17 + PostGIS 3.5;
- smokes GIS existentes;
- smoke P0/P1 de finca/edición/geometría;
- Playwright del selector real con fixture server determinista.

## Fuera de alcance deliberadamente

Este frente **no** incluye:

- rediseño visual general;
- integración o merge de `integrate/v20-beta-closure`;
- `main`;
- clima/radar;
- Admin/CMS;
- Foundation;
- staging compartido;
- cambios de modelo visual de Mi Campo;
- ortofoto o nuevas capas cartográficas.

## Regla de absorción

Absorber el PR completo en `integrate/v20-beta-closure` cuando sus checks estén verdes. Si la rama de integración ya contiene cambios concurrentes en `new-farm-wizard.tsx`, `fincas/ver/page.tsx` o `beta-farmer.spec.ts`, resolver el conflicto conservando estas invariantes funcionales:

1. `Finca` sigue siendo la entidad de usuario.
2. Se puede guardar sin geometría.
3. La selección GIS real ocurre en alta y edición.
4. `field_land_refs` conserva referencias técnicas.
5. `fields.geometry` es la geometría canónica.
6. El editor recupera el estado persistido.
7. Un fallo GIS nunca destruye ni duplica la finca.
