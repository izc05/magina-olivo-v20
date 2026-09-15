# Mágina Olivo V20 — Product Priorities

> Rama: `feat/v20-ui-ux-premium`
>
> Este documento fija la jerarquía oficial de producto desde el 15-09-2026. Si existe conflicto de prioridad con documentos anteriores, prevalece esta jerarquía.

## 1. Los tres pilares de V20

### P0-A — Mágina Aventura / Mi Aventura — PROTAGONISTA PRINCIPAL

Es el gran gancho de adquisición y uso recurrente de Mágina Olivo.

**Definición oficial:** Mágina Aventura es un producto de **senderismo gamificado**. No es un modo de exploración libre por cualquier punto del territorio. La inspiración tipo juego geolocalizado se limita a la experiencia de GPS, proximidad, descubrimientos, checkpoints, colecciones, progreso y recompensas, siempre sobre rutas de senderismo y aventuras asociadas a esas rutas.

Debe priorizar:
- catálogo de rutas reales;
- ficha de sendero con distancia, desnivel, dificultad, duración, GPX, mapa, seguridad y meteorología;
- preparación antes de iniciar la ruta;
- mapa GPS como protagonista durante la marcha;
- posición del usuario y track recorrido/pendiente;
- checkpoints por proximidad dentro del recorrido;
- descubrimientos de flora, fauna, patrimonio, olivar, tradiciones y paisaje;
- retos contextuales ligados a puntos de la ruta;
- XP, niveles, kilómetros y porcentaje explorado;
- insignias y colecciones;
- grabación de actividad;
- seguridad y avisos por encima de la gamificación;
- funcionamiento offline razonable;
- final de ruta/aventura espectacular;
- integración directa con Mi Olivo.

La sensación buscada es: **elegir una ruta, salir a caminar por Sierra Mágina y convertir el senderismo real en una aventura de descubrimiento y progreso**.

### P0-B — Mi Olivo — SEGUNDO GRAN PROTAGONISTA

Es el vínculo emocional, visual y de fidelización.

Debe priorizar:
- olivo casi real y vivo;
- evolución visual por nivel;
- XP y aceitunas;
- memoria de actividad;
- colecciones e insignias;
- conexión con lo descubierto durante las rutas de Mágina Aventura;
- recompensas reales de AOVE;
- reserva y QR de canje;
- escenas especiales de subida de nivel;
- versión premium móvil y escritorio.

Mágina Aventura genera actividad mediante senderismo real; Mi Olivo la transforma en progreso, apego y recompensa.

### P0-C — Mi Campo — TERCER PILAR ESTRATÉGICO

Es el producto útil y profesional para agricultor.

Debe priorizar:
- fincas y parcelas;
- campañas;
- cuaderno de campo;
- riegos;
- tratamientos;
- abonado;
- poda;
- recolección;
- producción y entregas;
- rendimiento;
- mapas;
- meteorología y alertas;
- histórico y trazabilidad;
- conexión de hechos reales con la memoria de Mi Olivo.

Visualmente debe ser más técnico, rápido y operativo que Aventura y Mi Olivo, pero mantener la identidad de Mágina Olivo.

## 2. Módulos secundarios

Los siguientes módulos siguen siendo importantes, pero dejan de competir por protagonismo en Inicio o navegación principal:

- Pueblos
- Almazaras
- Comunidad
- Noticias y Agenda
- Empresas
- Experiencias
- Turismo / patrimonio / naturaleza

Su misión principal es alimentar a los tres pilares.

### Pueblos
Aporta territorio, lugares, patrimonio, rutas, eventos y progreso municipal a Mágina Aventura.

### Almazaras
Aporta AOVE, experiencias y recompensas reales a Mi Olivo.

### Comunidad
Aporta fotos, reseñas, actividad y contenido social moderado a las rutas y al territorio.

### Noticias / Agenda
Aporta contexto vivo, eventos y avisos a Inicio, Pueblos y Mi Campo.

### Empresas / Experiencias
Aportan servicios, actividades y posibles recompensas o contenidos asociados a rutas y territorio.

## 3. Nueva jerarquía de Inicio

Inicio no debe presentar ocho módulos con el mismo peso.

Orden visual recomendado:
1. Mágina Aventura / elegir o continuar una ruta.
2. Mi Olivo / progreso actual.
3. Mi Campo para usuarios agricultores.
4. Hoy en Mágina: tiempo, avisos y estado de rutas.
5. Contenido secundario: pueblos, almazaras, noticias, comunidad y experiencias.

Para un visitante nuevo, el CTA principal debe conducir a **descubrir rutas de senderismo**.

Para un usuario registrado, Inicio debe priorizar la continuidad: ruta guardada o en curso, progreso de Mi Olivo y, si aplica, situación de Mi Campo.

## 4. Nueva jerarquía móvil

Navegación inferior objetivo:

`Inicio · Explorar · Aventura · Mi Olivo · Perfil`

`Aventura` ocupa la posición central y debe ser el acceso más visible.

Mi Campo aparece con protagonismo contextual para agricultores desde Inicio, Perfil y accesos rápidos; no se fuerza al visitante general en la barra inferior.

`Explorar` agrupa Pueblos, Almazaras, Noticias, Experiencias y contenido territorial secundario.

## 5. Nueva prioridad de Figma

Orden de producción visual:

1. Sistema base y navegación.
2. Mágina Aventura — Home móvil.
3. Catálogo de rutas.
4. Ficha de ruta.
5. Preparación antes de salir.
6. Aventura en curso — GPS/track/checkpoints.
7. Descubrimiento / checkpoint.
8. Reto ligado a la ruta.
9. Final de aventura.
10. Mi Aventura — progreso, kilómetros, rutas, colecciones.
11. Mi Olivo Premium — móvil.
12. Mi Olivo Premium — escritorio.
13. Evolución y subida de nivel del olivo.
14. Recompensas AOVE → reserva → QR → canje.
15. Mi Campo — dashboard.
16. Mi Campo — finca/mapa.
17. Mi Campo — registro rápido/cuaderno.
18. Mi Campo — campaña/producción.
19. Inicio V20 rediseñado alrededor de los tres pilares.
20. Módulos secundarios.
21. Admin y estados restantes.

## 6. Bucle principal del producto

El bucle prioritario de V20 será:

`Elegir ruta → revisar ficha y seguridad → descargar/preparar → iniciar senderismo → seguir GPS/track → descubrir checkpoints y retos → terminar ruta → ganar XP/aceitunas/colección → evolucionar Mi Olivo → desbloquear recompensa → canjear AOVE mediante QR → volver a elegir otra ruta`

Para agricultor se añade un segundo bucle:

`Registrar actividad real en Mi Campo → conservar trazabilidad → alimentar memoria de Mi Olivo → recibir contexto/avisos → volver a gestionar la explotación`

## 7. Criterio de desarrollo

No se debe dedicar una fase grande de desarrollo a módulos secundarios mientras no estén cerrados de extremo a extremo:

- Mágina Aventura / Mi Aventura como senderismo gamificado;
- Mi Olivo;
- Mi Campo núcleo.

El primer vertical territorial debe completarse en Bedmar antes de extender masivamente el contenido a toda Sierra Mágina.

## 8. Identidad frente a referencias externas

Se pueden estudiar productos de exploración geolocalizada para entender patrones de UX como proximidad, descubrimiento, colección, progresión y retorno, pero la experiencia de Mágina Aventura estará centrada en **senderismo real y rutas verificadas**.

Mágina Olivo debe usar:
- identidad visual propia;
- iconografía propia;
- lenguaje propio;
- colecciones propias de Sierra Mágina;
- rutas, patrimonio, flora, fauna, olivar y tradiciones reales;
- recompensas AOVE propias.

No se copiarán personajes, criaturas, nombres, interfaz, mapa estilizado, sonidos, arte ni otros activos protegidos de terceros.
