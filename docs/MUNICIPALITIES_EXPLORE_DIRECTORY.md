# V20 — Explorador público de municipios

## Objetivo

Convertir `/ayuntamientos` en una puerta de entrada territorial a los 16 municipios de Sierra Mágina sin añadir nuevas fuentes, rankings ni datos inventados.

## Fuente de datos

La pantalla sigue usando exclusivamente `loadPublicMunicipalities()` y el contrato público existente. No se añaden llamadas `fetch` directas, endpoints, tablas ni migraciones.

## Capacidades

- búsqueda por nombre de municipio, localidad y código INE;
- filtro `Todos`;
- filtro `Turismo oficial`, basado en `tourism_url` verificado;
- filtro `Con actualidad`, basado en recuentos publicados de noticias/eventos;
- filtro `Economía local`, basado en cooperativas/almazaras y directorio publicados;
- orden alfabético A–Z;
- orden por mayor cantidad de contenido publicado;
- orden por mayor cantidad de actualidad publicada;
- contador accesible de municipios visibles;
- reset completo cuando no hay resultados;
- tarjetas con localidades, señales de contenido y accesos a la ficha municipal y web oficial.

## Principio de producto

No se muestra un ranking editorial de pueblos ni se afirma que un municipio sea mejor que otro. Los únicos órdenes distintos de A–Z se derivan matemáticamente de los recuentos públicos existentes.

## Accesibilidad y responsive

Los filtros mantienen `aria-pressed`, el número de resultados usa `aria-live="polite"`, los controles tienen `focus-visible` y la cuadrícula pasa de 3 a 2 y 1 columnas según el ancho disponible.

## Fuera de alcance

- modificar fichas municipales individuales;
- añadir patrimonio/turismo nuevo;
- imágenes IA;
- mapas, GIS o coordenadas;
- Rutas/GPX;
- Empresas;
- Clima/Radar;
- Admin;
- API/base de datos;
- `main`.

## Contrato

`scripts/check-municipality-explore-directory.mjs` protege búsqueda, filtros verificados, ordenación determinista, accesibilidad, reset y estilos responsive.
