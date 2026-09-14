# Mágina Olivo V20 — Mágina Aventura

## Propósito

Mágina Aventura convierte rutas reales de Sierra Mágina en una experiencia de exploración territorial dentro de Mágina Olivo. La capa lúdica se apoya en tracks, POI y contenido editorial reales, pero nunca sustituye la navegación técnica, la señalización, las restricciones, los avisos oficiales ni la evaluación personal de seguridad.

La experiencia se integra en V20 mediante `/aventura`, las fichas de Rutas, `/perfil`, `/aventura/actividad` y el Admin existente. No es una web ni una cuenta separada.

## Contrato de confianza

- Una aventura pública solo puede vivir sobre una ruta `published` con track real `validated`.
- Debe existir al menos un checkpoint activo y obligatorio.
- El progreso del juego no se guarda en `route_completions`.
- Completar el juego no certifica una actividad deportiva ni el estado del sendero.
- Las respuestas correctas de trivia nunca salen en payload público.
- El desbloqueo geográfico se valida en servidor con PostGIS.
- En el juego normal no existe seguimiento continuo: la coordenada enviada al pulsar `Estoy aquí` se usa para calcular proximidad y se descarta; solo se conserva la distancia al checkpoint.
- El único modo que conserva una traza GPS es **Grabar recorrido**, separado del juego y siempre iniciado expresamente por el usuario.
- Los tracks grabados son privados del propietario, no tienen endpoint público, se pueden borrar y no alimentan rankings por velocidad.
- No hay incentivos para correr o asumir riesgos.

## Seguridad operacional

El preflight de publicación comprueba ruta publicada, track validado y presente, checkpoint activo, checkpoint obligatorio y ausencia de un safety hold crítico vigente.

Un **safety hold** se activa únicamente para un `route_condition_report` que sea simultáneamente `approved`, `critical`, de tipo `closed`, `blocked`, `fire_risk` o `flooded`, y que no esté caducado. Un reporte de comunidad pendiente o no moderado no se trata como orden oficial.

Si una aventura estaba activa y posteriormente un editor aprueba un safety hold crítico, un trigger de base de datos la desactiva automáticamente. Al desaparecer o caducar el problema **no se reactiva sola**: un administrador debe revisar la ruta y activarla manualmente.

## Persistencia

Migraciones:

- `database/migrations/0086_routes_adventure.sql`: juego, checkpoints, partidas y desbloqueos.
- `database/migrations/0087_route_activity_recording.sql`: grabaciones GPS privadas y voluntarias.

Tablas del juego: `route_adventures`, `route_adventure_checkpoints`, `route_adventure_runs` y `route_adventure_unlocks`.

Tablas de actividad: `route_activity_sessions` y `route_activity_points`.

`route_activity_sessions_one_open_user_idx` impide que un usuario mantenga dos grabaciones abiertas a la vez. Los puntos exactos están aislados en `route_activity_points` y se eliminan en cascada al borrar su actividad.

## Partida y checkpoints

La partida mantiene una sesión activa por usuario y ruta, estados `active`, `completed` y `abandoned`, XP persistente y progreso recuperable. La finalización exige completar los checkpoints obligatorios.

Mecánicas disponibles: `landmark`, `trivia`, `observation`, `photo`, `collection` y `rest`. Cada checkpoint puede definir posición, distancia, radio de desbloqueo, XP, obligatoriedad, texto, pista, pregunta, opciones y orden.

## Álbum territorial

La mecánica del checkpoint y el contenido territorial son conceptos separados. Un checkpoint puede recibir una **categoría editorial verificada**: `flora`, `fauna`, `heritage`, `olive_culture`, `tradition` o `landscape`.

La rareza puede ser `common`, `uncommon`, `rare` o `legendary`.

La importación masiva desde POI **no infiere categoría ni rareza**. Un editor debe confirmarlas en Admin. El formulario conserva ambos campos al reeditar para evitar degradar accidentalmente un hallazgo raro a `common`.

El perfil global agrupa el álbum por categoría y rareza y deriva insignias como `coleccionista_de_magina` y `hallazgo_legendario`. La ficha de cada ruta muestra su miniálbum y la categoría/rareza cuando la etapa ya es visible.

## Modos de progresión

Cada aventura puede funcionar como `free` o `linear`. En modo lineal la API exige completar los checkpoints obligatorios anteriores antes de avanzar. La interfaz oculta el contenido de etapas futuras, no muestra su acción GPS y explica el bloqueo. Los retos extra no frenan el avance.

## Desbloqueo geográfico del juego

El usuario inicia la aventura, pulsa `Estoy aquí`, el navegador solicita una posición puntual, la API valida progresión y proximidad con PostGIS y, si aplica, valida la trivia. El desbloqueo se guarda una sola vez, la coordenada exacta se descarta y el usuario recibe XP y, cuando corresponda, categoría + rareza del hallazgo.

Esta operación es independiente de `Grabar recorrido`: se puede jugar a Mágina Aventura sin almacenar un track GPS.

## Perfil, Pasaporte, niveles y kilómetros

Por ruta se muestran porcentaje descubierto, rango, XP conseguido/posible, checkpoints, miniálbum, rareza e insignias.

El perfil global `GET /api/v1/adventures/me` incluye aventuras iniciadas/completadas, descubrimientos únicos, XP acumulado sin duplicar un mismo checkpoint, álbum territorial, insignias, últimas expediciones y Pasaporte territorial.

Los **km conquistados** se calculan a partir de rutas distintas completadas. Repetir la misma aventura no vuelve a sumar su distancia, desnivel ni duración. Se derivan también las insignias `veinticinco_km` y `cien_km_magina`.

Los **km registrados** son otra métrica: proceden de actividades GPS que el usuario ha grabado expresamente. Una ruta repetida puede sumar nueva actividad registrada, pero nunca infla los km conquistados.

`/perfil` muestra ambos conceptos por separado mediante `AdventureExplorerProfile` y `RecordedActivityProfileCard`.

Los niveles se derivan del XP existente, a razón de 500 XP por nivel, hasta Guardián de Sierra Mágina.

## Grabar recorrido

`/aventura/actividad` permite registrar una actividad libre o asociarla a una ruta pública con track validado.

Flujo:

1. El usuario entra en la pantalla y pulsa `Iniciar grabación`.
2. El navegador solicita permiso/posición GPS; sin una posición inicial no se crea una grabación nueva.
3. Mientras la actividad está `active`, el cliente usa `watchPosition` y envía muestras de forma limitada.
4. El usuario puede pausar, reanudar, finalizar o descartar.
5. Al finalizar se guardan distancia real estimada, tiempo activo, número de puntos y track privado.
6. El historial puede eliminarse; al eliminar la actividad desaparecen también sus puntos GPS.

### Calidad del track

- puntos con precisión peor de ±80 m no se almacenan ni suman distancia;
- muestras demasiado próximas en tiempo se ignoran;
- movimientos inferiores a 3 m se consideran estacionarios;
- saltos superiores a 1.000 m en menos de 180 s se rechazan;
- el primer punto tras reanudar no suma el desplazamiento producido durante la pausa;
- la distancia de segmentos aceptados se calcula con `ST_DistanceSphere`.

Estas reglas son filtros prácticos de calidad, no una certificación deportiva profesional.

### Foreground-only y cierres bruscos

V1 no implementa tracking GPS oculto en segundo plano.

Cuando la página pasa a `hidden`, el cliente cancela `watchPosition` e intenta pausar la sesión mediante una petición `keepalive`. Como un navegador móvil puede matar una pestaña antes de completar esa petición, el servidor mantiene además un heartbeat: una sesión `active` que lleve más de dos minutos sin actividad se auto-pausa cuando se recupera. El tiempo activo se cierra en el último heartbeat, no en la hora de regreso.

## API de actividad privada

Endpoints autenticados:

- `GET /api/v1/activities/current`
- `GET /api/v1/activities/me`
- `GET /api/v1/activities/:id/track`
- `POST /api/v1/activities/start`
- `POST /api/v1/activities/:id/points`
- `POST /api/v1/activities/:id/pause`
- `POST /api/v1/activities/:id/resume`
- `POST /api/v1/activities/:id/finish`
- `DELETE /api/v1/activities/:id`

Todos operan únicamente sobre el usuario autenticado. No existe endpoint público para leer tracks de actividad.

## Administración

Admin → Rutas permite activar/desactivar Aventura, configurar presentación/cierre, elegir progresión libre/lineal, crear etapas manuales, reutilizar/importar POI de forma idempotente, editar/eliminar/ordenar checkpoints, configurar GPS/XP/trivia/pistas, asignar y preservar categoría territorial y rareza, consultar métricas, preflight y safety hold, y usar la pantalla de candidatas para priorizar rutas con mejores datos reales.

Las acciones administrativas están protegidas por permisos y auditadas.

La actividad GPS personal no forma parte del contenido editorial público ni de la clasificación de aventuras.

## Catálogo y candidatas

Aventura está sincronizada con el catálogo territorial actual de `feat/v20-routes-explore`. El catálogo ayuda a priorizar contenido, pero **no convierte automáticamente una ruta en publicable**. Una ruta sin track real validado o con bloqueo de seguridad no se activa.

## QA

`V20 routes adventure check` valida secuencia de migraciones, TypeScript y build de API/web, migraciones reales sobre PostGIS 17, persistencia de partida/desbloqueo, índice idempotente de POI, progresión lineal, categoría/rareza e índice del Álbum, ausencia de GPS exacto dentro de los desbloqueos del juego, rechazo de publicación no preparada, activación de ruta preparada, auto-desactivación ante safety hold crítico y bloqueo de reactivación mientras siga vigente.

También valida que una ruta repetida no duplique los km conquistados y que la infraestructura de actividad voluntaria persista métricas/track privado y rechace dos sesiones abiertas simultáneamente.

Además se ejecutan full candidate, foundation, environment, platform admin, staging readiness y browser E2E.

## Criterio de cierre 10/10 técnico

No se considera cerrado por cantidad de funciones. Para marcar la base como 10/10 técnico deben cumplirse simultáneamente:

1. Aventura sincronizada con el HEAD vigente de Rutas sin quedar commits por detrás;
2. PR mergeable y mantenido como Draft hasta handoff;
3. todos los gates verdes sobre el mismo HEAD;
4. privacidad GPS, actividad opt-in y safety hold cubiertos por pruebas;
5. Admin → API → DB → UI coherentes para progresión y Álbum;
6. ninguna aventura ficticia publicada para rellenar el catálogo;
7. primera aventura real solo cuando exista una ruta publicable con track validado y checkpoints verificados.

## Límites deliberados

No se implementan tracking en segundo plano, validación automática de fotografías como verdad, rankings por velocidad, AR que afirme reconocer flora/fauna sin metodología validada ni generación automática de hechos territoriales.

El grabador V1 es una herramienta privada, voluntaria y foreground-only. No convierte Mágina Aventura en una app de seguimiento permanente ni autoriza compartir la ubicación del usuario públicamente.
