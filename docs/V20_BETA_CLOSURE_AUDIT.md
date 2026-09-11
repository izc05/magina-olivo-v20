# V20 — Cierre de Beta

Estado: cierre técnico preparado para ejecutar staging real sobre `feat/v20-visual-prototype`.

Objetivo: dejar de ampliar módulos grandes y cerrar una beta coherente, API-first, móvil y verificable.

## Criterio de salida Beta

La Beta no se considerará cerrada hasta cumplir simultáneamente:

1. `V20 full candidate check` en verde sobre el HEAD candidato.
2. `V20 beta browser E2E` en verde sobre el mismo HEAD.
3. `V20 staging readiness` en verde sobre el mismo HEAD candidato o sobre un commit de cierre posterior que no altere funcionalidad.
4. Ninguna pantalla privada principal depende de datos demo/local cuando `NEXT_PUBLIC_API_URL` está configurado.
5. Los recorridos críticos funcionan de extremo a extremo con API real.
6. No se presentan como funcionales flujos GIS/localización que todavía sean solo UI.
7. Revisión móvil de Inicio, Mi Campo, Finca, Registrar, Hoy, Campaña, Profesional, Cliente, Presupuesto/Factura, Mapa, OCR y documento público.
8. Auditoría mínima de seguridad/performance automatizada y despliegue staging real antes de merge.

## Estado verificado de gates

Último HEAD de código/runtime completamente verificado: `b1dd423e002f582ff9de620f2e7cbc49b4c01e23`.

- ✅ `V20 full candidate check` #2070.
- ✅ `V20 beta browser E2E` #373.
- ✅ `V20 staging readiness` #25.

Ese HEAD valida conjuntamente candidate completo, navegador real, responsive, PostGIS 17, migraciones, seguridad en modo producción, build web sin preview y bundle budget. Además construye las imágenes Docker de API/worker, valida Tesseract/Poppler dentro del worker final y arranca PostGIS → migraciones → API containerizada hasta obtener `/health` correcto.

**Lo que todavía no se ha declarado como aprobado:** un staging externo con secretos, bucket, Google Auth, AEMET/VAPID y dominio HTTPS reales. Esa validación sigue siendo obligatoria antes de merge.

## Cambios ya aplicados durante el cierre

- ✅ Wizard de nueva finca: no falsea vínculo Catastro/SIGPAC/mapa; guarda finca real y deja geometría pendiente.
- ✅ `NEXT_PUBLIC_PREVIEW_MODE` separa preview de runtime real; GitHub Pages lo activa expresamente.
- ✅ Inicio, Mi Campo, Ficha, Nueva finca, Registrar, Mapa, Planificar, Hoy y Campaña son API-first en runtime real.
- ✅ Rutas `fincas/local` bloqueadas fuera de preview explícita.
- ✅ Registrar no escribe localmente fuera de preview.
- ✅ Campaña etiqueta `cobrado − costes registrados` como comparación informativa, no flujo de caja real.
- ✅ Documentos de finca ya exigen contexto API/workspace real.
- ✅ Revisión OCR legacy/parcial usa `extraction + confirmed_fields + corrections`, sin perder campos de la extracción base.
- ✅ Revisión OCR sin workspace muestra estado de sesión, no una carga infinita.
- ✅ Perfil deja de mostrar municipio demo y acciones decorativas como si fueran funcionales.
- ✅ Full candidate ejecuta `map-context-smoke`, `document-smoke`, economía, profesional, facturas y documento comercial.
- ✅ API incorpora CORS explícito con credenciales y allowlist de orígenes; en producción no se permite ningún origen si `CORS_ALLOWED_ORIGINS` no está configurado.
- ✅ `.env.example` documenta `CORS_ALLOWED_ORIGINS`, `NEXT_PUBLIC_PREVIEW_MODE=false`, `ALLOW_DEV_AUTH_HEADERS=false`, PostGIS, worker y OCR de staging.
- ✅ El workflow `V20 beta browser E2E` usa PostgreSQL/PostGIS, API real, Next real y Playwright Chromium.
- ✅ El recorrido browser cubre: identidad/membership real de CI → nueva finca → añadir límites → mapa → registrar trabajo → entrega de cosecha → rendimiento posterior → ficha de finca → campaña.
- ✅ El navegador cubre documento real: subida → storage controlado → integridad → persistencia → revisión OCR → prellenado.
- ✅ Los fallos Playwright conservan trace y screenshot como artifact para diagnóstico.
- ✅ Auditoría responsive automática en 360/390/430 px con Inicio, Mi Campo, Ficha, Mapa, Registrar, Registrar trabajo, Hoy, Campaña, revisión OCR, Profesional, Cliente, Presupuestos, presupuesto concreto, factura concreta, estados vacíos y Perfil.
- ✅ Documento público tiene comprobaciones móviles específicas.
- ✅ Pasada adicional de layout crítico en tablet 768 px y escritorio 1280 px.
- ✅ La auditoría comprueba scroll horizontal y controles interactivos claramente demasiado pequeños (<28 px) como umbral duro de regresión.
- ✅ API privada responde `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` y política de referrer/permisos.
- ✅ El hosting estático incluye `_headers` con hardening equivalente compatible con Cloudflare Pages.
- ✅ `V20 staging readiness` valida defaults seguros, migraciones PostGIS, security smoke de producción y build/bundle budget sin preview.
- ✅ Worker de producción incorpora adaptador Tesseract CLI: S3 → SHA-256 → límites → PDF/imagen → OCR → revisión humana.
- ✅ La imagen Docker del worker incluye Tesseract `spa+eng`, `pdfinfo` y `pdftoppm` y se valida en CI.
- ✅ `deploy/staging/docker-compose.yml` encapsula PostGIS, migraciones one-shot, API y worker.
- ✅ PostgreSQL no se publica al host; API solo se expone en loopback para proxy/túnel HTTPS.
- ✅ API/worker containerizados usan filesystem de solo lectura, `tmpfs`, `cap_drop: ALL` y `no-new-privileges`.
- ✅ Readiness construye las imágenes y levanta la API real containerizada con su base/migraciones.

## Clasificación

- `REAL`: consume API/datos persistidos y su flujo principal existe.
- `HYBRID CONTROLADO`: API real en Beta + preview explícita solo para demo.
- `PREVIEW`: contenido estático/demo o interacción no conectada.
- `P0`: bloquea Beta.
- `P1`: debe cerrarse antes de candidate final.
- `P2`: puede completarse después de Beta sin falsear funcionalidad.

## Inventario vivo

| Área | Estado | Prioridad | Hallazgo / acción |
|---|---|---:|---|
| Inicio privado | HYBRID CONTROLADO | P1 | API real en Beta; responsive automatizado. |
| Inicio público / actualidad | PREVIEW | P1 beta pública | Noticias, eventos y patrocinado siguen estáticos. |
| Mi Campo | HYBRID CONTROLADO | P1 | Fincas API reales; preview solo explícita; responsive verificado. |
| Ficha de finca | HYBRID CONTROLADO / E2E | P1 | API-first; navegador y responsive con finca real. |
| Nueva finca — datos básicos | REAL / E2E | — | Alta `/api/v1/fields` y catálogo territorial reales. |
| Nueva finca — localización | REAL / E2E PARCIAL | P1 | Alta de finca enlaza a Mapa; Catastro/SIGPAC manuales reales. Selección visual avanzada queda para después. |
| Rutas `fincas/local` | PREVIEW AISLADA | P1 | Bloqueadas fuera de preview. |
| Registrar | HYBRID CONTROLADO / E2E | P1 | Trabajo, cosecha y rendimiento cubiertos por Playwright. |
| Planificar | REAL | P1 | Tareas, filtros, calendario y ejecución integrados en el candidato. |
| Hoy / agenda | REAL | P1 | Responsive verificado; falta profundidad funcional solo si se exige para lanzamiento público. |
| Campaña | HYBRID CONTROLADO / E2E | P1 | Playwright valida kilos y rendimiento ponderado tras registros reales. |
| Profesional | REAL | P1 | Responsive verificado; candidate cubre summary/attention/delivery. |
| Presupuestos | REAL / E2E VISUAL | P1 | Listado, presupuesto concreto, cliente y factura concreta en matriz determinista. |
| Documentos/OCR | REAL / HARDENED / E2E / RUNTIME | P1 | Browser real + integridad; Tesseract de producción e imagen worker validados. Falta comprobar bucket/documento real en staging externo. |
| Perfil | REAL / HONESTO | P1 | Sin demo implícita ni CTAs ficticios; responsive verificado. |
| GIS Catastro/SIGPAC backend | REAL | P1 | Enlace manual real disponible desde Mapa; selección por click/visual queda fuera del cierre Beta núcleo. |
| Mapa Mi Campo | REAL / HYBRID CONTROLADO | P1 | API real con selección de finca y referencias Catastro/SIGPAC; demo solo en preview. |
| Tiempo AEMET | REAL | P1 | Backend real; proveedor/credenciales reales se validan en staging externo. |
| Radar | REAL / WORKER PREPARADO | P1 | Worker y almacenamiento preparados; credenciales/latencia reales se validan en staging externo. |
| Runtime staging backend | REAL / CI VALIDADO | P1 | Compose + imágenes + PostGIS + migraciones + API health verificados; falta host externo. |
| Explorar | PREVIEW | P2 beta privada / P1 beta pública | CMS/directorio incompleto. |
| Admin/CMS | INCOMPLETO | P2 beta privada / P0 lanzamiento público | Falta contenido, negocios, publicidad, imágenes y moderación. |
| Mi Olivo | BLUEPRINT | P2 | No bloquea Beta núcleo. |

## P0

### P0-1 — Wizard de finca simulaba vínculo GIS

**RESUELTO para Beta.** La finca se crea de forma real y la geometría/referencias se gestionan desde Mapa.

### P0-2 — Web y API en orígenes distintos sin política CORS explícita

**RESUELTO para Beta.** La API usa `@fastify/cors`, credenciales, headers limitados y allowlist mediante `CORS_ALLOWED_ORIGINS`. En producción, una configuración ausente no abre CORS por defecto.

## P1 — Preview/local

Regla de candidate:

- Con API configurada, nunca caer automáticamente a `demo-data` o `local-prototype-store` tras un error.
- Sin API, solo hay demo cuando `NEXT_PUBLIC_PREVIEW_MODE=true`.
- Un error real produce estado vacío/error; nunca datos inventados.
- Las rutas local-only no forman parte del flujo Beta.

Estado actual: núcleo privado principal corregido, incluyendo documentos, Perfil y Mapa.

## P1 — Recorridos E2E

### Agricultor

1. Login → workspace. **Browser E2E mediante identidad de CI + membership real.**
2. Crear finca. **Browser E2E.**
3. Posponer localización sin engaño. **Browser E2E.**
4. Añadir límites / abrir mapa de esa finca. **Browser E2E.**
5. Abrir finca. **Browser E2E.**
6. Registrar trabajo. **Browser E2E.**
7. Ver actividad y costes. **Browser E2E básico en ficha.**
8. Registrar entrega. **Browser E2E con 1.842 kg.**
9. Añadir rendimiento posterior. **Browser E2E con 21,4 %.**
10. Ver campaña. **Browser E2E valida kilos y rendimiento ponderado.**
11. Documento → upload → integridad → OCR/revisión → prellenado. **Browser E2E con storage fixture controlado.**
12. Tiempo/radar/alertas. **Smokes API; proveedor/credenciales reales se validan en staging externo.**

El full candidate cubre por smokes reales finca, riego/idempotencia, proyecciones, cosecha, rendimiento, map-context, documentos, economía y profesional. Playwright añade validación real del navegador y del límite web/API.

### Profesional

Candidate y matriz visual cubren cliente, presupuesto concreto, documento/factura concreta, attention, invoice y delivery. El flujo público compartir → aceptar/rechazar sigue teniendo checks especializados y documento público móvil; la validación final con dominio/URLs reales corresponde a staging.

## P1 — Auditoría móvil

Objetivos mínimos alcanzados de forma automática:

- 360 / 390 / 430 px para rutas críticas.
- Cliente, presupuesto concreto, factura concreta, Mapa, OCR y estados vacío/error incluidos.
- documento público con suite móvil específica.
- pasada crítica adicional a 768 px y 1280 px.
- sin overflow horizontal detectado en los gates verdes.
- sin controles críticos por debajo de 28 px en la matriz automatizada.

Pendiente exclusivamente de staging/manual: teclado móvil real, sensación táctil, contraste/foco final y comportamiento con latencia/red externa real.

## P1 — Seguridad / performance / runtime

Automatizado y verde:

- CORS restrictivo por allowlist.
- dev auth headers rechazados en producción.
- API privada `no-store`.
- `nosniff`, anti-frame, referrer y permissions policy en API.
- `_headers` equivalente para hosting estático.
- migraciones PostGIS 17 desde cero.
- build web con `NEXT_PUBLIC_PREVIEW_MODE=false`.
- bundle budget ejecutado como parte de `@magina/web build`.
- gate dedicado `V20 staging readiness`.
- Docker Compose validado.
- imágenes API y worker construidas desde el monorepo/lockfile.
- OCR verificado dentro de la imagen worker.
- API Docker levantada detrás de PostGIS + migraciones y health comprobado.
- DB sin puerto host y API limitada a loopback en la plantilla de staging.

Pendiente exclusivamente de staging externo:

- CSP basada en los orígenes externos definitivos.
- HSTS cuando el dominio HTTPS definitivo esté confirmado.
- latencia/requests iniciales con red real y mapa real.
- observabilidad persistente de API/worker.
- rate limiting por endpoint según exposición pública/privada.
- backup/restore real de staging.
- prueba externa de que PostgreSQL no es alcanzable.

## P1 — OCR de producción

**Implementado y validado a nivel de runtime.**

El worker de producción usa `OCR_PROVIDER=tesseract` y:

- descarga desde S3-compatible;
- limita tamaño;
- verifica SHA-256 de la versión documental encolada;
- limita páginas PDF;
- convierte PDF mediante Poppler;
- ejecuta Tesseract mediante `execFile`;
- impone timeout;
- limpia temporales;
- conserva revisión humana obligatoria.

La imagen incluye Tesseract con `spa+eng`, `pdfinfo` y `pdftoppm`; readiness comprueba esos binarios dentro del contenedor final.

Pendiente para cerrar staging real: subir un documento al bucket real de staging, procesarlo con el worker desplegado y confirmar revisión/prellenado desde la web real.

## P1 — Hardening conocido

- ✅ Candidate completo.
- ✅ Browser E2E verde sobre HEAD verificado.
- ✅ Documento/OCR visual con storage fixture controlado.
- ✅ Responsive móvil/tablet/escritorio automatizado.
- ✅ Security/performance readiness automatizado.
- ✅ OCR de producción conectado y runtime Docker verificado.
- ✅ Backend staging reproducible y smokeado en contenedores.
- Parser-specific OCR fixtures pueden ampliarse después de Beta.
- Races/idempotencia de liquidaciones/cobros siguen siendo hardening posterior si no bloquean el flujo Beta.
- Catálogo documental puede ampliar paginación/búsqueda/límites tras Beta.
- Observabilidad, CSP/HSTS/rate limiting definitivos dependen del host/dominio real.

## P2 — Después de Beta núcleo

- Admin/CMS completo.
- Noticias/eventos/pueblos/almazaras dinámicos.
- Cerca de ti/publicidad/promociones.
- Mi Olivo.
- Monetización/planes.
- Nowcast solo con metodología validada.
- Selección GIS avanzada por click/recinto sobre mapa si no entra en el cierre inmediato.

## Orden de ejecución actual

1. ~~Resolver P0 del wizard de finca.~~ ✅
2. ~~Convertir preview implícita del núcleo privado en modo explícito.~~ ✅
3. ~~Auditar documentos/OCR y Perfil.~~ ✅
4. ~~E2E de navegador y reparaciones.~~ ✅
5. ~~Cosecha/rendimiento en E2E.~~ ✅
6. ~~Documento/OCR con storage fixture controlado.~~ ✅
7. ~~Auditoría responsive móvil/tablet/escritorio.~~ ✅
8. ~~Security/performance readiness automatizado.~~ ✅
9. ~~Conectar/probar OCR de producción y preparar runtime de staging.~~ ✅
10. Ejecutar staging externo con secretos, bucket, Google Auth, AEMET/radar, VAPID y dominio HTTPS reales.
11. Validar backup/restore, observabilidad y recorridos reales.
12. Actualizar PR y decidir candidate final.

Este documento es el checklist vivo de Cierre Beta. No se añade una función grande nueva salvo que cierre un P0/P1.
