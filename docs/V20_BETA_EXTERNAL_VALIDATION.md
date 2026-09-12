# V20 Beta — Validación externa antes de promoción

Este checklist empieza **después** del cierre funcional interno de `integrate/v20-beta-closure`.

Referencia funcional verde previa:

- integración funcional: `3a934e979fa6f279316c75a61ac9610432786887`;
- Full Candidate #2308 ✅;
- Browser E2E #620 ✅;
- Staging Readiness #221 ✅;
- Foundation #110 ✅;
- Runtime Hardening #60 ✅;
- Platform Admin #179 ✅;
- Notification Center #39 ✅;
- Planes #106 ✅;
- Mi Olivo #62 ✅;
- Environment Contract #127 ✅;
- Lockfile Guard #111 ✅;
- Visual Preview / GitHub Pages #612 ✅.

Los workflows dedicados GIS y Weather/Radar quedaron verdes tras su absorción en la rama coordinadora. Los commits exclusivamente documentales posteriores no invalidan esta referencia funcional.

Documentación operativa relacionada:

- `docs/V20_STAGING_RUNBOOK.md`;
- `docs/V20_STAGING_FIRST_DEPLOY_CHECKLIST.md`;
- `deploy/staging/OPERATIONS.md`.

## 1. Preflight del host real

Comprobar antes del deploy:

- Docker/Compose disponibles;
- DNS/TLS del staging resuelven al host correcto;
- espacio libre suficiente;
- reloj/NTP correcto;
- acceso al registro de imágenes si aplica;
- variables de entorno cargadas sin secretos en el repositorio;
- directorios persistentes de PostgreSQL y backups montados;
- política de backup definida.

Antes de desplegar ejecutar:

```bash
node scripts/staging-env-preflight.mjs deploy/staging/.env
```

El contrato actual de staging exige explícitamente:

- `NODE_ENV=production`;
- `NEXT_PUBLIC_PREVIEW_MODE=false`;
- `ALLOW_DEV_AUTH_HEADERS=false`;
- orígenes web/API HTTPS distintos;
- Google Identity configurado con el mismo client ID en API y web;
- S3 compatible real;
- worker con `ocr,radar,notifications`;
- OCR Tesseract `spa+eng`;
- VAPID real;
- AEMET real.

`SESSION_SECRET`, `PUBLIC_WEB_ORIGIN`, `GOOGLE_CLIENT_SECRET` y `OCR_PROCESSOR_MODE` **no forman parte del runtime actual** y el preflight los rechaza si aparecen.

**Criterio:** `Staging env preflight passed` y ningún secreto versionado.

## 2. PostgreSQL/PostGIS

- arrancar PostgreSQL/PostGIS real;
- ejecutar migraciones desde cero en una base vacía;
- repetir migraciones para comprobar idempotencia del migrador;
- verificar `postgis` y geometrías `EPSG:4326`;
- crear usuario/workspace/finca de smoke;
- comprobar persistencia tras reinicio.

**Criterio:** migraciones limpias, checksum válido y datos persistentes.

## 3. API y Runtime

Validar desde fuera del contenedor:

- `/health`;
- `/ready`;
- request-id;
- rate limiting;
- CORS/orígenes permitidos;
- headers de producción;
- logs sin secretos;
- rechazo de cabeceras de identidad de desarrollo.

Smoke automatizado disponible:

```bash
STAGING_API_URL=https://<api-staging> \
STAGING_WEB_ORIGIN=https://<web-staging> \
STAGING_REJECTED_ORIGIN=https://untrusted.invalid \
node scripts/staging-postdeploy-smoke.mjs
```

Ese smoke exige además que `/health` confirme base, Google Auth y web-push configurados.

**Criterio:** API utilizable solo con configuración de producción/staging segura.

## 4. Storage S3/R2

- subida real de un documento;
- lectura real mediante URL/flujo autenticado;
- persistencia después de reinicio;
- acceso del worker;
- asset radar leído desde storage;
- verificar límites de tamaño y errores upstream.

**Criterio:** escritura/lectura completas sin usar mocks locales.

## 5. OCR / Worker

- subir PDF/imagen real de prueba;
- confirmar procesamiento por worker;
- revisar estado de `ocr_runs` / extracción;
- comprobar documento y resultado desde la web;
- reiniciar worker y verificar recuperación normal.

**Criterio:** flujo documento → storage → OCR → extracción → UI completo.

## 6. AEMET / Radar real

### Previsión

- consulta AEMET desde el host;
- caché `fresh`;
- simular/observar fallo upstream y comprobar `stale` o `sin datos` según contrato;
- refresco manual desde la UI.

### Radar

- ingestión real del producto configurado;
- snapshot con timestamp real;
- análisis por finca con geometría canónica;
- overlay PNG servido por API;
- georreferenciación correcta por `bbox`;
- degradación si storage/AEMET/raster falla.

**No aceptar:** nowcast, ETA o conversión dBZ→mm/h sin metodología validada.

**Criterio:** finca real visible con geometría y reflectividad observada, sin inventar predicción.

## 7. Google Auth real

Google Identity **forma parte del contrato actual de staging** y no puede omitirse sin cambiar expresamente dicho contrato.

Validar:

- origen HTTPS registrado en Google Cloud;
- login real;
- creación/lectura del usuario;
- membership/workspace;
- persistencia de sesión/recarga;
- logout/login;
- acceso denegado a recursos de otro workspace.

## 8. Notificaciones / VAPID

VAPID **forma parte del contrato actual de staging**.

Validar:

- claves VAPID reales del entorno;
- `/health` reporta web-push configurado;
- suscripción real;
- envío de notificación de smoke;
- aislamiento por usuario/workspace;
- tolerancia a suscripciones expiradas.

## 9. Admin / CMS / multimedia

- acceso Admin solo autorizado;
- crear/editar/publicar contenido;
- comprobar superficie pública;
- subir/servir multimedia;
- revisar auditoría/log de cambios;
- verificar que contenido despublicado no se expone.

## 10. Backup / restore real

Antes de cualquier promoción:

1. crear datos de smoke identificables;
2. ejecutar backup en el host;
3. borrar/modificar deliberadamente esos datos en staging;
4. restaurar backup;
5. ejecutar migraciones nuevamente;
6. comprobar `/ready`;
7. confirmar recuperación de los datos.

**Criterio:** restore verificable con PostgreSQL 17, no solo creación del archivo de backup.

## 11. Smoke post-deploy funcional

Recorrido mínimo externo:

1. abrir web de staging;
2. autenticar con Google;
3. entrar en Mi Campo;
4. crear finca;
5. seleccionar/vincular geometría GIS real;
6. volver a editar y recuperar la geometría;
7. registrar trabajo;
8. registrar cosecha;
9. registrar rendimiento;
10. abrir clima/radar de la finca;
11. comprobar documento/OCR;
12. abrir Campaña;
13. abrir Profesional;
14. comprobar una superficie pública/CMS;
15. revisar Centro de Avisos;
16. probar notificación web real.

## 12. Auditoría visual/manual final

Revisar en al menos:

- 360 × 844;
- 390 × 844;
- 430 × 932;
- escritorio ≥ 1280 px.

Superficies críticas:

- Inicio/Hoy;
- Mi Campo;
- alta/edición de Finca GIS;
- ficha de Finca;
- Registrar;
- Campaña;
- Clima/Radar/Mapa;
- Documentos/OCR;
- Profesional;
- Perfil;
- Admin;
- Explorar/Público;
- Avisos.

Buscar expresamente:

- overflow horizontal;
- textos técnicos visibles al usuario;
- botones fuera de viewport;
- modales imposibles de cerrar;
- estados loading/error/empty/stale ilegibles;
- contraste/foco/teclado;
- mapas que oculten controles;
- navegación inferior/topbar solapada.

## Cierre

La Beta puede promoverse al candidate solo cuando:

- preflight del `.env` real pase;
- staging externo esté desplegado;
- Google Auth, VAPID, S3/R2, OCR y AEMET/radar reales estén validados;
- backup/restore real esté probado;
- `staging-postdeploy-smoke.mjs` pase contra HTTPS real;
- recorrido agricultor/profesional pase;
- auditoría manual no deje P0/P1;
- la rama coordinadora siga sin regresiones funcionales.

No fusionar directamente a `main` como parte de este checklist.