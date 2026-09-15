# Mágina Olivo V20 — Product Priorities

> Rama: `feat/v20-ui-ux-premium`
>
> Este documento fija la jerarquía oficial de producto desde el 15-09-2026. Si existe conflicto de prioridad con documentos anteriores, prevalece esta jerarquía.

## 1. Los tres pilares de V20

### P0-A — Mágina Aventura / Mi Aventura — PROTAGONISTA PRINCIPAL

Es el gran gancho de adquisición y uso recurrente de Mágina Olivo.

Inspiración funcional: exploración geolocalizada tipo juego de mundo real, con mapa vivo, GPS, descubrimientos, puntos cercanos, colecciones, progreso y recompensas. Debe tener identidad propia de Sierra Mágina y no copiar interfaz, personajes, marcas ni recursos visuales de terceros.

Debe priorizar:
- mapa vivo como pantalla principal durante la exploración;
- posición GPS del usuario;
- puntos de interés y descubrimientos alrededor;
- rutas y aventuras geolocalizadas;
- checkpoints por proximidad;
- retos contextuales;
- colecciones territoriales;
- XP, niveles, km y porcentaje explorado;
- insignias;
- actividad grabada;
- seguridad y avisos por encima de la gamificación;
- funcionamiento offline razonable;
- final de aventura espectacular;
- integración directa con Mi Olivo.

La sensación buscada es: abrir la app y tener ganas de salir a descubrir Sierra Mágina.

### P0-B — Mi Olivo — SEGUNDO GRAN PROTAGONISTA

Es el vínculo emocional, visual y de fidelización.

Debe priorizar:
- olivo casi real y vivo;
- evolución visual por nivel;
- XP y aceitunas;
- memoria de actividad;
- colecciones e insignias;
- conexión con lo descubierto en Mágina Aventura;
- recompensas reales de AOVE;
- reserva y QR de canje;
- escenas especiales de subida de nivel;
- versión premium móvil y escritorio.

Mágina Aventura genera actividad; Mi Olivo la transforma en progreso, apego y recompensa.

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
Aporta fotos, reseñas, actividad y contenido social moderado a Aventura y territorio.

### Noticias / Agenda
Aporta contexto vivo, eventos y avisos a Inicio, Pueblos y Mi Campo.

### Empresas / Experiencias
Aportan servicios, actividades y posibles recompensas/retos territoriales.

## 3. Nueva jerarquía de Inicio

Inicio no debe presentar ocho módulos con el mismo peso.

Orden visual recomendado:
1. Mágina Aventura / continuar exploración.
2. Mi Olivo / progreso actual.
3. Mi Campo para usuarios agricultores.
4. Hoy en Mágina: tiempo, avisos y actividad.
5. Contenido secundario: pueblos, almazaras, noticias, comunidad, experiencias.

Para un visitante nuevo, el CTA principal debe conducir a explorar Mágina Aventura.

Para un usuario registrado, Inicio debe priorizar la continuidad: aventura pendiente, progreso de Mi Olivo y, si aplica, situación de Mi Campo.

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
3. Mágina Aventura — mapa/exploración libre.
4. Aventura en curso — GPS/checkpoints.
5. Descubrimiento cercano / checkpoint.
6. Reto geolocalizado.
7. Final de aventura.
8. Mi Aventura — progreso, mapa explorado, colecciones.
9. Mi Olivo Premium — móvil.
10. Mi Olivo Premium — escritorio.
11. Evolución y subida de nivel del olivo.
12. Recompensas AOVE → reserva → QR → canje.
13. Mi Campo — dashboard.
14. Mi Campo — finca/mapa.
15. Mi Campo — registro rápido/cuaderno.
16. Mi Campo — campaña/producción.
17. Inicio V20 rediseñado alrededor de los tres pilares.
18. Módulos secundarios.
19. Admin y estados restantes.

## 6. Bucle principal del producto

El bucle prioritario de V20 será:

`Abrir app → ver mapa/aventura → salir al territorio → descubrir/checkpoint → ganar XP/aceitunas/colección → evolucionar Mi Olivo → desbloquear recompensa → canjear AOVE mediante QR → volver a explorar`

Para agricultor se añade un segundo bucle:

`Registrar actividad real en Mi Campo → conservar trazabilidad → alimentar memoria de Mi Olivo → recibir contexto/avisos → volver a gestionar la explotación`

## 7. Criterio de desarrollo

No se debe dedicar una fase grande de desarrollo a módulos secundarios mientras no estén cerrados de extremo a extremo:

- Mágina Aventura / Mi Aventura;
- Mi Olivo;
- Mi Campo núcleo.

El primer vertical territorial debe completarse en Bedmar antes de extender masivamente el contenido a toda Sierra Mágina.

## 8. Identidad frente a referencias externas

Se puede estudiar la mecánica de productos de exploración geolocalizada para entender patrones de UX: mapa vivo, proximidad, descubrimiento, colección, progresión y retorno.

Mágina Olivo debe usar:
- identidad visual propia;
- iconografía propia;
- lenguaje propio;
- colecciones propias de Sierra Mágina;
- rutas, patrimonio, flora, fauna, olivar y tradiciones reales;
- recompensas AOVE propias.

No se copiarán personajes, criaturas, nombres, interfaz, mapa estilizado, sonidos, arte ni otros activos protegidos de terceros.
