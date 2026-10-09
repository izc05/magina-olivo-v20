# #705 — CR-014 navegación Android

Codex implementa el único slice Android productivo, rama `codex/705-cr014-navigation`, desde main `324af37a8ff83efdb12c71482fa5b9c1caba432d` (#713). [Contrato aprobado](../00-master/RC1.2-CHANGE-REQUEST-014-GLOBAL-ADD-NAVIGATION.md). Validación local terminada; revisión del commit y tres controles remotos pendientes antes de integrar.

## Comportamiento y datos

Cuatro pestañas y botón central «Añadir registro», con semántica Button y sin estado de pestaña seleccionada. La campana de Inicio abre la Agenda existente. Los seis accesos reutilizan los formularios actuales. La hoja se cierra antes de navegar y cancelar no escribe registros agrícolas.

Se valida finca, workspace y parcela; una finca inválida pide selección. La parcela se observa continuamente: archivar, mover o cambiar finca elimina el contexto inválido. Jornal/Pesada esperan al resultado conocido de campañas. Una campaña histórica no fuerza el destino del Gasto general: permanece la elección explícita de #411. La finca visible de Cuaderno se comunica a su entrada de navegación, evitando que cambiar la preferencia desde una hoja cancelada desplace el contexto visible. No cambia Room, migraciones, ledger ni adjuntos.

La cabecera de Agenda permite que «Planificar trabajo» pase a la siguiente línea con fuente grande; evita que «Avisos» quede partido en 360dp. Las etiquetas inferiores admiten dos líneas.

## PASS verificado local

- Foundation: lintDevDebug, testDevDebugUnitTest, assembleDevDebug, assembleStagingDebug, assembleProductionDebug y assembleDevDebugAndroidTest. BUILD SUCCESSFUL, 9m38s; XML: **526 JVM**, cero fallos, errores u omisiones. [Log](evidence/705/local-foundation.txt).
- **62 instrumentadas**, 342,113s: 31 AppNavigationTest, 10 GlobalQuickActionsTest, 3 NotificationAccessTest, 14 AgendaReminderContractTest, 4 ReminderPreferencesContractTest. [Log](evidence/705/local-62-instrumentation.txt).
- **Dos pruebas offline**, 25,197s: botón/cancelación en cada raíz y seis acciones. Red ausente comprobada antes de iniciar: airplane=1, default network none/null, Wi-Fi apagado. Conectividad restaurada al terminar. [Pruebas](evidence/705/local-offline-navigation.txt), [estado previo](evidence/705/offline-network-state.txt). Son casos repetidos, no 64 casos distintos.
- Recorridos reales: Trabajo; Pesada sin campaña con bloqueo correcto; Pesada con finca/campaña; Jornal directo y global sin duplicar jornada tras recreación; Cuaderno A→hoja B→cancelar→A, incluso tras recreación. Cancelaciones conservan recuentos agrícolas.
- Riego, Tratamiento y Gasto abiertos manualmente y cancelados sin guardar: XML incluidos. Gasto exige elegir campaña o fuera de campaña.

Comandos ejecutados con JDK17, Gradle9.4.1, `--no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`, API35, emulador desechable **5580**. APK local SHA256: `d6dbae2f8058595548d46adb8b633f65b239fce6c4bbb10552df377d070d2363`.

## Evidencia visual y regresiones

[Manifiesto SHA256](evidence/705/sha256.json): **36 PNG**, 33 finales y tres anteriores al ajuste de Agenda. Capturas 360/390/430dp, densidad440, fuente1,3: Inicio, cuatro hojas +, Agenda, Mi Campo, Cuaderno, Perfil, selector de finca y tres formularios Riego con teclado realmente visible. Las 33 capturas finales son posteriores a las 62 pruebas y usan el APK final. Los estados IME prueban `mInputShown=true` y `mIsInputViewShown=true`.

RED reproducidos: [tres fallos iniciales de navegación](evidence/705/red-navigation.txt), [dos fallos por parcela archivada/movida](evidence/705/red-parcel-context.txt), [finca visible desplazada](evidence/705/red-visible-farm.txt). Todos cubiertos en la ejecución final verde. Intentos intermedios afectados por diálogo SystemUI al arrancar o selectores de test ambiguos no se presentan como PASS. Las aserciones nuevas de Pesada se ajustaron a su contrato existente: bloqueo sin campaña y cierre del editor antes del segundo Back.

El emulador retenido **5566**, sus datos y fotos permanecen intactos. Los fixtures y restauración sintética se limitan al clon5580. No se publican bases, fotografías, backups ni APK local en esta evidencia.

## Integración y siguiente bloque

Integrar únicamente tras revisión independiente del SHA final y SUCCESS de foundation, gate3-emulator y gate3-evidence sobre ese mismo SHA. CI/PR/merge todavía pendientes; no se anticipan resultados remotos. Después continúa #707 (apariencia), seguido de B3/B4/B5 según #711/#696. Gate21 en móvil físico pendiente del propietario; Web V3 pausada.
