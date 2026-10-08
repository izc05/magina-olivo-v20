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
| Servicios/Avisos (#332/#462/#496/#619) | Servicios meteorológicos/mercado y avisos presentes | No se han repetido freshness/fallback/medianoche/notificación efectiva | No acreditado | Claude; modo avión, dato caducado, permiso denegado, reinicio y zona Workspace |
| Cuenta/nube/seguridad/recuperación (#325/#335/#330/#321) | Outbox/Room identificados; Auth/RLS/Storage/restore productivo no acreditados | Falta gate 22/23, aislamiento A/B, idempotencia y cambio de móvil | No acreditado | Claude/backend; inventario y evidencia real antes de afirmar copia cloud; Codex solo preparación |
| Adjuntos (#517/#430) | #692 implementado, pendiente integrar; datos y Sync sin cambio | SHA f84da9b0: 14 unitarios + 4 Compose API35 locales; lint/APK correctos; tres checks remotos SUCCESS | No acreditado | Claude integración/revisión; propietario cámara real; no confundir UI labels con ciclo íntegro de Storage |
| Web V3/WEB-1 (#389/#394/#552/#598/#691/#591/#326) | Cadena web pendiente; Home estática y board conceptual, /mi DEMO | #691 validate/preview SUCCESS; CI histórica no acredita consolidación sobre main | Capturas móviles Chromium; no dispositivo físico acreditado | Codex; gate assets/continuidad y motor/fallback; portar estado aprobado selectivamente desde main |

## Bloqueos P0

1. #680 y #689 no integrables: fallos reproducibles en CI al SHA actual. [Handoff detallado](ANDROID-CI-HANDOFF-680-689.md).
2. #591: Home cinematográfica/continuidad pendiente. Home estática no completa #389. No fusionar la cadena histórica; rama final desde main y repetir CI/Docker/Pages.
3. Gate 22/23 y recuperación de datos no acreditados; persistencia local/outbox no demuestran backup ni aceptación oficial CUE.
4. Aceptación física de los recorridos completos, migración sin desinstalar y release firmado no acreditadas.

## Cobertura restante de #340 (sin casillas aprobadas por intuición)

Esta primera matriz cubre flujos agrupados; aún requiere inventario por subrequisito A1–A23. Pendientes de evidencia adicional: Admin/roles (#327/#322), soporte/feedback/ayuda (#323), contenido/cooperativas/avisos remotos, publicidad y privacidad (#324/#336), mantenimiento seguro/tombstones, telemetría aprobada (#331), exportación/eliminación de cuenta (#320), informes #328 y publicación #334. No se clasifican como implementados o ausentes sin inspección específica.

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

**NO-GO para declarar 1.0 terminado.** Motivo: gates Android rojos, Home V3 incompleta, nube/restore y prueba física integral sin evidencia. No se modifica app/** ni se corrigen ramas Android. Próximo slice Codex: completar inventario A1–A23 y auditoría Web V3 conforme #693; Claude recibe el handoff mediante documentación enlazada, sin mensaje automático a otro chat.
