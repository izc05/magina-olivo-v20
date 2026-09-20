# Mágina Olivo Web — Motion & Scroll Spec v1

Estado: **IMPLEMENTATION BASELINE**

## Objetivo

Convertir el diseño promocional estático en una experiencia de scroll cinematográfico sin sacrificar rendimiento, accesibilidad ni claridad.

## Ritmo

La Home se divide en tres velocidades:

- **Lenta:** hero, campo→móvil, cierre territorial.
- **Media:** explicación de funciones.
- **Rápida:** beneficios, overview y navegación convencional.

## Timeline principal

### T0 · Hero · 0–100svh
- paisaje estable;
- texto entra sin desplazamientos agresivos;
- CTA visible;
- invitación a deslizar.

### T1 · Contexto · 100–210svh
- presentación breve del problema;
- tres fotogramas visuales: olivar / observar / decidir.

### T2 · Película campo→móvil · ~430vh
El progreso de scroll se normaliza entre 0 y 1.

#### 0.00–0.18
Paisaje + agricultor. Texto: “El campo primero.”

#### 0.18–0.38
Zoom gradual hacia la persona.

#### 0.30–0.62
Aparecen mano y teléfono desde la zona inferior.

#### 0.55–0.82
El teléfono gira progresivamente hasta quedar frontal.

#### 0.72–0.91
La interfaz se hace legible.

#### 0.82–1.00
El fondo pasa a crema y el producto toma el control.

### T3 · Funciones
Seis escenas de 74–92vh cada una:
- bienvenida;
- fincas;
- catastro;
- campaña;
- cosecha;
- tiempo/mercado.

Teléfono sticky durante toda la secuencia.

### T4 · Overview
Cinco dispositivos simultáneos en escritorio.  
Carrusel horizontal con snap en móvil.

### T5 · Beneficios
Movimiento mínimo. El usuario debe descansar visualmente.

### T6 · Territorio
Imagen panorámica con parallax muy suave y gran titular.

### T7 · CTA
Cierre estable. Sin animaciones que distraigan del botón.

## Curvas de movimiento

Preferencias:
- entradas: ease-out;
- salidas: ease-in;
- scroll-follow: interpolación lineal con suavizado visual por capas;
- no usar rebotes;
- no usar rotaciones mayores de 10–12° en el teléfono.

## Fotogramas

Las imágenes finales reemplazarán progresivamente los proxies actuales.

Formato:
- AVIF preferente;
- WebP fallback si es necesario;
- tamaños responsive;
- hero con prioridad;
- resto lazy.

No cargar una secuencia completa de decenas de imágenes a resolución máxima al inicio.

## Accesibilidad

Con `prefers-reduced-motion: reduce`:
- eliminar timeline;
- mostrar teléfono frontal;
- mostrar copy final;
- conservar toda la información;
- navegación normal.

## Rendimiento

Objetivos antes de producción:
- evitar JS de animación pesado si CSS + RAF son suficientes;
- cero listeners de scroll sin `passive`;
- trabajo de scroll dentro de `requestAnimationFrame`;
- limitar blur y filtros en móvil;
- imágenes dimensionadas;
- no usar vídeo 4K como fondo de toda la Home.

## Estado

- [x] IntersectionObserver para funciones.
- [x] RAF para película campo→móvil.
- [x] teléfono sticky.
- [x] fases de progreso.
- [x] reducción de movimiento.
- [x] overview responsive.
- [ ] assets fotográficos finales.
- [ ] prueba 60 fps Android.
- [ ] ajuste final de curvas.
- [ ] auditoría Lighthouse.
