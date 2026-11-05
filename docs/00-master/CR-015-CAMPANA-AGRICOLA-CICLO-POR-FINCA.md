# CR-015 — Campaña agrícola anual por finca, recolección y balance económico

**Fecha:** 05/11/2026  
**Origen:** decisión directa del propietario del producto Mágina Olivo 1.0.  
**Prioridad:** P0 producto, iniciar **después** del cierre del trabajo actual de Codex en PR #853.  
**Estado:** **diseño funcional aprobado por el propietario**; propuesta técnica sujeta a auditoría; **implementación NO iniciada**.  
**Prohibido por este documento:** merges automáticos, cambios en producción, despliegues Supabase, migraciones Room, reasignación masiva de históricos y alteraciones a la PR #853.  
**Referencias:** CR-005, CR-010, CR-014, #355, #726, #739, #852, #853 y docs/sync/MANUAL_SYNC_1_0_HANDOFF.md.

## 0. Decisión ejecutiva

La app debe ser evidente para cualquier agricultor:
- **Mi Campo = qué fincas tengo**: fincas, parcelas, olivos, SIGPAC/mapas, recursos, personas y permisos.
- **Cuaderno = qué hago y registro**: diario de trabajos, riegos, tratamientos, jornales, gastos, pesadas, documentos y sus detalles. Registro **canónico único** y sin duplicados.
- **Campaña = cómo ha ido el ciclo agrícola**: por finca y campaña, labores generales, **recolección incluida**, estadísticas, costes anuales, ingresos, liquidaciones y resultado.
- **+ global = añadir** desde cualquier pantalla; abre los mismos formularios de escritura en su ámbito correcto.
- Barra inferior objetivo **Inicio | Mi Campo | [+] | Cuaderno | Campaña**. El asistente IA futuro pasa a acción de cabecera; Perfil por avatar superior y Avisos por campana. Cuatro destinos seleccionables y acción central independientes, sin fragilidad por índices.
- Los términos visibles serán **Campaña agrícola** (ciclo completo), **Recolección** (parte de la campaña), **Cuaderno** (registro). No crear una cuarta sección llamada Temporada. El dominio técnico actual Campaign = recolección y OliveSeason = agrupación anual; **no renombrar tablas/clases ni cambiar significados por simple estética**.

Objetivo final: resultado económico por **finca y campaña agrícola**, más una vista consolidada de fincas seleccionadas con advertencia de datos incompletos.

## 1. Ciclo de vida por finca decidido por el agricultor

**Regla de producto aprobada:** El fin de una campaña agrícola se determina cuando el titular/gestor **confirma que ha terminado la recolección de toda esa finca**. Los trabajos posteriores corresponden a la campaña agrícola siguiente. No imponer 1 de enero, 1 de septiembre ni 31 de agosto. Cada finca tiene fechas independientes, aunque se comparta cuenta o workspace.

### 1.1 Estados visibles
- **Campaña abierta:** permite trabajos normales y recolección en su finca.
- **Cerrar campaña agrícola:** acción explícita, con **fecha efectiva del final de recolección** elegida/revisada por el usuario. Revisión previa de pesadas/jornales/gastos e indicación de pendientes; no cerrar en silencio por fecha de calendario.
- **Campaña cerrada agrícolamente, balance pendiente:** histórico conservado; no más trabajos ordinarios nuevos en ese ciclo; **sí** liquidaciones, rendimientos, cobros, ajustes e incidencias económicas tardías autorizadas ligados al origen.
- **Balance completo/provisional:** indicador separado del cierre agrícola. No presentar «beneficio final» si faltan costes o ingresos; nunca marcar «todo cobrado» solo porque la recogida haya terminado.
- **Nueva campaña:** proponer fecha de inicio al día siguiente del cierre efectivo anterior, **editable** por el usuario antes de confirmar. La primera campaña de una finca se crea manualmente o con fecha sugerida, sin inventar historia. Si el usuario reanuda labores sin nueva campaña abierta, pedir asignar/iniciar, sin perder el borrador.

### 1.2 Invariantes de fecha
- Como criterio por defecto, el cierre efectivo pertenece a la campaña que acaba; la siguiente comienza **a partir del día siguiente**. Sin dos campañas agrícolas activas ni intervalos solapados para la misma finca.
- Mostrar confirmación si quedan días sin campaña y se añade un registro; nunca repartir por fecha sin consentimiento si existe ambigüedad.
- Si un trabajo se registra con fecha histórica de un ciclo cerrado, mostrar el ciclo real y pedir autorización/explicación de corrección en lugar de asignarlo a la campaña actual.
- Un trabajo hecho después del último día de recogida, pero **antes del cierre manual**, no debe ser reclasificado en bloque: al cerrar, previsualizar los registros que quedarían fuera del periodo y ofrecer corrección individual antes de confirmar.
- Reapertura/rectificación de fecha **con revisión explícita de efectos**, registro de auditoría, validación de intervalos de campañas vecinas y protección contra pérdidas. No hacer borrados ni cambios silenciosos.
- Puede existir **campaña sin recolección** (por vecería, daño o decisión del agricultor): permitir cierre manual declarando «Sin recolección», sin fingir 0 kg ni ventas.
- Los rótulos «2026/27» son identificadores fáciles de reconocer, **no la fuente de verdad de los límites**. Elegir rótulo por defecto según el ciclo previsto y permitir ajustes, sin asociar cálculos únicamente a ese texto ni suponer que el cierre cae siempre en un año.
- **Ejemplo:** poda 15/02/2026, abonado 20/04/2026, riego 10/07/2026, recogida 15/12/2026–25/01/2027 → campaña 2026/27; siguiente poda 10/02/2027 → 2027/28, cuando se haya abierto esa siguiente campaña. Finca B puede seguir recogiendo hasta febrero y cerrar en otra fecha.

### 1.3 Separar cierre agrícola y contabilidad
Una liquidación cooperativa **cobrada** en mayo de 2027 por aceituna entregada en enero de 2027 se atribuye a la campaña **2026/27**, conservando también fecha real de cobro. Debe distinguirse ingreso devengado/liquidado, anticipos, cobros, saldo pendiente y ajustes; no contar anticipo y liquidación dos veces ni confundir ingresos con pagos. Los gastos o facturas tardías conservan fecha de factura/pago y asignación justificada a la campaña de origen.

## 2. Arquitectura de información y rutas

### 2.1 Barra inferior
**Inicio | Mi Campo | + | Cuaderno | Campaña**. El «+» nunca se convierte en una pestaña ni cambia de identidad. Perfil/cuenta arriba mediante avatar, Avisos arriba mediante campana y acceso no intrusivo a Asistente IA en la cabecera. Mantener destinos de detalle, retorno/Back y accesibilidad de 360/390/430 dp y fuente 1,3. Evitar una sexta pestaña.

### 2.2 Mi Campo (territorio y gestión)
Listado compacto de fincas y resumen de superficies, parcelas y olivos; ficha de finca: Parcelas/mapa, datos del terreno, inventario/maquinaria, **Personas y permisos**, Documentos y atajo «Ver esta finca en Campaña». Quitar de su pantalla principal los bloques grandes de kilos, coste/kg y balances, pues pertenecen a Campaña. No eliminar cálculos existentes ni enlaces históricos sin reubicar.

**Roles/colaboración**: acceso contextual «Mi Campo → Finca → Personas y permisos → Invitar / Ver personas». Perfil puede mostrar «Invitaciones recibidas / Mis accesos» sin convertir rol en atributo global. Titularidad legal ≠ permisos del servidor. QR/WhatsApp/email, concesión/revocación y contratos **pendientes de validación real** (#739). No mostrar acceso económico a un operario por defecto y nunca confiar en ocultar botones como control de autorización.

### 2.3 Cuaderno (registro diario)
- Finca seleccionada y parcela opcional; Diario, Fitosanitario, Gastos e historial de registros. Sacar la pestaña interna «Campaña» del hub **solo cuando exista y funcione la nueva raíz Campaña**.
- Trabajo, Riego, Tratamiento, Jornal, Pesada y Gasto siguen disponibles vía +, Cuaderno y sus accesos contextuales.
- «Trabajo general / Gasto general» sustituye al término visible «Fuera de campaña»; todos están dentro de una campaña **agrícola**, mientras que algunos quedan **fuera de la recolección**.
- El Cuaderno lee las mismas entidades canónicas para evitar duplicidad; no copiar registros a una tabla espejo de estadísticas.

### 2.4 Campaña (nueva raíz)
Selección superior **Campaña agrícola (por etiqueta)** y **Finca**, o «Todas mis fincas» **solo en lectura agregada** cuando el desglose sea veraz. Pantalla limpia, cuatro puertas:
1. **Trabajos del año:** actividades generales y sus costes, jornales generales, tratamientos, abonados, poda, riego, mantenimiento, documentos; acceso a registrar.
2. **Recolección:** días, pesadas, cooperativas, rendimiento ponderado/cobertura, jornales/cuadrillas, pagos, maquinaria, gastos de recogida, faltantes, accesos rápidos «Añadir pesada», «Añadir jornal», «Añadir gasto»; «Ver gráficas de recolección» destacado.
3. **Economía y balance:** gastos generales + costes de recolección, ingresos por ventas/liquidaciones, anticipos/cobros, pendientes, resultado económico (cuando datos suficientes), coste/kg e informe PDF.
4. **Gráficas e histórico:** kg diarios y acumulados (con ejes diferenciados), % rendimiento y cobertura, costes por categoría, comparación multi-campaña por finca, unidades/fechas visibles; sin inventar ceros para datos desconocidos.

Campaña muestra estados: abierta/cerrada, faltan ingresos o costes, liquidaciones pendientes. El resumen superior debe evitar un «beneficio» ficticio. Fincas sin cosecha también presentan trabajos y costes.

### 2.5 Inicio
Solo tarjeta compacta «Tu campaña 2026/27» con acceso directo a Campaña, sin replicar el dashboard completo.

## 3. Registro único y clasificación sencilla

**Regla principal:** todos los caminos de entrada conducen al MISMO formulario y al mismo repositorio; cada gasto, pesada o jornal existe una sola vez. Al abrir desde una finca, campaña o jornada, preseleccionar **solo** contexto verificado; desde + global pedir finca si es ambigua. Una campaña histórica consultada nunca se convierte silenciosamente en contexto para escribir hoy.

| Entrada | Comportamiento |
|---|---|
| + → Trabajo/Riego/Tratamiento | finca y parcela verificadas; trabajo general de la campaña agrícola correspondiente |
| + → Jornal | pregunta «Trabajo general» / «Recolección» si no lo determina el contexto explícito; conserva borrador |
| + → Gasto | pregunta «Gasto general» / «Gasto de recolección» si es ambiguo; no inferir por mes |
| + → Pesada | requiere recolección operativa de esa finca; si no existe, solicitar crear/seleccionar; sin jornada manual obligatoria |
| Campaña → Trabajos del año → Añadir | abre escritor general con finca correcta, fecha editable y campaña agrícola indicada |
| Campaña → Recolección → Añadir | abre escritor de ese contexto de recolección, comprobando estado y permisos |
| Cuaderno → cualquier registro | abre mismo escritor, vuelve a Cuaderno tras guardar/cancelar sin duplicar |

**Jornales**: la recogida ya tiene personas, medias/horas, tarifas, pagos parciales y detalle por jornada. **Carencia verificada**: el «jornal general» actual usa un gasto laboral con concepto+importe y **no** tiene el mismo nivel de personas y horas. Completarlo en un slice separado, sin duplicar coste entre jornal y gasto, ni romper pagos históricos. Los jornales de recogida siguen perteneciendo a la recolección y también suman a la campaña agrícola.

**Pesadas**: el kg canónico proviene de cada entrega/pesada; varias en un día y en distintas cooperativas; no pedir de nuevo kg al crear jornada. Parcelas mixtas sin reparto exacto se declaran como «sin reparto preciso», nunca se dividen por suposición.

**Gastos**: clasificación persistente general vs recolección por vínculo explícito, no por mes. Si está ligado a una actividad, respetar el contexto de la actividad; si equivale a un coste ya registrado por jornal o maquinaria, evitar doble contabilización. No mezclar divisas sin tasa/fecha y consentimiento; costes pendientes/no confirmados se señalan como incompletos.

## 4. Balance anual fiable

Para una finca y campaña agrícola seleccionadas:
- **Gasto general**: costes de labores, jornales generales, tratamientos, agua/riego, fertilizantes, reparaciones, suministros y otros gastos efectivamente atribuidos al ciclo.
- **Coste de recolección**: jornales, maquinaria, combustible, transporte y otros vinculados a la(s) recolección(es) del ciclo. No duplicar costes que ya incluyan tarifas/cálculos de jornada.
- **Coste agrícola total = generales + recolección**, más otros costes atribuibles previstos explícitamente. Si hay reparto de costes de varias fincas, mantenerlos «Sin asignar» hasta que el usuario confirme un reparto; nunca dividirlos automáticamente.
- **Ingresos**: liquidaciones/ventas de aceituna o aceite, abonos, deducciones documentadas, anticipos y cobros sin doble cuenta. Registrar cooperativa, importe, divisa, fecha de liquidación, campaña y, si es posible, pesadas relacionadas. Distinción devengo/cobro.
- **Resultado económico provisional = ingresos reconocidos − costes contabilizados**. La cifra no es beneficio contable/fiscal completo sin definir amortización, impuestos, subvenciones y trabajo propio; comunicar cobertura y supuestos.
- **Pendiente de cobro / pago** es indicador aparte del resultado.
- **Coste total/kg** solo con kilos confirmados y costes en moneda homogénea; rendimiento graso promedio ponderado por kg con cobertura; ausencia de análisis no es 0%.
- **Todas las fincas**: sumar solo cifras comparables de un mismo periodo/campaña definido; al tener límites distintos, el agregado debe declarar explícitamente **qué campañas y fincas incluye**, evitando presentar cierres de fechas desiguales como un periodo homogéneo.
- No inventar ingresos cuando solo hay kg; un valor oficial de mercado tampoco es venta personal.

**Pendiente funcional importante:** crear un módulo de ingresos/liquidaciones y su sincronización/permisos; no se ha certificado un ledger de ingresos equivalente a Expense. En 1.0 puede mostrarse «Ingresos pendientes de registrar / balance incompleto» hasta que exista.

## 5. Diagnóstico técnico comprobado 05/11/2026

Fuente revisada: HEAD de **PR #853 = 2895623aee2505599aea7decd10bafe609e45ff3 (Draft)**; no se cambia la PR.

| Existente | Evidencia en código | Implicación |
|---|---|---|
| Selección de raíces y + | navigation/AppDestination.kt, AppNavigation.kt, ui/components/MoBottomBar.kt | Cambiar una raíz y sus destinos manteniendo cuatro pestañas + acción separada; actualizar pruebas de navegación |
| Temporada fija | domain/analytics/FarmOverview.kt, OliveSeason: 1 septiembre–31 agosto | NO usar ese intervalo para el balance final con cierres variables sin diseñar una migración |
| Gastos generales vs recolección | FarmNotebook, ExpenseForms, FarmOverview y SeasonReport | Reutilizables; clasificación explícita con campaignId de recolección; adaptar agregaciones al ciclo por finca |
| Recolección canónica | domain/campaign/CampaignRepository.kt, Campaign.startDate/endDate/status; Harvest/Jornada/Delivery | Conservar IDs y semántica de recolección; no convertir automáticamente Campaign en «temporada agrícola» |
| Jornal general limitado | feature/harvests/QuickLabourScreen.kt | Completar personas/horas/pagos en fase propia |
| Estadísticas y PDF | feature/notebook/CampaignStatsSection.kt, domain/analytics, domain/report/SeasonReport.kt | Reubicar componentes y recalcular sin crear otra contabilidad |
| Sync DEV parcial | docs/sync/MANUAL_SYNC_1_0_HANDOFF.md / PR #852 | Solo workspace, finca, parcela, campaña, jornada, pesada y borrado de pesada probados A2↔B2 en entorno desechable; no gastos/trabajos/docs ni fincas compartidas |
| Roles | WorkspaceAuthorizationContract + backend grants/issue #739 | Autorización real todavía no equivale a UI completa de invitaciones/colaboración |

Se deben leer las CR antiguas (#005 Cuaderno/Campaña, #010 campaña = recolección, #014 + global). **Este CR-015 prevalece solo para la nueva terminología e información visual aprobadas**, sin derogación automática de sus invariantes de datos y seguridad.

## 6. Propuesta técnica — PENDIENTE AUDITORÍA Y GO

### 6.1 Fase A: navegación/UX sin cambiar esquemas
Crear ruta/pantalla Campaña que reutilice datos disponibles, separar Mi Campo y Cuaderno, acceder a gráficas y gastos existentes. Mantener Campaign como recolección. **NO afirmar que el periodo por finca ya está implementado** mientras solo haya datos agregados por OliveSeason; etiquetar cualquier periodo provisional como tal. No cambiar motor sync, DB ni endpoints; evitar que los datos existentes desaparezcan visualmente al cambiar rutas.

### 6.2 Fase B: modelo de ciclo agrícola por finca
Realizar auditoría de identidad, migraciones Room, sync, autorizaciones, históricos y gastos antes de escribir código. Propuesta de entidad **FarmAgriculturalCycle** (nombre provisional):
- cycle_id UUID estable; workspace_id y farm_id; label para mostrar («2026/27»); fecha inicio y fin efectivo opcional (tipo LocalDate); estado abierto/cerrado; metadata de cierre/versión/tombstone para histórico y sincronización.
- Un único ciclo abierto y **sin solapamientos** temporales por finca; fechas y transiciones validables tanto local como remotamente.
- Relacionar recolecciones Campaign con ciclo agrícola mediante clave explícita o mapeo verificable, **sin renumerar ni sustituir campaignId existente**. Permitir 0..N recolecciones si lo exige el dominio.
- General expenses / activities se atribuyen al ciclo por fecha solo cuando el encaje es **unívoco**; diseñar vínculo explícito y mecanismo de corrección/reasignación para históricos, ciclos con huecos, facturas tardías y registros de trabajo. Revisar Workspaces, parcelas ligadas a varias fincas y costes comunes.
- No depender del rótulo de dos años como ID. Cierre/reapertura, recepción tras sincronización, duplicados/conflictos, migración vN→vN+1, revisiones CAS/idempotentes y RLS de cada finca.
- Migración histórica aditiva: crear ciclos sugeridos sin reetiquetar automáticamente gastos ni cambiar cantidades; ofrecer **previsualización por finca** y confirmación de agrupación. Conservar TODO el histórico y no convertir datos desconocidos en cero.
- Diseñar el mecanismo de cierre offline-first: actualización local transaccional + outbox; si la misma finca se edita desde dos móviles, resolver con revisiones y conflicto legible, no «último gana» silencioso.
- Cuándo intervenir Supabase: **solo después** del diseño de contratos y pruebas. La nueva entidad, relaciones y eventual ingreso requerirán persistencia, políticas RLS y transporte si se desea recuperación/sync multidispositivo. No confundir cambio UI sin SQL con funcionalidad sincronizada real.

### 6.3 Fase C: economía/ingresos
Diseñar entidad(s) de liquidación y movimiento de cobro/pago con fecha de operación, fecha de cobro, importes menores exactos, moneda, finca, ciclo, recolección/entrega(s) relacionadas y ajustes. Evitar que anticipo y liquidación cuenten como dos ingresos. Definir derechos de lectura/escritura por finca; exportación PDF; sync incremental segura. No intervenir el ledger de Expense sin pruebas de no duplicidad.

## 7. Plan por entregas para Codex (orden obligado)

**P0 — Al terminar PR #853:** comprobar estado exacto de main y PRs, CI, backups, Rooms/Supabase DEV, contratos y dependencias; inventario + diff de alcance + matriz de riesgos. No fusionar ni desplegar sin permiso.

**P1 — Navegación y limpieza UI (PR propia):** cuatro raíces +, sustituir Asistente IA de barra por Campaña, mantener avatar y campana; hacer perfil IA accesible arriba sin habilitar funcionalidades ficticias; Mi Campo limpio, Cuaderno sin su subpestaña Campaña, nueva Campaña con 4 tarjetas y selectores; pruebas Compose/rutas/Back; **sin** Room/SQL.

**P2 — Especificación y pruebas de ciclos por finca (PR documental/contratos):** comparar opciones (entidad propia vs adaptación de modelos), inventario de cálculos basados en OliveSeason, matriz de compatibilidad/sync/G2.6 y propuesta migración aditiva. **Pedir GO antes de tocar modelos persistentes**.

**P3 — Ciclos locales (PR propia tras GO):** crear/cerrar/reabrir, fechas libres, historial, gastos/trabajos asignables, campañas sin cosecha, sin doble cuenta, recalcular resumen y PDF; migraciones Room con datos reales sintéticos derivados, sin destrucción.

**P4 — Servidor/sync (PR backend separada tras GO):** RLS, DTO, CAS/identidad/cierre, sync bidireccional y pruebas A↔B aisladas para ciclos; backups DEV actualizados y restaurados; no alterar proyecto persistente ni producción sin aprobación explícita. Advertir que gastos y trabajos siguen sin sincronizar hasta completar su transporte.

**P5 — Jornales generales e ingresos (PRs separadas):** personas/horas/pagos de trabajo general, y liquidaciones/anticipos/cobros/beneficio; inclusión en Room y sync según gates. No declarar balance «cerrado» hasta estar completo.

**P6 — QA de campo y cierre:** dos fincas con fechas distintas, una sin recolección, historia con datos y otra nueva; pantallas 360/390/430, letra 1,3, claro/oscuro, offline, reinstalación segura, A↔B, prueba de permisos y revisión por el propietario. Solo después solicitar merge/publicación.

## 8. Criterios de aceptación verificables

- [ ] Barra inferior exactamente Inicio / Mi Campo / + / Cuaderno / Campaña; sin cinco/séis raíces solapadas; Perfil y Avisos arriba; IA accesible sin prometer funcionalidad.
- [ ] Mi Campo lista fincas/parcelas y colaboradores, no presenta el dashboard financiero completo.
- [ ] Cuaderno funciona igual o mejor para los seis registros; la vista Campaña interna se retira **tras** crear su reemplazo.
- [ ] Campaña elige finca y ciclo; muestra Trabajos, Recolección, Economía e Histórico con estados claros.
- [ ] Finca A cierra recolección 25/01/2027 y finca B sigue abierta hasta 12/02/2027; no mezcla fechas ni balances.
- [ ] Poda posterior al cierre se asigna a campaña nueva tras confirmación; entrada tardía con fecha antigua no se mueve de ciclo sin consentimiento.
- [ ] Campaña sin cosecha permite trabajos/costes y cierre honesto; kg «sin datos» y no venta inventada.
- [ ] Recolección mantiene pesadas, horas, medias, personas, tarifas, pagos parciales, maquinaria, cooperativa, fotos y rendimiento, sin nuevo ingreso de kg en la jornada.
- [ ] Jornal general permite registrar persona/horas y coste sin duplicar un gasto asociado.
- [ ] Un gasto general o de recogida suma **solo una vez** al total; no se deduce su ámbito por fecha. Trabajos planificados/drafts no forman gasto confirmado por defecto.
- [ ] Liquidación posterior al cierre mantiene atribución a campaña origen y fecha de cobro; anticipo no duplica ingreso; resultado no se presume completo.
- [ ] Gráficas e informes respetan filtros finca/ciclo; parcelas mixtas y % sin análisis muestran ausencia/cobertura.
- [ ] Vista global identifica campañas incluidas por finca cuando sus rangos difieren, sin sumar datos incompatibles silenciosamente.
- [ ] No cambian UUID, importes, versiones ni registros históricos sin migración consentida y reversibilidad.
- [ ] Contratos de sync/servidor: cierre concurrente A/B, reintento, caída, offline y revocación; no grants falsos ni fuga de fincas.
- [ ] CI de mismo SHA verde + pruebas funcionales emulator y respaldo/restauración aislados antes de GO; nada de PROD.

## 9. Orden de trabajo y responsables

**Ahora:** Codex continúa el cierre de **PR #853** Perfil/Cuenta; esta decisión se conserva **solo como documento y tarea futura P0**. No tocar su rama ni Supabase.  
**Siguiente encargo:** Codex audita P0 y propone la PR P1 de UI; luego se decide P2/P3 con GO explícito.  
**Estado que se permite comunicar:** «Diseño aprobado y documentado; implementación y sincronización de los ciclos agrícolas todavía pendientes». No llamar funcional o publicado a algo sin APK/QA.

## 10. Mensaje de relevo a Codex

«Cuando termines tu trabajo actual en #853, lee CR-015 por completo. Esta es la prioridad de producto número uno: Mi Campo, Cuaderno y nueva pestaña Campaña agrícola con Recolección dentro y balance anual por finca. Presenta una auditoría concreta de código, datos históricos, Room, Supabase y sync; separa PR P1 solo UX de P2 modelo de ciclos y de P3+ migraciones. No cambies ni despliegues SQL, no toques el motor actual ni fusionas PRs sin GO. Preserva los seis formularios, pesadas y jornales; entrega plan por archivos, pruebas y matriz de riesgos antes de escribir cambios persistentes.»
