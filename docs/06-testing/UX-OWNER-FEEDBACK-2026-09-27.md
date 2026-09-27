# Revisión UX con feedback del agricultor — 27 septiembre 2026

## Base revisada

- Rama de trabajo: `codex/android-ux-review-0.3.0`.
- HEAD de partida: `4b6013c58487f3693252a74f5aca7ec992995a90` (actualización Android más reciente observada en `origin/main`, tras PR #269).
- Emulador: Android API 35, `emulator-5554`; APK instalada antes de los cambios: `0.3.0-dev`.
- Recorrido de datos del 27/09/2026: el emulador conserva tres fincas (`Los Llanos`, `La Umbria`, `La Umbria Norte`), la parcela `Loma Alta` de 2,4 ha en `Los Llanos`, dos campañas `Campana_2026_2027` en estado Preparación (una por finca) y tres movimientos completados —Poda, Riego y Tratamiento— asociados a `Loma Alta`. Se forzó el cierre y se reabrió la app; la campaña y el Cuaderno conservaron los registros. No se borraron los datos al cerrar/reabrir.
- Las seis imágenes de bienvenida quedan fuera de esta revisión, según lo acordado.

## Hallazgos y decisiones

| Hallazgo | Evidencia/estado | Decisión |
| --- | --- | --- |
| “Maquinaria” compite con la navegación de “Mis fincas”. | Reproducido en captura actual `artifacts/ux-feedback/03-farms.png`. La entrada de Maquinaria sigue disponible en Perfil. | Retirar solo el acceso redundante de la lista de fincas; no borrar Maquinaria. |
| Finca vacía muestra estadísticas a cero y dos botones para crear. | Reproducido en la misma captura. | En estado vacío ocultar estadísticas sin datos y dejar únicamente “Crear mi primera finca”. |
| Formulario de finca enseña ubicación, descripción y notas desde el primer momento. | Confirmado visualmente en emulador al abrir “Nueva finca”. | Plegar los cuatro campos opcionales bajo “Más detalles”; al editar, expandir si ya hay valores. |
| Ficha blanca reservada para futuras alertas/datos de interés. | Nota de producto del propietario; no es una función pedida en esta fase. | Conservar la ficha; no añadir alertas nuevas ahora. |
| El distintivo de campaña parece variar entre capturas. | En la última fuente, la ficha usa el nombre de campaña activa o “Sin campaña activa”. Aún no se ha reproducido con una finca/campaña de prueba en el emulador. | Sin cambio de lógica; verificar ambos casos en el recorrido funcional posterior. |
| Formulario antiguo mostraba casillas para todos los tipos de labor. | La fuente de `origin/main` usa selección única y campos tipados para el trabajo elegido; la foto no representa este flujo actual. | No recuperar ni rehacer el formulario antiguo. Probar visualmente los tipos actuales antes de proponer otro ajuste. |
| Parcelas ofrece dos entradas competidoras (“Mapa” y “Añadir”). | Reproducido con el estado vacío de Parcela en emulador. | Unificar en “Añadir” y presentar “A mano” o “Desde el mapa y Catastro” en el mismo punto; el formulario manual comienza solo con alias y superficie. |
| El registro de actividad puede parecer largo incluso tras elegir Poda. | Reproducido en el editor tipado; tipo, operarios, horas y gestión de restos aparecían juntos. | Mantener la pantalla especializada por tipo y plegar los datos agronómicos opcionales en “Detalles de poda” (o el tipo correspondiente). Los campos existentes siguen accesibles y se abren al editar datos guardados. |
| Al guardar una parcela, el formulario no debe dejar al usuario en un flujo abierto. | Reproducido en el emulador tras completar alias y superficie. | El guardado cierra el formulario automáticamente y muestra la parcela en su finca. |
| “Registrar hoy” guardaba la labor con estado Planificada. | Reproducido en la ruta del Cuaderno; al reabrir el Diario el trabajo figuraba como Planificada pese a que se registró como realizado. | Añadir la intención `completeImmediately` al caso de uso: guardar desde “Registrar hoy” como Completada, mantener Planificada en la ruta de planificación y conservar Borrador cuando se guarda como borrador. |
| “Campaña en marcha” en Inicio no se corresponde a simple existencia de campaña. | Tras reabrir, Inicio muestra “Sin campaña en marcha”; las dos campañas creadas están en estado Preparación. No es un fallo de persistencia, pero el texto puede interpretarse como si la campaña no existiera. | Revisar en el siguiente bloque si conviene expresar explícitamente el estado Preparación. No se cambia la regla de campaña en esta iteración. |
| Importar parcela desde el mapa deja al usuario en el mapa. | Reproducido en código actual: importación completa guarda las parcelas y deja la ruta de mapa abierta con un mensaje. | Al completar todo el lote, regresar a la lista de parcelas de la finca. Si hay fallos parciales, permanecer en el mapa y explicar el error. |

## Cambios de esta iteración

- Mi Campo vacío: una sola acción de creación y sin métricas vacías.
- Maquinaria: eliminada únicamente del encabezado de “Mis fincas”; se conserva su entrada en Perfil.
- Formulario de finca: al crear, solo el nombre es visible inicialmente; los datos opcionales se mantienen disponibles bajo “Más detalles”. Al editar con datos guardados, esos detalles se muestran.
- Parcelas: una sola acción “Añadir” abre las dos vías existentes; el formulario manual pliega olivos, riego y Catastro, y vuelve a la lista tras guardar.
- Cuaderno: “Registrar hoy” abre primero la elección de grupo y después el tipo concreto; su editor especializado muestra contexto de finca/parcela y fecha, con los datos tipados opcionales plegados por defecto.
- Mapa/Catastro: añadir señal de importación completada y volver desde la ruta de mapa tras éxito total.
- Estado de actividad: guardar desde “Registrar hoy” crea una labor Completada; el resto de rutas conserva la planificación existente.
- Pruebas: ampliar el test de estado vacío, preservar todos los campos al expandir detalles y comprobar el evento unitario de importación correcta.

## Evidencia de ejecución visual

Capturas del recorrido sobre la versión Android de `origin/main`, tomadas en este turno:

- `artifacts/ux-feedback/03-farms.png`: Mi Campo vacío; evidencia del ruido visual corregido en este cambio.
- `artifacts/ux-feedback/06-farms-after-fix.png`: APK instalada con Mi Campo vacío simplificado.
- `artifacts/ux-feedback/10-farm-form-after-fix.png`: formulario con detalles opcionales plegados.
- `artifacts/ux-feedback/11-farm-created.png` y `13-reopened-farm.png`: finca local creada y conservada tras forzar cierre/reapertura.
- `artifacts/ux-feedback/12-reopened.png`: Inicio tras reabrir; la carga se completó y la finca de prueba se mantuvo.
- `artifacts/ux-feedback/31-first-parcel.png`: la parcela `Loma Alta` queda en la lista al guardar; el formulario vuelve a la pantalla de parcelas.
- `artifacts/ux-feedback/42-campaign-created.png`: campaña `2026-2027` guardada y visible como Preparación.
- `artifacts/ux-feedback/46-notebook.png` y `49-pruning-form.png`: campaña activa en Cuaderno y selección única de tipo de trabajo con campos de poda específicos.
- `artifacts/ux-feedback/parcels-chooser.png`: elección unificada “A mano” / “Desde el mapa y Catastro”.
- `artifacts/ux-feedback/parcel-form.png`: formulario breve con datos opcionales plegados y Guardar visible.
- `artifacts/ux-feedback/pruning-form.png`: pantalla de Poda especializada, con los datos avanzados plegados.
- `artifacts/ux-feedback/final-reopened-notebook.png`: Cuaderno reabierto con Poda, Riego y Tratamiento como Completadas y vinculadas a `Loma Alta`.
- `artifacts/ux-feedback/final-cold-start.png`: Inicio tras forzar cierre y volver a abrir la app; muestra datos conservados y el estado de campaña en Preparación.

## Validación

- `:app:testDevDebugUnitTest`, `:app:lintDevDebug` y `:app:assembleDevDebug`: correctos tras los últimos cambios.
- `:app:installDevDebug`: correcto; instalado en `emulator-5554` (API 35), paquete `com.isivoltpro.maginaolivo.dev`, versión `0.3.0-dev`.
- `ParcelScreensTest`: 6/6 pasan en `MaginaOlivo_UX_API35`.
- `ActivityEditorCompactTest`: 4/4 pasan; el test nuevo comprueba que los datos específicos de Poda quedan plegados y siguen disponibles.
- E2E dirigido `AppNavigationTest.theActivityEditorShowsOnlyTheTypedBlockOfTheChosenType`: pasa después de adaptar la elección de Parcela al nuevo flujo y expandir explícitamente los detalles de actividad.
- E2E dirigido `AppNavigationTest.registerTodayShowsInTheDiaryAndSurvivesARestart`: pasa; crea finca, parcela, campaña, registra Poda y comprueba Diario y persistencia tras recrear la actividad.
- Pruebas instrumentadas dirigidas: `ParcelScreensTest` 6/6, `ActivityEditorCompactTest` 4/4, `ActivityEngineContractTest.registerTodayCreatesCompletedWorkWhilePlanningRemainsPlanned` pasa, y los dos E2E dirigidos de editor tipado y persistencia de “Registrar hoy” pasan.
- La suite completa de 277 pruebas Android no se ha completado en esta continuación. Una ejecución anterior se detuvo por timeout en `AppNavigationTest.registrarPlusCreatesARealActivityOnTheSelectedFarm` (6/277 reportadas al fallar; 7 completadas al detenerse). Es una limitación pendiente, no un fallo atribuido a los cambios actuales.
- Las pruebas instrumentadas reiniciaron/desinstalaron la app durante el ciclo. Se reinstaló la variante final y se volvió a crear un conjunto de prueba realista en el emulador; no se usa `pm clear` en el recorrido final. En la verificación de cierre, el proceso se forzó a detener y se lanzó de nuevo; Inicio y Cuaderno cargaron sin crash y mostraron los datos guardados.
- Captura antes/después: `03-farms.png` tiene estadísticas a cero, botón duplicado y acceso a Maquinaria; `06-farms-after-fix.png` muestra solo la tarjeta de creación; `10-farm-form-after-fix.png` enseña el formulario compacto.
- El test de datos del formulario requiere scroll explícito hasta Guardar con el teclado activo; tras añadirlo, la clase completa pasa.
- La suite completa de 277 pruebas Android se interrumpió al fallar `AppNavigationTest.registrarPlusCreatesARealActivityOnTheSelectedFarm` con timeout de 15 s (6/277 al informar el fallo; 7 completadas al detenerla). No se atribuye a estos cambios sin reproducirlo en una ejecución dirigida.

## Pendiente de recorrido funcional

- Siguiente bloque de revisión visual: Inicio, que en `12-reopened.png` mantiene fotografía, resumen, campaña, próximos trabajos y accesos rápidos; valorar jerarquía y densidad junto al propietario antes de tocarlo.
- Completar la revisión manual de Producción/Gastos, Perfil/Ajustes, sesión/autenticación, avisos, maquinaria, mapas/Catastro real, modo offline/sincronización y todos los tipos de actividad. El conjunto de varias fincas/campañas y movimientos ya se dejó en el emulador como referencia para seguir probando.
- Verificar el retorno visual del mapa con un test determinista local (Catastro en vivo depende de red/servicio externo).
- Comprobar el distintivo de campaña y afinar el mensaje de Inicio para distinguir “no existe campaña” de “campaña en Preparación”. El estado observado se explica por las campañas creadas, que están en Preparación.
- Recorrido visual final completo de navegación global pendiente; no se declara terminada la auditoría de toda la app.
