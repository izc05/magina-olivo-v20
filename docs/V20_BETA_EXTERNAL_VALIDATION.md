# V20 Beta — Validación externa antes de promoción

Este checklist empieza **después** del cierre funcional interno de `integrate/v20-beta-closure`.

Referencia interna verde previa:

- integración funcional: `4a04410686eed936b12d359101f1ff2a0faa6fc0`;
- Full Candidate #2286 ✅;
- Browser E2E #598 ✅;
- Staging Readiness #199 ✅;
- GIS #789 ✅;
- Weather/Radar #13 ✅.

Los commits exclusivamente documentales posteriores no invalidan esa referencia funcional.

## 1. Preflight del host real

Comprobar antes del deploy:

- Docker/Compose disponibles;
- DNS/TLS del staging resuelven al host correcto;
- espacio libre suficiente;
- reloj/NTP correcto;
- acceso al registro de imágenes si aplica;
- variables de entorno cargadas sin secretos en el repositorio;
- directorios persistentes de PostgreSQL y storage montados;
- política de backup definida.

**Criterio:** ningún servicio arranca con credenciales de desarrollo ni cabeceras preview inseguras.

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

## 7. Auth real

Si Google Auth forma parte de esta Beta:

- callback/redirect correcto en staging;
- creación/lectura del usuario;
- membership/workspace;
- logout/login;
- acceso denegado a recursos de otro workspace.

Si no forma parte del alcance inmediato, dejarlo explícitamente documentado como diferido.

## 8. Notificaciones / VAPID

Si se habilitan en Beta:

- claves VAPID del entorno;
- suscripción real;
- envío de notificación de smoke;
- aislamiento por usuario/workspace;
- tolerancia a suscripciones expiradas.

Si se difiere, registrar la decisión de alcance.

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

**Criterio:** restore verificable, no solo creación del archivo de backup.

## 11. Smoke post-deploy

Recorrido mínimo externo:

1. abrir web de staging;
2. autenticar;
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
15. revisar Centro de Avisos.

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

- el staging externo anterior esté validado o cada integración diferida esté documentada;
- backup/restore real esté probado;
- smoke post-deploy pase;
- auditoría manual no deje P0/P1;
- la rama coordinadora siga sin regresiones funcionales.

No fusionar directamente a `main` como parte de este checklist.
