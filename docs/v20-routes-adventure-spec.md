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
- La coordenada GPS del usuario se usa solo para calcular proximidad y no se conserva; se guarda únicamente la distancia al checkpoint necesaria para auditar el desbloqueo.

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
7. La coordenada exacta se descarta después del cálculo y no forma un historial de localización.

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

## Administración implementada

El editor vive dentro de la gestión existente de Rutas y permite:

- activar/desactivar Aventura por ruta;
- definir título, introducción y mensaje de cierre;
- crear checkpoints mediante coordenadas reales;
- reutilizar POI existentes copiando su posición y distancia;
- editar y eliminar etapas;
- ordenar etapas;
- definir radio GPS, puntos, obligatoriedad y visibilidad;
- configurar preguntas, opciones, respuesta correcta y pista;
- consultar partidas totales, activas y completadas y puntuación media.

Las acciones administrativas requieren permisos de plataforma y quedan auditadas. La respuesta correcta solo se entrega al administrador y nunca al endpoint público del juego.

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

## Límites deliberados

La V1 no incorpora seguimiento GPS continuo, validación automática de fotografías ni rankings públicos por velocidad. Estas funciones requieren una revisión específica de privacidad, moderación y seguridad. La app no debe incentivar correr o asumir riesgos en senderos para mejorar una posición.
