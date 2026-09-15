# Mágina Olivo V20 — UI/UX Premium

## Objetivo

Esta rama es el frente transversal de interfaz y experiencia de usuario de Mágina Olivo V20.

Base de trabajo: `integrate/v20-beta-closure`.

No sustituye las ramas funcionales ni introduce lógica de dominio nueva. Su función es convertir el producto integrado en una experiencia visual coherente, reconocible, rápida y premium en móvil, tablet y escritorio.

## Regla principal

**Primero coherencia; después espectacularidad.**

Cada módulo debe sentirse parte de la misma aplicación aunque tenga una personalidad propia. La UI no debe inventar datos, estados, recomendaciones ni métricas para mejorar una pantalla.

## Baseline existente

V20 ya dispone de una base visual transversal integrada:

- `visual-final.css`;
- `visual-mi-campo.css`;
- `visual-farm-detail.css`;
- `visual-today.css`;
- `visual-business.css`;
- `visual-business-mobile.css`;
- `visual-map-radar.css`;
- `responsive-beta.css`;
- `visual-premium-pass.css`;
- iconografía premium;
- navegación responsive;
- accesibilidad y `prefers-reduced-motion`.

Este frente debe evolucionar esa base. **No se añadirá una nueva cascada global de parches sin revisar primero si la regla pertenece al sistema visual común, a un componente o a una superficie concreta.**

## Dirección visual

La identidad de V20 debe combinar:

- Sierra Mágina como territorio protagonista;
- olivar, piedra, montaña, luz natural y paisaje;
- tonos oliva, verde profundo, arena, papel y acentos dorados;
- fotografía real/editorial cuando exista una fuente válida;
- iconografía clara y consistente;
- superficies limpias y legibles;
- profundidad y movimiento contenidos, nunca decorativos por sí mismos;
- lenguaje contemporáneo sin perder carácter rural/territorial.

## Sistema UI transversal

Antes de rediseñar módulos individualmente, consolidar:

1. tokens de color, superficie, sombra, radio, espaciado y tipografía;
2. tamaños y jerarquía de títulos;
3. botones primario/secundario/terciario/destructivo;
4. cards, paneles, métricas y chips;
5. formularios, inputs, selects, fechas y validaciones;
6. estados `loading`, `empty`, `error`, `stale`, `offline` y `sin sesión`;
7. barras, tabs, filtros, buscadores y navegación contextual;
8. tablas/listados Admin y vistas densas de escritorio;
9. modal, sheet móvil, toast y confirmaciones;
10. skeletons y transiciones;
11. iconografía;
12. mapas, overlays y paneles geográficos;
13. accesibilidad, contraste, foco y reduced motion.

## Prioridad de producto

### P0 — Shell y navegación

- Inicio;
- navegación principal móvil;
- navegación escritorio;
- topbar y cabeceras;
- buscador/accesos rápidos;
- consistencia de ancho, gutters y safe areas;
- estados globales y feedback.

### P1 — Descubrir Sierra Mágina

Unificar visualmente:

- Explorar;
- Pueblos de Mágina;
- ficha de pueblo;
- Mis pueblos;
- Rutas/Senderismo;
- Mágina Aventura;
- Eventos;
- Noticias;
- Empresas/Servicios;
- Almazaras/Cooperativas;
- Experiencias;
- Mágina Pass.

Objetivo: que el usuario perciba un único ecosistema territorial, no un conjunto de micrositios.

### P1 — Ecosistema personal

Unificar:

- Mi Olivo;
- progreso, niveles, logros y colecciones;
- recompensas;
- historial;
- QR/canje;
- Mi Campo;
- fincas;
- campañas;
- Hoy/Agenda;
- Perfil.

Mi Olivo puede tener una personalidad emocional y coleccionable propia, pero debe heredar tipografía, navegación, accesibilidad y patrones comunes.

### P2 — Herramientas técnicas

- Radar;
- mapas;
- GIS/Catastro/SIGPAC;
- clima;
- documentos;
- herramientas rápidas;
- Profesional.

La prioridad aquí es claridad operativa y lectura rápida por encima de efectos visuales.

### P2 — Administración

- dashboard;
- CMS;
- territorio;
- pueblos;
- rutas;
- aventuras;
- empresas;
- almazaras;
- experiencias;
- Mágina Pass;
- Mi Olivo/recompensas;
- fuentes;
- analítica;
- usuarios y operaciones.

Admin debe compartir identidad con la web, pero con mayor densidad, jerarquía y eficiencia para escritorio.

## Responsive

Móvil sigue siendo la experiencia prioritaria.

Matriz mínima obligatoria:

- 360 px;
- 390 px;
- 430 px;
- 768 px;
- 1024 px;
- 1280 px;
- 1440 px;
- 1920 px.

No se considerará una pantalla terminada únicamente porque escale: escritorio debe tener composición propia cuando el espacio lo permita.

## Principios de interacción

- objetivo táctil mínimo de 44 px para acciones principales;
- navegación alcanzable con una mano en móvil;
- no ocultar acciones críticas solo en hover;
- jerarquía clara de CTA;
- evitar carruseles obligatorios para información esencial;
- transiciones cortas y con sentido;
- feedback inmediato al guardar, canjear, registrar o completar;
- `prefers-reduced-motion` siempre respetado;
- formularios largos divididos por bloques lógicos;
- ninguna animación debe bloquear una acción.

## Regla para mapas y territorio

Mapas, GPX, checkpoints, fincas y radar deben usar un lenguaje de controles común. Los paneles sobre mapa tienen que seguir siendo utilizables en 360–430 px y no cubrir permanentemente el área geográfica útil.

## Regla para gamificación

Mi Olivo, Aventura, XP, insignias, aceitunas, colecciones y recompensas pueden usar más color, ilustración y progresión visual, pero:

- no deben parecer criptomoneda real;
- no deben mostrar valor monetario ficticio;
- no deben fomentar mecánicas confusas de inversión;
- el progreso debe explicarse con claridad;
- las recompensas físicas deben diferenciar saldo/progreso, reserva y canje.

## QA visual

Cada bloque importante debe comprobar:

- coherencia con tokens y componentes;
- no overflow horizontal;
- foco visible;
- contraste;
- keyboard navigation;
- targets táctiles;
- reduced motion;
- loading/empty/error;
- estado sin autenticación cuando aplique;
- responsive en la matriz acordada;
- capturas comparables móvil/escritorio para revisión visual.

## Gates antes de integrar

Como mínimo, según el alcance del cambio:

- TypeScript/typecheck;
- build de producción;
- responsive E2E;
- accessibility smoke;
- Browser E2E si se toca navegación o una superficie funcional;
- Full Candidate;
- workflows específicos del módulo afectado.

No integrar un rediseño si rompe un flujo funcional ya verde.

## Integración

Flujo esperado:

`rama funcional` → `integrate/v20-beta-closure` → `feat/v20-ui-ux-premium` → QA visual/funcional → absorción controlada en candidate.

La rama visual se resincronizará periódicamente con el candidate para incorporar módulos nuevos sin reescribirlos ni duplicar lógica.

`main` permanece fuera de este frente.

## Primera fase

1. inventario de superficies actuales del candidate;
2. inventario de patrones duplicados y deuda CSS;
3. consolidación de tokens y componentes comunes;
4. revisión del shell/navegación;
5. pasada visual de Inicio + Explorar;
6. bloque territorial Pueblos/Rutas/Aventura;
7. bloque personal Mi Olivo/Mi Campo;
8. Almazaras/Empresas/Experiencias/Mágina Pass;
9. Perfil y Admin;
10. QA transversal y comparación visual final.
