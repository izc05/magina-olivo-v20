# Mágina Olivo V20 · GIS, finca y referencias territoriales

Estado: arquitectura de implementación en `feat/v20-visual-prototype`.

## Regla principal

La entidad que entiende y gestiona el usuario es la **finca** (`fields` en la capa técnica actual).

```text
FINCA "Las Cenillas"
├── identidad y datos agrícolas propios
├── geometría canónica de la finca
└── referencias territoriales[]
    ├── Catastro
    ├── SIGPAC
    ├── manual/importación
    └── futuras fuentes
```

**Una finca no es automáticamente una parcela catastral ni un recinto SIGPAC.**

Una finca puede corresponder a una parcela completa, varias parcelas, parte de una parcela, varios recintos SIGPAC o una geometría compuesta definida por el usuario.

## Geometría canónica

`fields.geometry` es la geometría que usa Mágina Olivo para representar la finca en Mi Campo y para operaciones derivadas que requieran localización.

Metadatos V20:

- `geometry_source`: `manual | catastro | sigpac | import | composite`.
- `geometry_status`: `unlocated | draft | verified | needs_review`.
- `geometry_checked_at`: momento de la última comprobación relevante.
- `calculated_area_ha`: superficie de trabajo asociada a la geometría canónica.

Vincular una referencia territorial **no cambia** esta geometría por defecto. El cambio exige `set_as_geometry=true` y permiso suficiente.

## Referencias territoriales

`field_land_refs` conserva las fuentes oficiales/técnicas asociadas a una finca:

- `source`
- `reference`
- geometría obtenida de la fuente
- superficie
- estado de verificación
- metadatos normalizados
- `checked_at`

Existe unicidad por `(field_id, source, reference)` cuando hay referencia, por lo que volver a comprobar la misma parcela/recinto actualiza la evidencia en vez de duplicarla.

## Catastro

Adaptador V20: `apps/api/src/gis/catastro.ts`.

- Fuente: WFS INSPIRE de la Dirección General del Catastro.
- `GetParcel` por referencia catastral de 14 caracteres.
- búsquedas espaciales limitadas mediante bbox pequeño.
- para Sierra Mágina se solicita `EPSG:25830` (ETRS89 / UTM 30N).
- la respuesta GML se normaliza a geometría GeoJSON WGS84 (`EPSG:4326`) antes de entrar en la aplicación.
- límites de tamaño, número de features y timeout evitan respuestas descontroladas.

La app nunca acepta una geometría enviada por el navegador como si fuese una geometría oficial Catastro verificada: al vincular una referencia, la API vuelve a consultar el proveedor.

## SIGPAC

Adaptador V20: `apps/api/src/gis/sigpac.ts`.

- Fuente: OGC API Features de SIGPAC.
- colección: `recintos` de la campaña en uso.
- salida GeoJSON.
- consulta por bbox y por ID numérico del feature.
- se normalizan solo campos conocidos: provincia, municipio, agregado, zona, polígono, parcela, recinto, pendiente, altitud, superficie y uso SIGPAC.
- propiedades desconocidas del upstream no se propagan automáticamente.

Al igual que Catastro, una geometría SIGPAC solo se persiste como verificada después de recuperarla desde el backend.

## API V20

Consultas territoriales, siempre dentro de un contexto de workspace autenticado:

```text
GET /api/v1/gis/catastro/parcels
GET /api/v1/gis/catastro/parcels/:reference
GET /api/v1/gis/sigpac/recintos
GET /api/v1/gis/sigpac/recintos/:featureId
```

Referencias de una finca:

```text
GET  /api/v1/fields/:fieldId/land-references
POST /api/v1/fields/:fieldId/land-references/catastro
POST /api/v1/fields/:fieldId/land-references/sigpac
```

Body de vinculación Catastro:

```json
{
  "reference": "23044A00100001",
  "set_as_geometry": false
}
```

Body de vinculación SIGPAC:

```json
{
  "feature_id": "233788127",
  "set_as_geometry": false
}
```

## Seguridad y permisos

- Las rutas GIS privadas requieren una sesión/workspace válido.
- No se permite leer ni modificar referencias de una finca de otro workspace.
- Consultar candidatos territoriales puede hacerlo un miembro autenticado.
- Vincular referencias y cambiar la geometría canónica queda limitado a `owner`, `admin` y `manager` (más el contexto temporal de desarrollo en CI).
- El frontend no decide que una geometría sea oficial.
- Los datos de una finca privada no se publican en la Guía ni en Comunidad.

## Mapa único V20

La siguiente capa visual debe usar un único `MapPlatform`, no mapas independientes por módulo:

```text
MAPA V20
├── Base
│   ├── calles / OSM
│   └── ortofoto / PNOA
├── Mi Campo
│   ├── geometría canónica
│   └── finca seleccionada
├── Catastro
│   └── parcelas candidatas / vinculadas
├── SIGPAC
│   └── recintos candidatos / vinculados
├── Tiempo
│   ├── radar
│   └── precipitación
└── Operaciones
    ├── observaciones
    ├── incidencias
    └── trabajos
```

La primera implementación visual debe priorizar: `Mi ubicación`, `Mis fincas`, `Ortofoto`, `Catastro`, `SIGPAC` y más adelante `Radar lluvia`.

## Flujo de alta recomendado

```text
Añadir finca
   ↓
¿Cómo quieres localizarla?
   ├── Usar mi posición
   ├── Pulsar en mapa
   ├── Buscar referencia catastral
   ├── Buscar Catastro alrededor
   ├── Buscar SIGPAC alrededor
   └── Dibujar / importar
   ↓
Seleccionar referencia(s)
   ↓
Vista previa de límites
   ↓
Confirmar geometría de la finca
   ↓
Guardar
```

La confirmación final distingue siempre entre **vincular una referencia** y **usar su geometría como geometría de la finca**.

## Pruebas obligatorias

El workflow `V20 GIS land references check` debe validar sin depender de Internet:

1. construcción y validación de URLs/IDs/bbox de ambos adaptadores;
2. conversión Catastro EPSG:25830 → WGS84 sobre GML sintético;
3. normalización segura de SIGPAC;
4. persistencia PostGIS de referencias;
5. idempotencia/upsert de la misma referencia;
6. cambio explícito de geometría canónica;
7. que vincular otra referencia con `set_as_geometry=false` no pise la geometría existente;
8. aislamiento entre workspaces.

## Siguiente fase

Cuando este contrato esté verde en CI:

1. endpoint `field map context` con geometría canónica + referencias;
2. componente MapLibre compartido;
3. selección Catastro/SIGPAC desde mapa;
4. ortofoto PNOA;
5. centroides/áreas robustos en PostGIS;
6. AEMET y radar asociados al centroide/geometría de cada finca;
7. alertas meteorológicas de finca independientes de la ubicación actual del usuario.
