# #700 — Catálogo territorial reutilizable (borrador implementable)

Estado: rama aislada; no fusionar ni aplicar migración Room hasta cerrar #696/Gate21 y revisar esquema actual. Este contrato no modifica la app.

## Objetivo
Un único catálogo offline-first para municipios de Andalucía. Primera carga: provincia de Jaén (código INE provincial 23); posteriores Granada (18) y Córdoba (14), sin cambiar IDs ni perder preferencias.

## Entidades y claves
- `territory_province`: `province_code TEXT PRIMARY KEY` (2 dígitos), `name TEXT NOT NULL`.
- `territory_municipality`: `municipality_code TEXT PRIMARY KEY` (5 dígitos INE, incluyendo provincia), `province_code TEXT NOT NULL` FK, `name TEXT NOT NULL`, `search_name TEXT NOT NULL` (normalizado), `latitude REAL NULL`, `longitude REAL NULL`, `active INTEGER NOT NULL DEFAULT 1`, `source_updated_at TEXT NULL`.
- `territory_place`: `place_id TEXT PRIMARY KEY` (identificador oficial de núcleo o ID interno estable con procedencia), `municipality_code TEXT NOT NULL` FK, `name TEXT NOT NULL`, `kind TEXT NOT NULL`, `source TEXT NOT NULL`.
- `territory_link`: `municipality_code TEXT NOT NULL` FK, `kind TEXT NOT NULL` (official_web, directory), `url TEXT NOT NULL`, `verified_at TEXT NULL`, `source TEXT NOT NULL`; PK (municipality_code,kind,url).
- `territory_catalog_version`: `dataset TEXT PRIMARY KEY`, `version TEXT NOT NULL`, `sha256 TEXT NOT NULL`, `imported_at TEXT NOT NULL`, `attribution TEXT NOT NULL`.

**Preferencia de usuario:** `preferred_information_municipality_code` en el almacenamiento de preferencias ya existente; NO duplicar perfil ni mezclar con ubicación de finca/campaña, municipio catastral o ubicación meteorológica. Inspeccionar implementación real antes de elegir DataStore/Room.

## Importación transaccional
1. Obtener exportación oficial de la Junta y documentar URL, fecha, licencia y esquema real. No fijar nombres de campos hasta inspeccionar payload.
2. Normalizar códigos como texto, conservar ceros iniciales, validar provincia y longitud, Unicode y nombres no vacíos.
3. Rechazar duplicados con distinto nombre/código y registros con provincia inexistente; registrar errores de origen. No completar coordenadas inventadas.
4. Construir snapshot versionado de Jaén; empaquetarlo para primera ejecución offline.
5. Importar con transacción atómica e índices en provincia y search_name; usar upsert conservando referencias y sin borrado destructivo. Los registros desaparecidos pasan a inactive hasta confirmar fuente.
6. Al añadir Granada/Córdoba: importar nuevas provincias y municipios, sin reemplazar Jaén ni cambiar códigos.
7. Guardar hash, fecha y atribución de cada fuente; actualizar enlaces oficiales con verificación independiente.

## UI
- Selector con búsqueda sin tildes, resultados por municipio y provincia, y cambio reversible.
- Tarjeta compacta Mi Pueblo en Inicio, web oficial solo si URL validada, alternativa al directorio provincial.
- Pueblos cercanos solo si existen coordenadas verificadas; distancia aproximada geográfica, no por carretera.
- Finca/Catastro solo reciben sugerencia editable, nunca actualización automática de datos existentes.

## Pruebas mínimas
- Catálogo Jaén importado offline; importación repetida idempotente.
- Códigos como strings de cinco caracteres, búsqueda con y sin tildes.
- Granada/Córdoba añadidas sin pérdida de Jaén.
- Municipio y núcleo de población distintos.
- Cambio de localidad preferida no modifica finca, campaña ni tiempo.
- Datos sin URL o coordenadas se presentan sin enlaces/distancias ficticias.
- Upgrade preserva datos locales; rollback si falla la transacción.
- Pantallas 360dp/390dp/tablet, accesibilidad y navegador externo.

## Fuentes para verificar en T0
- Junta, datos abiertos municipales: https://www.juntadeandalucia.es/datosabiertos/portal/dataset/datos-abiertos-municipios
- Diputación de Jaén: https://www.dipujaen.es/municipios/
- IECA, nomenclátor: https://www.juntadeandalucia.es/institutodeestadisticaycartografia/dega/nomenclator-de-entidades-y-nucleos-de-poblacion-de-andalucia

## Integración
Antes de crear entidades Room o tocar formularios, inspeccionar migraciones existentes, tablas de Catastro, ubicación meteorológica y componentes Compose. Abrir PR aislada con tests y CI cuando termine Gate21. No confundir este contrato con implementación validada.
