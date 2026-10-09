# #706 — contraste de superficies Android

Issue [#706](https://github.com/izc05/magina-olivo-v20/issues/706), B2 de [#711](https://github.com/izc05/magina-olivo-v20/issues/711). Rama `codex/706-surface-contrast`, base main `45e21fe05624ba68a6020c43258ef75e1351c700`. Android únicamente; Web V3 pausada. Estado: integrada mediante PR #713 el 2026-10-09T09:28:15Z.

## Cambio acotado

Cinco roles leen el esquema Material activo: `appBackground`, `cardSurface`, `cardStroke`, `cardElevated`, `secondaryText`. Componentes comunes, tarjetas y pantallas agrícolas usan esos roles. Se mantiene la identidad verde/crema, las categorías de iconos y el CTA olivo. No cambia Room, navegación, selección de finca/campaña ni contratos monetarios.

| Rol | Antes | Nuevo claro |
| --- | --- | --- |
| Fondo | `#F8F6EE` | `#F1ECDF` |
| Tarjeta | `#FFFEFA` | `#FFFEFA` |
| Contorno | `#E5E1D6`, a veces con alfa | `#8B7E68`, opaco |
| Superficie de campos/elevada | `#F4F1E8` | `#F8F3E8` |
| Texto secundario | `#67625A` | `#575248` |

Los valores de compatibilidad anteriores siguen disponibles para usos explícitos de marca y fotografía. No se han sustituido los blancos sobre fotos o sobre el botón primario. MapLibre/PNOA, hero fotográfico de Inicio y barras del sistema quedan fuera del cambio. Las barras de navegación de la aplicación reciben color y borde, sin cambiar destinos ni callbacks.

La lectura de roles es composable y sigue el esquema activo. Esto prepara el cambio independiente #707; no supone que el modo oscuro ya esté implementado.

## Pruebas y revisión

- RED inicial: seis pruebas de contraste, dos fallos en contorno y error sobre el fondo; `artifacts/696-e2e/706-red-contrast.txt`.
- Primera corrección: seis pruebas de contraste y build DEV PASS, 3m49s; `706-green-contrast-build.txt`.
- Validación completa intermedia: lint DEV, 526 JVM sin fallos/errores/omitidos, builds DEV/staging/production y test APK PASS, 9m24s; `706-green-foundation.txt`.
- Revisión independiente de código: detectó alfa residual en el contorno del pronóstico y roles antiguos en las barras comunes; corregidos. Revisión posterior sin bloqueadores de código, a falta de ejecución final y capturas.
- Ampliación posterior de los casos de chips: RED confirmado, 526 JVM / un fallo de contraste de 4,38:1, build fallido 8m46s; `706-red-chip-foundation.txt`. Los chips ahora calculan su relleno suave sobre la tarjeta y lo pintan opaco; mantienen los tonos y etiquetas, sin depender del fondo de la pantalla. La prueba mide el relleno que usa el componente y exige opacidad completa. La validación posterior se registra debajo.
- Corrección de chips validada con el build completo: 526 JVM sin fallos, lint y tres variantes PASS / 8m54s; `706-green-chip-foundation.txt`.
- Última ampliación: nubes, lluvia, cielo, sol, aviso offline/fuente con alfa real y tres estados de sincronización. RED de contorno sobre nubes: 2,99:1, seis pruebas / un fallo, 1m21s; `706-red-tinted-outlines.txt`. Se ajusta el borde a `#8B7E68`: aproximadamente 3,37:1 sobre fondo, 3,94:1 sobre tarjeta, 3,35:1 sobre nubes y 3,09:1 sobre el aviso con alfa 0,12. La validación final completa se registra debajo; los PASS intermedios no sustituyen el cierre final.

La medición programática cubre pares concretos de color; no declara conformidad WCAG de toda la aplicación. TalkBack, luz exterior y aceptación física siguen pendientes de Gate21.

Validación completa anterior al último ajuste visual de selectores: `lintDevDebug testDevDebugUnitTest assembleDevDebug assembleStagingDebug assembleProductionDebug assembleDevDebugAndroidTest`, PASS / 12m29s, 526 JVM sin fallos/errores/omitidos. Se usó `--no-daemon --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process` para limitar la memoria local; no cambia el CI. Log conservado `706-green-pre-chip-controls-foundation.txt`.

La revisión visual detectó que los FilterChip de vistas y campañas conservaban el borde tenue predeterminado de Material, igual que antes. Se explicita `cardStroke` para el estado normal y el olivo primario para el seleccionado, conservando los callbacks, selección y tamaño. La regresión también incluye `secondaryContainer`. Validación actualizada PASS: `706-final-foundation.txt`, BUILD SUCCESSFUL en 9m40s. 526 JVM sin fallos, errores ni omitidas; lint DEV y builds DEV/staging/production/test APK. Treinta capturas finales de las diez superficies y tres adicionales de foco/teclado completadas con esa APK; instrumentación dirigida PASS: 89 tests en API35 / 204,58s, log `706-final-directed-ui.txt`.

## Capturas comparativas

Antes: DEV 1606 de main `fe79efd17c67450d0923868be110a52c33e6d4a3`, AVD API35 conservado. Treinta capturas reales: diez superficies, 360/390/430dp con fuente 1,3x, en [evidence/706](evidence/706/). Las modificaciones de contexto de #712 y título de #710 son independientes del contraste; la comparación puede mostrar esas diferencias de contenido.

Después: treinta imágenes finales obtenidas después del último ajuste de selectores en AVD desechable con una copia comprobada de los datos QA, sin guardar formularios ni registros. Tres imágenes adicionales de Gasto acreditan campo enfocado y teclado visible, comprobado con dumpsys input_method. Las 63 PNG y sus hashes están en el README/manifest de evidence/706. No instalar la APK local con versión inferior en el AVD conservado ni ejecutar allí fixtures destructivas.

La APK local de QA no incluye `SUPABASE_ANON_KEY`, que el CI recibe de su configuración. Inicio muestra «Fuente del tiempo no configurada» en esa ejecución, mientras la captura anterior del CI muestra MET Norway. Esa diferencia de estado no procede del contraste; el hero meteorológico no cambió. No presentar las capturas locales como verificación de conexión ni cerrar B4 con ellas.

## Integración y siguiente bloque

PR #713 integrada. HEAD revisado `330e21f943773aac772e1c01e0a523d212a805b8`, merge main `324af37a8ff83efdb12c71482fa5b9c1caba432d`. Revisión independiente final conforme y los tres controles SUCCESS sobre ese HEAD: Android run37893515488, evidencia run37893515594. Logs remotos confirman 690 instrumentadas, 196 offline y cuatro tandas de 14 capturas, sin sumar pruebas solapadas. Resultado publicado en #711/#696/#706 y #706 cerrado. Continúa #705/CR-014 en PR separada. La APK candidata desde main y el móvil físico corresponden a B5; Gate21 permanece abierto.
