# Mágina Olivo V20 — Modo Aventura de Rutas

## Propósito

El Modo Aventura convierte una ruta real y validada en una experiencia de exploración territorial. La capa lúdica se apoya en el track y en puntos geográficos reales, pero nunca sustituye la navegación técnica, la señalización sobre el terreno, las restricciones oficiales ni la evaluación personal de seguridad.

## Contrato de confianza

- Solo puede existir sobre una ruta publicada con track validado.
- El progreso del juego no se guarda en `route_completions`.
- Completar el juego no certifica una actividad deportiva ni el estado del sendero.
- Los avisos oficiales, restricciones y datos técnicos conservan prioridad visual y semántica.
- La respuesta correcta de un reto nunca sale en el payload público.
- El desbloqueo geográfico se valida en servidor con PostGIS.
- La V1 no realiza seguimiento GPS continuo: solicita posición al pulsar `Estoy aquí`.

## V1 implementada

### Partida
- una partida activa por usuario y ruta;
- estados `active`, `completed`, `abandoned`;
- puntuación persistente;
- progreso recuperable al volver a abrir la ruta.

### Checkpoints
Tipos disponibles:

- `landmark`: lugar o hito del territorio;
- `trivia`: pregunta contextual;
- `observation`: reto de observación;
- `photo`: punto preparado para reto fotográfico;
- `collection`: coleccionable territorial;
- `rest`: parada o pausa narrativa.

Cada checkpoint puede definir:

- posición real;
- distancia aproximada sobre la ruta;
- radio de desbloqueo;
- puntos;
- obligatorio u opcional;
- texto, pista y pregunta;
- opciones de respuesta.

### Desbloqueo

1. El usuario inicia la aventura autenticado.
2. En un checkpoint pulsa `Estoy aquí`.
3. El navegador solicita una posición puntual.
4. La API calcula la distancia al checkpoint con PostGIS.
5. Si está dentro del radio, valida el reto cuando exista.
6. El desbloqueo y la puntuación se guardan una sola vez.

### Progreso e insignias

La primera versión expone tres insignias derivadas del progreso:

- `primer_paso`;
- `explorador_magina`;
- `ruta_100`.

Son insignias lúdicas, no certificados.

## Experiencia objetivo

La ruta debe sentirse como una pequeña aventura sobre el territorio real:

1. **Inicio** — presentación del recorrido y de la misión.
2. **Etapas** — checkpoints colocados en lugares con sentido.
3. **Descubrimiento** — patrimonio, paisaje, olivar, agua, flora, fauna o cultura local.
4. **Retos** — observar, responder, localizar o recopilar.
5. **Colección** — recuerdos virtuales de la ruta.
6. **Cierre** — mensaje final, puntuación e insignias.

## Evolución compatible con la V1

### Álbum territorial

Los checkpoints `collection` pueden evolucionar a un álbum de:

- árboles y plantas;
- aves y fauna;
- castillos, torres y patrimonio;
- fuentes y elementos hidráulicos;
- cultura del olivar;
- paisajes y miradores;
- leyendas e historias locales.

### Retos fotográficos

Los checkpoints `photo` podrán reutilizar la infraestructura de medios y moderación ya existente en Rutas. La validación automática por IA no debe asumirse como verdad; la primera versión debería basarse en subida, moderación y reglas explícitas.

### Narrativa por capítulos

Una aventura podrá agrupar checkpoints en capítulos o misiones sin modificar el track técnico. Ejemplo:

- Capítulo 1 — El agua;
- Capítulo 2 — El olivar;
- Capítulo 3 — La montaña;
- Capítulo 4 — El patrimonio.

### Modo familiar

Futura capa de equipo para familias o grupos, con una partida compartida y retos adaptados a niños. No debe modificar dificultad técnica ni recomendaciones de seguridad de la ruta.

### Eventos y temporadas

Se podrán activar colecciones o retos temporales por fiestas, campañas de aceite, floración, otoño o actividades municipales, siempre diferenciando contenido editorial de información oficial.

### Offline

Una futura versión PWA podrá precargar track, checkpoints y contenido editorial antes de salir. Los desbloqueos offline deberán guardar evidencia local mínima y sincronizarse posteriormente con reglas anti-duplicado.

## Administración pendiente del siguiente corte

La V1 deja listo el modelo y la experiencia de usuario. El siguiente bloque natural es un editor visual en Admin para:

- activar/desactivar Aventura por ruta;
- crear checkpoints pulsando sobre el mapa;
- vincularlos a POI existentes;
- ordenar etapas;
- definir radio, puntos, obligatoriedad y reto;
- previsualizar la aventura como usuario;
- consultar métricas agregadas de inicio, avance y finalización.

No se deben crear rankings públicos basados en velocidad sin una revisión específica de seguridad y diseño: la app no debe incentivar correr o asumir riesgos en senderos para mejorar una posición.
