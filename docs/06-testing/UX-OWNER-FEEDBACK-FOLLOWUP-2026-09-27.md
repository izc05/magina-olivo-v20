# Revisión de comentarios UX — continuación (2026-09-27)

## Contexto de revisión

- Rama: `codex/review-current-ux-20260927` (derivada de `main`; no se modifica `main`).
- Base revisada: `dc487a722e7bb39885f1cadb2c98126441a7c1c2` (merge PR #277).
- Aplicación observada: `0.4.0-dev`, paquete `com.isivoltpro.maginaolivo.dev`, emulador `emulator-5554`.
- Rol de esta rama: auditoría y documentación. No competir con Claude implementando los mismos cambios.
- Datos encontrados al empezar: finca «Los Llanos», 3 parcelas, campaña `Campana_2026_2027` en Preparación sin parcelas, y tres actuaciones ya registradas (poda, riego y tratamiento). Conteo de olivos desconocido. Se conservaron; no se limpió ni se inventó información agronómica.
- Recorrido: Inicio, Mi Campo/finca, detalle y activación de campaña, Cuaderno, opciones de registro, ruta de Jornal, formulario de riego, planificación/avisos y cierre forzado/reapertura.

Las capturas están en [`artifacts/audit-20260927/`](../../artifacts/audit-20260927/). La captura `01-current` ya existía al comenzar esta continuación y se ha preservado.

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

En Inicio se muestra el Pulso diario de AOVE.net con atribución. El tiempo dice «Sin fuente configurada» en esta instalación, y la tendencia semanal oficial está sin configurar hasta desplegar la función de mercado; no implica que falle el registro de labores. La gráfica de tendencia semanal de precios está prevista para el slice 20D-3, después del despliegue manual pendiente.

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
6. Desplegar/verificar fuentes de clima/mercado y terminar la gráfica semanal cuando toque el gate 20D-3.

## Validación y límites

- Prueba visual en emulador: realizada para las pantallas arriba listadas.
- Detención/reapertura: realizada; los registros locales anteriores se conservaron.
- Fallo funcional reproducible: activar campaña sin parcelas produce un mensaje genérico (causa esperada: no hay parcelas asignadas).
- Crash durante el recorrido o reapertura: no observado.
- Tests, lint, build y CI: no ejecutados en esta revisión de auditoría; no hubo cambios de producción.
- Offline/sincronización, login/registro, mapas, maquinaria, avisos entregados y otros tipos de actividad: pendientes de recorrido separado; este informe no cierra la auditoría global.
- Código de la app, modelo de datos y datos persistidos: sin cambios.
