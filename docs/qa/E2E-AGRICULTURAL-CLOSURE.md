## Estado vigente — #696, 8 de octubre de 2026 (15:15 UTC)

Prioridad: Android; Codex ejecuta una sola tarea productiva cada vez, Claude apoya revisión/pruebas. **Web V3 y Web-1 pausadas** hasta orden expresa. #696 sustituye el reparto/cursor anteriores.

| PR | HEAD validado | Merge en main | Validación final |
| --- | --- | --- | --- |
| #689 | 6ad5848a | 8618a721 | 514 unitarios; 671 instrumentados CI; tres checks SUCCESS |
| #680 | 73f79a02 | cda14003 | 517 unitarios; 676 instrumentados CI; offline196; tres checks SUCCESS |
| #684 | d30e3ec4 | 690e2195 | 520 unitarios; parser extremo y persistencia UI API35; tres checks SUCCESS |
| #692 | 4157013d | c2556e4e | 524 unitarios; 677 instrumentados CI; offline196; tres checks SUCCESS |

Main integrado: `c2556e4e96787faea7fde015a850b91404f9c0f2`. Los informes ANDROID-689-FIX, ANDROID-680-FIX, ANDROID-684-INTEGRATION y ANDROID-692-INTEGRATION de esta carpeta y los comentarios DONE en #696 conservan ramas, commits, pruebas y gates. Ninguna de estas cuatro PR sigue bloqueada por los errores descritos en la auditoría inicial. En #680 la causa confirmada fue la selección del workspace en fixtures; no se relajó ownership productivo. Además se corrigió la mezcla de snapshots regulatorios y se probó migración soportada 1–24→25.

NEXT vigente: ejecutar recorrido agrícola sobre este main, preparar candidata APK y evidencia de actualización sin borrar datos; aceptación física por el propietario pendiente (Gate21). La cámara virtual y tests no acreditan móvil físico. Exportación PDF pendiente Fase25; Gates22–28 no abiertos. #694 conserva evidencia histórica, no ejecuta Web ni acepta release.

### Auditoría histórica conservada

**Todo lo que sigue describe el snapshot inicial c4617f87 / af1384ab, anterior a #696.** Sus fallos CI, PRs pendientes, asignaciones y próximos pasos son históricos y quedan sustituidos por el estado vigente de arriba. Sus resultados Web se conservan como evidencia previa y no autorizan reanudarla. La existencia de tests o guiones en aquel snapshot no acredita recorrido completo.
# Guion E2E agrícola — A2/A3 #695

Solo auditoría/preparación al main c4617f87. Responsable de ejecución Android: Claude; físico: propietario. No ejecutado en esta entrega. No insertar datos reales ni confundir fixture con explotación registrada.

## Evidencia por paso

Guardar build/SHA, teléfono/emulador/Android/zona horaria, estado de red, IDs de fixture, pasos, expected/actual, captura sanitizada y resultado. Tras cada escritura, volver a abrir y reiniciar el proceso. Si el paso falla, enlazar el issue existente; no crear duplicado por cada síntoma.

## A2 — Finca a coste/kg

| Paso | Expected / issue |
| --- | --- |
| Crear finca y dos parcelas con alias distintos | Contexto finca/parcela consistente; IDs independientes de Catastro (#356/#409) |
| Preparar y activar campaña de recogida | Solo parcelas confirmadas, snapshot conservado; sin parcelas guía explícita; no modificar histórico al renombrar (#403/#428) |
| Crear Jornada y dos Pesadas manuales | Números de vale y cooperativa por Pesada; suma desde registros, sin duplicación ni kg fabricados por parcela (#497/#482) |
| Reparto conocido + desconocido | Total distinguible de atribución parcial; no asignar kg por intuición (#464) |
| Añadir rendimiento a una Pesada | Cobertura parcial visible; no cambiar kg de la entrega ni inventar rendimiento para la otra (#465) |
| Jornales y pago parcial con fecha real | Saldo desde pagos; no pago futuro; no contar coste dos veces (#434/#505) |
| Maquinaria con base día/hora/uso | Identidad Machine, base y precio explícitos; precio desconocido no es cero (#487/#445) |
| Gasto manual y coste generado | Expense POSTED canónico; moneda y alcance coherentes, sin doble registro (#416/#500) |
| Resumen campaña/coste por kg | Conciliar libro y entregas; ausencia de precios/overflow = incompleto/desconocido, nunca cero falso (#449/#689) |
| Corregir fecha/archivar/reabrir | No mover silenciosamente pagos/costes de Jornada ni perder snapshots; avisos y contexto sin huérfanos (#470/#427/#428) |

Pruebas existentes para localizar cobertura (no ejecutadas aquí): `data/local/HarvestContractTest.kt`, `DeliveryContractTest.kt`, `LabourPaymentContractTest.kt`, `CampaignLifecycleContractTest.kt`; `CampaignChartsScreenTest.kt`, `LabourPaymentsUiTest.kt`. Paths relativos a app/src/androidTest/java/com/isivoltpro/maginaolivo/. La existencia no acredita el escenario completo ni móvil físico. #689 impide compilar parte de estas pruebas al SHA auditado.

## A3 — Cuaderno anual fuera de recogida

| Paso | Expected / issue |
| --- | --- |
| Finca sin campaña → Trabajo/Riego/Tratamiento/Jornal/Gasto | Guardado local posible donde corresponda, sin campaña ficticia (#410/#412/#417) |
| Mismos registros con campaña activa en las mismas fechas | Cuaderno/finca los incluye; Campaña de recogida no los absorbe por fecha (#417/#411) |
| Entrar desde parcela → guardar y volver | Target y retorno conservan origen; alias homónimo no cambia identidad (#514/#466) |
| Consultar campaña antigua y usar acceso rápido actual | Año histórico no se presenta como contexto actual sin decisión explícita (#511) |
| Completar PLANNED con datos reales | Fecha real y ajustes antes del Diario; sin Expense POSTED mientras sigue planificado (#424/#438/#429) |
| Corregir COMPLETED | Mantener realizado, fecha/notas/superficie/históricos ocultos; no pasarlo a Avisos como edición (#426/#453/#440) |
| Coste Trabajo + productos/jornales/máquina | Una fuente monetaria canónica; no sumar estimación y gasto real dos veces (#416) |
| Volver/reiniciar/sin red | Operación local e IDs conservados; sin depender de mapa/servicios/cloud para guardar (#409/#332) |

Pruebas existentes para revisar: `data/local/ActivityEngineContractTest.kt`, `TypedActivityDetailContractTest.kt`, `ActivityRelatedExpenseTest.kt`, tests de Notebook y CampaignNotebook. #680 falla en detalles/targets con workspaceId/context_mismatch: resolver antes de interpretar failures secundarios como bugs agronómicos. Auditoría futura debe cruzar expected con navegación/ViewModels reales; este guion no declara esas rutas correctas.
