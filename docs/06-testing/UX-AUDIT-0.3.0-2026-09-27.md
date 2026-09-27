# Auditoría UX Android 0.3.0

Fecha: 2026-09-27  
Base: `origin/main` / `0cd1fa4e8b8696f1163b81a5f99337a422b57188` (Mágina Olivo 0.3.0)  
Rama: `codex/ux-audit-0.3.0`

## Alcance y estado inicial

Se partió de la rama Android estable en `origin/main`, tras revisar commits y cambios recientes. No se modificó `main`. La app abre desde un onboarding de seis pantallas y permite continuar sin cuenta. En 0.3.0 no hay alta/inicio/recuperación de sesión que probar; no se inventó ese flujo.

La pantalla inicial conserva la fotografía del olivar, crema y verde olivo, un resumen vacío accionable y los cinco destinos acordados. “Mi Campo” es de consulta y Cuaderno de registro. La primera finca se guarda localmente. Evidencia de la ejecución visual limpia: `artifacts/ux-audit/01-onboarding.png`, `03-inicio.png`, `05-finca-vacia.png`, `07-finca-creada.png`, `08-finca-detalle.png` y `09-parcelas-vacias.png` (capturas y árboles UI del AVD API 35).

## Hallazgos y cambios

- **Mi Campo vacío:** mostraba cifras a cero, un botón “Añadir finca” y otro “Crear mi primera finca”. Ahora hay una sola acción para comenzar; las estadísticas aparecen al existir fincas. El test verifica ambos estados.
- **Alta/edición de finca:** nombre como dato inicial; municipio, provincia, descripción y notas continúan disponibles en “Más detalles”. Al editar se despliegan si ya tienen datos. El test verifica visibilidad y conservación de campos.
- **Ruta finca → Parcelas:** la pantalla repetía “Parcelas” y no ofrecía una vuelta visible; el agricultor dependía del botón Atrás del sistema. Ahora muestra una acción táctil con icono y texto “Volver a [finca]”, conserva el contenido/título de la sección y retorna al detalle de esa finca. Se añadió prueba instrumental que lo abre, lo pulsa y vuelve a entrar en Parcelas.

Antes → Después: estado vacío con tres señales competidoras → un inicio claro; formulario con cinco campos visibles → nombre y detalles opcionales plegados; pantalla anidada sin regreso visible → regreso explícito con el nombre de la finca. No se eliminaron datos ni capacidades.

## Recorrido y limitaciones de producto

Se instaló y ejecutó la APK `dev/debug` en un AVD nuevo API 35 x86_64 conforme al nivel de CI, desde onboarding limpio. Se recorrieron onboarding, Inicio, Mi Campo, alta de finca, detalle y parcelas vacías; se comprobó teclado sin cubrir el botón Guardar, persistencia local y regreso/cold launch. La captura `artifacts/ux-audit/19-parcels-back.png` verifica visualmente el regreso y el título único de Parcelas. Tras forzar cierre y reabrir, Inicio volvió a cargar con los datos locales (`21-reopen.png`). Las pruebas instrumentales existentes recorren ciclo de finca/parcela/campaña, Cuaderno y registro de actividad, reinicio, mapas, campañas, cosecha/entregas/gastos, maquinaria, avisos, perfil, Room y flujos offline.

No se encontró autenticación en esta versión. Catastro en vivo es un flujo dependiente de red; no se pudo completar la consulta real en el AVD (ver validación). Las secciones parciales no se ampliaron fuera del alcance. No se afirma haber hecho manualmente cada combinación de actividad, gasto, perfil o ajuste: se usó la cobertura automatizada existente para esos flujos.

## Validación

- Compilación Kotlin de app y androidTest: correcta.
- Prueba instrumental focal `AppNavigationTest.parcelCanBeCreatedAndOpenedFromItsFarm`: 1/1 correcta.
- Suite instrumental completa en `MaginaOlivo_UX_API35`: 276 tests, 272 correctos y 4 fallidos, 0 omitidos. Los cuatro requieren consulta oficial de Catastro en vivo; el emulador no resuelve `ovc.catastro.meh.es` (`UnknownHostException`/`CadastreException: NETWORK`). `AppNavigationTest`: 18/18 correcta; Room migrations y pruebas offline incluidas sin fallos.
- `:app:testDevDebugUnitTest`: 208 tests, 0 fallos.
- `:app:lintDevDebug`: correcto; informe indica 36 warnings y 1 hint existentes.
- `:app:assembleDevDebug`: correcto tras los cambios; APK `0.3.0-dev`, `versionCode=1`.
- APK local: `app/build/outputs/apk/dev/debug/app-dev-debug.apk`, variante `dev`, nombre `0.3.0-dev`, código 1. No es la APK firmada de CI/código 535.

## Git

Base `0cd1fa4e`; commits existentes en la rama: `54d518d9` (simplificación de alta vacía) y `d3411d75` (avance de auditoría). Los cambios de regreso visible y este informe se registrarán en un commit separado. Sin push, PR nuevo ni merge.
