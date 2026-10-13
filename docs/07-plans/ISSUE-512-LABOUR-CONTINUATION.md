# Continuación #512 — jornal según contexto

Base main `26111d6b`, tras fusionar #747 con autorización. Worktree y rama propios. Se ejecuta el diseño ya aprobado por el propietario en #512 (11/10): formulario directo, Asignar a dentro del formulario, sin modal previo ni botones permanentes nuevos.

1. Probar el guardado de recogida: campaña explícita todavía operativa, fecha/contexto correcto, error antes de escribir ante ambigüedad/cambio de campaña, mismos precios/pagos/personas. La jornada solo se abre al guardar, dentro de la misma transacción que el jornal.
2. Reutilizar LabourSheet para recogida y ExpenseEditor para general. Conservar sus borradores al alternar; compartir fecha y mantener finca/parcela. Explicar que general sigue siendo Expense/LABOR sin detalle completo de persona/horas hasta #412. No inventar un Harvest general.
3. Corregir los dos accesos globales a Jornal. Campaña/Jornada y Trabajo general conservan sus entradas específicas. Guardado general con campaignId/harvestId nulos; recogida usa el ledger derivado existente una sola vez.
4. JVM, pruebas Room reales de ambos ámbitos/costes/reinicio, formulario nativo y matriz 360/390/430 dp/fuentes/temas. No cambiar Room ni cálculos. Revisar, commit, push, PR y #744; autorización antes de fusionar.

No APK candidata ni producción; no escribir en ramas de otros agentes. SQLite del emulador propio se respalda antes de pruebas con escrituras sintéticas.
