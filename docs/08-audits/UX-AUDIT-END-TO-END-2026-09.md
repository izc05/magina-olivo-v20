# Mágina Olivo — revisión UX end-to-end, septiembre de 2026

Fecha: 2026-09-24. Issue #229. Draft PR #230. Rama `codex/ux-audit-end-to-end`.

**Estado: primera revisión estática entregada; auditoría visual e interactiva pendiente.**
Este documento no certifica que se haya recorrido la aplicación en un dispositivo.
Se han inspeccionado rutas, pantallas, componentes y estados del código de producción;
las referencias visuales y los tests existentes no se presentan como ejecuciones nuevas.

## Base, alcance y límites

- Base `main`: `4ae671dc7ddc9b65a9d4994759e281ffb0b4236f`.
- HEAD inspeccionado: `6ed4b0c5d28d06307f217992e91e6168ff170c25`, que añade el brief a esa base.
- #228, navegación: OPEN al consultar; modifica `AppNavigation.kt` y `AppNavigationTest.kt`.
- #227, mapa: OPEN al consultar, HEAD `bc66e4c4c50c190d64772570f42562b7db827cae`.
  Su lista de archivos incluye navegación, parcela, sección de finca y Catastro, además del mapa.
  No se ha mezclado su implementación con la base auditada.
- El encargo actual reserva esta rama a documentación. Los documentos anteriores atribuyen
  otros roles a Codex/Claude; este informe no reasigna responsables ni inicia producción.
- Se conservan las cinco raíces, la identidad CR-004 y las fronteras de datos de CR-005.
- No se han modificado Kotlin, recursos, tests, dependencias, esquema ni migraciones.

**Bloqueo de evidencia B-01:** `adb devices` no devuelve dispositivos y
`emulator -list-avds` no devuelve AVD. No se ha podido lanzar ni capturar la app.
Faltan capturas actuales, pulsaciones, teclado, Back real, restauración, TalkBack,
modo avión, notificaciones, fuente grande y movimiento reducido. No se atribuye un
fallo de producto a esa falta de entorno. Tampoco se afirma un PASS visual.

La habilidad de auditoría de Product Design exige evidencia capturada para afirmar
una auditoría visual. El brief permite evidencia de código y tests cuando esté
disponible: se entrega esa parte y se mantiene abierta la comprobación en Android.

## Criterios

- **Blocker:** impide completar el trabajo esencial o compromete datos de forma demostrada.
- **High:** riesgo relevante de perder entrada, registrar en contexto equivocado o no poder corregir un fallo.
- **Medium:** fricción clara, orientación incompleta o estados difíciles de distinguir.
- **Polish:** coherencia y acabado después de resolver lo anterior.

Evidencia **C**: estructura o comportamiento explícito en código. **R**: riesgo inferido
que requiere reproducción. **P**: pendiente de ejecución. No hay Blocker de producto
demostrado en esta revisión; B-01 bloquea el cierre de la auditoría.

## Recorrido y salud por paso

Todas las filas tienen validación visual/interactiva P. «Base presente» solo describe código.
Las rutas de archivos se dan desde `app/src/main/java/com/isivoltpro/maginaolivo/`.

| Paso | Superficie | Salud estática y evidencia | Hallazgos / siguiente comprobación |
| --- | --- | --- | --- |
| 01 | Onboarding 1, bienvenida | Marca, texto, Saltar y Siguiente presentes | U08; captura inicial, lectura y foco |
| 02 | Onboarding 2, fincas | Continúa en el mismo componente | U08; avanzar y recrear Activity |
| 03 | Onboarding 3, mapa | Presenta importación y dibujo como capacidades | Comprobar texto frente a capacidades entregadas tras #227; no validar mapa futuro |
| 04 | Onboarding 4, actividad | Narrativa presente | U08; transición, progreso accesible |
| 05 | Onboarding 5, cosecha/documentos | Narrativa presente | U08; fuente grande y espacio disponible |
| 06 | Onboarding 6 → Inicio | Comenzar y salida al shell presentes | U08; finalización persistida, relanzamiento y movimiento reducido |
| 07 | Inicio | Foto, saludo, fecha, datos locales, campaña y próximos trabajos; no solo tarjetas genéricas | U01, U02, U09; jerarquía visual P |
| 08 | Navegación / Back | Selección con fondo, peso y semántica; fix de pila en #228 abierto | U03; recorrer las cinco raíces, repetir pestaña, entrar profundo y volver tras merge |
| 09 | Mi Olivar | Lista, creación, archivado y mensajes presentes en `feature/farms/FarmScreens.kt` | U04; editar, cancelar, restaurar y error |
| 10 | Finca | Hub con secciones, foto y contexto | `FarmScreens.kt` y `FarmSectionScreen.kt`; conservar el nombre en cada sección; #227 toca esta zona |
| 11 | Parcelas | Alta, lista, importación y editor presentes | U04, U05, U06; no cambiar ahora archivos solapados con #227 |
| 12 | Parcela | Detalle y edición con información manual diferenciada de Catastro | U04, U06; texto largo, archivo/restauración y vuelta al mapa P |
| 13 | Campaña | Estado, histórico, métricas y acciones de ciclo presentes | U01, U07; cambios destructivos y contexto de producción |
| 14 | Registrar y formularios | Tipos agronómicos separados por campos, pero editor común largo | U03–U06; matriz por tipo debajo |
| 15 | Calendario | Agenda/mes, atrasos, avisos y confirmaciones presentes | U10; hechos/cancelados se remiten al histórico; notificación → detalle → Back P |
| 16 | Cosecha | Kilos recogidos y reparto desconocido explícitos | U03–U06; preservar distinción frente a entrega y no inventar reparto |
| 17 | Entregas/Pesadas | Destino por entrega, bruto/tara/neto y ticket visibles | U03–U06, U12; OCR y retorno pendientes |
| 18 | Rendimiento | Editor separado y texto «la entrega no cambia» | U04–U06; guardar/corregir/quitar y cobertura del agregado P |
| 19 | Gastos | Importe y relaciones; documento puede crear borrador | U03–U06; confirmar sin duplicar dinero; vuelta tras adjuntar P |
| 20 | Documentos/OCR | Lectura local, revisión, relectura y descarte explícitos | `feature/expenses/DocumentReviewScreens.kt`; original/revisión/error/permiso y documento solo P |
| 21 | Maquinaria | Alta, edición, retirada y restauración presentes | U04–U06; `feature/machinery/MachineryScreens.kt` |
| 22 | Perfil | Ajustes reales, avisos, maquinaria y sincronización futura indicada | U11; regreso desde ajustes de Android P |
| 23 | Mapa/Catastro | Consulta/importación Phase 17 inspeccionada; Phase 18 no integrada | Esperar #227 y auditar su versión canónica; no declarar defecto por pantalla aún no entregada |

### Cobertura de los tipos de Registrar

Fuente: `feature/activities/ActivityScreens.kt::ActivityEditor`,
`ActivityDetailForm.kt::buildActivityDetail` y `PlanningFields.kt`.

| Tipo | Base inspeccionada | Prueba necesaria |
| --- | --- | --- |
| Observación | Sin bloque tipado adicional | Descripción/fecha/parcelas, borrador y guardar |
| Poda | Detalle Pruning | Campos opcionales, cambio de tipo sin arrastrar datos |
| Abonado | Detalle Fertilization | Cantidades, unidades, errores y coma decimal |
| Tratamiento | Detalle Phytosanitary | Campos propios, error cercano al campo y guardado |
| Suelo | Detalle SoilWork | Registro mínimo y opciones secundarias |
| Riego | Detalle Irrigation y precio | Comunidad/proveedor/sector, importe histórico y unidades |
| Mantenimiento | Detalle Maintenance | Máquina opcional y horas |
| Incidencia | Detalle Incident | Severidad, texto y recuperación del borrador |
| Otro | Sin bloque tipado adicional | Que el registro mínimo siga siendo corto |
| Jornada de cosecha (tipo Activity) | HARVEST_DAY sin detalle tipado | No confundir con el registro canónico de kilos Harvest; no adelantar Phase 19 |
| Cosecha | `HarvestEditor` | Finca/campaña, kg, exacto/parcial/desconocido |
| Entrega/Pesada | `DeliveryEditor` | Destino, kg, ticket, OCR revisado por la persona |
| Gasto | `ExpenseEditor` | Coma decimal, relaciones, documento, borrador/confirmación |

Para cada fila falta ejecutar `idle → pressed → focus → validation → saving → saved →
failure → retry`, incluyendo Back con cambios y recreación de Activity.

## Hallazgos priorizados

### U01 — High · El destino pierde la campaña que se acaba de pulsar [C]

**Pantallas:** Inicio y Campaña → Cosecha/Entregas.
`HomeScreen.kt` asigna el mismo `onHarvest` a cada fila `home-campaign`;
`HomeViewModel.kt::HomeCampaign` no transporta ID. `navigation/AppNavigation.kt`
conecta Inicio y las acciones de Campaña con rutas globales `Harvest`/`Deliveries`.
Con dos fincas, pulsar una campaña no abre una vista filtrada de esa campaña.

**Cambio:** transportar ID de finca/campaña y mostrar el contexto en destino; la entrada
global debe seguir existiendo. Validar con dos fincas y campañas de nombres distintos.
**Riesgo/espera:** navegación solapa #228 y #227; esperar ambos. La orquestación definitiva
del Cuaderno debe coordinarse con 19A, sin duplicar datos ni introducirlo en este PR.
**Grupo:** `ux-context-navigation`.

### U02 — High · Inicio transforma un fallo de workspace en «primera finca» [C]

**Pantalla:** Inicio. `HomeViewModel.kt` convierte `AppResult.Failure` de
`ensureLocalWorkspace()` en `flowOf(emptyList())`. `HomeUiState` no tiene error y
`HomeScreen` interpreta la lista vacía como invitación a crear la primera finca.
Esto confunde indisponibilidad de datos con ausencia real de datos.

**Cambio:** separar error de vacío, explicar el fallo y ofrecer reintentar; conservar
datos previos cuando corresponda. Prueba con repositorio que falla y después se recupera.
No se ha inyectado ese fallo en Android durante esta revisión.
**Riesgo/espera:** bajo, circunscrito a Home; candidato después del informe, sin dependencia
funcional de Phase 18/19. **Grupo:** `ux-error-empty-states`.

### U03 — High · Registrar promete contexto que no transmite completo [C]

**Pantallas:** sheet Registrar desde finca/parcela/campaña.
`QuickAddSheet.kt::QuickAddContext` muestra nombres, pero solo conserva `farmId` como ID.
En `AppNavigation.kt` las acciones HARVEST/DELIVERY/EXPENSE van a listas globales;
ACTIVITY y PLAN comparten la misma llamada y solo pasan finca.
No se transmite ID de parcela/campaña ni intención de planificación.

**Cambio:** transportar contexto e intención explícitos hasta el formulario, dejando
visible la finca/campaña efectiva. Comprobar entradas desde parcela, campaña histórica
y acceso global, sin seleccionar silenciosamente otra campaña.
**Riesgo/espera:** alto solapamiento con #228/#227 y 19A; esperar navegación estable y
acordar la solución con el Cuaderno. **Grupo:** `ux-context-navigation`.

### U04 — High · Salir de editores puede descartar entrada sin advertencia [C/R]

**Pantallas:** Cosecha, Entregas, Rendimiento, Gastos, Maquinaria y Parcela.
Los editores usan estado local `remember(initial)`; por ejemplo `HarvestScreens.kt:324`,
`DeliveryScreens.kt::DeliveryEditor/YieldEditor`, `ExpenseForms.kt:100` y
`MachineryScreens.kt:184`. Los `ModalBottomSheet.onDismissRequest` cierran directamente.
No hay comprobación de cambios en esas llamadas. Parte de Actuación también usa `remember`
para detalle, planificación y maquinaria. La pérdida exacta al recrear requiere reproducción.

**Cambio:** acordar política común para formulario modificado: conservar borrador o
confirmar descarte; estado restaurable donde corresponda. Mientras guarda, impedir salida
ambigua o mantener un resultado recuperable. No añadir confirmación cuando nada ha cambiado.
**Aceptación:** introducir datos, Back/deslizar, reabrir y recrear Activity; no perderlos
silenciosamente. **Riesgo/espera:** desplegar por familia, no reformar todos los editores a
la vez; Parcela después de #227 y recolección coordinada con Phase 19.
**Grupo:** `ux-form-drafts`.

### U05 — Medium · Guardar desactiva botones sin explicar el progreso en varios editores [C]

**Componentes:** `MoButtons.kt::MoPrimaryButton` no expone estado de carga;
`CampaignEditor`, `ParcelEditor`, `MachineEditor`, `DeliveryEditor` y `YieldEditor`
mantienen la etiqueta mientras `enabled = !isSaving`. Cosecha/Entregas/Gastos muestran
«Guardando…» en la pantalla base, fuera de la hoja. Finca y Catastro sí cambian su CTA.
No se afirma que no exista feedback Material de pulsación.

**Cambio:** etiqueta/progreso dentro del formulario, anuncio accesible y resultado breve
en el destino visible. Mantener protección contra doble envío y datos en caso de error.
**Aceptación:** escritura lenta, fallo y reintento conservando campos; una sola entidad creada.
**Riesgo/espera:** componente común más una familia por PR; Parcela/Catastro esperan #227.
No requiere negocio Phase 19. **Grupo:** `ux-form-feedback`.

### U06 — Medium · Formularios poco progresivos y teclado genérico [C]

**Componentes:** `ActivityEditor` enumera todos los tipos, parcelas y máquinas y llama
siempre a `PlanningFields`; este expone hora, duración, personas, proveedor y avisos.
El tipo único se elige con checkboxes. `MoFields.kt::MoTextField` no permite
`keyboardOptions`/acciones IME, por lo que kg, porcentaje e importes heredan el teclado
por defecto. `ParcelEditor` muestra juntos los campos manuales catastrales.

**Cambio:** tipo único con semántica de selección exclusiva, bloque esencial corto y
opciones secundarias desplegables; exponer teclado decimal y siguiente/hecho manteniendo
validación de coma decimal. No limitar la entrada al teclado como sustituto de validación.
**Riesgo/espera:** Actuaciones puede mejorarse por separado; Parcela espera #227.
No rediseñar recolección antes de 19A/B. **Grupo:** `ux-form-feedback`, después `ux-form-progressive`.

### U07 — Medium · Editar campaña conserva título de alta y pierde validación por campo [C]

**Pantalla:** Campaña en preparación → Editar. `CampaignScreens.kt::CampaignEditor`
siempre escribe «Nueva campaña». La llamada de edición pasa `null, null` como errores.
`CampaignDetailViewModel.update` produce un error general para nombre/fecha inválidos.

**Cambio:** título según intención y errores junto a nombre/fecha dentro de la hoja.
Conservar la entrada y enfocar el primer error. Probar alta y edición por separado.
**Riesgo/espera:** bajo; coordinar si 19A modifica la misma pantalla; no necesita nueva
lógica de campaña. **Grupo:** `ux-form-feedback`.

### U08 — Medium · Onboarding sin estado restaurable ni progreso semántico explícito [C/R]

**Pantallas:** 1–6. `OnboardingReferenceScreen.kt:80` usa `remember` para el índice;
`PageDots` dibuja cajas sin descripción «paso N de 6». Avanzar cambia el índice directamente;
no se define una transición propia entre páginas en este componente.

**Cambio:** conservar paso, anunciar progreso y definir Back interno si esa es la conducta
aprobada. Después añadir transición breve respetando movimiento reducido; no hacer la
animación requisito para avanzar. **Aceptación:** paso 4, recreación, TalkBack, finalizar,
relanzar y escala de animación cero. **Riesgo/espera:** bajo, sin dependencia de Phase 18/19.
**Grupo:** `ux-onboarding-states`; movimiento en un PR posterior.

### U09 — Medium · Vacíos operativos de Inicio sin acción directa [C; jerarquía R]

**Pantalla:** Inicio. `home-no-campaign` y `home-no-upcoming` describen qué hacer pero
no pasan `actionText/onAction`. El hero solicita `heightFraction = 0.46f`; el bloque de
campañas usa `forEach` sin límite antes de próximos trabajos. Existe una jerarquía inicial,
pero con varias fincas su eficacia debe medirse en pantalla, no inferirse de una captura antigua.

**Cambio:** una acción contextual para crear/abrir campaña y planificar; mantener campaña
y próximo trabajo legibles pronto. Evaluar hero y resumen con 0/1/varias fincas, antes de
cambiar dimensiones bloqueadas por CR-004. No exigir estado «seleccionado» persistente a un
acceso rápido que solo ejecuta navegación; sí feedback de pulsación.
**Riesgo/espera:** layout local bajo; enlaces dependen de #228/#227 y coordinación 19A.
**Grupo:** `ux-home-states`.

### U10 — Medium · «Esta semana» representa mañana y próximos siete días [C]

**Pantalla:** Calendario. `AgendaScreens.kt::AgendaGroup.of` agrupa `TOMORROW` y
`NEXT_7_DAYS` bajo `WEEK("Esta semana")`. El periodo móvil puede incluir la semana siguiente.
**Cambio:** usar etiqueta fiel al intervalo («Próximos días») o agrupar por semana natural.
Probar domingo/lunes y cambio de mes. **Riesgo/espera:** bajo, sin dependencia de 18/19.
**Grupo:** `ux-calendar-clarity`.

### U11 — Medium · Perfil promete que nada depende de internet [C]

**Pantalla:** Perfil → Modo sin conexión. `ProfileScreen.kt` dice «nada depende de internet»
y «Siempre», mientras la consulta a Catastro usa un proveedor externo.
**Cambio:** distinguir registro/consulta local de búsqueda externa («Tus datos guardados
funcionan sin cobertura. Consultar Catastro necesita conexión»). Validar el texto con el
mapa final, sin prometer cartografía base offline. **Riesgo/espera:** texto bajo; comprobar
tras #227. **Grupo:** `ux-error-empty-states`.

### U12 — Polish · Vocabulario Entrega/Pesada pendiente de transición controlada [C]

**Pantallas:** Inicio, Registrar y Entregas. Las etiquetas actuales usan «Entrega»;
CR-005 fija «Pesada» como expresión de usuario para la misma entidad Delivery.
**Cambio:** glosario y migración coherente de textos al ejecutar Phase 19, sin crear una
segunda entidad ni confundir kilos recogidos con netos entregados.
**Riesgo/espera:** esperar Phase 19; no hacer ahora sustitución masiva.
**Grupo:** slice de vocabulario del Cuaderno.

### U13 — Polish · Movimiento común por definir y validar [C/P]

**Componentes:** botones, listas, onboarding y navegación. Se reutilizan `Button`,
`Surface(onClick)` y `Modifier.clickable/selectable`, que proporcionan comportamiento
Material; no hay base para afirmar «ningún botón da feedback». La búsqueda en tema,
onboarding y navegación no encontró tokens propios de movimiento.
**Cambio:** primero observar el feedback real; después centralizar solo lo necesario:
pulsación/selección 120–180 ms y contenido relacionado 180–260 ms como candidatos del brief.
Mantener las hojas Material, no retrasar acciones ni añadir bucles decorativos.
**Aceptación:** movimiento reducido, rapidez al registrar, sin animación que bloquee entrada.
**Riesgo/espera:** después de #228/#227, sin cambiar Phase 19.
**Grupo:** `ux-motion-tokens` / `ux-nav-feedback` según evidencia en dispositivo.

## Lenguaje global de estados

| Estado | Evidencia de la base | Regla propuesta / verificación pendiente |
| --- | --- | --- |
| Loading local | Indicadores en Home, Agenda y otras pantallas | No confundir con vacío; anunciar carga y conservar contexto |
| Saving | `isSaving` y botones deshabilitados; etiquetas dinámicas en Finca/Catastro | Feedback dentro del editor; U05 |
| Saved | `state.message` en distintas pantallas; a menudo texto secundario | Confirmación breve visible y accesible; no depender solo del cierre de la hoja |
| Validation | `MoTextField.isError/supportingText`; edición de campaña es excepción | Campo + motivo + corrección; U07 |
| Warning | `MoStatusChip` con texto, p. ej. atrasos | Ámbar más texto/icono; comprobar contraste exterior |
| Error recuperable | Textos y `MoErrorState`; Home carece de estado específico | Reintentar sin perder entrada ni mostrar un falso vacío; U02 |
| Offline | Datos locales; Perfil explica funcionamiento pero exagera alcance | Mensaje no bloqueante y distinción servicio externo/local; U11 |
| Sync pending | Perfil declara sincronización futura; adjuntos tienen estado local | No prometer subida ni presentar guardado local como copia remota |
| Empty | `MoEmptyState` con y sin CTA | Una siguiente acción útil; U09 |
| Disabled | Material `enabled = false` | Explicar el requisito que falta y distinguirlo de guardado |
| Selected | Barra con pill/peso/semántica; familia de iconos vía `MoIconTone` | Preservar señales existentes; probar fondo, texto largo y TalkBack |
| Destructive | Hojas de confirmación y botones destructivos existen | Confirmación precisa; cancelación vuelve sin cambios; no fiarse solo del rojo |

No se propone un segundo sistema de color: `MoCompact.kt::MoIconBadge` ya obtiene tono y
fondo de `MoIconTone.of(icon)`. Revisar cobertura y contraste reales antes de añadir colores.
Riesgos de accesibilidad pendientes: chips contextuales largos en `QuickAddContextLine`
(Row sin adaptación explícita), etiquetas de navegación de una línea, fuente grande,
teclado/CTA, foco en error y anuncios de guardado. No se declara incumplimiento de contraste
ni recorte observado sin medición/captura actual.

## Orden de PR pequeños y coordinación

Esta tabla propone trabajo; no autoriza ni contiene implementación.

| Orden | PR sugerido | Alcance | Condición |
| --- | --- | --- | --- |
| 1 | `ux-error-empty-states` | U02; después textos U11 | Home aislado; texto final de mapa tras #227 |
| 2 | `ux-form-feedback` | U05/U07, teclado de U06; una familia por entrega | Evitar archivos de #227; revisar impacto del componente común |
| 3 | `ux-form-drafts` | U04 en una familia piloto | Probar ciclo de vida antes de generalizar |
| 4 | `ux-context-navigation` | U01/U03 | #228 y #227 fusionados; coordinar con 19A |
| 5 | `ux-home-states` | U09, jerarquía basada en capturas | Contexto resuelto; respetar CR-004 |
| 6 | `ux-onboarding-states` / `ux-calendar-clarity` | U08 / U10 por separado | Sin solapamiento activo |
| 7 | `ux-form-progressive` | Resto de U06 | Una familia cada vez, conservar datos y reglas |
| 8 | `ux-motion-tokens`, `ux-nav-feedback` | U13 | Medición visual/táctil tras navegación estable |
| 9 | Vocabulario del Cuaderno | U12 | Gate 18 PASS y slice Phase 19 correspondiente |

Cada PR parte del `main` actualizado y muestra antes/después con el mismo escenario.
Ninguno debe declarar cerrado un hallazgo solo porque compile.

## Evidencia necesaria para cerrar

1. Preparar emulador o teléfono de pruebas con APK correspondiente a SHA identificado;
   no borrar datos del teléfono del propietario. Dataset separado: vacío, una finca,
   dos fincas/campañas, parcela sin superficie, trabajo vencido, cosecha mixta y rendimiento pendiente.
2. Capturar los 23 pasos anteriores; onboarding con sus seis capturas independientes.
   Guardar screenshot y nota con SHA, dispositivo, tamaño, escala de fuente y estado.
3. Repetir formularios con datos válidos/inválidos, escritura lenta/fallida, doble tap,
   cierre con cambios, recreación y regreso desde selector de documento/cámara.
4. Navegación tras #228: cada raíz, toque repetido, ruta profunda, Back, Registrar contextual,
   notificación → actuación → retorno; confirmar destino y selección de barra juntos.
5. Calendario: hoy/mañana/atrasado/hecho/cancelado, aviso permitido/denegado y sin recordatorio.
6. Tras #227 en `main`: proveedor cargando, sin resultado, candidatos, referencia inválida,
   duplicado, confirmación, área gestionada/catastral, selección/controles y mapa ↔ detalle;
   geometría local tras modo avión y reinicio, sin prometer mapa base descargado.
7. Fuente grande, TalkBack, movimiento reducido, teclado visible y uso con una mano;
   contraste y sensación exterior requieren medición/prueba, no aprobación por código.

## Verificación de esta entrega

- Lectura de brief #229/#230 y estado/lista de archivos de #227/#228 en esta sesión.
- Revisión estática de fuentes citadas; sin capturas nuevas ni ejecución de APK.
- No se han ejecutado build, unit tests ni instrumentación: el cambio es documental;
  los tests existentes no se contabilizan como pasados en esta auditoría.
- `git diff --cached --check`: PASS, sin errores. `git diff --cached --name-only`:
  únicamente este informe. Verificación documental; no equivale a validación de la app.
- No se cierra ningún Gate. No se añade alcance de Phase 19. La siguiente acción permitida
  es completar evidencia en Android y revisar prioridades; no una reforma de producción.
