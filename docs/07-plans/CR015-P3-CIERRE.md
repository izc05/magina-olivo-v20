# CR-015 P3 — Cierre técnico

Fecha: 07/11/2026. Visto bueno del propietario tras revisar la APK DEV.

## Alcance cerrado

Ciclos agrícolas locales por finca en Room (migración 34→35, tablas laterales aditivas):
creación y cierre por finca, vínculo de trabajos, gastos, recolecciones, pesadas y jornales,
histórico conservado, auditoría en `agricultural_cycle_operations` y protección de ciclos cerrados
(`source_cycle_closed`, `cycle_closed`). Sin cambios en Supabase, SQL, sincronización ni economía P5.

## Integración

- `feat/cr015-p3-local-cycles` (#859) avanzada por fast-forward `cfad9dd58..0c1178f66`.
- Referencia de seguridad: etiqueta `backup/cr015-p3-pre-integration` → `cfad9dd58`.
- Incluye #860 (protección de ciclos cerrados, CI foundation) y #861 (jornal de campaña, aviso compacto,
  estabilidad de pruebas). El trabajo local de Codex sobre `QuickLabourScreen.kt` y `QuickLabourFormTest.kt`
  está incorporado sin cambios en `4a85665bb`.
- Validado en `0c1178f66`: foundation, gate3-emulator (1238 pruebas, 312 en modo avión) y gate3-evidence,
  en #861 y de nuevo en #859 tras el fast-forward (Android CI 42378515751, Gate 3 42378515767).
- Recorrido con la APK DEV sin Auth DEV: finca → parcela → ciclo → gasto → recolección → pesada →
  jornal → cierre → reinicio en modo avión, sin pérdida de datos.

## Pendientes posteriores (no bloquean P3)

- Retoques visuales y de usabilidad menores detectados en la revisión del propietario.
- Flujo de primera pesada: exige parcela y recolección activada antes; valorar guiarlo desde el asistente
  de fincas y parcelas.
- El cierre del ciclo exige finalizar antes la recolección; valorar un acceso directo desde el aviso.
- `ActivityEditorCompactTest`: intermitencia previa a P3, tarea aparte.
- Reapertura controlada de ciclos: fuera de alcance por decisión del propietario.
- Sincronización de ciclos: P4, con autorización independiente.
