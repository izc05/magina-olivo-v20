# Mágina Olivo 1.0 — matriz de cierre verificable

Primer slice #693, auditoría 2026-10-08. Base `main c4617f87`. Fuentes normativas: [#340](https://github.com/izc05/magina-olivo-v20/issues/340), [#338](https://github.com/izc05/magina-olivo-v20/issues/338), [#693](https://github.com/izc05/magina-olivo-v20/issues/693). No es aceptación de release ni una segunda hoja de ruta.

**Estados separados:** implementado = código identificado; integrado = PR/commit en main; probado = ejecución con SHA y resultado; móvil validado = aceptación física documentada del propietario. `No acreditado` significa que falta evidencia en este slice, no prueba de inexistencia. Ningún flujo completo se declara cerrado por un test parcial.

## Flujos obligatorios

| Flujo / issues | Implementación e integración observadas | Prueba / CI identificada | Móvil físico | Responsable / bloqueo / siguiente paso |
| --- | --- | --- | --- | --- |
| Finca → Parcela → Campaña (#356/#409/#427/#428/#511) | Núcleo presente; cierre integral de altas/archivo/contexto no acreditado aquí | Falta recorrido E2E fijado al main actual | No acreditado para cierre 1.0 | Claude; probar alta, herencia y archivo con dependencias; registrar PR integrada de cada defecto |
| Recolección (#409/#463/#482/#497/#500) | #685 scope y #687 cierre de jornadas integrados; #683 integrado en 275b5a11; #684/#689 pendientes | #684 tres checks SUCCESS; #689 tres FAILURE al SHA actual, ver handoff | No acreditado | Claude; reparar compilación #689, conservar desconocido ≠ cero y verificar parcelas/vale/cooperativa/rendimiento/jornales/maquinaria/gastos |
| Cuaderno fuera de campaña (#409/#410/#416/#418) | #426 integrado f4b09148; presencia de Activity no cierra pertenencia/costes | No hay nueva ejecución integral en este slice | No acreditado | Claude; demostrar Trabajo/Riego/Tratamiento/Jornal/Gasto sin campaña y sin doble imputación |
| Fitosanitarios (#534/#535/#544/#553/#677) | Recursos UI #676 integrados b695469f; tratamiento v25 #680 pendiente; catálogo y gateway solo PREP | #680 foundation SUCCESS, dos gates emulador FAILURE; #688/#690 documentación integrada | No acreditado | Claude modelo/UX; Codex contratos; resolver scope/fixtures y luego escenario completo, sin afirmar aceptación CUE |
| Mapa/Catastro/SIGPAC (#361/#574/#540) | Mapas presentes; geometría/importación no equivale a reconciliación oficial | Cámara útil/etiquetas/búsqueda y SIGPAC no reprobados aquí | No acreditado para cierre | Claude; evidencia geométrica y captura desktop no sustituyen teléfono; Codex documenta reconciliación |
| Consulta/estadísticas/PDF (#328/#340/#465/#464) | Núcleo de consulta presente; informe PDF completo no acreditado; #689 bloquea totales seguros | Falta conciliación de resumen/detalle, cobertura de rendimiento y totales parciales | No acreditado | Claude; probar por finca/campaña/parcela, parcialidad, monedas y PDF offline |
| Servicios/Avisos (#332/#462/#496/#619) | Servicios meteorológicos/mercado y avisos presentes | Forecast fixtures 18/18; #669 integrado; lifecycle/medianoche/notificación efectiva en teléfono pendientes | No acreditado | Claude; modo avión, dato caducado, permiso denegado, reinicio y zona Workspace |
| Cuenta/nube/seguridad/recuperación (#325/#335/#330/#321) | Outbox/Room identificados; Auth/RLS/Storage/restore productivo no acreditados | Falta gate 22/23, aislamiento A/B, idempotencia y cambio de móvil | No acreditado | Claude/backend; inventario y evidencia real antes de afirmar copia cloud; Codex solo preparación |
| Adjuntos (#517/#430) | #692 implementado, pendiente integrar; datos y Sync sin cambio | SHA f84da9b0: 14 unitarios + 4 Compose API35 locales; lint/APK correctos; tres checks remotos SUCCESS | No acreditado | Claude integración/revisión; propietario cámara real; no confundir UI labels con ciclo íntegro de Storage |
| Web V3/WEB-1 (#389/#394/#552/#598/#691/#591/#326) | Cadena web pendiente; Home estática y board conceptual, /mi DEMO | #691 validate/preview SUCCESS; CI histórica no acredita consolidación sobre main | Capturas móviles Chromium; no dispositivo físico acreditado | Codex; gate assets/continuidad y motor/fallback; portar estado aprobado selectivamente desde main |

## Bloqueos P0

1. #680 y #689 no integrables: fallos reproducibles en CI al SHA actual. [Handoff detallado](ANDROID-CI-HANDOFF-680-689.md).
2. #591: Home cinematográfica/continuidad pendiente. Home estática no completa #389. No fusionar la cadena histórica; rama final desde main y repetir CI/Docker/Pages.
3. Gate 22/23 y recuperación de datos no acreditados; persistencia local/outbox no demuestran backup ni aceptación oficial CUE.
4. Aceptación física de los recorridos completos, migración sin desinstalar y release firmado no acreditadas.

## Cobertura restante de #340 (sin casillas aprobadas por intuición)

Esta primera matriz cubre flujos agrupados; incluye a continuación inventario por subrequisito A1–A23; falta acreditar ejecución por requisito. Pendientes de evidencia adicional: Admin/roles (#327/#322), soporte/feedback/ayuda (#323), contenido/cooperativas/avisos remotos, publicidad y privacidad (#324/#336), mantenimiento seguro/tombstones, telemetría aprobada (#331), exportación/eliminación de cuenta (#320), informes #328 y publicación #334. No se clasifican como implementados o ausentes sin inspección específica.

OCR de Pesadas está fuera de 1.0 (#342/#448); entrada manual + foto opcional. Features 2.0 no entran en esta cola.

## Matriz E2E a ejecutar después de reparar gates

| Escenario | Evidencia exigida / responsable |
| --- | --- |
| Sin cuenta y modo avión | Claude: alta/consulta/edición reinicio; Room conserva registros y servicios externos no bloquean |
| Dataset grande | Claude: volúmenes documentados, latencia/memoria, totales sin overflow ni pérdida de cobertura |
| Upgrade sin desinstalar | Claude/propietario: APK origen/destino, schema, registros/adjuntos antes-después |
| Outbox, retry y conflictos | Gate 23: replay/idempotencia, pérdida de red, orden, errores, aislamiento A/B |
| Cambio de móvil | Gate 22/23: login/restore, adjuntos privados, totales/históricos; no basta export local |
| Accesibilidad y PDF | Claude/propietario: fuente ampliada, TalkBack, contraste exterior, PDF conciliado y compartible offline |
| Privacidad/seguridad | Backend: RLS A/B, export/borrado, no secretos en APK, limpieza con gracia y ownership |

Guion físico: crear finca/parcela → registrar trabajo general sin campaña → activar recogida → dos Pesadas con destinos distintos y reparto parcial → rendimiento parcial → jornales/pagos/maquinaria/gasto → foto del vale → revisar totales y cobertura → modo avión/reinicio → avisos/archivo → upgrade. Registrar build, teléfono/Android, fecha, resultado y defecto por paso. Prueba cloud/restore solo cuando los gates reales estén disponibles.

## Decisión actual

**NO-GO para declarar 1.0 terminado.** Motivo: gates Android rojos, Home V3 incompleta, nube/restore y prueba física integral sin evidencia. No se modifica app/** ni se corrigen ramas Android. Próximo slice Codex: Web V3 B1–B9 conforme #693; Claude recibe el handoff mediante documentación enlazada, sin mensaje automático a otro chat.

## Inventario A1–A23 del alcance #340

Auditoría estática al main c4617f87; evidencia de tests existentes no significa que se hayan ejecutado en esta auditoría. Este inventario completa la cobertura de requisitos, no su implementación. Los enlaces a issues canónicos se resuelven en el repositorio izc05/magina-olivo-v20.

| ID / requisito | Estado y evidencia concreta | Integración / prueba acreditada | Responsable | Pendiente exacto / gate |
| --- | --- | --- | --- | --- |
| A1 Android agrícola | Parcial en main: paquetes feature de fincas/parcelas/cuaderno/recogida/gastos/avisos | Flujos en tabla anterior; #680/#689 rojos; #692 PR verde, no integrada | Claude | Recorrido integral + defectos P0; Gate21 y beta física |
| A2 Cuenta/nube #325/#335 | Pendiente: HelpScreens.kt dice hoy sin cuenta/copia cloud; supabase/ contiene tres familias de servicios externos | Auth/schema/RLS/Storage productivos no acreditados | Claude/backend | Cuenta opcional y aislamiento A/B; Gate22 |
| A3 Sync/recuperación #330/#321 | Outbox local presente: SyncOutboxDao/Entity, OutboxWriter; motor cloud y restore no acreditados | No ejecución de sync real documentada aquí | Claude/backend | Retries, idempotencia, conflictos, tombstones, adjuntos y segundo móvil; Gate23 |
| A4 Centro de estado Sync | No acreditado; no equiparar chip Guardado localmente con backup | HelpScreens.kt distingue teléfono/nube | Claude | Estados/pending/conflictos/último éxito y acción real; Gate23 |
| A5 Compatibilidad upgrades | RoomMigrationTest.kt existe; no prueba nueva de upgrade completo | Existencia del test, no ejecución en este slice | Claude | APK anterior→actual sin desinstalar, datos/outbox/adjuntos intactos y posterior sync; Gates22/23/26 |
| A6 Recuperación desastre | Backup/restore cloud y rollback de infraestructura no acreditados | Sin restore real nuevo | Backend/propietario | Plan y ensayo en entorno seguro, alcance y RPO/RTO demostrados; Gate22/23 |
| A7 Seguridad final | Aislamiento local tiene gates parciales; RLS/Storage/sesión/Admin no acreditados | #680 scope falla; no scan completo nuevo | Backend/Claude | RLS A/B, no secrets, logs/token y funciones privilegiadas; Gate22 y26 |
| A8 Accesibilidad campo | Pruebas Compose y Web presentes; #692 4 UI/14 unitarios pasan | #691 Web verde; no beta física integral | Claude/Codex/propietario | Texto grande/TalkBack/sol/teclado y tamaños; Gate26/27 |
| A9 Dataset grande | No benchmark integral acreditado; no extrapolar cifras de demo | Falta dataset/timing/RSS versionados | Claude | Arranque/cuaderno/gráficos/PDF/sync con cientos/miles de filas; Gate26 |
| A10 Degradación externa #332 | weather-forecast/radar/oil-market en main; módulos Home/Catastro presentes | Fallo por proveedor y local-first integral no reejecutados | Claude/backend | Stale visible, cache/retry/kill switch donde aplique, modo avión; Gate26 |
| A11 Admin #327 | Implementación completa no acreditada; /mi web es DEMO, no Admin | Sin prueba roles/admin server-side | Backend/web | Roles, soporte/contenido/salud/auditoría sin acceso indiscriminado; Gate24 |
| A12 Cooperativas #333 | Organization local y destino histórico no acreditan ficha oficial/avisos remotos | No prueba targeting/cache/privacidad nueva | Claude/backend | Organización verificada, vínculo, aviso/fallback; Gate24 |
| A13 Publicidad #337 | Lead/campaña/creatividad/métricas reales no acreditados | DEMO web no constituye publicidad operativa | Backend/web/Claude | MVP municipal revisado/expirable; sin targeting agrícola ni pagos; Gate24C |
| A14 PDF/reportes #328 | Consulta/gráficos presentes; paquete de reportes completo no identificado en feature actual | Conciliación/PDF offline/compartir no acreditados | Claude/web | ReportSnapshot, finca/parcela/campaña, parcialidad/anonimización; Gate25 |
| A15 Web #326 | Árbol Web V3 pendiente de consolidación; /mi DEMO; Auth/cloud no conectados | #691 validate/Pages verdes; no Gate591/WEB1 | Codex/backend | V3-A, CI desde main, read-only con Auth tras22/23; Gate591 y WEB1 |
| A16 Soporte #323 | Help/Profile presentes; ticket con diagnóstico sanitizado no acreditado | Sin envío/ticket probado | Claude/backend/web | Sin cuenta y ticket con cuenta, versión y Admin; Gate24 |
| A17 Ayuda | HelpScreens.kt integrado; cobertura de todas las materias #340 no auditada completa | No recorrido Perfil→cada ayuda nuevo | Claude/Codex | Matriz ayuda vs altas/pesadas/sync/cambio móvil/PDF/privacidad; Gate26 |
| A18 Feedback | Flujo Sugerir mejora separado de problema no acreditado | Sin prueba de contacto/consentimiento | Claude/backend | Mensaje/categoría/contacto opcional, sin diagnóstico automático; Gate24 |
| A19 Mantenimiento | Metadata outbox/adjuntos no acredita cleanup seguro | Idempotencia/gracia/ownership de cleanup no acreditadas | Backend/Claude | Inventario huérfanos/cache/tokens/tombstones/logs; Gates23/24/26 |
| A20 Privacidad/legal #320/#336/#334 | Ayuda describe almacenamiento local; obligaciones cloud/Store no cerradas | No Data Safety/export/borrado de cuenta probado | Propietario/backend/Claude | SDK/proveedores/retención/privacidad/términos coherentes y borrados separados; Gate22/28 |
| A21 Telemetría #331 | Proveedor/activación no acreditados | Ninguna métrica real asumida | Propietario/backend | Decidir provider/privacidad antes de activar; no bloquea núcleo si desactivada |
| A22 QA/beta | Gates de PR parciales, no aceptación global | #684/#692 verdes; #680/#689 rojos; móvil físico integral pendiente | Claude/Codex/propietario | Torture/performance/migración/sync y días en campo; Gates26/27 |
| A23 Store #334 | Signing/track/rollback/RC no acreditados | No publicación ni APK candidata validados aquí | Propietario/Claude | Firma, assets/legal, tracks, crash/ANR y rollout/rollback; Gate28 |

**Fuera de 1.0:** OCR de Pesadas (#342), edición web completa sin gate adicional, features 2.0 de #340. No se trasladan a blockers de release por decisión de esta matriz.
