# Estado de integración V20 Beta

- Rama coordinadora: `integrate/v20-beta-closure`
- Base inicial: `feat/v20-visual-prototype` @ `99fcd6ed3eb3a6491467210fe8c45737b0c94794`
- `main`: **no tocar** durante este cierre.
- Gate de fase: **funcional pre-visual**.

## Referencia funcional verificada

La integración funcional interna de V20 queda cerrada y revalidada sobre:

`3a934e979fa6f279316c75a61ac9610432786887`

Este SHA reúne en una única rama el núcleo agricultor, Mi Campo/Campaña, Profesional, Documentos/OCR, superficies públicas, Mercado, Admin/CMS/Territorio, Mi Olivo, Planes, Centro de Avisos, Engineering Foundation, Runtime hardening, QA móvil/browser, selector GIS real y Clima/Radar funcional.

Los commits posteriores que modifiquen exclusivamente documentación no sustituyen esta referencia funcional salvo que se indique expresamente una nueva tanda de validación.

## Gates verdes sobre la referencia funcional

- ✅ V20 full candidate check #2308
- ✅ V20 beta browser E2E #620
- ✅ V20 staging readiness #221
- ✅ V20 foundation check #110
- ✅ V20 runtime hardening check #60
- ✅ V20 platform admin check #179
- ✅ V20 notification center check #39
- ✅ V20 plans check #106
- ✅ V20 Mi Olivo check #62
- ✅ V20 environment contract #127
- ✅ V20 lockfile guard #111
- ✅ V20 visual preview / GitHub Pages #612

Los workflows dedicados GIS y Weather/Radar quedaron verdes tras su absorción en la coordinadora. No se retriggeraron por los últimos cambios, que no tocaron su lógica funcional; Full Candidate y Browser E2E sí revalidaron la convivencia integrada.

## Auditoría del artefacto Pages

Se auditó el artefacto exacto desplegado por `V20 visual preview #612`:

- 70 HTML revisados;
- 346 enlaces `<a>` revisados;
- 0 enlaces internos escapando de `/magina-olivo-v20`;
- 0 destinos internos inexistentes;
- 0 atributos `src` o `action` root-absolute fuera del `basePath`.

Se corrigieron los cuatro accesos rápidos de Admin (`Fuentes`, `Territorio`, `Multimedia`, `Editar web`) para usar `Link` de Next y respetar el `basePath`. El workflow de Pages contiene ahora un guard que falla si un enlace interno vuelve a escapar del prefijo configurado.

También se endureció el E2E de Explorar para aceptar `/ruta` y `/ruta/`, equivalentes en la exportación estática, sin alterar las rutas del producto.

## Cobertura móvil y recorridos clave

La matriz Browser E2E recorre en 360/390/430 el flujo Agricultor:

`Finca → trabajo → cosecha → rendimiento → ficha → campaña`.

La suite móvil cubre además las rutas principales de Mi Campo y Profesional, comprobando respuesta válida, ausencia de errores de página, ausencia de overflow y controles utilizables.

Profesional conserva el E2E completo:

`cliente → presupuesto → trabajo → factura → documento/compartir → cobro`.

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
- Admin, Planes, Mi Olivo, Avisos, Mercado y los flujos agricultor/profesional siguen incluidos en los gates coordinados;
- la exportación Pages queda protegida contra navegación que ignore el `basePath`.

## Bloqueo restante antes de declarar Beta externa cerrada

Ya no es un bloqueo de código interno. Queda validación de entorno real:

1. staging externo con host y credenciales reales;
2. PostgreSQL/PostGIS real y migraciones contra el entorno objetivo;
3. S3/R2 real y lectura/escritura de documentos/radar;
4. OCR/worker real;
5. AEMET/radar real desde el host;
6. Google Identity si se habilita en Beta;
7. VAPID/notificaciones reales si se habilitan;
8. CMS/multimedia real;
9. backup/restore real en el host de staging;
10. smoke post-deploy HTTPS externo;
11. recorridos Agricultor/Profesional sobre ese host;
12. auditoría visual/manual final en 360/390/430 y escritorio.

El detalle operativo vive en `docs/V20_BETA_EXTERNAL_VALIDATION.md`.

## Regla de promoción

PR #58 puede considerarse **funcionalmente integrada y CI-verde** sobre la referencia indicada, pero debe permanecer en **Draft** y sin fusionar al candidate hasta completar la validación externa anterior o decidir explícitamente que alguno de esos servicios queda fuera del alcance de la Beta.

No iniciar un rediseño visual global que oculte defectos funcionales. Una vez validado staging real y la auditoría manual, el siguiente frente puede ser el rediseño/pulido visual sobre esta base estable.

## Trabajo no bloqueante

`Herramientas rápidas` y otras mejoras aisladas no forman parte del cierre funcional interno. Pueden integrarse después de congelar esta referencia o pasar a post-Beta para no reabrir el conjunto ahora que está verde.
