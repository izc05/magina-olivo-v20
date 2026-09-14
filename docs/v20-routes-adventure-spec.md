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

El preflight de publicación comprueba ruta publicada, track validado y presente, checkpoint activo, checkpoint obligatorio y ausencia de un safety hold crítico vigente.

Un **safety hold** se activa únicamente para un `route_condition_report` que sea simultáneamente `approved`, `critical`, de tipo `closed`, `blocked`, `fire_risk` o `flooded`, y que no esté caducado. Un reporte de comunidad pendiente o no moderado no se trata como orden oficial.

Si una aventura estaba activa y posteriormente un editor aprueba un safety hold crítico, un trigger de base de datos la desactiva automáticamente. Al desaparecer o caducar el problema **no se reactiva sola**: un administrador debe revisar la ruta y activarla manualmente.

## Persistencia

Migración: `database/migrations/0086_routes_adventure.sql`.

Tablas: `route_adventures`, `route_adventure_checkpoints`, `route_adventure_runs` y `route_adventure_unlocks`.

La numeración `0086` preserva las migraciones territoriales ya integradas: enlaces institucionales, catálogo completo de municipios y validación AEMET.

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

## Desbloqueo

El usuario inicia la aventura, pulsa `Estoy aquí`, el navegador solicita una posición puntual, la API valida progresión y proximidad con PostGIS y, si aplica, valida la trivia. El desbloqueo se guarda una sola vez, la coordenada exacta se descarta y el usuario recibe XP y, cuando corresponda, categoría + rareza del hallazgo.

## Cuaderno, Pasaporte y niveles

Por ruta se muestran porcentaje descubierto, rango, XP conseguido/posible, checkpoints, miniálbum, rareza e insignias.

El perfil global `GET /api/v1/adventures/me` incluye aventuras iniciadas/completadas, descubrimientos únicos, XP acumulado sin duplicar un mismo checkpoint, álbum territorial, insignias, últimas expediciones y Pasaporte territorial.

`/aventura` muestra Sierra Mágina explorada, descubrimientos disponibles/desbloqueados, municipios con aventuras, municipios con progreso y avance por municipio.

Los niveles se derivan del XP existente, a razón de 500 XP por nivel: Caminante de Mágina, Explorador de Mágina desde nivel 3, Aventurero de Mágina desde nivel 6 y Guardián de Sierra Mágina desde nivel 10.

## Administración

Admin → Rutas permite activar/desactivar Aventura, configurar presentación/cierre, elegir progresión libre/lineal, crear etapas manuales, reutilizar/importar POI de forma idempotente, editar/eliminar/ordenar checkpoints, configurar GPS/XP/trivia/pistas, asignar y preservar categoría territorial y rareza, consultar métricas, preflight y safety hold, y usar la pantalla de candidatas para priorizar rutas con mejores datos reales.

Las acciones administrativas están protegidas por permisos y auditadas.

## Catálogo y candidatas

Aventura está sincronizada con el catálogo territorial actual de `feat/v20-routes-explore`. El catálogo ayuda a priorizar contenido, pero **no convierte automáticamente una ruta en publicable**. Una ruta sin track real validado o con bloqueo de seguridad no se activa.

## QA

`V20 routes adventure check` valida secuencia de migraciones, TypeScript y build de API/web, migraciones reales sobre PostGIS 17, persistencia de partida/desbloqueo, índice idempotente de POI, progresión lineal, categoría/rareza e índice del Álbum, ausencia de almacenamiento GPS exacto, rechazo de publicación no preparada, activación de ruta preparada, auto-desactivación ante safety hold crítico moderado y bloqueo de reactivación mientras siga vigente.

También se ejecutan full candidate, foundation, environment, platform admin, staging readiness y browser E2E.

## Criterio de cierre 10/10 técnico

No se considera cerrado por cantidad de funciones. Para marcar la base como 10/10 técnico deben cumplirse simultáneamente:

1. Aventura sincronizada con el HEAD vigente de Rutas sin quedar commits por detrás;
2. PR mergeable y mantenido como Draft hasta handoff;
3. todos los gates verdes sobre el mismo HEAD;
4. privacidad GPS y safety hold cubiertos por pruebas;
5. Admin → API → DB → UI coherentes para progresión y Álbum;
6. ninguna aventura ficticia publicada para rellenar el catálogo;
7. primera aventura real solo cuando exista una ruta publicable con track validado y checkpoints verificados.

## Límites deliberados

No se implementan seguimiento GPS continuo, validación automática de fotografías como verdad, rankings por velocidad, AR que afirme reconocer flora/fauna sin metodología validada ni generación automática de hechos territoriales. Estas decisiones mantienen Aventura divertida sin convertir la capa lúdica en una fuente de riesgo, privacidad invasiva o información inventada.
