# V20 — Portada pública municipal

## Objetivo

Elevar la ficha `/ayuntamientos/[slug]` para que funcione como una entrada editorial al municipio sin crear una segunda fuente de datos y sin inventar contenido turístico.

La portada reutiliza exclusivamente la respuesta pública municipal ya existente (`loadPublicMunicipality`) y el contenido CMS publicado y vinculado explícitamente mediante `municipality_id` / `municipality_role`.

## Hero territorial

La cabecera mantiene el nombre, resumen y datos institucionales existentes. Cuando hay una imagen editorial ya publicada, la reutiliza en este orden:

1. imagen del perfil municipal;
2. primera imagen disponible entre los descubrimientos publicados;
3. si no existe ninguna, se conserva el fondo territorial de Mágina Olivo.

No se generan imágenes ni se introducen URLs visuales externas nuevas.

El hero expone accesos reales a turismo oficial, web oficial y sede electrónica únicamente cuando sus URLs están disponibles.

## Lo imprescindible

La nueva sección muestra hasta tres contenidos del catálogo municipal ya publicado.

La selección es determinista y no pretende crear un ranking turístico externo:

1. primero respeta entradas marcadas como `featured` por el CMS;
2. después intenta completar la selección con diversidad editorial: `heritage`, `nature`, `tourism`;
3. finalmente completa huecos con los descubrimientos restantes en su orden existente;
4. nunca repite una entrada y nunca muestra más de tres.

Por tanto, “Lo imprescindible” significa una portada editorial construida con el catálogo disponible, no una afirmación de popularidad, valoración o recomendación oficial.

## Resumen por categorías

La portada muestra los conteos reales de:

- Patrimonio;
- Naturaleza;
- Turismo.

Cada contador enlaza con la sección `#descubrir` y activa el filtro público real del PR anterior. Las tarjetas de imprescindibles hacen lo mismo con su categoría.

## Responsive y accesibilidad

- hero adaptado a escritorio, tablet y móvil;
- 3 tarjetas en escritorio;
- tarjetas horizontales compactas en móvil;
- `focus-visible` en tarjetas enlazables;
- navegación por anclas conservada;
- no se cambia el orden semántico de las secciones existentes.

## Límites deliberados

Este cambio no modifica:

- `cms_entries`;
- API pública o administrativa;
- migraciones;
- catálogo de 32 recursos de #90/#91;
- Rutas/GPX;
- Empresas;
- GIS/coordenadas;
- clima/radar;
- Admin/importador;
- `main`.

## Contrato

`scripts/check-municipality-public-landing.mjs` protege:

- selección determinista de imprescindibles;
- prioridad editorial + diversidad de roles;
- reutilización de imágenes CMS reales;
- enlaces con filtros existentes;
- conteos por categoría;
- ausencia de `Math.random` o fetches paralelos arbitrarios;
- responsive y foco visible.
