# Mágina Olivo V20 — Mágina Aventura

## Propósito

Mágina Aventura convierte rutas reales de Sierra Mágina en una experiencia de exploración territorial dentro de Mágina Olivo. La capa lúdica se apoya en tracks, POI y contenido editorial reales, pero nunca sustituye la navegación técnica, la señalización, las restricciones, los avisos oficiales ni la evaluación personal de seguridad.

La experiencia se integra en V20 mediante `/aventura`, las fichas de Rutas y el Admin existente. No es una web ni una cuenta separada.

## Contrato de confianza

- Una aventura pública solo puede vivir sobre una ruta `published` con track real `validated`.
- Debe existir al menos un checkpoint activo y obligatorio.
- El progreso del juego no se guarda en `route_completions`.
- Completar el juego no certifica una actividad deportiva ni el estado del sendero.
- Las respuestas correctas de trivia nunca salen en payload público.
- El desbloqueo geográfico se valida en servidor con PostGIS.
- No existe seguimiento GPS continuo.
- La coordenada exacta enviada al pulsar `Estoy aquí` se usa únicamente para calcular proximidad y se descarta; la base conserva la distancia al checkpoint, no un historial de coordenadas.
- No hay ranking por velocidad ni incentivos para correr o asumir riesgos.

## Seguridad operacional

El preflight de publicación comprueba:

1. ruta publicada;
2. track validado y presente en `route_tracks`;
3. checkpoint activo;
4. checkpoint obligatorio;
5. ausencia de un safety hold crítico vigente.

Un **safety hold** se activa únicamente para un `route_condition_report` que sea simultáneamente:

- moderado como `approved`;
- severidad `critical`;
- tipo `closed`, `blocked`, `fire_risk` o `flooded`;
- no caducado.

Un reporte de comunidad pendiente o no moderado no se trata como orden oficial.

Si una aventura estaba activa y posteriormente un editor aprueba un safety hold crítico, un trigger de base de datos la desactiva automáticamente. Al desaparecer/caducar el problema **no se reactiva sola**: un administrador debe revisar la ruta y activarla manualmente de nuevo.

## Persistencia

Migración: `database/migrations/0084_routes_adventure.sql`.

Tablas:

- `route_adventures`;
- `route_adventure_checkpoints`;
- `route_adventure_runs`;
- `route_adventure_unlocks`.

La numeración `0084` es deliberada: Rutas incorporó `0083_territory_sierra_magina_expansion.sql` y Aventura fue renumerada al sincronizarse con el HEAD de Rutas.

## Partida

- una partida activa por usuario y ruta;
- estados `active`, `completed`, `abandoned`;
- XP/puntuación persistente;
- progreso recuperable al volver a abrir la ruta;
- finalización condicionada a los checkpoints obligatorios.

## Checkpoints

Mecánicas disponibles:

- `landmark`: lugar o hito;
- `trivia`: pregunta contextual;
- `observation`: observación;
- `photo`: reto fotográfico preparado;
- `collection`: coleccionable;
- `rest`: pausa narrativa.

Cada checkpoint puede definir posición, distancia sobre ruta, radio de desbloqueo, XP, obligatoriedad, texto, pista, pregunta, opciones y orden.

### Álbum territorial

La mecánica del checkpoint y el contenido territorial son conceptos separados. Un checkpoint puede además recibir una **categoría editorial verificada**:

- `flora`;
- `fauna`;
- `heritage`;
- `olive_culture`;
- `tradition`;
- `landscape`.

Y una rareza:

- `common`;
- `uncommon`;
- `rare`;
- `legendary`.

La importación masiva desde POI **no infiere categorías ni rareza**. Un editor debe confirmarlas en Admin. Esto evita etiquetar automáticamente un POI como flora, fauna o patrimonio sin evidencia.

El perfil global agrupa el álbum por categoría y rareza y deriva insignias como `coleccionista_de_magina` y `hallazgo_legendario`.

## Modos de progresión

Cada aventura puede funcionar como:

- `free`: checkpoints desbloqueables en cualquier orden;
- `linear`: los checkpoints obligatorios anteriores deben completarse antes de avanzar.

En modo lineal la API impone la secuencia antes de ejecutar la comprobación GPS. La interfaz oculta el contenido de etapas futuras, no muestra su acción GPS y explica por qué están bloqueadas. Los retos extra no bloquean la progresión.

## Desbloqueo

1. El usuario inicia una aventura autenticado.
2. Pulsa `Estoy aquí` en un checkpoint disponible.
3. El navegador solicita posición puntual.
4. La API comprueba primero la progresión lineal cuando corresponda.
5. PostGIS calcula la distancia al checkpoint.
6. Si está dentro del radio se valida trivia/reto cuando exista.
7. El desbloqueo se guarda una sola vez.
8. La coordenada exacta se descarta.
9. El usuario recibe XP y, cuando aplica, categoría + rareza del hallazgo.

## Cuaderno del Explorador

### Por ruta

- porcentaje descubierto;
- rango;
- XP conseguido/posible;
- checkpoints desbloqueados;
- categorías del álbum presentes en la ruta;
- insignias;
- mapa sincronizado pendiente/desbloqueado.

### Global — Mi Expedición

Endpoint: `GET /api/v1/adventures/me`.

Incluye:

- aventuras iniciadas/completadas;
- descubrimientos únicos;
- XP acumulado sin duplicar un mismo checkpoint entre partidas;
- álbum territorial por categoría/rareza;
- insignias globales;
- últimas expediciones;
- Pasaporte territorial.

## Pasaporte de Sierra Mágina

`/aventura` incluye un tablero territorial derivado de checkpoints reales:

- Sierra Mágina explorada (%);
- descubrimientos disponibles/desbloqueados;
- municipios con aventuras;
- municipios con progreso;
- avance por municipio.

El nivel se deriva del XP existente, no de una segunda fuente de verdad:

- 500 XP por nivel;
- Caminante de Mágina;
- Explorador de Mágina desde nivel 3;
- Aventurero de Mágina desde nivel 6;
- Guardián de Sierra Mágina desde nivel 10.

## Administración

Dentro de Admin → Rutas se puede:

- activar/desactivar Aventura;
- configurar título, introducción y cierre;
- seleccionar progresión libre/lineal;
- crear etapas manuales con coordenadas verificadas;
- reutilizar POI reales;
- importar masivamente POI activos de forma idempotente;
- editar/eliminar/ordenar checkpoints;
- configurar radio GPS, XP, obligatoriedad, trivia, pista y visibilidad;
- asignar categoría territorial y rareza editorial;
- ver partidas y puntuación media;
- consultar preflight y safety hold;
- abrir la pantalla de candidatas para priorizar rutas reales con track/POI adecuados.

Las acciones administrativas están protegidas por permisos y auditadas.

## Catálogo y candidatas

Aventura está sincronizada con el catálogo territorial actual de `feat/v20-routes-explore`. Los datos del catálogo ayudan a priorizar contenido, pero **no convierten automáticamente una ruta en publicable**. Una ruta sin track real validado o con bloqueo de seguridad no debe convertirse en aventura activa.

## API principal

Pública:

- `GET /api/v1/public/adventures`;
- `GET /api/v1/public/routes/:slug/adventure`.

Autenticada:

- progreso;
- start;
- unlock;
- complete;
- abandon;
- `GET /api/v1/adventures/me`.

Administración:

- CRUD de configuración/checkpoints;
- importación de POI;
- readiness/preflight;
- candidatos de Aventura;
- progresión libre/lineal.

## QA

Workflow dedicado: `V20 routes adventure check`.

Comprueba:

- secuencia de migraciones;
- TypeScript API + web;
- build API + web;
- aplicación real de todas las migraciones en PostGIS 17;
- persistencia de aventura, partida y desbloqueo;
- índice idempotente de POI;
- progresión lineal;
- categoría y rareza del Álbum;
- índice del Álbum;
- ausencia de almacenamiento de GPS exacto;
- rechazo de publicación cuando la ruta no está lista;
- activación cuando track/ruta/checkpoints sí están listos;
- auto-desactivación ante safety hold crítico moderado;
- bloqueo de reactivación mientras el hold siga vigente.

También se ejecutan los gates generales de V20: full candidate, foundation, environment, platform admin, staging readiness y browser E2E.

## Límites deliberados

No se implementa todavía:

- seguimiento GPS continuo;
- validación automática de fotografías como verdad;
- rankings por velocidad;
- AR que afirme reconocer flora/fauna sin metodología validada;
- creación automática de hechos territoriales o categorías a partir de IA.

Estas decisiones mantienen Aventura divertida sin convertir una capa lúdica en una fuente de riesgo, privacidad invasiva o información territorial inventada.
