# V20 · Pueblos de Mágina — modelo territorial

## Alcance

La capa territorial usa como referencia de producto los **16 municipios de Sierra Mágina**. El Parque Natural Sierra Mágina ocupa término municipal de 9 de ellos; esa condición se conserva como atributo y no limita el directorio.

## Objetivo

La ficha de pueblo funciona como hub territorial. No duplica contenido de otros módulos: enlaza y filtra información ya existente por municipio.

## Capas

1. **Identidad del municipio**: slug canónico, nombre oficial, aliases, imagen, resumen, descripción y web oficial.
2. **Descubrir**: historia, patrimonio, naturaleza, gastronomía y lugares destacados.
3. **Actividad viva**: eventos, noticias, tiempo y avisos.
4. **Economía local**: empresas, servicios, almazaras/cooperativas y AOVE.
5. **Exploración**: rutas y Mágina Aventura.
6. **Administración**: Ayuntamiento y recursos oficiales.

## Fuente única de verdad

Cada módulo mantiene sus propios datos y la ficha municipal solo los relaciona mediante el identificador territorial estable `municipio` / `townSlug`.

- Rutas → Rutas / Explorar.
- Empresas → Empresas.
- Almazaras → Almazaras / Cooperativas.
- Noticias → Noticias.
- Eventos → Eventos.
- Ayuntamiento → Ayuntamientos.
- Tiempo → Tiempo / Radar.

## Contrato de navegación

Los enlaces desde la ficha municipal añaden `?municipio=<slug>` a los módulos. Los módulos pueden adoptar ese filtro progresivamente sin romper la navegación existente.

## URLs públicas

- `/pueblos` — directorio.
- `/pueblos?slug=<slug>` — detalle compatible actual.
- objetivo posterior: `/pueblos/<slug>` con compatibilidad/redirección desde la variante query.

## Mis pueblos

`feat/v20-my-towns` añadirá favoritos del usuario sin modificar la fuente territorial. Un usuario podrá seguir varios municipios y recibir vistas personalizadas.
