# Fase 18 — mapa y geometrías de parcelas

Fecha: 2026-09-24. Estado: propuesta concreta para revisión, sin implementación todavía.
Rama: `codex/phase18-parcel-map`, creada desde `dcb63c11` de la fase 17.

## Objetivo y autorización

El propietario solicita continuar hasta dejar cerrado el trabajo de la fase 18.
Se prepara esta fase sobre la importación de Catastro ya validada. La autorización para
avanzar no constituye evidencia de las pruebas físicas pendientes ni permite declarar
aprobado el Gate 16. Los resultados técnicos y la aceptación física se registrarán por separado.

Éxito: importar una parcela real, cerrar la aplicación, activar modo avión y volver a
ver y seleccionar su geometría desde la finca y desde el detalle de la parcela.

## Diseño recomendado

MapLibre Native dentro de Jetpack Compose, con geometría GeoJSON local como fuente
del mapa. Un estilo local incluido en la APK permite representar parcelas sin depender
de una URL de estilos, glifos, imágenes remotas o servicios de Catastro.

Con conexión se ofrece una base ortofotográfica oficial IGN/PNOA y una capa catastral
para orientación y selección. Las atribuciones permanecen visibles. Antes de integrar
el servicio se comprobarán sus capacidades, URL HTTPS y condiciones de uso vigentes.

Sin conexión se conservan límites, selección, encuadre, nombre y superficies. No se
promete fotografía aérea sin conexión: las imágenes base que no estén disponibles se
sustituyen por el fondo local, con un mensaje explícito. Descargar regiones completas
para fotografía sin conexión requeriría un alcance adicional de almacenamiento y licencias.

Alternativas consideradas: un Canvas propio exigiría implementar proyección, cámara y
selección que ya resuelve MapLibre; una página web embebida añadiría un segundo entorno
de ejecución. La solución nativa sigue el contrato de mapas aprobado.

## Flujos

1. **Mapa de finca:** acceso desde la finca; muestra sus parcelas activas con geometría,
   encuadra sus límites y permite seleccionar una para abrir su detalle. Las parcelas
   sin geometría se contabilizan con un mensaje, sin inventar ubicaciones.
2. **Detalle de parcela:** muestra su geometría guardada y acceso al mapa ampliado.
   Superficie gestionada y catastral se presentan por separado; un valor ausente no se
   sustituye por el otro.
3. **Referencia catastral:** conserva el flujo de fase 17, usando la misma representación
   cartográfica para revisar el candidato antes de confirmar.
4. **Selección visual:** mapa desplazable con zoom, capa catastral y consulta explícita
   de una zona pequeña alrededor del punto seleccionado. Se muestran los candidatos y
   se exige seleccionar uno; una consulta nunca guarda datos automáticamente.
5. **Archivo GML:** selector de documentos Android, lectura acotada, validación y
   transformación del CRS dentro del adaptador. Si contiene varias parcelas, elección
   explícita de una antes de entrar en el mismo formulario de confirmación.
6. **Duplicado:** ofrece abrir la parcela existente en lugar de crear otra.

La navegación raíz permanece `Inicio · Mi Olivar · Registrar (+) · Calendario · Perfil`.
La ubicación del usuario es opcional; el flujo debe funcionar sin permiso de localización.

## Límites de arquitectura

- `domain/registry`: candidato neutral con proveedor, identidad externa, geometría WGS84,
  superficie de origen, fecha de obtención y metadatos de procedencia.
- Adaptador Catastro WFS: consulta por referencia y por área pequeña; XML queda dentro
  del adaptador. Límite espacial inferior al máximo oficial, límites de tamaño y timeout,
  cancelación de resultados obsoletos y ninguna descarga territorial automática.
- Adaptador de archivo GML: produce el mismo candidato; no precisa consultar la red.
- Geometría: Polygon/MultiPolygon con huecos, CRS explícito, coordenadas finitas, anillos
  cerrados y validación topológica. Rechazo visible de geometrías que no se puedan
  normalizar de forma segura. No se altera destructivamente el contorno maestro.
- CRS de archivo: WGS84 y los CRS españoles documentados y verificados mediante fixtures,
  incluidos UTM y Canarias. Los CRS no soportados se rechazan con explicación.
- Room: procedencia y fecha de importación persistidas con migración aditiva y pruebas.
  UUID, pertenencia a finca y outbox se conservan en una transacción local.
- Historial: importar o visualizar no reescribe snapshots de campañas ni sustituye
  automáticamente una geometría guardada por otra nueva del proveedor.
- MapLibre: ciclo de vida ligado a la pantalla, actualización de fuentes locales,
  selección inequívoca, botón de encuadre y estado de error recuperable.

## Presentación

Mantener crema, oliva y tipografía del sistema actual. Límites con trazo visible,
parcela seleccionada con relleno y contorno diferenciados, leyenda y ficha inferior
con nombre/referencia/superficies. Controles con etiquetas accesibles y tamaño táctil
adecuado. Evitar que las hojas y botones impidan manipular el mapa en pantallas pequeñas.

## Verificación requerida

- JVM: CRS y ejes con fixtures conocidos, límites de consulta, huecos, multipartes,
  autointersecciones, XML inseguro, archivos inválidos y selección espacial.
- Room: migración desde versión anterior; geometría y procedencia tras reapertura;
  duplicados, transacción y preservación del histórico.
- Compose: navegación desde finca/parcela, estados vacíos/error, selección de candidato,
  confirmación explícita y apertura del duplicado.
- Emulador con red: consulta visual y por referencia de parcela oficial; archivo GML;
  renderizado MapLibre y captura del contorno sobre la base cartográfica.
- Gate 18: importar, forzar cierre, activar modo avión, reiniciar y comprobar la
  geometría visible/seleccionable y sus datos sin recurrir al servicio externo.
- Regresión: CI Android completo y capturas en tamaños de pantalla y texto existentes.
- Dispositivo físico: registrar modelo, Android, APK/commit y resultado. Nunca sustituir
  esta evidencia por un resultado de emulador.

## Entrega y secuencia

Primero candidato/adaptadores y validación, después persistencia/migración, mapa local,
descubrimiento visual y archivo, y finalmente pruebas integradas y APK. PR apilado sobre
la fase 17. No fusionar la cadena mientras los gates previos sigan pendientes.

Quedan fuera de esta fase analítica histórica (19), servicios meteorológicos (20),
descargas masivas, datos de propietarios y edición manual de lindes no especificada
en el alcance de fase 18.

## Referencias

- Contrato del repositorio: `docs/03-maps/CADASTRE-CONTRACT-RC1.md`.
- Roadmap: `docs/07-plans/ROADMAP-RC1.2.md`, fase 18.
- MapLibre: https://maplibre.org/maplibre-native/android/examples/geojson-guide/
- Catastro: https://www.catastro.hacienda.gob.es/webinspire/index.html
- PNOA: https://pnoa.ign.es/pnoa-imagen/ortofotos-pnoa-maxima-actualidad
