# Auditoría UX Android 0.3.0 — avance y bloqueo

Fecha: 2026-09-27  
Base: `origin/main` / `0cd1fa4e8b8696f1163b81a5f99337a422b57188` (Mágina Olivo 0.3.0)  
Rama: `codex/ux-audit-0.3.0`

## Observado

En el estado vacío de “Mi Campo” aparecían a la vez cifras en cero, “Añadir finca” y “Crear mi primera finca”. El formulario de alta mostraba de entrada municipio, provincia, descripción y notas, aunque todos son opcionales. Evidencia visual previa al cambio: [Mi Campo vacío](../../artifacts/ux-audit/mi-campo-portrait.png), [alta de finca](../../artifacts/ux-audit/farm-create-empty.png) y [teclado/formulario](../../artifacts/ux-audit/farm-create-keyboard.png).

En la instalación 0.3.0 el onboarding permite continuar al Inicio sin cuenta. No se encontró un flujo de alta de usuario, login o recuperación que recorrer en esa versión; no se añadió ninguno.

## Cambio aplicado

- El estado vacío conserva una sola acción “Crear mi primera finca”; las cifras vacías y el segundo CTA desaparecen. Con fincas existentes se mantienen las cifras y “Añadir finca”.
- Municipio, provincia, descripción y notas continúan en el modelo y en el formulario, pero quedan plegados bajo “Más detalles” al crear. Al editar, se abren automáticamente si ya contienen datos.
- Se mantienen los test tags de navegación existentes para no romper los recorridos automatizados.

Antes: dos acciones de creación y estadísticas vacías antes de iniciar. Después: un CTA en el estado vacío y solo nombre como dato visible al crear; el resto queda a un toque, sin perder los campos.

## Validación

- `:app:testDevDebugUnitTest`: 208 tests, 0 fallos.
- `:app:lintDevDebug`: correcto; informe indica 36 warnings y 1 hint (sin advertencias nuevas en las líneas modificadas).
- `:app:assembleDevDebug`: correcto; APK `app/build/outputs/apk/dev/debug/app-dev-debug.apk` (`0.3.0-dev`, `versionCode=1`).
- `FarmScreensTest`: 5/5 pasó en el emulador tras el cambio principal. Después amplié el test para comprobar que los cuatro campos opcionales empiezan ocultos y que sus valores se conservan; la versión ampliada compila, pero no se pudo volver a ejecutar por la desconexión del dispositivo.
- Suite Android completa: arrancó 276 tests y el dispositivo `emulator-5554` se desconectó durante `AppNavigationTest.registerTodayShowsInTheDiaryAndSurvivesARestart`. Llegó a completar 8 casos; el informe atribuye el fallo a `device offline` y no contiene fallo de aserción de la aplicación. Después `adb devices` quedó vacío y no hay AVD local configurado para reabrir.

## Pendiente

Esta es una corrección localizada, no la auditoría completa solicitada. No se pudo hacer el recorrido manual visual posterior al cambio, comprobar la APK nueva en cold start/reapertura ni completar todos los flujos de parcelas, campañas, Cuaderno, producción/gastos, mapas, avisos y perfil. La pantalla del dispositivo estaba temporalmente ajustada a 1080×1920 y 420 dpi; al desaparecer el dispositivo no fue posible restaurar esos valores por ADB. Al reconectarlo, ejecutar `adb shell wm size reset` y `adb shell wm density reset` y completar la pasada en emulador antes de dar por cerrada la auditoría.

No se hizo push, PR ni merge. El cambio está en el commit `54d518d9` de la rama indicada.
