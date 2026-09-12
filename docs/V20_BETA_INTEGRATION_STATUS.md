# Estado de integración V20 Beta

- Rama coordinadora: `integrate/v20-beta-closure`
- Base inicial: `feat/v20-visual-prototype` @ `99fcd6ed3eb3a6491467210fe8c45737b0c94794`
- `main`: **no tocar** durante este cierre.
- Gate de fase: **funcional pre-visual**.

## Estado actual

La integración funcional interna de V20 queda cerrada sobre el merge commit:

`4a04410686eed936b12d359101f1ff2a0faa6fc0`

Este HEAD reúne en una única rama el núcleo agricultor, Mi Campo/Campaña, Profesional, Documentos/OCR, superficies públicas, Mercado, Admin/CMS/Territorio, Mi Olivo, Planes, Centro de Avisos, Engineering Foundation, Runtime hardening, QA móvil/browser, selector GIS real y Clima/Radar funcional.

## Gates verdes sobre el mismo HEAD

- ✅ V20 full candidate check #2286
- ✅ V20 beta browser E2E #598
- ✅ V20 staging readiness #199
- ✅ V20 GIS finca selector check #789
- ✅ V20 weather radar map closure #13
- ✅ V20 foundation check #88
- ✅ V20 runtime hardening check #38
- ✅ V20 platform admin check #157
- ✅ V20 notification center check #17
- ✅ V20 plans check #84
- ✅ V20 Mi Olivo check #40
- ✅ V20 environment contract #105
- ✅ V20 lockfile guard #89

No queda ningún rojo conocido en el HEAD coordinador.

## P0/P1 funcionales internos cerrados

### GIS real de Finca

- selección real Catastro/SIGPAC en alta/edición;
- geometría canónica persistida;
- recuperación al volver a editar;
- estados sin geometría/error;
- E2E dedicado y convivencia con mapa/clima.

### Clima/Radar

- AEMET por finca;
- radar de reflectividad observada;
- overlay PNG georreferenciado por `bbox`;
- endpoint binario autenticado;
- ingestión periódica con timestamp real;
- estados `loading/error/stale/sin datos` independientes;
- degradación segura por fuente;
- sin nowcast, ETA ni conversión dBZ→mm/h no validada.

### Integración transversal

- GIS + Radar conviven en `farm-map.tsx`;
- Runtime mantiene `/ready`, request-id, rate limiting y logs;
- Foundation mantiene lockfile, env contract, contenedores inmutables y CI con Actions fijadas por SHA;
- Admin, Planes, Mi Olivo, Avisos, Mercado y los flujos agricultor/profesional siguen incluidos en los gates coordinados.

## Bloqueo restante antes de declarar Beta externa cerrada

Ya no es un bloqueo de código interno. Queda validación de entorno real:

1. staging externo con credenciales y host reales;
2. PostgreSQL/PostGIS real y migraciones contra el entorno objetivo;
3. S3/R2 real y lectura/escritura de documentos/radar;
4. OCR/worker real;
5. AEMET/radar real desde el host;
6. Google Auth si se habilita en Beta;
7. VAPID/notificaciones reales si se habilitan;
8. CMS/multimedia real;
9. backup/restore real en el host de staging;
10. smoke post-deploy externo;
11. auditoría visual/manual final en 360/390/430 y escritorio.

El detalle operativo vive en `docs/V20_BETA_EXTERNAL_VALIDATION.md`.

## Regla de promoción

PR #58 puede considerarse **funcionalmente integrada y CI-verde**, pero debe permanecer sin fusionar al candidate hasta completar la validación externa anterior o decidir explícitamente que alguno de esos servicios queda fuera del alcance de la Beta.

No iniciar un rediseño visual global que oculte defectos funcionales. Una vez validado staging real y la auditoría manual, el siguiente frente puede ser el rediseño/pulido visual sobre esta base estable.

## Trabajo no bloqueante

`Herramientas rápidas` y otras mejoras aisladas no forman parte del cierre funcional interno. Pueden integrarse después de congelar esta referencia o pasar a post-Beta para no reabrir el conjunto ahora que está verde.
