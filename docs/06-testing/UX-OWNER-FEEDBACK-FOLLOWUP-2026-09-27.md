# Revisión de comentarios UX — continuación (2026-09-27)

## Contexto de revisión

- Rama: `codex/review-current-ux-20260927` (derivada de `main`; no se modifica `main`).
- Base revisada: `dc487a722e7bb39885f1cadb2c98126441a7c1c2` (merge PR #277).
- Aplicación observada: `0.4.0-dev`, paquete `com.isivoltpro.maginaolivo.dev`, emulador `emulator-5554`.
- Rol de esta rama: auditoría y documentación. No competir con Claude implementando los mismos cambios.
- Datos encontrados al empezar: finca «Los Llanos», 3 parcelas, campaña `Campana_2026_2027` en Preparación sin parcelas, y tres actuaciones ya registradas (poda, riego y tratamiento). Conteo de olivos desconocido. Se conservaron; no se limpió ni se inventó información agronómica.
- Recorrido: Inicio, Mi Campo/finca, detalle y activación de campaña, Cuaderno, opciones de registro, ruta de Jornal, formulario de riego, planificación/avisos y cierre forzado/reapertura.

Las capturas están en [`artifacts/audit-20260927/`](../../artifacts/audit-20260927/). La captura `01-current` ya existía al comenzar esta continuación y se ha preservado.

## Decisiones confirmadas por el propietario (2026-09-27)

Estas decisiones quedan cerradas para la PR de implementación de Claude; Codex mantiene el rol de auditor y no modifica producción:

1. La opción general que registra el coste cambia de «Jornal» a **«Gasto de jornales»**. **«Jornales»** nombra la asistencia/personas dentro de una Jornada de recolección.
2. Una Jornada se puede crear antes de cualquier Pesada; corregir el texto y el estado vacío que afirmen o insinúen que nace con la primera Pesada.
3. En Cuaderno, quitar solo la fila duplicada de accesos bajo «Registrar hoy»; conservar la hoja de opciones.
4. Al activar una campaña sin parcelas, explicar qué falta y ofrecer acceso a **Editar campaña**.
5. En Riego, compactar tarifas y campos secundarios y usar selectores de hora/fecha cuando corresponda, sin eliminar datos ni opciones.
6. El número de olivos sigue siendo opcional. No bloquear fincas ni estimar ingresos usando el número de árboles.
7. El alcance del precio de 20D es exclusivamente mercado del aceite: **AOVE, Virgen y Lampante**. El precio pagado por kg de aceituna de una cooperativa y la estimación económica de una finca son conceptos distintos, fuera de 20D.
8. Claude implementa todo lo anterior en su propia PR. No iniciar fases ni ampliar el alcance. Codex revisará el diff, pruebas, APK y emulador.

### Revalidación de Tiempo y radar

Se corrigió la lectura inicial de esta auditoría: el propietario confirma que `weather-forecast` y `weather-radar` están desplegados y operativos. Además, el workflow manual **Deploy weather functions** ejecutó correctamente el 2026-09-27: [run 36306333591](https://github.com/izc05/magina-olivo-v20/actions/runs/36306333591). Su paso «Real call — Bedmar (by name) and radar» pasó, y el workflow solo concluye con éxito si las llamadas autenticadas de pronóstico y radar responden HTTP 200 y el pronóstico sin clave responde 401.

La captura del emulador anterior sí muestra «Sin fuente configurada en esta versión», pero esto no contradice que el backend esté operativo. En `AppCompositionRoot`, las fuentes Android solo se construyen si `BuildConfig.WEATHER_ANON_KEY` no está vacío; `app/build.gradle.kts` obtiene ese valor de Gradle, entorno o `local.properties`. `android-ci.yml` inyecta el secreto público en sus builds. Así que la captura indica que esa APK concreta se compiló o instaló sin fuente configurada (o no es el artefacto CI con la clave), no que fallen las Edge Functions.

**Comprobación de auditoría (21:40 UTC):** `origin/main` continúa en `dc487a722e7bb39885f1cadb2c98126441a7c1c2`. El CI Android de ese SHA terminó correctamente (run [36350063900](https://github.com/izc05/magina-olivo-v20/actions/runs/36350063900)) y publicó `magina-olivo-dev-debug`; el secreto público se inyecta desde `android-ci.yml`. En el mismo emulador, la instalación anterior es `0.4.0-dev` (`lastUpdateTime=20:47`) y rechaza `adb install -r` del artefacto de CI con `INSTALL_FAILED_UPDATE_INCOMPATIBLE` por firmas distintas. No se desinstaló la app ni se borraron datos. Se intentó abrir una segunda instancia aislada, pero Android Emulator no permite duplicar ese AVD mientras el original está activo; se detuvo únicamente el proceso de prueba lanzado para ese intento. El emulador principal sigue en su versión y estado anteriores.

El código explica el texto observado: `AppCompositionRoot` construye `EdgeWeatherSource`/`EdgeRadarSource` solo cuando `BuildConfig.WEATHER_ANON_KEY` no está vacío, y `CachedWeatherFeed` devuelve `NotConfigured` si la fuente es nula. Por tanto, la captura «Sin fuente configurada» corresponde a la configuración de esa APK concreta; no refuta el despliegue ni la validación de la función. No ocultar el estado si una build realmente carece de clave, pero tampoco aceptarlo como resultado final de una build configurada.

**Pendiente de auditoría visual:** abrir el artefacto CI actual en una instalación aislada/dispositivo con firma compatible; verificar Inicio conectado (fuente y frescura), abrir radar, y repetir en modo avión para confirmar el uso de caché. El backend está validado; este recorrido móvil aún no.

## Revisión de la PR #279 — altura del Pulso diario (2026-09-27)

- PR propia de Claude: [`#279`](https://github.com/izc05/magina-olivo-v20/pull/279), `claude/pulse-widget-height`, base `main`, head `345a458d0187d2c995a45176fcc224ab07ee00b7`.
- Estado observado: abierta, no draft, sin decisión de review; estado de merge `CLEAN`. Los tres checks de Android CI (`foundation`, `gate3-emulator`, `gate3-evidence`) pasaron en run [36351103847](https://github.com/izc05/magina-olivo-v20/actions/runs/36351103847).
- Alcance del diff: `OilMarketCard.kt` + línea de changelog. La altura se mide periódicamente, se detiene tras altura estable o 20 s y se limita a 160–560 dp; no añade puente JavaScript. Revisión estática: no encontré un bloqueo de código evidente. La medición es altura HTML actual, no una garantía de que el proveedor no cambie su widget más tarde.
- Límite: no pude instalar ese APK CI sobre la instalación persistente porque Android rechazó la firma distinta; la segunda instancia del mismo AVD tampoco pudo arrancar con el principal activo. Por ello **el ajuste aún requiere confirmación visual conectado**: las tres categorías completas, incluida Lampante, sin scroll interior y sin cortar el contenido. La propia documentación Android describe `WebView.getContentHeight()` como la altura del contenido HTML; la prueba dinámica del widget real sigue siendo necesaria ([Android API](https://developer.android.com/reference/android/webkit/WebView#getContentHeight())).
- La #279 no contiene los cambios de Cuaderno, Jornadas, campaña ni Riego confirmados en este informe; mantenerlos en la PR UX propia de Claude, como acordó el propietario. No se fusionó nada ni se emitió aprobación de merge.

## Hallazgos y propuesta

### 1. Activar campaña — error reproducible, prioridad alta

La campaña sin parcelas se mantiene correctamente en Preparación: el contrato exige elegir al menos una parcela antes de activarla. Al pulsar «Activar campaña», el repositorio rechaza la operación (`Validation("parcelIds", "empty")`), pero la pantalla muestra el mensaje genérico «La operación no se pudo completar». No es una pérdida de datos ni un fallo de Room: falta explicar el requisito.

**Propuesta:** impedir la activación mientras no haya parcelas, o devolver el foco a «Editar» con un mensaje claro: «Añade al menos una parcela a esta campaña para activarla». Conservar la causa técnica y probar la validación visual y el éxito al seleccionar parcelas.

Evidencia: [campaña sin parcelas](../../artifacts/audit-20260927/02-campaign-no-parcels.png), [error genérico reproducido](../../artifacts/audit-20260927/03-campaign-activation-error.png). Existe contrato instrumentado para la regla (`CampaignLifecycleContractTest`); no se ejecutó en esta revisión.

### 2. Cuaderno — atajos repetidos y significado de «Jornal»

«Registrar hoy» abre una hoja de selección visual clara y amplia. En la pantalla principal vuelven a aparecer los mismos ocho accesos como chips, por lo que la acción se duplica sin aportar otra capacidad.

**Propuesta:** conservar «Registrar hoy» y la hoja visual con Trabajo, Riego, Tratamiento, Pesada, Gasto, Maquinaria y Documento; quitar solo la segunda fila de chips si Claude confirma que no es un acceso alternativo necesario. Mantener intactos los destinos existentes.

Al tocar «Jornal» desde el Cuaderno general, la app abre «Gastos y documentos». Esto coincide con CR-007: fuera de recolección, Jornal es un gasto de mano de obra; dentro de recolección, abre los jornales de una Jornada. El código lo implementa así, pero la etiqueta aislada induce a esperar que se anote quién trabajó.

**Propuesta:** nombrar la opción general «Gasto de jornales» (o «Pago de jornales») y reservar «Jornales» para la asistencia dentro de una Jornada de recolección. Mostrar claramente finca/campaña en esa ruta y comprobar que la hoja de registro pueda preseleccionar el contexto. No duplicar el gasto entre la actividad y el libro de gastos.

Evidencia: [Cuaderno con accesos repetidos](../../artifacts/audit-20260927/04-cuaderno-quick-actions.png), [hoja de registro](../../artifacts/audit-20260927/05-register-choice-sheet.png), [destino actual de Jornal](../../artifacts/audit-20260927/06-jornal-opens-expenses.png).

### 3. Jornadas, Pesadas y asignación a finca/parcela

La jerarquía aprobada es finca → campaña → Jornada/Pesadas. Las Jornadas y sus jornales pertenecen a la finca y campaña; el registro admite contexto de parcela cuando se conoce. La campaña debe tener parcelas seleccionadas para activarse. Según CR-005, una Jornada puede abrirse antes de conocer los kilos y recibir varias Pesadas durante el día.

Hay una discrepancia que debe resolverse antes de simplificar la interfaz: el estado vacío actual indica que «Mágina abrirá su jornada» al guardar la primera pesada, mientras CR-005 permite empezar por la Jornada. Conviene acordar un único inicio de flujo y luego ofrecer en esa ficha «Añadir pesada» y «Registrar jornales». No inventar reparto de kilos por parcela cuando no se conoce.

**A revisar con Claude:** que la entrada desde «Jornales» en contexto de recolección vaya a la Jornada activa o permita crearla; que el coste asociado continúe apareciendo una sola vez en Gastos; que se pueda identificar finca, campaña y, si procede, parcela.

### 4. Riego y planificación — campos presentes, presentación mejorable

El formulario ya separa el tipo de trabajo, fecha, parcela y datos de riego, y conserva detalles avanzados. Sin embargo, en la prueba el campo inicial se entiende como descripción genérica (“Descripción/Riego”), las bases de tarifa aparecen como una lista vertical extensa y los campos se despliegan con demasiado peso visual. Planificación pide teclear la hora y el aviso personalizado usa un formato técnico de fecha/hora.

**Propuesta:**

- Cambiar el texto del campo a «Nombre del riego» o «Referencia» sin imponer un nombre artificial.
- Mantener duración, volumen, sector y sistema disponibles, pero plegados si son secundarios.
- Presentar la tarifa en un selector compacto de elección única; mostrar debajo solo el importe/unidad que corresponda, conservando todos los tipos y la tarifa histórica.
- Elegir hora con selector nativo y fecha/hora del aviso con controles adecuados, no con entrada manual tipo `AAAA-MM-DD HH:MM`.
- Mantener los avisos locales existentes («la tarde anterior» / «el mismo día») y explicar cuándo notificará este teléfono. No prometer avisos si Android no concede permiso de notificación.
- Usar el color de la familia de agua/planificación como acento y el mismo icono de riego ya establecido; evitar añadir colores sin semántica.

Evidencia: [tarifas del riego](../../artifacts/audit-20260927/07-irrigation-tariff-fields.png), [planificación y avisos](../../artifacts/audit-20260927/08-planning-reminders.png). Los controles y reglas de persistencia no se modificaron.

### 5. Olivos, precio y gráfica — requiere distinguir conceptos

El diseño aprobado deja opcional el número de olivos y muestra «—» si falta. Hacerlo obligatorio cambia el contrato de producto y puede bloquear una finca real cuyo recuento se desconoce; requiere Change Request y compatibilidad con datos existentes. No rellenar con cero ni estimar por superficie.

«Precio por oliva» puede significar cosas distintas: precio de la aceituna por kg, precio de aceite por kg/litro, o estimación de valor de una finca a partir de kilos realmente recogidos. El recuento de árboles por sí solo no determina producción ni ingresos.

En la APK inspeccionada, Inicio muestra el Pulso diario de AOVE.net con atribución y el tiempo dice «Sin fuente configurada». Sin embargo, la función de pronóstico y radar está desplegada y el workflow en vivo pasó; queda diagnosticar la configuración de esa APK conforme a la sección de revalidación. La tendencia semanal oficial del mercado del aceite es independiente: su función `oil-market` y el slice 20D-3 siguen siendo asunto distinto.

**Propuesta:** mantener separados (a) precio de mercado por fecha y categoría, con líneas/series diferenciadas cuando se active la fuente oficial, y (b) ingresos reales de la explotación calculados únicamente desde kilos/precios guardados en Pesadas/Gastos. Para una estimación por parcela hacen falta cosecha atribuida y precio; no multiplicar precio por número de olivos.

Evidencia: [Inicio y estado de finca](../../artifacts/audit-20260927/09-home-status.png), [Tiempo y mercado](../../artifacts/audit-20260927/11-home-weather-market.png). Estado de fuente contrastado con `docs/00-master/CURRENT-STATE.md`.

### 6. Persistencia

Se forzó la detención del proceso y se reabrió la aplicación. Tras esperar a que terminara el arranque, persistieron finca, campaña seleccionada y las tres actuaciones del Cuaderno. No se observó crash en el reinicio. Esto valida únicamente el estado local ya existente; no prueba sincronización remota ni modo avión.

Evidencia: [Cuaderno tras reabrir](../../artifacts/audit-20260927/10-notebook-after-reopen.png). No se añadieron fincas/campañas ficticias ni se modificaron los datos guardados.

## Secuencia de trabajo sugerida

1. Mensaje humano para activación de campaña sin parcelas.
2. Claridad de Jornales vs. gasto de jornales y decisión única sobre cuándo nace la Jornada.
3. Quitar la fila duplicada de accesos bajo «Registrar hoy» si no rompe el flujo.
4. Afinar riego y selectores de hora/avisos, manteniendo cada campo y tarifa existente.
5. Registrar decisión de producto sobre olivos obligatorios y sobre qué precio se quiere representar; no implementar una estimación engañosa.
6. Reinstalar un build Android de `main` que incluya la clave pública, validar forecast/radar online y offline; mantener el mercado oficial y su gráfica semanal dentro del gate 20D-3, sin mezclar fuentes ni tipos de precio.

## Validación y límites

- Prueba visual en emulador: realizada para las pantallas arriba listadas.
- Detención/reapertura: realizada; los registros locales anteriores se conservaron.
- Fallo funcional reproducible: activar campaña sin parcelas produce un mensaje genérico (causa esperada: no hay parcelas asignadas).
- Crash durante el recorrido o reapertura: no observado.
- Tests, lint, build y CI: no ejecutados en esta revisión de auditoría; no hubo cambios de producción.
- Offline/sincronización, login/registro, mapas, maquinaria, avisos entregados y otros tipos de actividad: pendientes de recorrido separado; este informe no cierra la auditoría global.
- Código de la app, modelo de datos y datos persistidos: sin cambios.
