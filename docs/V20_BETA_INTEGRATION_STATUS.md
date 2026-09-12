# Estado de integración V20 Beta

- Rama coordinadora: `integrate/v20-beta-closure`
- Base inicial: `feat/v20-visual-prototype` @ `99fcd6ed3eb3a6491467210fe8c45737b0c94794`
- `main`: **no tocar** durante este cierre.
- Gate de fase: **funcional pre-visual**.

## Referencia funcional verificada

La integración funcional interna de V20 queda cerrada y revalidada sobre:

`c7acf3c6f049ff55dea1e40542b5dccb0af1aee6`

Este SHA reúne en una única rama el núcleo agricultor, Mi Campo/Campaña, Profesional, Documentos/OCR, superficies públicas, Mercado, Admin/CMS/Territorio, Mi Olivo, Planes, Centro de Avisos, Engineering Foundation, Runtime hardening, QA móvil/browser, selector GIS real, Clima/Radar funcional y Herramientas rápidas.

## Gates verdes sobre la referencia funcional

- ✅ V20 full candidate check #2313
- ✅ V20 beta browser E2E #625
- ✅ V20 staging readiness #226
- ✅ V20 foundation check #115
- ✅ V20 runtime hardening check #65
- ✅ V20 platform admin check #184
- ✅ V20 notification center check #44
- ✅ V20 plans check #111
- ✅ V20 Mi Olivo check #67
- ✅ V20 environment contract #132
- ✅ V20 lockfile guard #116
- ✅ V20 visual preview / GitHub Pages #617

Los workflows dedicados GIS y Weather/Radar quedaron verdes tras su absorción en la coordinadora. Full Candidate y Browser E2E revalidan la convivencia integrada sobre el SHA indicado.

## Pages y navegación

GitHub Pages #617 pasó build, validación de enlaces internos y deploy. El workflow contiene un guard que falla si un enlace interno escapa del `basePath` `/magina-olivo-v20`.

Se corrigieron los accesos rápidos de Admin (`Fuentes`, `Territorio`, `Multimedia`, `Editar web`) para usar `Link` de Next y respetar el `basePath`. También se endureció el E2E de Explorar para aceptar `/ruta` y `/ruta/`, equivalentes en la exportación estática, sin alterar las rutas del producto.

## Cobertura móvil y recorridos clave

La matriz Browser E2E recorre en 360/390/430 el flujo Agricultor:

`Finca → trabajo → cosecha → rendimiento → ficha → campaña`.

La suite móvil cubre además las rutas principales de Mi Campo y Profesional, comprobando respuesta válida, ausencia de errores de página, ausencia de overflow y controles utilizables.

Profesional conserva el E2E completo:

`cliente → presupuesto → trabajo → factura → documento/compartir → cobro`.

`/herramientas` queda también cubierta a 360/390/430 con cálculos locales, coma decimal, controles táctiles y ausencia de overflow.

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

### Herramientas rápidas

- conversiones m² ↔ ha;
- marco rectangular y densidad teórica;
- estimación opcional de número de olivos;
- costes €/kg y €/ha a partir de datos introducidos;
- cálculo completamente local, sin persistencia ni red;
- sin dosis, mezclas, fitosanitarios, fertilización ni recomendaciones agronómicas sensibles.

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
6. Google Identity real;
7. VAPID/notificaciones reales;
8. CMS/multimedia real;
9. backup/restore real en el host de staging;
10. smoke post-deploy HTTPS externo;
11. recorridos Agricultor/Profesional sobre ese host;
12. auditoría visual/manual final en 360/390/430 y escritorio.

El detalle operativo vive en `docs/V20_BETA_EXTERNAL_VALIDATION.md`.

## Regla de promoción

PR #58 puede considerarse **funcionalmente integrada y CI-verde** sobre la referencia indicada, pero debe permanecer en **Draft** y sin fusionar al candidate hasta completar la validación externa anterior o decidir explícitamente que alguno de esos servicios queda fuera del alcance de la Beta.

No iniciar un rediseño visual global que oculte defectos funcionales. Una vez validado staging real y la auditoría manual, el siguiente frente puede ser el rediseño/pulido visual sobre esta base estable.
