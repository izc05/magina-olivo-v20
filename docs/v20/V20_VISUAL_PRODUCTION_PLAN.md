# Mágina Olivo V20 — Visual Production Plan

> Rama: `feat/v20-ui-ux-premium`
>
> Objetivo: convertir el masterplan de producto y UX en una secuencia concreta de producción visual para Figma y posterior implementación en código.

## 1. Regla de trabajo

El flujo oficial será:

`decisión de producto -> concepto visual -> Figma -> aprobación -> componentes -> implementación React -> QA visual -> candidate -> mini PC`

GitHub conserva la definición estable. Figma es la fuente visual de referencia. Codex implementa la versión aprobada. El mini PC ejecuta únicamente versiones probadas.

## 2. Breakpoints de referencia

- Mobile: 390 px
- Tablet: 768 px
- Desktop: 1440 px

Mobile-first, pero escritorio tendrá composición propia y no será una versión estirada del móvil.

## 3. Prioridades

### P0 — imprescindible para V20.0

#### Sistema global
- Foundations: color, tipografía, spacing, radios, sombras, iconografía.
- Header / top navigation desktop.
- Bottom navigation mobile.
- Search overlay / page.
- Notifications panel.
- Loading, empty, error, offline y permission states.

#### Acceso y onboarding
- Login / registro.
- Bienvenida inicial.
- Selección de primer camino: explorar / Mi Olivo / tengo olivar.
- Permisos contextuales de ubicación y notificaciones.

#### Inicio V20
- Desktop 1440.
- Mobile 390.
- Hero territorial.
- Hoy en Mágina.
- Continúa donde lo dejaste.
- Aventura destacada.
- Resumen Mi Olivo.
- Pueblos / AOVE / comunidad / noticias.

#### Mágina Aventura
- Home desktop.
- Home mobile.
- Catálogo de rutas / aventuras.
- Ficha de ruta.
- Preparación antes de iniciar.
- Aventura en curso.
- Checkpoint.
- Reto / descubrimiento.
- Fin de aventura.
- Mi Aventura.
- Colecciones.

#### Mi Olivo
- Home desktop.
- Home mobile.
- OliveHero vivo / estado del árbol.
- Nivel / XP / aceitunas.
- Evolución del olivo.
- Cuidados y memoria.
- Colecciones.
- Insignias.
- Catálogo de recompensas.
- Detalle de recompensa.
- Reserva.
- QR de canje.
- QR usado / caducado / inválido.
- Historial de canjes.

#### Pueblos
- Hub / mapa.
- Ficha de pueblo.
- Progreso en el pueblo.
- Lugares / patrimonio.
- Mis Pueblos.

#### Almazaras
- Hub.
- Ficha de almazara.
- Ficha AOVE.
- Recompensas asociadas.

#### Mi Campo — núcleo
- Dashboard.
- Mis fincas.
- Ficha de finca.
- Campaña.
- Cuaderno rápido.
- Producción.
- Tiempo / alertas agrícolas.

#### Perfil
- Perfil propio.
- Actividad personal.
- Privacidad.
- Favoritos / pueblos seguidos / historial.

#### Admin mínimo V20.0
- Dashboard de atención.
- Rutas / aventuras.
- Mi Olivo: niveles / reglas.
- Recompensas / stock / reservas / QR.
- Contenido / noticias / agenda.
- Moderación comunidad.
- Usuarios / roles.

### P1 — importante tras cerrar P0

- Comunidad completa.
- Feed editorial.
- Perfil público.
- Reseñas con fotos.
- Galerías de pueblos y lugares.
- Experiencias de almazara.
- Panel de almazara.
- Panel de empresa / experiencias.
- Comparativas avanzadas en Mi Campo.
- Centro de actividad personal completo.
- Notificaciones configurables por categoría.
- Buscador global enriquecido.

### P2 — evolución posterior

- Árbol 3D real con Three.js.
- Efectos avanzados de viento y estación.
- Variaciones por hora del día.
- Offline avanzado con mapas completos.
- Personalización profunda del Inicio.
- Animaciones cinematográficas de subida de nivel.
- Recomendaciones inteligentes avanzadas.
- Experiencias AR / cámara.

## 4. Pantallas ancla

Estas pantallas fijan la calidad visual del resto del producto y deben diseñarse primero:

1. Inicio V20 — Desktop
2. Inicio V20 — Mobile
3. Mágina Aventura — Mobile
4. Aventura en curso — Mobile
5. Mágina Aventura — Desktop
6. Mi Olivo Premium — Mobile
7. Mi Olivo Premium — Desktop
8. Mi Campo — Desktop

Ningún módulo debe considerarse visualmente cerrado si no alcanza el nivel de estas pantallas ancla.

## 5. Componentes V20

### Globales
- `V20/TopNav`
- `V20/BottomNav`
- `V20/SectionHeader`
- `V20/Button`
- `V20/IconButton`
- `V20/Badge`
- `V20/StatCard`
- `V20/EmptyState`
- `V20/ErrorState`
- `V20/OfflineState`
- `V20/Modal`
- `V20/BottomSheet`
- `V20/Tabs`
- `V20/SearchResult`
- `V20/NotificationItem`

### Aventura
- `V20/RouteCard`
- `V20/AdventureCard`
- `V20/MapPanel`
- `V20/CheckpointCard`
- `V20/ChallengeCard`
- `V20/AdventureProgress`
- `V20/ElevationProfile`
- `V20/SafetyAlert`

### Mi Olivo
- `V20/OliveHero`
- `V20/LivingOliveScene`
- `V20/LevelCard`
- `V20/OliveBalance`
- `V20/EvolutionTimeline`
- `V20/CareMemoryCard`
- `V20/CollectionCard`
- `V20/BadgeCard`
- `V20/RewardCard`
- `V20/QRCard`

### Territorio
- `V20/TownCard`
- `V20/PlaceCard`
- `V20/AlmazaraCard`
- `V20/AOVECard`
- `V20/EventCard`
- `V20/NewsCard`

### Mi Campo
- `V20/FarmStat`
- `V20/FarmMap`
- `V20/FieldCard`
- `V20/CampaignCard`
- `V20/FarmActionRow`
- `V20/WeatherAgricultureCard`
- `V20/FarmAlert`

## 6. Estados obligatorios

Cada componente crítico debe definir los estados que realmente puedan ocurrir.

### Genéricos
- default
- hover / focus
- active / selected
- disabled
- loading
- empty
- error
- offline

### GPS / Aventura
- permiso no solicitado
- permiso denegado
- GPS débil
- GPS disponible
- fuera de ruta
- checkpoint próximo
- checkpoint completado
- actividad pausada
- sincronización pendiente

### Recompensas / QR
- disponible
- bajo stock
- agotada
- reservada
- QR activo
- QR usado
- QR caducado
- QR inválido
- reserva cancelada

### Mi Campo
- usuario sin finca
- finca sin campaña
- campaña activa
- campaña cerrada
- sin registros
- alerta meteorológica
- sincronización pendiente

## 7. Movimiento

Principio: el movimiento debe informar o reforzar la experiencia, no decorar por decorar.

### Mi Olivo
- ramas y hojas: movimiento suave.
- luz: variación mínima.
- partículas / hojas: uso muy puntual.
- parallax: profundidad.
- subida de nivel: transición especial.

Primera implementación recomendada: 2.5D con capas + CSS/GSAP. Three.js queda en P2.

### Mágina Aventura
- posición GPS con pulso.
- checkpoint con pulso al aproximarse.
- cards desde bottom sheet.
- XP / descubrimientos con microanimación.
- transiciones suaves de mapa a detalle.

### Inicio
- hero con parallax sutil.
- cards con hover mínimo.
- Mi Olivo con movimiento casi imperceptible.

## 8. Datos que debe conocer el diseño

Las pantallas se diseñarán con estructuras reales o representativas del backend; no con texto decorativo que luego no pueda existir.

### Mi Olivo
- nivel
- XP actual
- XP siguiente nivel
- saldo aceitunas
- etapa visual
- actividad reciente
- cuidados reales
- colección
- insignias
- recompensas / stock / reserva / QR

### Aventura
- route_id
- aventura_id
- GPX / track
- checkpoints
- progreso
- GPS state
- seguridad / avisos
- XP / recompensas
- actividad offline / sync

### Pueblos
- municipio
- lugares
- rutas
- almazaras
- eventos
- noticias
- progreso del usuario

### Mi Campo
- fincas
- campañas
- registros
- producción
- rendimiento
- tiempo oficial
- alertas calculadas

## 9. Plan de trabajo con un mes de Figma Professional

### Semana 1 — sistema y pantallas ancla
- Foundations.
- Components v1.
- Navegación.
- Inicio desktop / mobile.
- Mágina Aventura mobile.
- Aventura en curso.
- Mi Olivo Premium mobile.

### Semana 2 — mundos principales
- Mágina Aventura desktop.
- Ficha de ruta.
- flujo completo de aventura.
- Mi Olivo desktop.
- evolución / recompensas / QR.
- Pueblos.
- Almazaras.

### Semana 3 — funcional y operativo
- Mi Campo.
- Perfil.
- Search / notifications.
- Comunidad.
- Noticias / agenda.
- Admin mínimo.

### Semana 4 — cierre
- Tablet 768.
- todos los estados.
- prototipos interactivos.
- iconografía final.
- fotografía / assets finales.
- QA visual.
- documentación para Codex.
- comparación Figma vs implementación.

## 10. Definition of Done visual

Una pantalla no se considera terminada solo porque “se vea bonita”. Debe cumplir:

- Mobile 390 revisado.
- Tablet 768 revisado cuando aplique.
- Desktop 1440 revisado cuando aplique.
- estados críticos diseñados.
- componentes reutilizables usados cuando corresponda.
- tipografía / spacing / tokens coherentes.
- accesibilidad y contraste razonables.
- contenido sin placeholders ficticios para la versión final.
- comportamiento interactivo definido.
- datos necesarios identificados.
- comparación con implementación realizada antes de cerrar.

## 11. Orden de implementación en código

Tras aprobación visual:

1. tokens / shell / navegación
2. Inicio
3. Mágina Aventura
4. Mi Olivo
5. Pueblos
6. Almazaras
7. Mi Campo
8. Comunidad / Noticias
9. Perfil / Search / Notifications
10. Admin

Cada bloque sigue:

`Figma aprobado -> rama -> Codex -> local -> screenshot -> comparación -> correcciones -> tests -> candidate`

No se integra un bloque visual que todavía dependa de placeholders críticos o que no pase QA responsive.
