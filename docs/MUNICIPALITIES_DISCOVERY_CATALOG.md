# Catálogo municipal de descubrimientos

## Objetivo

Extender el hub de los 16 municipios con una segunda capa de contenido real y verificable, equilibrando patrimonio histórico y naturaleza sin duplicar el CMS ni invadir Rutas, Empresas o GIS.

## Arquitectura

La publicación sigue usando `cms_entries` de tipo `place`. El catálogo de código no es una segunda base de datos pública: es una fuente auditada para que un administrador autenticado importe o actualice fichas en el CMS conservando autoría real.

El conjunto importable se compone de:

- `MUNICIPALITY_HERITAGE_CATALOG`: primera capa, 16 recursos;
- `MUNICIPALITY_DISCOVERY_CATALOG`: segunda capa, 16 recursos;
- `OFFICIAL_DISCOVERY_CATALOG`: unión de ambas, 32 recursos.

Cada elemento conserva:

- municipio canónico por slug;
- slug CMS estable;
- clasificación `heritage`, `nature` o `tourism`;
- resumen y texto factual breve;
- `source_url`;
- `source_label`;
- `verified_at`.

## Segunda capa

1. Albanchez de Mágina — Fuente de la Seda — patrimonio.
2. Bedmar y Garcíez — Nacimiento del Río Cuadros — naturaleza.
3. Bélmez de la Moraleda — Barranco del Arroyo Gargantón — naturaleza.
4. Cabra del Santo Cristo — Puente Arroyo Salado — patrimonio.
5. Cambil — Mata-Bejid y sus chopos singulares — naturaleza.
6. Campillo de Arenas — Desfiladero de Puerta Arenas — naturaleza.
7. Cárcheles — Paraje de Cazalla — naturaleza.
8. La Guardia de Jaén — Plaza Monumental de Isabel II — patrimonio.
9. Huelma — Iglesia de la Inmaculada Concepción — patrimonio.
10. Jimena — Pinar de Cánava — naturaleza.
11. Jódar — Las Quebradas — naturaleza.
12. Larva — El Pozuelo, La Laguna y Cueva del Joso — naturaleza.
13. Mancha Real — Peña del Águila — naturaleza.
14. Noalejo — Navalcán — naturaleza.
15. Pegalajar — Cueva de Aro — naturaleza.
16. Torres — Manantial de Fuenmayor — naturaleza.

## Criterio de fuentes

Se priorizan páginas oficiales de ayuntamientos y portales turísticos municipales enlazados por esos ayuntamientos. No se importa una ficha cuando solo existe una referencia débil o no verificable.

El texto del catálogo resume únicamente información factual suficiente para identificar el recurso. No se generan horarios, accesos, coordenadas, precios, restricciones ni imágenes si la fuente no los documenta de manera clara.

## Importación

`/admin/ayuntamientos/patrimonio` muestra el catálogo combinado y permite importar una ficha o las 32.

La operación es idempotente por `type + slug`:

- si no existe, crea `place` publicado;
- si existe, mantiene título/resumen editados cuando ya están presentes, `body` editorial, imagen, destacado, estado, ventanas y orden;
- siempre refresca municipio canónico, `municipality_role` y procedencia oficial.

La escritura se realiza con `adminApi.createContent` / `adminApi.updateContent`, por lo que `created_by` y `updated_by` pertenecen al administrador autenticado.

## Límites

Este catálogo no incorpora:

- trazados senderistas o GPX;
- coordenadas GIS;
- empresas o alojamientos;
- imágenes generadas;
- horarios o condiciones de visita no verificadas;
- relaciones inferidas por texto.

Rutas, Empresas y GIS mantienen sus ramas y modelos propios.
