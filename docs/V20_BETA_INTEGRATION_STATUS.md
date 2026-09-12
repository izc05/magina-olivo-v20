# Estado de integración V20 Beta

- Rama coordinadora: `integrate/v20-beta-closure`
- Base: `feat/v20-visual-prototype`
- PR coordinador: #58
- `main`: **no tocar** durante este cierre.
- Gate de fase: **funcional pre-visual + preparación de staging externo**.

## Fuente de verdad del candidato

Este documento no fija un SHA ni números de workflow como “últimos”: quedarían obsoletos en cuanto avance la rama coordinadora.

La fuente viva es el HEAD de PR #58. Antes de cualquier despliegue o promoción, el SHA exacto elegido debe demostrar en ese mismo commit:

- `V20 full candidate check` ✅
- `V20 beta browser E2E` ✅
- `V20 staging readiness` ✅
- `V20 visual preview / GitHub Pages` ✅

Los checks auxiliares de Foundation, Runtime, Admin, Planes, Mi Olivo, Avisos, Environment Contract y Lockfile deben permanecer también verdes.

El workflow `V20 staging deploy` exige de forma automática los tres gates de ejecución críticos (Full Candidate, Browser E2E y Staging Readiness) para el `expected_sha` solicitado.

## Integración funcional interna

La rama coordinadora reúne el núcleo Agricultor, Mi Campo/Campaña, Profesional, Documentos/OCR, superficies públicas, Mercado, Admin/CMS/Territorio, Mi Olivo, Planes, Centro de Avisos, Engineering Foundation, Runtime hardening, QA móvil/browser, selector GIS real, Clima/Radar funcional y Herramientas rápidas.

### GIS real de Finca

- selección real Catastro/SIGPAC en alta y edición;
- geometría canónica persistida;
- recuperación al volver a editar;
- estados sin geometría/error;
- convivencia con mapa, clima y radar.

### Clima/Radar

- AEMET por finca;
- radar de reflectividad observada;
- overlay PNG georreferenciado por `bbox`;
- endpoint binario autenticado;
- ingestión periódica con timestamp real;
- estados `loading/error/stale/sin datos` independientes;
- degradación segura por fuente;
- **sin nowcast, ETA ni conversión dBZ→mm/h no validada**.

### Cobertura transversal

- recorrido Agricultor: `Finca → trabajo → cosecha → rendimiento → ficha → campaña`;
- recorrido Profesional: `cliente → presupuesto → trabajo → factura → documento/compartir → cobro`;
- matriz móvil Browser E2E: 360 / 390 / 430 px;
- `/herramientas` con cálculos locales y sin persistencia ni recomendaciones agronómicas sensibles;
- Runtime con `/ready`, request-id, rate limiting y seguridad de producción;
- exportación Pages protegida contra enlaces internos fuera de `basePath`.

## Estado del primer staging real

Se ejecutó un primer intento controlado mediante `deploy/staging/STAGING_DEPLOY_REQUEST.json`.

El controlador validó la solicitud, el SHA y sus gates y se detuvo **antes de SSH, host, backup, migraciones o despliegue** porque el GitHub Environment `staging` no tenía disponible la configuración privada mínima observada (`STAGING_ENV_FILE` y `STAGING_WEB_URL`). Por tanto, ese intento no modificó ningún host ni dato externo.

El workflow ahora valida primero la presencia de **todos** los nombres requeridos y reporta en una sola ejecución los ausentes, sin imprimir valores ni secretos.

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

## Bloqueo restante antes de declarar Beta externa cerrada

Ya no es un bloqueo funcional interno. Falta validar el entorno real:

1. GitHub Environment `staging` con destino y credenciales reales;
2. PostgreSQL/PostGIS real y migraciones;
3. S3/R2 real y lectura/escritura de documentos/radar;
4. OCR/worker real;
5. AEMET/radar real desde el host;
6. Google Identity real;
7. VAPID/notificaciones reales;
8. CMS/multimedia real;
9. backup/restore real;
10. smoke post-deploy HTTPS externo;
11. recorridos Agricultor/Profesional sobre ese host;
12. auditoría visual/manual final 360/390/430 + escritorio.

El detalle operativo vive en:

- `docs/V20_BETA_EXTERNAL_VALIDATION.md`
- `docs/V20_STAGING_FIRST_DEPLOY_CHECKLIST.md`
- `docs/V20_STAGING_RUNBOOK.md`
- `deploy/staging/OPERATIONS.md`

## Regla de promoción

PR #58 debe permanecer en **Draft** y sin fusionar hasta completar la validación externa o decidir explícitamente qué servicios quedan fuera del alcance de la Beta.

No iniciar un rediseño visual global que oculte defectos funcionales. `main` permanece fuera de este proceso hasta la decisión final del candidato.
