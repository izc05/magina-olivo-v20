# Mágina Olivo Web V2 — Roadmap de ejecución

Estado: **ACTIVE EXECUTION PLAN**  
Fuente de verdad: `WEB-V2-CINEMATIC-MASTER-SPEC.md`

## Gate 0 — Dirección V2

Estado: **CERRADO**

Entregables:
- enfoque product-first;
- eliminación del posicionamiento territorial de la Home;
- narrativa cinematográfica definida;
- regla visual-first;
- stack actual conservado;
- GitHub Pages como preview.

Criterio de salida:
- concepto aprobado y documentado.

---

## Gate 1 — Brand & Art Direction

Objetivo: elevar identidad antes de producir cientos de frames.

Tareas:
- revisar logo;
- construir variantes de logo;
- cerrar paleta/tipografía;
- definir tratamiento fotográfico;
- definir modelo de agricultor, vestuario y continuidad;
- definir teléfono/dispositivo;
- diseñar poster hero desktop y mobile;
- crear moodboard final.

Entregables:
- logo V2;
- mini brand sheet;
- hero keyframe desktop;
- hero keyframe mobile;
- guía fotográfica.

Criterio de salida:
- una única imagen del hero ya debe transmitir la calidad objetivo.

No pasar al Gate 2 con un hero mediocre.

---

## Gate 2 — Storyboard & Shot List

Objetivo: convertir la idea en una película dibujada antes de generar frames.

Tareas:
- storyboard 16–24 keyframes;
- encuadres desktop;
- encuadres mobile;
- continuidad de agricultor/ropa/olivar/luz;
- posiciones del móvil;
- momentos exactos de copy;
- duración de scroll por escena;
- mapa de transición campo → móvil → app.

Entregables:
- contact sheet;
- timeline;
- shot list;
- copy final de cada escena.

Criterio de salida:
- al pasar los keyframes manualmente debe entenderse la historia sin explicación adicional.

---

## Gate 3 — Sequence Engine Proof of Concept

Objetivo: demostrar el efecto de “vídeo controlado por scroll” antes de producir la secuencia final.

Tareas:
- componente canvas;
- cálculo scroll → frame;
- preload window;
- cache;
- poster;
- resize;
- DPR;
- fallback;
- reduced motion;
- secuencia de prueba 24–40 frames.

Entregables:
- demo funcional en GitHub Pages;
- desktop;
- móvil;
- métricas básicas.

Criterio de salida:
- scroll continuo;
- sin parpadeos;
- sin saltos;
- sin bloquear interacción;
- sin overflow;
- memoria estable.

---

## Gate 4 — Hero Final Sequence

Objetivo: producir la primera secuencia premium real.

Secuencia:
- olivar;
- agricultor;
- caminar;
- inspeccionar;
- mano;
- móvil;
- primer plano del móvil.

Tareas:
- generar/renderizar frames definitivos;
- mantener continuidad absoluta;
- comprimir;
- crear desktop/mobile;
- integrar copy mínimo;
- afinar curvas.

Objetivo de volumen:
- desktop 120–180 frames;
- móvil 70–110 frames.

Criterio de salida:
- debe sentirse como vídeo al hacer scroll;
- agricultor reconocible y coherente en todos los frames;
- ausencia de saltos de luz/ropa/manos/olivos.

---

## Gate 5 — App Takeover

Objetivo: hacer que el móvil real se transforme en la aplicación.

Tareas:
- transición teléfono contextual → frontal;
- integración de UI real;
- escala hasta ocupar escena;
- sincronizar pantallas;
- evitar mockup falso;
- mantener legibilidad.

Criterio de salida:
- no debe percibirse un corte entre fotografía y producto.

---

## Gate 6 — Product Story

Objetivo: vender funciones sin grids extensos.

Escenas:
- Tus fincas.
- Tus parcelas.
- Tu mapa.
- Tu campaña.
- Tu cosecha.
- Tus gastos.
- Tu tiempo.
- Tu histórico.

Cada escena:
- 1 pantalla;
- 1 transición;
- 1 frase;
- 0–1 CTA.

Criterio de salida:
- cada función se entiende en 3–5 segundos visuales.

---

## Gate 7 — Return to Field & Final CTA

Objetivo: cerrar la historia en el mundo real.

Tareas:
- móvil vuelve a mano;
- agricultor continúa;
- transición de luz/campo;
- CTA final;
- Google Play “próximamente” mientras corresponda;
- logo final.

Criterio de salida:
- final emocional y sencillo;
- no introducir información nueva.

---

## Gate 8 — Secondary Pages V2

Objetivo: alinear el resto de la web sin replicar la complejidad de la Home.

Producto:
- narrativa del producto;
- pantallas reales;
- detalle funcional.

Cómo funciona:
- finca → parcela → campaña → actuación → cosecha.

Beneficios:
- orden;
- histórico;
- control;
- menos pérdida de información.

Contacto:
- simple.

Criterio de salida:
- misma identidad;
- carga rápida;
- sin intentar convertir cada página en otra película completa.

---

## Gate 9 — Quality & Performance

Pruebas:
- Playwright;
- capturas visuales;
- Lighthouse;
- Core Web Vitals;
- Android real;
- desktop;
- reduced motion;
- teclado;
- imágenes rotas;
- 404;
- Pages;
- Docker.

Presupuesto a vigilar:
- peso inicial;
- memoria de frames;
- fps;
- LCP;
- INP;
- CLS.

Criterio de salida:
- QA completamente verde;
- experiencia fluida en móvil medio.

---

## Gate 10 — Release Candidate

Entregables:
- GitHub Pages preview aprobada;
- staging Docker aprobado;
- SEO final;
- dominio final;
- analytics/consentimiento si se usa;
- textos legales;
- assets finales;
- documentación.

Criterio de salida:
- aprobación visual y funcional explícita.

---

# Orden obligatorio

`G0 → G1 → G2 → G3 → G4 → G5 → G6 → G7 → G8 → G9 → G10`

No producir 150 frames antes de cerrar branding, storyboard y motor de secuencia.

No rehacer infraestructura que ya está verde.

No añadir módulos territoriales a la Home comercial V2.

## Estado actual

- G0 ✅
- G1 ▶️ en progreso — hero desktop/mobile y logo V2 ya tienen candidatos funcionales
- G2 ▶️ en progreso — storyboard de 24 keyframes definido; faltan keyframes visuales críticos
- G3 ▶️ POC activo — motor canvas scroll→imagen funcionando con keyframes provisionales
- G4 ⏳ pendiente de secuencia fotográfica final
- G5 ▶️ prototipo activo — móvil/product story ya integrado visualmente
- G6 ▶️ prototipo activo — funciones principales en scrollytelling sticky
- G7 ▶️ primera versión — regreso al campo y CTA implementados
- G8 ⏳
- G9 ▶️ QA continuo con Playwright, desktop/mobile y reduced-motion
- G10 ⏳
