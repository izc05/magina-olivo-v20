# Mágina Olivo Web V2 — Asset Quality Gate

Estado: **ACTIVE / CANONICAL**  
Fecha: 2026-09-21

## Problema detectado

Los assets provisionales usados durante el prototipo eran demasiado pequeños para una experiencia fullscreen:

- hero limpio local: 640×508;
- agricultor V1: 400×180;
- detalle de aceitunas: 389×180;
- móvil en mano: 260×260;
- hero V1: 384×350;
- paisaje: 449×155.

Esos tamaños son válidos como referencia o thumbnail, pero no como imagen final a pantalla completa.

## Regla V2

Ningún asset fullscreen puede marcarse como final si no cumple:

### Desktop
- ancho recomendado: 2200–3200 px;
- mínimo CI: 1600 px;
- alto mínimo CI: 900 px.

### Mobile
- composición vertical específica cuando el plano lo requiera;
- ancho recomendado: 1200–1800 px;
- no reutilizar thumbnails ni crops de menos de 1000 px.

## Prioridad de calidad

1. Hero.
2. Agricultor/campo.
3. Detalle de aceitunas.
4. Planos de transición.
5. Cierre.
6. UI/contexto secundario.

## Producción

Para preview V2 se permiten candidatos HQ remotos con licencia compatible.

Para release:
- preferencia por assets propios/autogenerados aprobados;
- self-host dentro de `web/public/media/v2/`;
- WebP/AVIF;
- desktop/mobile cuando proceda;
- sin texto incrustado.

## QA

Playwright debe fallar si:
- hero fullscreen < 1600×900;
- escena fullscreen principal < 1600×900;
- una imagen no carga;
- aparece fallback por error.

## Rendimiento

La calidad no implica descargar originales de 10–20 MB.

Objetivo:
- fuente 2000–2800 px;
- compresión visual alta;
- WebP/AVIF en versión final;
- poster inicial optimizado;
- precarga solo de frames cercanos.

## Fuentes HQ candidatas actuales

### Agricultor / olivar
Pexels photo 5035605 · Andrea Piacquadio.  
Uso: hero y continuidad de campo en preview.

### Detalle de cosecha
Pexels photo 31694875 · Betül Üstün.  
Uso: plano detalle de aceitunas/cosecha en preview.

Estas fotografías son candidatas de preview, no bloquean la futura producción visual propia K01–K24.
