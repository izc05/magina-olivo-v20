# Incidencia y nitidez de mapas — 27/09/2026

## Diagnóstico

- La pantalla de ubicación usa MapLibre con el fondo IGN (`MapBase.MAP`) por defecto. La foto aérea PNOA ya existe como opción del selector de capas.
- El área vacía de la captura no se debe a que falte una capa en la app: el log del emulador muestra `Unable to resolve host "www.ign.es": No address associated with hostname` para las peticiones de mosaicos.
- Los mismos servicios IGN y PNOA responden HTTP 200 desde el equipo. El mosaico consultado para cada uno mide 256×256 px.
- La app declaraba esos dos fondos con `tileSize=512`, por lo que MapLibre los interpretaba a una escala que los hacía verse borrosos. Se ajusta a 256 para corresponder con el tamaño publicado. Catastro WMS conserva 512×512, que es el tamaño solicitado en su URL.

## Cambio

- IGN base y ortofoto PNOA pasan a `tileSize=256`, mejorando detalle al nivel de zoom equivalente.
- La resolución nativa aumenta las peticiones necesarias (aproximadamente cuatro veces frente a 512 en una misma superficie visible); por eso el mapa base ligero continúa por defecto y la foto aérea se mantiene como elección explícita.
- No se cambió el proveedor ni se añadió una integración. En este ciclo no fue posible comprobar visualmente los mosaicos dentro del emulador porque su DNS no resuelve `www.ign.es`; no se declara corregida esa conectividad externa.

## Validación

- Prueba TDD `ParcelMapFeatureTest.eachBaseLayerIsLightByDefaultAndCatastroLinesOnlyWhenAsked`: falló antes del cambio por recibir 512 en la capa aérea; pasó después y comprueba IGN=256, PNOA=256 y Catastro=512.
- Suite `:app:testDevDebugUnitTest`: correcta.
- `:app:assembleDevDebug` y `:app:installDevDebug`: correctos; APK `0.3.0-dev` instalada en `emulator-5554`, conservando los datos locales.
- `:app:lintDevDebug`: no se pudo cerrar correctamente en este entorno. El intento de proporcionar el SDK temporalmente con `local.properties` produjo `PropertyEscape`; se retiró ese archivo, pero Lint siguió reutilizando un informe/modelo anterior con el mismo diagnóstico. Los 37 avisos reportados son preexistentes; no se identificó un aviso en los dos archivos cambiados. Pendiente repetir Lint desde un entorno Android/Gradle limpio.

## Siguiente revisión con el propietario

- Decidir si “Foto aérea” debe ser la capa inicial al ubicar una parcela o continuar siendo una opción explícita para cuidar datos móviles.
- Repetir la prueba visual con DNS funcional y confirmar nitidez, cobertura de PNOA y encuadre en un dispositivo conectado.
- Añadir un estado visible de conexión/fallo de mosaicos si se confirma que los errores de teselas quedan silenciosos en dispositivos reales.
