# #711 B3 — Tocar una parcela en el mapa y consultar sus datos

Estado: implementado y verificado en rama; sin APK candidata ni PASS físico de Gate21.
Base: `main` `45f618a2bce8f661a7e5d964c52e784469701046` (B3 foto + linderos + foco ya integrados).
Rama: `feat/android-b3-map-usability`.

## Qué pedía el propietario

Del orden de cierre, Bloque 1, puntos 4, 5 y 6: «permitir tocar una parcela para seleccionarla y
consultar su información», «mostrar referencia, superficie y otros datos fiables cuando estén
disponibles» y «poder añadir una parcela seleccionada a una finca».

## Hueco real encontrado

El mapa ya abría con la ortofoto PNOA, los linderos de Catastro y los recintos SIGPAC dibujados
(`a71fa8c3`), y el flujo de alta por toque existía desde la Fase 18 — **pero solo en modo
«Añadir de Catastro»**. Viendo el mapa (modo VIEW, el que se abre por defecto), un toque sobre una
parcela oficial no hacía nada: `tapMap` se limitaba a deseleccionar. El agricultor veía la parcela
dibujada y tenía que cambiar de modo antes de poder leerla.

## Cambio

- Viendo el mapa, un toque consulta Catastro en ese punto y abre la ficha de la parcela oficial:
  referencia catastral, polígono y parcela, superficie catastral y municipio/provincia cuando
  Catastro los da. Se dice que es una parcela de Catastro que **no está en tu olivar** y que el
  contorno guardado es una referencia, **no un certificado catastral**.
- Se elige la parcela **bajo el dedo**, no la primera que devuelve el servicio: punto en polígono
  por lanzamiento de rayo sobre el anillo exterior de cada polígono.
- Una parcela ya guardada **en esta finca** abre su propia ficha guardada en vez de ofrecerse otra
  vez. Una guardada **en otra finca** lo dice y no se ofrece.
- «Añadir a la finca» marca esa parcela y pasa a modo añadir: se confirma con la misma hoja de
  revisión y el mismo guardado que cualquier otra. Aquí no se guarda nada.
- El contorno de la parcela leída se dibuja mientras su ficha está abierta.
- Leer no cambia de pantalla ni marca nada para guardar; cerrar no deja datos en pantalla.

Sin rediseño: la ficha usa `MoSectionCard`, `MoLabeledValue` y los botones del sistema de diseño
actual, en el mismo panel inferior donde ya aparecía la parcela guardada seleccionada.

## Pruebas

JVM (`FarmMapViewModelTest`):
- `aTapWhileViewingReadsTheParcelUnderTheFingerAndOffersToAddIt`: con dos parcelas devueltas por el
  servicio, el toque dentro de la segunda lee la segunda; superficie y municipio llegan a la ficha;
  el modo sigue en VIEW y nada queda marcado; `addInspected()` la marca y el alta la guarda.
- `aTapOnAParcelAlreadyInTheFarmOpensItInsteadOfOfferingItAgain`.
- `aTapOnAParcelSavedInAnotherFarmSaysSoAndOffersNothing`.

Instrumentada (`FarmMapScreenTest.touchingAnOfficialParcelShowsItsDataAndOffersToAddIt`):
la ficha muestra referencia, 1,2 ha y «Huelma · Jaén», dice que no está en el olivar y que no es un
certificado, «Añadir a la finca» y «Cerrar» llaman a su acción, y sin parcela leída la ficha no existe.

Verificación local previa al push: Gradle 9.4.1 + JDK 17, `lintDevDebug testDevDebugUnitTest
assembleDevDebug assembleDevDebugAndroidTest` BUILD SUCCESSFUL.

### Emulador local (nuevo en esta sesión)

Se ha creado un AVD propio, `MaginaOlivo_Claude_API35` (API 35, google_apis, x86_64), para ejecutar
pruebas instrumentadas sin esperar a CI y sin tocar los AVD de QA anteriores. Resultados sobre el
mismo código que se publica:

- `DemoFarmSeederTest`: 2/2 PASS (incluye cargar dos veces, restablecer y retirar).
- `HomeFeedsScreenTest`: 12/12 PASS (incluye la tarjeta de riego nueva).
- `AppNavigationTest` + `B3MapViewportTest` + `B3FocusedPointViewportTest` + `FarmMapScreenTest`:
  42/43 PASS. El único fallo, `coordinateFocusClearsPanelsAndViewportChangesPreserveLaterPanAndFrame`,
  es «No compose hierarchies found in the app» en este AVD recién creado: el caso pasa en CI sobre
  `main`, así que se registra como limitación del entorno local, no como regresión.
- En CI, `gate3-emulator` falló una vez en `AppNavigationTest.centralAddIsAButtonAndCancelsOnEveryRootWithoutNavigation`
  con `RootViewWithoutFocusException` en `pressBack` (10 s esperando el foco de ventana), mientras
  `foundation` y `gate3-evidence` pasaban. Es el mismo tipo de inestabilidad de foco/SystemUI ya
  documentado en B3; no toca ningún fichero de este cambio.

## Límites y siguiente paso (no inventados)

- **La información del recinto SIGPAC no se consulta todavía.** El WMS de SIGPAC se usa para dibujar
  (GetMap 1.3.0); su `GetFeatureInfo` respondió 200 en el preflight de B3, pero leer el uso del
  recinto es una integración aparte y no se simula. Queda como siguiente *slice* de mapa.
- No hay leyenda permanente que distinga a la vista linderos de Catastro, recintos SIGPAC y parcelas
  guardadas: se distinguen por su trazo y por el menú de capas. Mejora cosmética pendiente.
- Sin conexión no hay consulta: el toque informa del fallo y las parcelas guardadas siguen visibles
  y seleccionables, que es el comportamiento ya verificado en B3.
- Falta la prueba física del propietario en el móvil real (Gate21).
