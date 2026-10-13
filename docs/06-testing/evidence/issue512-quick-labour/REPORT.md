# #512 — Jornal dentro/fuera de campaña

Base main `26111d6b`, rama/worktree independientes `codex/issue512-labour-continuation`. Se ejecuta el formulario aprobado en #512 el 11/10: acceso directo y Asignar a dentro del alta, sin selector/modal previo ni botones permanentes nuevos.

## Comportamiento y conservación

Cuaderno y + global abren Añadir jornal; General está visible y editable cuando hay campaña. Sin campaña no se muestra selector inútil. El alta general reutiliza ExpenseEditor, categoría LABOR, sin campaignId ni harvestId, y muestra honestamente que persona/horas generales aún dependen de #412. Los trabajos elegibles son generales; se revalida su contexto al guardar. Campaña/Jornada y Trabajo conservan sus rutas específicas.

Recogida reutiliza LabourSheet, sus personas, horas, tarifas, pagos y reglas. La campaña escogida mantiene su UUID: un cambio no sustituye la selección silenciosamente; varias campañas operativas bloquean recogida global. Se crea/resuelve la jornada únicamente al guardar y dentro de la misma transacción que el jornal. Un AppResult.Failure revierte también día y outbox.

Los dos borradores, finca/parcela y fecha se conservan al alternar. Recarga o fecha inválida bloquean guardar sin desmontar los campos. La moneda histórica usa el resolvedor existente; una moneda distinta exige revisar explícitamente precio y pago, con aviso antes de limpiarlos, sin conversión. El token de revisión se consume una sola vez y se conserva al restaurar. La carga inicial espera moneda confirmada, evitando pedir conversión sobre un borrador vacío.

No cambian Room, esquema, contratos de repositorios, cálculos, ledger, sumatorios, coste/kg, informes/PDF ni sincronización. General usa Expense/LABOR único; recogida usa LabourEntry y el Expense derivado existente, sin copiar dinero entre ámbitos.

## Pruebas y evidencia

Compilación DEV/debug e instrumental y 590 JVM pasan, cero fallos/errores. Matriz definitiva completa con adjustResize de MainActivity: 18/18 (tres tests por perfil: 360/390/430 dp × fuentes 1.0/1.3, claro/oscuro y moneda inicial). El selector de ámbito permanece visible al escribir con el teclado abierto, también en 360 dp y fuente 1.3. Scope conserva concepto/importe general y persona/3h/precio de recogida; revisa cambio monetario, recarga, fecha inválida, campaña reemplazada, ninguna/varias campañas y finca/parcela. Controles de ámbito mínimos 48 dp.

Los logs RED conservados muestran sustitución de campaña, desmontaje de borrador, reset monetario repetido y moneda prematura. Se revisó el código de forma independiente y se corrigieron los defectos, sin relajar asserts.

Room real usa exclusivamente bases UUID desechables, no la base operativa. Comprueba rollback del día/outbox ante persona rechazada, repetición sin duplicar, cierre/reapertura, 65 EUR general más 36 EUR recogida, y coste/kg con pesada sintética de 1000 kg (origen explícito). 45/45 pruebas instrumentadas pasan con modo avión activo y Wi-Fi desactivado: formulario, Room, accesos globales, Campaña, gastos relacionados, resumen de jornales, moneda/desbordamiento y aislamiento. Los dos recorridos reales de MainActivity pasan también offline: sin campaña y con campaña, 65 EUR general / 36 EUR recogida, reapertura, recreación de Activity y acceso desde Campaña. Total local: 47/47 pruebas offline, además de 18/18 de la matriz.

AppNavigationTest usa IsolatedAppStateRule: vincula temporalmente una base de prueba al singleton y conserva/restaura todas las preferencias en finally, también ante excepción. No vacía la base original. La prueba de aislamiento verifica todos los tipos de preferencias y el contador original de fincas. Las expectativas anteriores que abrían la jornada por existir campaña se adaptan al contrato aprobado; se conservan las comprobaciones de contexto, dinero, recreación y acceso desde Campaña.

## Límites

Capturas directas, sin edición. Solo emulador propio API35 emulator-5614. verify-quick-labour.ps1 restaura densidad/fuente y no borra datos operativos. El modo avión/Wi-Fi/Bluetooth se respalda y restaura por separado; estado final disabled/1/1, coincidente con el original. Sin prueba física, reinicio completo del proceso, detalle de trabajador general de #412, APK candidata, despliegue Supabase ni producción. CI se registra por SHA tras publicar.