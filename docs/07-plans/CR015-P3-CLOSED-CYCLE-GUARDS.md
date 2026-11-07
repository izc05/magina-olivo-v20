# CR-015 P3 — Protección de ciclos cerrados y CI foundation

Base: P3 publicada `cfad9dd58` (PR #859). Rama independiente; no sustituye a #859.

## Decisión del propietario (07/11/2026)

- Un ciclo cerrado conserva su historial.
- `assign` no mueve ningún registro desde un ciclo cerrado (`source_cycle_closed`).
- `assign` no asigna gastos a un ciclo cerrado (`cycle_closed`).
- No hay movimientos silenciosos entre campañas: toda asignación exige motivo y queda en
  `agricultural_cycle_operations`.
- La reapertura controlada queda fuera de alcance; se estudiará más adelante.
- Los vínculos y operaciones ya registrados no se modifican.

Sin cambios: añadir trabajos o recolecciones históricas a un ciclo cerrado dentro de su
intervalo sigue las reglas ya existentes de P3. El panel deja de ofrecer gastos como
candidatos de un ciclo cerrado.

Pruebas: `AgriculturalCycleRoomTest` (RED 2 fallos con `cfad9dd58`, GREEN 17/17 con la corrección).

## CI foundation

La ejecución de #859 se canceló a los 30 minutos con las tres compilaciones Kotlin
(dev, staging, production) arrancadas a la vez y sin terminar tras 26 minutos, sin errores.
En P1 las mismas tres compilaciones tardaron unos 5 minutos. Hipótesis: presión de memoria
en el daemon Kotlin compartido (3 GB), ya vista antes a 2 GB.

Cambios: límite del job a 45 minutos, `--max-workers=2` (se ejecutan todas las tareas y
variantes) y muestreo de GC de los daemons cada 2 minutos. La hipótesis solo se dará por
confirmada con una ejecución de CI correcta y sus muestras.

Resultado en #860 (`0de05f137`): `foundation` correcto en 9 min 37 s (build 9 min 11 s). Con dos
workers, la generación antigua del daemon Kotlin llegó al 93 % con 10 GC completos (7,1 s):
la presión de memoria es real; con tres compilaciones simultáneas habría superado los 3 GB.

## Jornal de campaña (continuación, rama `feat/cr015-p3-completion`)

- Causa del fallo de Gate 3 en #859: el selector completo de ciclo se dibujaba sobre el formulario
  de altura fija del jornal de campaña, sin guardar nada (el jornal hereda el ciclo de su recolección),
  y dejaba `labour-add-worker` fuera de alcance.
- Se recupera sin cambios la corrección local que dejó Codex (quitar el selector + su prueba).
- Se añade un aviso de una línea, solo lectura, en la cabecera, únicamente si guardar sería rechazado:
  recolección sin vincular o ciclo cerrado. Sin ciclos o con ciclo abierto no se muestra nada.

## Limitaciones conocidas

- La APK DEV de revisión se compila sin Auth DEV (`MAGINA_AUTH_DEV_URL`/`PUBLIC_KEY` vacíos), como en
  la validación local de Codex: la cuenta remota y la sincronización no están activas en esa APK.
- Las clases de Catastro en vivo y las siete `*HttpsEphemeralTest` necesitan red o el certificado TLS
  desechable; Gate 3 las excluye a propósito y se validan en sus propios jobs.
- `ActivityEditorCompactTest` es intermitente con la suite completa (un método distinto en cada fallo,
  3/3 en aislado). No ejecuta código de P3 y su fichero no cambia en P3.
- Reapertura controlada de ciclos: no implementada (decisión del propietario).
- Sin cambios en Supabase, SQL, sincronización remota, OCR ni economía P5.
