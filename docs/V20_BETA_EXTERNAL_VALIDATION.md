# V20 Beta — Validación externa antes de promoción

Este checklist empieza **después** del cierre funcional interno de `integrate/v20-beta-closure`.

## Referencia a validar

No usar un SHA histórico escrito en documentación como candidato implícito. En cada despliegue se elige de forma explícita un `expected_sha` completo de la rama coordinadora.

Antes de desplegar, ese mismo SHA debe tener verdes:

- `V20 full candidate check`;
- `V20 beta browser E2E`;
- `V20 staging readiness`;
- `V20 visual preview / GitHub Pages`.

El workflow remoto vuelve a comprobar los tres primeros antes de tocar el host. El estado vivo del cierre se mantiene en PR #58.

Documentación operativa relacionada:

- `docs/V20_STAGING_RUNBOOK.md`;
- `docs/V20_STAGING_FIRST_DEPLOY_CHECKLIST.md`;
- `deploy/staging/OPERATIONS.md`.

## 1. Configuración privada de GitHub Environment `staging`

Secrets requeridos:

```text
STAGING_ENV_FILE
STAGING_SSH_PRIVATE_KEY
STAGING_SSH_KNOWN_HOSTS
```

Variables requeridas:

```text
STAGING_WEB_URL
STAGING_HOST
STAGING_USER
STAGING_PORT
STAGING_PATH
```

El controlador comprueba primero la presencia de los ocho nombres y, si falta alguno, informa **solo del nombre**, nunca de su valor. Ningún dato privado debe copiarse al repositorio ni a comentarios del PR.

## 2. Preflight del host real

Comprobar antes del deploy:

- Docker Engine y Docker Compose v2;
- DNS/TLS del staging;
- espacio libre y reloj/NTP;
- `curl`, `tar` y `sha256sum`;
- SSH con clave dedicada y `known_hosts` verificado;
- almacenamiento persistente de PostgreSQL y backups;
- salida HTTPS hacia S3/R2, Google, AEMET y web-push.

PostgreSQL no debe publicarse a Internet.

## 3. `.env` privado

Partir de `deploy/staging/.env.example`, completar placeholders y ejecutar:

```bash
node scripts/staging-env-preflight.mjs deploy/staging/.env
```

El contrato exige:

- `NODE_ENV=production`;
- `NEXT_PUBLIC_PREVIEW_MODE=false`;
- `ALLOW_DEV_AUTH_HEADERS=false`;
- web/API en HTTPS y orígenes distintos;
- Google Identity con el mismo client ID en API y web;
- S3-compatible real y bucket aislado;
- worker con `ocr,radar,notifications`;
- OCR Tesseract `spa+eng`;
- VAPID real;
- AEMET real.

Estas variables son obsoletas y el preflight las rechaza:

```text
GOOGLE_CLIENT_SECRET
SESSION_SECRET
PUBLIC_WEB_ORIGIN
OCR_PROCESSOR_MODE
```

**Criterio:** `Staging env preflight passed` y ningún secreto versionado.

## 4. PostgreSQL/PostGIS

- arrancar PostgreSQL/PostGIS real;
- ejecutar migraciones desde cero y repetirlas;
- verificar `postgis` y geometrías `EPSG:4326`;
- crear datos de smoke;
- comprobar persistencia tras reinicio.

**Criterio:** migraciones limpias, checksum válido y datos persistentes.

## 5. API y Runtime

Validar desde fuera del contenedor:

- `/health` y `/ready`;
- request-id;
- rate limiting;
- CORS allow/deny;
- headers de producción;
- logs sin secretos;
- rechazo de cabeceras dev-auth.

Smoke automatizado:

```bash
STAGING_API_URL=https://<api-staging> \
STAGING_WEB_ORIGIN=https://<web-staging> \
STAGING_REJECTED_ORIGIN=https://untrusted.invalid \
node scripts/staging-postdeploy-smoke.mjs
```

## 6. Storage S3/R2 y OCR/Worker

Validar de extremo a extremo:

- subida real de PDF/imagen;
- checksum y confirmación;
- lectura autenticada/temporal;
- procesamiento por worker;
- resultado OCR visible en UI;
- persistencia tras reinicio;
- acceso del worker y lectura del asset radar.

**Criterio:** documento → storage → OCR → extracción → UI completo, sin mocks locales.

## 7. AEMET / Radar real

### Previsión

- consulta AEMET desde el host;
- caché `fresh`;
- fallo upstream con `stale` o `sin datos` según contrato;
- refresco manual desde UI.

### Radar

- ingestión del producto real configurado;
- snapshot con timestamp real;
- análisis por finca con geometría canónica;
- overlay PNG por API y `bbox` correcto;
- degradación segura ante fallo de storage/AEMET/raster.

**No aceptar:** nowcast, ETA o conversión dBZ→mm/h sin metodología validada.

## 8. Google Auth real

- origen HTTPS registrado en Google Cloud;
- login real;
- usuario + membership/workspace;
- persistencia de sesión/recarga;
- logout/login;
- aislamiento entre workspaces.

## 9. Notificaciones / VAPID

- claves reales del entorno;
- `/health` reporta web-push configurado;
- suscripción real;
- envío de smoke;
- aislamiento por usuario/workspace;
- tolerancia a suscripciones expiradas.

## 10. Admin / CMS / multimedia

- acceso Admin autorizado;
- crear/editar/publicar/despublicar contenido;
- comprobar superficie pública;
- subir/servir multimedia;
- revisar auditoría/log de cambios.

## 11. Backup / restore real

1. crear datos de smoke identificables;
2. ejecutar backup en host;
3. alterar esos datos en staging;
4. restaurar backup con PostgreSQL 17;
5. ejecutar migraciones otra vez;
6. comprobar `/ready` y recuperación de datos.

**Criterio:** restore probado, no solo existencia de un dump.

## 12. Recorrido Agricultor real

```text
Google login
→ crear finca
→ seleccionar/vincular geometría GIS real
→ volver a editar y recuperar geometría
→ registrar trabajo
→ registrar cosecha
→ añadir rendimiento
→ Campaña
→ subir documento
→ OCR
→ revisión humana
→ tiempo/radar real
```

## 13. Recorrido Profesional real

```text
cliente
→ presupuesto
→ PDF
→ compartir
→ abrir enlace público en incógnito
→ aceptar/rechazar
→ convertir a trabajo
→ factura
→ cobro
```

El enlace público no debe exponer sesión privada ni datos ajenos.

## 14. Auditoría visual/manual final

Revisar al menos:

- 360 × 844;
- 390 × 844;
- 430 × 932;
- escritorio ≥ 1280 px.

Superficies críticas: Inicio/Hoy, Mi Campo, Finca GIS, ficha de Finca, Registrar, Campaña, Clima/Radar/Mapa, Documentos/OCR, Profesional, Perfil, Admin, Explorar/Público, Avisos y Herramientas.

Buscar overflow horizontal, textos técnicos visibles, controles fuera de viewport, modales imposibles de cerrar, estados loading/error/empty/stale ilegibles, problemas de foco/teclado/contraste y mapas que oculten controles.

## Cierre

La Beta puede promoverse al candidate solo cuando:

- staging externo esté desplegado;
- HTTPS/CORS/headers pasen;
- Google Auth, VAPID, S3/R2, OCR y AEMET/radar reales estén validados;
- backup/restore real esté probado;
- smoke post-deploy pase;
- recorridos Agricultor/Profesional pasen;
- auditoría manual no deje P0/P1;
- la rama coordinadora siga CI-verde.

No fusionar directamente a `main` como parte de este checklist.
