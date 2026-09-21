# Mágina Olivo Web V2 — G3 Sequence Engine Technical Spike

Estado: **TECHNICAL SPIKE COMPLETE · GATE NOT FORMALLY CLOSED**  
Fecha: 2026-09-21

> G3 no se marca como cerrado hasta completar las aprobaciones visuales pendientes de G1/G2.  
> El motor técnico, sin embargo, ya está probado y operativo.

## Implementado

### Canvas
- render fullscreen en `canvas`;
- `requestAnimationFrame`;
- scroll nativo → progreso 0..1;
- progreso → frame virtual;
- cover render con focal point;
- zoom/pan interpolado;
- crossfade únicamente cuando cambia la fuente visual.

### Responsive
- manifiesto desktop independiente;
- manifiesto mobile independiente;
- composición/focal points distintos;
- DPR limitado:
  - desktop: 1.75;
  - mobile: 1.5.

### Secuencia piloto
Desktop:
- 12 anchors;
- densificados a **34 frames virtuales**.

Mobile:
- 10 anchors;
- densificados a **28 frames virtuales**.

Fuentes visuales actuales:
1. agricultor;
2. detalle de aceitunas;
3. móvil en mano.

Estas fuentes son todavía material piloto. Los frames definitivos llegarán tras aprobar los keyframes de G1/G2.

## Carga

El motor no descarga 34 imágenes diferentes.

Los frames virtuales reutilizan las fuentes disponibles y modifican:
- focal X;
- focal Y;
- escala;
- blending.

Esto permite validar:
- sensación de progreso;
- scroll mapping;
- rendimiento;
- canvas;
- responsive;
sin producir todavía la secuencia final pesada.

## Product takeover

En el tramo final:
- se reduce visualmente el canvas fotográfico;
- aparece un dispositivo HTML nítido;
- el teléfono rota progresivamente a frontal;
- la escala llega a 1;
- se muestra `PhoneScreen(kind="welcome")`.

Objetivo:
probar la continuidad fotografía → producto antes de producir K09/K12 definitivos.

## Reduced motion

Con `prefers-reduced-motion: reduce`:
- la secuencia de canvas deja de depender del scroll animado;
- se utiliza poster estable;
- takeover permanece legible;
- contenido textual sigue en DOM.

## QA automatizado

Playwright valida:
- canvas preparado;
- mínimo 24 frames virtuales;
- el frame avanza al mover el scroll;
- takeover visible al 92 %;
- pantalla Welcome presente;
- desktop;
- mobile;
- reduced motion;
- ausencia de overflow horizontal;
- ausencia de errores runtime.

## CI

Validado:
- TypeScript ✅
- Next build ✅
- static Pages export ✅
- Docker build ✅
- Docker runtime ✅
- Playwright Chromium desktop ✅
- Playwright Chromium mobile ✅
- GitHub Pages deploy ✅

## Qué NO significa este spike

Todavía no tenemos:
- 120–180 fotografías finales;
- continuidad definitiva del agricultor;
- manos finales;
- teléfono fotográfico final;
- K09/K12 aprobados;
- secuencia visual final de producto.

Este spike demuestra que el motor soporta la experiencia que queremos construir.

## Siguiente

Volver a G1/G2 visual:
1. K01 hero definitivo;
2. K02 mobile hero;
3. K03 detalle aceituna;
4. K09 móvil en mano;
5. K12 teléfono frontal;
6. K13 UI Inicio;
7. K24 cierre.

Después:
- sustituir fuentes piloto por assets definitivos;
- mantener el motor;
- aumentar densidad de frames reales progresivamente.


## 4K video scrub enhancement

Se ha añadido una segunda capa experimental sobre el canvas:

- vídeo real 3840×2160;
- reproducción automática desactivada;
- el scroll controla `currentTime`;
- avance y retroceso según la dirección del scroll;
- `playsInline` y `muted`;
- preload limitado a metadata;
- canvas permanece debajo como fallback;
- `prefers-reduced-motion` desactiva el scrub;
- `Save-Data` desactiva el vídeo 4K y conserva fallback.

Fuente candidata de preview:
- Pexels video 20606525;
- agricultor trabajando/podando olivos;
- 3840×2160 · 24 fps;
- uso gratuito según la página fuente.

Este vídeo es un candidato de experiencia, no un asset final bloqueado. El objetivo es validar la sensación de “vídeo controlado con el dedo” antes de producir la película propia definitiva.

## Quality gate añadido

La V2 ya no acepta fullscreen final con thumbnails:

- hero >= 1600×900 en QA;
- feature story >= 1600×900;
- héroes de Producto/Beneficios/Territorio/Contacto >= 1600×900;
- fuente recomendada 2200–3200 px.

Los antiguos crops de 260–640 px quedan solo como fallbacks/pilotos.
