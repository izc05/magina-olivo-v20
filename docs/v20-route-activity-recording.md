# V20 · Grabar recorrido

## Objetivo

Añadir a Mágina Olivo una actividad GPS voluntaria asociada a rutas publicadas con track validado, manteniendo una separación estricta entre:

- **Km conquistados**: suma una sola vez la distancia oficial de cada ruta distinta completada en Mágina Aventura.
- **Km grabados**: suma cada salida GPS que el usuario decide registrar, incluidas repeticiones.

La grabación no sustituye el track oficial, la señalización, los avisos de seguridad ni constituye una certificación deportiva.

## Flujo de usuario

1. El usuario abre una ruta validada.
2. Pulsa **Iniciar recorrido**.
3. El navegador solicita permiso de ubicación.
4. Mientras la actividad está `recording`, la web envía muestras GPS a su actividad privada.
5. **Pausar** detiene el watcher GPS.
6. **Reanudar** crea un nuevo segmento para no unir la pausa con una línea artificial.
7. **Finalizar** detiene el watcher, vacía la cola pendiente y el servidor calcula métricas filtradas.
8. El recorrido queda visible en `/perfil`, donde se puede exportar a GPX o borrar.

## Privacidad

- No existe inicio automático de una grabación.
- No existe seguimiento oculto.
- V1 solo permite `visibility = private`.
- Cada lectura y mutación exige el usuario autenticado propietario.
- Otro usuario obtiene `404` al intentar leer una actividad ajena.
- Borrar una actividad elimina sus puntos GPS exactos mediante `ON DELETE CASCADE`.
- La exportación GPX exige autenticación y usa `Cache-Control: private, no-store`.
- Mágina Aventura continúa usando el patrón puntual **Estoy aquí** sin almacenar la posición exacta de los desbloqueos.

## Limitaciones web/PWA conscientes

La V1 es **foreground recording**. El navegador puede suspender JavaScript/GPS cuando:

- la pestaña queda oculta;
- la pantalla se apaga;
- el sistema operativo entra en ahorro de batería.

La UI lo comunica expresamente. Los huecos de más de 120 segundos no se suman al tiempo ni a la distancia calculada. Para un futuro registro fiable en segundo plano se deberá evaluar empaquetado nativo (por ejemplo Capacitor) y permisos específicos del sistema operativo.

## Modelo de datos

### `route_activity_recordings`

- propietario `user_id`;
- ruta opcional `route_id` para conservar el historial aunque una ruta se retire;
- estados `recording | paused | completed`;
- privacidad `private`;
- `current_segment`;
- métricas finales: distancia, desnivel GPS, duración y número de muestras;
- índice parcial que impide más de una actividad abierta por usuario.

### `route_activity_points`

Cada muestra conserva:

- segmento y secuencia;
- fecha de captura;
- `geometry(Point, 4326)`;
- altitud opcional;
- precisión horizontal y vertical.

Las coordenadas exactas existen únicamente porque el usuario inició explícitamente esta función.

## Cálculo de métricas

La métrica final se recalcula en servidor, no se acepta un total enviado por el cliente.

Se ignoran para distancia:

- saltos menores de 2 m;
- huecos temporales superiores a 120 s;
- tramos con velocidad implícita superior a 15 m/s (54 km/h);
- muestras con precisión horizontal peor de 100 m.

El desnivel GPS solo suma ascensos entre 3 y 50 m cuando ambas muestras incluyen precisión vertical de 50 m o mejor. Si no existe altitud suficientemente fiable se devuelve `null` en lugar de inventar desnivel.

Las pausas crean segmentos distintos; nunca se calcula distancia ni tiempo entre segmentos.

## API privada

- `GET /api/v1/activities/active`
- `POST /api/v1/routes/:id/activities/start`
- `POST /api/v1/activities/:id/points`
- `POST /api/v1/activities/:id/pause`
- `POST /api/v1/activities/:id/resume`
- `POST /api/v1/activities/:id/finish`
- `GET /api/v1/activities/me`
- `GET /api/v1/activities/:id`
- `DELETE /api/v1/activities/:id`
- `GET /api/v1/activities/:id/gpx`

## Perfil

`/perfil` muestra dos bloques complementarios:

### Pasaporte de explorador

Territorio único conquistado, XP, nivel, descubrimientos, álbum, municipios, insignias, distancia oficial única, desnivel oficial y ruta más larga.

### Mis recorridos grabados

- actividades finalizadas;
- km GPS acumulados;
- tiempo GPS acumulado;
- desnivel GPS acumulado cuando existe;
- salida más larga;
- actividad abierta;
- historial reciente;
- enlace a la ruta;
- exportación GPX;
- borrado completo.

## Antifraude y seguridad de producto

Los km grabados no conceden las insignias territoriales de 25/100 km y no sustituyen los km conquistados. No se implementan rankings por velocidad. La finalidad es memoria personal y registro de actividad, no incentivar desplazamientos peligrosos ni convertir precisión GPS de consumo en una certificación.

## QA

El workflow `V20 route activity check` exige:

- secuencia de migraciones válida;
- typecheck API + web;
- build API + web;
- PostGIS 17 y todas las migraciones;
- privacidad `private` por defecto;
- una sola grabación abierta por usuario;
- integración API real con inicio, duplicado idempotente, puntos, pausa, rechazo durante pausa, reanudación y finalización;
- cálculo segmentado de tiempo/distancia/desnivel;
- aislamiento entre usuarios;
- resumen agregado en el perfil;
- GPX con segmentos separados;
- borrado en cascada de puntos GPS.

## Evolución posterior

No forma parte de esta V1:

- tracking fiable en segundo plano;
- compartir actividades públicamente;
- rankings de velocidad;
- premios económicos por km;
- validación automática de una ruta mediante el track grabado;
- sincronización con Apple Health, Google Health Connect, Garmin, Strava u otros proveedores.

Estas extensiones deben mantener consentimiento explícito, control de privacidad y una separación clara entre dato deportivo estimado, progreso lúdico y datos oficiales de la ruta.
