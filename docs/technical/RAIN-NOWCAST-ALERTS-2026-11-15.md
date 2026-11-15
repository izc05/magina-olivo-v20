# Plan RAIN-1 — Radar de lluvia + notificaciones por finca

**2026-11-15 · Estado: propuesta técnica y spike aislado · SIN despliegue ni cambios en main.**

## Qué existe hoy

- `supabase/functions/weather-radar/handler.ts`: devuelve imágenes anteriores de RainViewer.
- `feature/home/RadarScreen.kt`: muestra teselas de radar sobre el mapa de parcelas, con deslizador temporal.
- `data/remote/weather/EdgeRadarSource.kt`: descarga fotogramas desde Edge Function.
- `feature/notifications/NotificationPermissionHost.kt` y `data/reminder/ReminderNotifier.kt`: permisos y avisos **locales** de trabajos. No existe aquí el push meteorológico.
- AGENTS.md obliga a una sola línea productiva y change control. Este spike no modifica navegación, Room, Auth/Sync, Edge Functions ni claves.

## Producto deseado

Tiempo > Radar: animación de la evolución observada; movimiento de ecos con dirección y tiempo real de cada observación; fincas con su límite; si hay predicción fiable, posible llegada 15–60 min, intensidad orientativa y nivel de confianza. «Sin datos suficientes» cuando corresponda. Perfil > Avisos > Tiempo: consentimiento opt-in, finca(s), antelación 15/30/60 minutos, umbral mínimo, silencio opcional. El aviso abre radar centrado en finca. **No** anunciar alertas oficiales AEMET a partir de nowcast propio.

## Arquitectura

```
AEMET georreferenciado -> ingesta/validación (worker cada ~10 min, según producto)
 -> histórico limitado y calidad -> estimación de desplazamiento
 -> intersección con fincas por geometría -> preferencias/autorización
 -> outbox idempotente -> FCM HTTP v1 -> Android con app cerrada
                                    -> Radar/Historial de avisos
```

### Fuente y licencia

AEMET publica radar regional en OpenData (~10 min) y material georreferenciado GeoTIFF EPSG:4326 en su visor; ciertos productos compuestos OpenData tienen otra periodicidad (~30 min). No confundir historial del visor con historial descargable de OpenData: almacenar observaciones propias, sujeto a condiciones de uso y atribución. La reflectividad dBZ **no** equivale directamente a precipitación mm/h; hay que calibrar y validar la conversión o seleccionar un producto de precipitación utilizable. Comprobar cobertura real en la orografía de Sierra Mágina.

RainViewer sirve como radar actual, pero su API gratuita está orientada a usos personales/educativos; para uso comercial negociar términos o sustituirla. No basar notificaciones en un servicio cuya licencia o disponibilidad no esté confirmada.

### Backend privado

Usar backend de cuentas/fincas autenticado; el antiguo proyecto meteorológico no puede leer directamente fincas privadas. No copiar service_role al Android. RLS por propietario/membresía **real**, no solo `TO authenticated`.

Tablas propuestas (NO creadas): `rain_watch_preferences` (account_id, farm_id, enabled, lead_min, min_mm_h, quiet_hours); `device_installations` (tokens FCM protegidos y revocables); `rain_observations` (source, observed_at, raster_ref, bbox, quality); `rain_forecasts` (issue_at, expires_at, motion, quality); `rain_notification_outbox` (account_id, farm_id, event_id, dedupe_key UNIQUE, status, sent_at, attempt).

Controlar consentimiento, token refresh, cierre de sesión, dispositivos múltiples, baja de finca, reintentos con idempotencia y límites de frecuencia. Un job cron debe ejecutarse **en servidor** y emitir por FCM HTTP v1 usando una identidad de servidor; sin Firebase o con el móvil apagado/sin red no hay garantía de entrega puntual.

### Android

Conservar visual actual y su mapa. Añadir reproducción/pausa/velocidad con fotogramas observados. Flechas **solo** cuando el algoritmo dé confianza suficiente. Etiquetar fuente y hora observada. Crear canal separado «Avisos de lluvia» y pantalla de preferencias por finca. Nunca activar de forma silenciosa avisos comerciales o meteorológicos por defecto. Limitar datos privados en pantalla bloqueada. Abrir radar de finca al tocar, con fallback seguro.

## Fases verificables

1. **R0 (este PR):** prototipo TypeScript con estimación simple y seis pruebas sobre datos sintéticos; no produce alertas reales.
2. **R1:** ingestor AEMET GeoTIFF, QA georreferenciación/cobertura/calibración, reteniendo imágenes con permiso. Validar varios episodios reales Sierra Mágina/Jaén.
3. **R2:** nowcasting espacial real (ej. pySTEPS/flujo óptico), polígonos de parcelas, validación false positives/negatives y horizonte de predicción 15/30/60.
4. **R3:** migraciones/RLS en DEV, preferencias, tokens y outbox, worker cron y FCM, pruebas de dos usuarios y dos móviles.
5. **R4:** UI Compose del radar, deep links y test real de notificación con app cerrada, offline y permisos denegados. Feature flag OFF hasta Gate y autorización.

## Bloqueos para una publicación

No existe hoy una fuente de precipitación **normalizada y comprobada en mm/h**, ni worker desplegado, ni claves/FCM/push meteorológico verificados; por tanto NO se pueden prometer avisos reales. Una trayectoria adveccionada no puede garantizar cuándo llueve. Revisar fechas de datos, atribuciones, licencia, métricas reales y cobertura orográfica antes de GO.

Fuentes oficiales:
- https://www.aemet.es/es/eltiempo/observacion/radar/ayuda
- https://datos.gob.es/es/catalogo/e05068001-datos-de-radares
- https://www.rainviewer.com/api.html
- https://supabase.com/docs/guides/functions/schedule-functions
- https://firebase.google.com/docs/cloud-messaging/send/v1-api
