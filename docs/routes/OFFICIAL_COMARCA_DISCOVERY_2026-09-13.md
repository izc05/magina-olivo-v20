# Barrido oficial de senderismo por municipios — 2026-09-13

Complementa `SIERRA_MAGINA_MASTER_CATALOG.md` y separa el núcleo del Parque Natural de las rutas oficiales/publicadas por ayuntamientos y portales turísticos de la comarca.

## Hallazgos fuera del núcleo de 15 senderos del Parque

### La Guardia de Jaén

Fuente municipal actual: Ayuntamiento de La Guardia de Jaén.

- Ruta / Sendero del Cerro San Cristóbal.
- Variante descrita como ruta por Cerrillo de San Cristóbal, Cueva Cabrera y ermita de San Sebastián.
- El Ayuntamiento documenta un recorrido circular cercano a 8 km y señalizado, con Cueva Cabrera, Allanadas del Santo y miradores.

Estado: `official_comarca`, localizada y documentable. Track pendiente de localizar/validar antes de publicar.

### Noalejo

Fuente municipal actual: Ayuntamiento de Noalejo, paraje de Navalcán.

Rutas de senderismo expresamente enumeradas:

- Navalcán – Molinos.
- Navalcán – Los Castillejos – Pinturas rupestres.
- Cañada del Toril.
- Noalejo – Navalcán.

Estado: `official_comarca`, localizadas. Datos técnicos y tracks pendientes de contraste individual.

### Cárcheles

Fuente municipal actual: Ayuntamiento de Cárcheles, Patrimonio Natural.

Rutas propuestas expresamente:

- Carchelejo – Cazalla – Convento.
- Cárchel – Carchelejo por el Barranco de la Parrilla.
- Ascensión a la Cueva del Puerto de las Palomas.

Estado: `official_comarca`, localizadas. Datos técnicos y tracks pendientes de contraste individual.

### Mancha Real

Fuente turística municipal actual: Turismo Mancha Real / Ayuntamiento de Mancha Real.

Rutas de senderismo publicadas:

- Mancha Real: El Puerto, Los Llanos y Peña del Águila.
- Mancha Real: Ruta Circular Peña del Águila.
- Mancha Real: Peña del Águila, El Morrón, Mojón Blanco, Refugio y Cueva de los Murciélagos.

Las tres aparecen como senderos de Nivel 2 y con descarga disponible en la web municipal turística.

Estado: `official_comarca`, localizadas. Descargables pendientes de ingestión y validación geométrica.

### Campillo de Arenas

Fuente municipal actual: Ayuntamiento de Campillo de Arenas, Patrimonio Natural.

- Sendero didáctico de botánica del Desfiladero de Puerta Arenas.

La ficha municipal confirma la existencia del sendero en el desfiladero, aunque no aporta en la página auditada una ficha técnica completa de ruta.

Estado: `official_comarca`, localizada como candidata oficial; requiere ficha/track adicional antes de alta publicable.

### Larva

Fuente turística provincial actual: Jaén Paraíso Interior.

- Paraje de El Pozuelo: identificado como lugar apto para senderismo y observación de naturaleza.

No se ha localizado todavía, en este barrido, una ficha oficial de sendero con nombre + recorrido técnico + track que cumpla el criterio de alta como ruta independiente.

Estado: municipio auditado parcialmente; `route_candidate_pending`.

### Cabra del Santo Cristo

Jaén Paraíso Interior publica actualmente recorridos intermodales/cicloturistas del PSTD de Sierra Mágina, entre ellos C7, pero el presente catálogo de senderismo no debe convertir una ruta ciclista en sendero a pie sin fuente específica.

Estado: municipio auditado parcialmente; búsqueda de senderos peatonales oficiales pendiente.

## Hallazgos adicionales que amplían municipios ya cubiertos

### Pegalajar

El II Plan de Desarrollo Sostenible de Sierra Mágina inventaría, con titularidad municipal:

- Sendero de la Serrezuela — 7 km circular.
- Sendero de la Huerta — 2,5 km circular.
- Hoyo de la Sierra — 5 km circular (sendero y ruta cicloturista).
- Sendero del Puerto de Villanueva — 10 km lineal.

Estas rutas no deben confundirse con el sendero oficial de Junta Veredón–Mojón Blanco.

### Torres

El mismo inventario recoge:

- Cerro de la Vieja.
- Camino Viejo de Fuenmayor.
- Ruta de los Manantiales.
- Cueva del Morrón.

### Mancha Real (inventario histórico oficial)

El II PDS también recoge:

- Peña del Águila – Nivel 1 — 1,9 km.
- Peña del Águila – Nivel 2 — 6,9 km.

La web turística municipal actual debe prevalecer para nombres y descargables vigentes; el II PDS sirve como evidencia de continuidad/inventario institucional.

## Resultado provisional del barrido

Este barrido demuestra que el catálogo comarcal es necesariamente mayor que los 15 senderos señalizados del Parque Natural. Ya se han localizado rutas oficiales/municipales adicionales en La Guardia, Noalejo, Cárcheles, Mancha Real, Campillo de Arenas, Pegalajar y Torres.

No marcar estos hallazgos como `publishable` hasta disponer de track real validado y ficha técnica suficiente.

## Siguiente control

Pendientes prioritarios:

1. Cabra del Santo Cristo — localizar senderismo peatonal oficial específico.
2. Larva — localizar itinerarios con ficha y trazado oficial.
3. Resolver descargables oficiales de Mancha Real.
4. Localizar tracks/fichas municipales de Noalejo y Cárcheles.
5. Cruzar todo el inventario con Jaén Paraíso Interior y el Circuito Intermodal PSTD para detectar duplicados y conexiones.
6. Incorporar cada candidato confirmado al JSON maestro con `source_url`, `source_status`, `track_found`, `track_validated` y `publishable`.
