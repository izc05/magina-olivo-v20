# Mágina Olivo Web — Asset Manifest v1

Estado: **ACTIVE**

Este documento define los archivos visuales finales que deben sustituir a los proxies actuales sin modificar el diseño ni el layout.

## Regla

Las rutas son estables. Cuando exista una imagen final:

1. se exporta a WebP/AVIF;
2. se guarda en la ruta indicada;
3. se cambia `status` de `placeholder` a `final` en `src/lib/visualAssets.ts`;
4. se valida desktop + móvil;
5. no se modifica la composición para “hacer encajar” una imagen distinta.

## Assets

| ID | Ruta final | Contenido |
|---|---|---|
| hero | `/media/home/hero-farmer.webp` | Agricultor con móvil + olivar + Sierra Mágina |
| territoryIntro | `/media/home/territory-intro.webp` | Panorama limpio de territorio |
| heritage | `/media/home/heritage-farmer.webp` | Agricultor caminando / trabajo real |
| fieldSequence | `/media/home/sequence-field.webp` | Base de película campo → móvil |
| phoneContext | `/media/home/phone-in-hand.webp` | Mano y teléfono en contexto |
| benefits | `/media/home/benefits-olives.webp` | Rama con aceitunas / sostenibilidad |
| territoryFinal | `/media/home/territory-final.webp` | Panorama final Sierra Mágina |
| ctaFinal | `/media/home/cta-olive-branches.webp` | Ramas / cierre oscuro |

## Secuencia cinematográfica

La escena `fieldSequence` puede evolucionar después a un conjunto de fotogramas:

`/media/home/film/f01.webp` … `f24.webp`

Pero no se cargarán 24 imágenes pesadas de entrada. La implementación final debe:

- precargar solo vecinos;
- usar responsive sizes;
- mantener fallback estable;
- limitar peso;
- conservar versión reducida de movimiento.

## Referencia visual aprobada

La landing vertical aprobada el 20-09-2026 es la referencia maestra para:

- proporciones generales;
- orden de bloques;
- tipo de fotografía;
- ondas orgánicas;
- decoración con hojas;
- tratamiento del agricultor;
- teléfonos y pantallas;
- balance crema/verde.

## Estado actual

Todos los assets continúan en `placeholder` hasta incorporar las imágenes finales.
